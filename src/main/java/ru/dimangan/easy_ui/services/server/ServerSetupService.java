package ru.dimangan.easy_ui.services.server;

import com.jcraft.jsch.JSchException;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.config.JschProperties;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.dto.internal.SFTP;
import ru.dimangan.easy_ui.dto.internal.Shell;
import ru.dimangan.easy_ui.enums.ServerSetupState;
import ru.dimangan.easy_ui.enums.ServerStatus;
import ru.dimangan.easy_ui.exceptions.ServerNotFoundException;
import ru.dimangan.easy_ui.services.UserService;
import ru.dimangan.easy_ui.services.secured.SecureFTPService;
import ru.dimangan.easy_ui.services.secured.SecureShellService;
import ru.dimangan.easy_ui.services.xui.XUIAuthService;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class ServerSetupService {
    private final JschProperties jschProperties;

    private final ServerRepository serverRepository;

    private final SecureShellService secureShellService;
    private final SecureFTPService secureFTPService;
    private final XUIAuthService xuiAuthService;
    private final UserService userService;

    /**
     * Метод для настройки конкретного сервера по его идентификатору
     * @param serverId идентификатор сервера для настройки
     */
    public void setupServer(@Nonnull Long serverId){
        Server server = serverRepository.findById(serverId).orElseThrow(ServerNotFoundException::new);
        if(!List.of(ServerStatus.WAITING_FOR_SETUP, ServerStatus.SETUP_FAILED).contains(server.getStatus())){
            return;
        }
        try {
            log.info("[SETUP] {}: Запущена настройка сервера. Текущий статус [{}]", server.getDomain(), server.getSetupState().getDescription());
            server.setStatus(ServerStatus.SETUP_IN_PROGRESS);
            serverRepository.save(server);

            setupServer(server)
                    .whenComplete((currentServerSetupState, e) -> setupServerManageState(server, e));
        }
        catch (Exception e) {
            setupServerManageState(server, e);
        }
    }

    /**
     * Обработка состояния настройки сервера
     * @param server настраиваемый сервер
     * @param throwable ошибка при настройке
     */
    private void setupServerManageState(@Nonnull Server server, Throwable throwable){
        log.info("[SETUP] {}: Настройка сервера закончена с текущим статусом [{}]", server.getDomain(), server.getSetupState().getDescription());
        if(throwable != null){
            log.error("[SETUP] {}: Во время настройки сервера произошла ошибка:", server.getDomain(), throwable);
            server.setStatus(ServerStatus.SETUP_FAILED);
            serverRepository.save(server);
        }
    }

    /**
     * Последовательная настройка сервера
     * @param server сервер для настройки
     * @return CompletableFuture со статусом с которым закончилась настройка сервера
     */
    private CompletableFuture<ServerSetupState> setupServer(@Nonnull Server server){
        return serverSetupNextAction(server)
                .thenCompose(newServerSetupState -> {
                    server.setSetupState(newServerSetupState);
                    if(newServerSetupState == ServerSetupState.CONFIGURED){
                        server.setStatus(ServerStatus.WORKING);
                        serverRepository.save(server);
                        return CompletableFuture.completedFuture(server.getSetupState());
                    }
                    else{
                        serverRepository.save(server);
                        return setupServer(server);
                    }
                });
    }

    /**
     * Выполнение следующего шага по настройке сервера
     * @param server сервер для настройки
     * @return CompletableFuture с новым статусом настройки после выполнения этого шага
     */
    private CompletableFuture<ServerSetupState> serverSetupNextAction(@Nonnull Server server) {
        AtomicReference<Shell> shell = new AtomicReference<>(null);
        AtomicReference<SFTP> sftp = new AtomicReference<>(null);
        try {
            Pair<Shell, SFTP> securedChannels = setupServerSecuredChannels(server);
            shell.set(securedChannels.getLeft());
            sftp.set(securedChannels.getRight());
            CompletableFuture<ServerSetupState> nextAction = switch (server.getSetupState()){
                case NOT_CONFIGURED -> setupServerAddSudoUser(server, shell.get()).orTimeout(20, TimeUnit.SECONDS);
                case SUDO_USER_ADDED -> setupServerImplementAuthorizedKeys(server, sftp.get()).orTimeout(1, TimeUnit.MINUTES);
                case AUTHORIZED_KEYS_ADDED -> setupServerEnableUfw(server, shell.get()).orTimeout(20, TimeUnit.SECONDS);
                case FIREWALL_ENABLED -> setupServerUpdate(server, shell.get()).orTimeout(2, TimeUnit.MINUTES);
                case UPDATED -> setupServerInstallNginx(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case NGINX_INSTALLED -> setupServerInstallSnapd(server, shell.get()).orTimeout(20, TimeUnit.SECONDS);
                case SNAPD_INSTALLED -> setupServerInstallCertbot(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case CERTBOT_INSTALLED -> setupServerLinkCertbot(server, shell.get()).orTimeout(20, TimeUnit.SECONDS);
                case CERTBOT_LINKED -> setupServerIssueCertificate(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case CERTIFICATE_ISSUED -> setupServerConfigureNginx(server, shell.get(), sftp.get()).orTimeout(1, TimeUnit.MINUTES);
                case NGINX_CONFIGURED -> setupServerNginxHtml(server, shell.get(), sftp.get()).orTimeout(1, TimeUnit.MINUTES);
                case NGINX_HTML_ADDED -> setupServerRestartNginx(server, shell.get()).orTimeout(20, TimeUnit.SECONDS);
                case NGINX_RESTARTED -> setupServerInstallXUI(server, shell.get()).orTimeout(2, TimeUnit.MINUTES);
                case XUI_INSTALLED -> setupServerConfigureSslXUI(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case XUI_SSL_CONFIGURED -> setupServerConfigureAdminXUI(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case XUI_ADMIN_ADDED -> setupServerLoadSettingsXUI(server, shell.get()).orTimeout(1, TimeUnit.MINUTES);
                case XUI_SETTING_LOADED -> setupServerUfwConfigurePorts(server, shell.get(), List.of(22, 80, 443, server.getXuiPort()), true).orTimeout(20, TimeUnit.SECONDS);
                case DEFAULT_PORTS_OPENED -> setupServerIssueXUIToken(server).orTimeout(20, TimeUnit.SECONDS);
                case XUI_TOKEN_ISSUED, CONFIGURED -> CompletableFuture.completedFuture(ServerSetupState.CONFIGURED);
            };
            return nextAction.handle((currentServerSetupState, e) -> {
                if(shell.get() != null){
                    shell.get().close();
                }
                if(sftp.get() != null){
                    sftp.get().close();
                }
                if(e != null){
                    throw new CompletionException(e);
                }
                return currentServerSetupState;
            });
        }
        catch (Exception e){
            if(shell.get() != null){
                shell.get().close();
            }
            if(sftp.get() != null){
                sftp.get().close();
            }
            throw new CompletionException(e);
        }
    }

    /**
     * Получение защищенных каналов соединения с сервером в зависимости от текущего шага в настройке сервера
     * @param server для получения защищенных каналов, необходимых для выполнения следующего шага по настройке сервера
     * @return Пара Shell и SFTP для подключений, если канал не нужен, то null
     */
    private Pair<Shell, SFTP> setupServerSecuredChannels(@Nonnull Server server) throws JSchException {
        ServerSetupState currentServerSetupState = server.getSetupState();
        if(List.of(ServerSetupState.XUI_TOKEN_ISSUED, ServerSetupState.CONFIGURED).contains(currentServerSetupState)){
            return Pair.of(null, null);
        }
        boolean needsShell = !List.of(ServerSetupState.SUDO_USER_ADDED, ServerSetupState.DEFAULT_PORTS_OPENED).contains(server.getSetupState());
        boolean needsSftp = List.of(ServerSetupState.SUDO_USER_ADDED, ServerSetupState.CERTIFICATE_ISSUED, ServerSetupState.NGINX_CONFIGURED).contains(currentServerSetupState);
        Shell shell = needsShell? secureShellService.openShell(server, jschProperties.isUseLogger()): null;
        SFTP sftp = needsSftp? secureFTPService.openSftp(server, jschProperties.isUseLogger()): null;
        return Pair.of(shell, sftp);
    }

    /**
     * Добавление нового sudo пользователя, если изначально заданный пользователь это root
     * @param server сервер для настройки
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerAddSudoUser(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется добавление sudo пользователя", server.getDomain());
        if(!Objects.equals(server.getUsername(), "root")){
            return CompletableFuture.completedFuture(ServerSetupState.SUDO_USER_ADDED);
        }
        Map<String, String> userInput = new HashMap<>();
        //Имя пользователя 32 символа, дефолтный максимум
        String username = String.format("user_%s", UUID.randomUUID().toString().replace("-", "")).substring(0, 12);
        String password = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        userInput.put("New password:", password);
        userInput.put("Retype new password:", password);
        userInput.put("Full Name []:", "");
        userInput.put("Room Number []:", "");
        userInput.put("Work Phone []:", "");
        userInput.put("Home Phone []:", "");
        userInput.put("Other []:", "");
        userInput.put("Is the information correct?", "");
        return secureShellService.execute(shell, String.format("adduser %s", username), userInput, Integer.class, jschProperties.isUseLogger())
                .thenCompose(userAddCapture -> {
                    if(!(userAddCapture instanceof Integer exitCodeUserAdd)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCodeUserAdd != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось создать нового пользователя. ExitCode: %s", exitCodeUserAdd)));
                    }
                    return secureShellService.execute(shell, String.format("sudo usermod -aG sudo %s", username), Integer.class, jschProperties.isUseLogger())
                            .thenApply(usermodSudoCapture -> {
                                if(!(usermodSudoCapture instanceof Integer exitCodeUsermodSudo)){
                                    throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                                }
                                if(exitCodeUsermodSudo != 0){
                                    throw new CompletionException(new RuntimeException(String.format("Не удалось установить новому пользователю права sudo. ExitCode: %s", exitCodeUsermodSudo)));
                                }
                                server.setUsername(username);
                                server.setPassword(password);
                                server.setKeyAdded(false);
                                serverRepository.save(server);
                                return ServerSetupState.SUDO_USER_ADDED;
                            });
                });
    }

    /**
     * Добавление публичного ключа на сервер в authorized_keys для последующего подключения через приватный ключ, а не логин/пароль
     * @param server сервер для подключения
     * @param sftp sftp для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerImplementAuthorizedKeys(@Nonnull Server server, @Nonnull SFTP sftp){
        log.info("[SETUP] {}: Выполняется добавление public key в список authorized_keys", server.getDomain());
        if(server.isKeyAdded()){
            return CompletableFuture.completedFuture(ServerSetupState.AUTHORIZED_KEYS_ADDED);
        }

        String sshDirectory = String.format("%s/.ssh", sftp.getHome());
        String authorizedKeysPath = String.format("%s/authorized_keys", sshDirectory);

        if(!secureFTPService.exists(sftp, sshDirectory)){
            try {
                secureFTPService.mkDir(sftp, sshDirectory);
            }
            catch (Exception e) {
                throw new CompletionException(new RuntimeException("Не удалось создать .ssh директорию для пользователя", e));
            }
        }

        CompletableFuture<File> authorizedKeysFileFuture;
        if(secureFTPService.exists(sftp, authorizedKeysPath)){
            authorizedKeysFileFuture = secureFTPService.getFile(sftp, authorizedKeysPath);
        }
        else{
            try {
                authorizedKeysFileFuture = CompletableFuture.completedFuture(File.createTempFile(UUID.randomUUID().toString(), null));
            }
            catch (Exception e) {
                throw new CompletionException(new RuntimeException("Не удалось создать временный файл", e));
            }
        }

        return authorizedKeysFileFuture.thenCompose(authorizedKeysFile -> {
            String serverPublicKey = server.getSecuredKey().getPublicKey();
            String authorizedKeysFileAsString;
            try {
                authorizedKeysFileAsString = Files.readString(authorizedKeysFile.toPath());
            }
            catch (Exception e) {
                throw new CompletionException(new RuntimeException("Не удалось прочитать загруженный файл", e));
            }

            if(authorizedKeysFileAsString.contains(serverPublicKey)){
                server.setKeyAdded(true);
                serverRepository.save(server);
                return CompletableFuture.completedFuture(ServerSetupState.AUTHORIZED_KEYS_ADDED);
            }

            try(FileWriter fileWriter = new FileWriter(authorizedKeysFile, true)){
                fileWriter.append(String.format("\n%s\n", serverPublicKey));
            }
            catch (Exception e){
                throw new CompletionException(new RuntimeException("Ошибка при модификации файла authorized_keys", e));
            }
            return secureFTPService.sendFile(sftp, authorizedKeysFile, authorizedKeysPath)
                    .thenApply(fileSent -> {
                        server.setKeyAdded(true);
                        serverRepository.save(server);
                        return ServerSetupState.AUTHORIZED_KEYS_ADDED;
                    });
        });
    }

    /**
     * Включение фаервола на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerEnableUfw(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется включение фаервола", server.getDomain());
        //80 порт нужен для получения SSL сертификата
        CompletableFuture<ServerSetupState> openSshPort = server.getOpenPorts().contains(server.getPort()) && server.getOpenPorts().contains(80)?
                CompletableFuture.completedFuture(server.getSetupState()): setupServerUfwConfigurePorts(server, shell, List.of(server.getPort(), 80), true);
        return openSshPort.thenCompose(serverSetupState -> {
            Map<String, String> userInput = new HashMap<>();
            userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
            userInput.put("Command may disrupt existing ssh connections. Proceed with operation (y|n)?", "y");
            return secureShellService.execute(shell, "sudo ufw enable", userInput, Integer.class, jschProperties.isUseLogger())
                    .thenApply(exitCodeCapture -> {
                        if(!(exitCodeCapture instanceof Integer exitCode)){
                            throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                        }
                        if(exitCode != 0){
                            throw new CompletionException(new RuntimeException(String.format("Не удалось включить фаервол. ExitCode: %s", exitCode)));
                        }
                        return ServerSetupState.FIREWALL_ENABLED;
                    });
        });
    }

    /**
     * Изменение доступности порта на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @param port порт для изменения доступности
     * @param allowed открыть или закрыть порт
     * @return CompletableFuture с новым статусом настройки сервера
     */
    public CompletableFuture<ServerSetupState> setupServerUfwConfigurePort(@Nonnull Server server, @Nonnull Shell shell, int port, boolean allowed){
        log.info("[SETUP] {}: Выполняется {} порта в фаерволе", server.getDomain(), allowed? "открытие": "закрытие");
        if(!allowed && port == server.getPort()){
            throw new CompletionException(new RuntimeException("Нельзя закрыть порт ssh"));
        }
        return secureShellService.execute(shell, String.format("sudo ufw %s allow %s", allowed? "": "delete", port),
                        new HashMap<>(Map.of(SecureShellService.sudoPasswordTrigger, server.getPassword())), Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось %s порт в фаерволе. ExitCode: %s", allowed? "открыть": "закрыть", exitCode)));
                    }
                    server.getOpenPorts().add(port);
                    serverRepository.save(server);
                    return server.getSetupState();
                });
    }

    /**
     * Изменение доступности порта на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @param ports порты для изменения доступности
     * @param allowed открыть или закрыть порт
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerUfwConfigurePorts(@Nonnull Server server, @Nonnull Shell shell, List<Integer> ports, boolean allowed){
        if(ports.isEmpty()){
            return CompletableFuture.completedFuture(ServerSetupState.DEFAULT_PORTS_OPENED);
        }
        int currentPort = ports.get(0);
        List<Integer> remainingPorts = ports.size() > 1? ports.subList(1, ports.size()): Collections.emptyList();
        return setupServerUfwConfigurePort(server, shell, currentPort, allowed)
                .thenCompose(serverSetupState -> setupServerUfwConfigurePorts(server, shell, remainingPorts, allowed));
    }

    /**
     * Установка обновлений пакетов на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerUpdate(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется обновление пакетов сервера", server.getDomain());
        return secureShellService.execute(shell, "sudo apt update", new HashMap<>(Map.of(SecureShellService.sudoPasswordTrigger, server.getPassword())), Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось выполнить обновление сервера. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.UPDATED;
                });
    }

    /**
     * Установка nginx на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerInstallNginx(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется установка nginx", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.putAll(SecureShellService.serviceInstallTrigger);
        return secureShellService.execute(shell, "sudo apt install nginx -y", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось установить nginx. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.NGINX_INSTALLED;
                });
    }

    /**
     * Установка snapd на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerInstallSnapd(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется установка snapd", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.putAll(SecureShellService.serviceInstallTrigger);
        return secureShellService.execute(shell, "sudo apt install snapd -y", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось установить snapd. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.SNAPD_INSTALLED;
                });
    }

    /**
     * Установка certbot на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerInstallCertbot(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется установка certbot", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.putAll(SecureShellService.serviceInstallTrigger);
        return secureShellService.execute(shell, "sudo snap install --classic certbot", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось установить certbot. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.CERTBOT_INSTALLED;
                });
    }

    /**
     * Создание линка для certbot на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerLinkCertbot(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Создание ссылки на certbot", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        return secureShellService.execute(shell, "sudo ln -s /snap/bin/certbot /usr/local/bin/certbot", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    //0 - Файл создан, 1 - Файл уже существует
                    if(exitCode != 0 && exitCode != 1){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось создать ссылку на certbot. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.CERTBOT_LINKED;
                });
    }

    /**
     * Выпуск сертификата на доменное имя сервера
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerIssueCertificate(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется выпуск сертификата для доменного имени", server.getDomain());
        if(StringUtils.isEmpty(server.getDomain())){
            throw new CompletionException(new RuntimeException("Не указан домен для сервера"));
        }
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.put("You must agree in order to register with the ACME server. Do you agree?", "Y");
        userInput.put("Enter email address or hit Enter to skip.", "");
        userInput.put("Please enter the domain name(s) you would like on your certificate", server.getDomain());
        userInput.put("Select the appropriate number", "1");
        return secureShellService.execute(shell, "sudo certbot certonly --nginx", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось выпустить сертификат. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.CERTIFICATE_ISSUED;
                });
    }

    /**
     * Настройка nginx на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @param sftp sftp для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerConfigureNginx(@Nonnull Server server, @Nonnull Shell shell, @Nonnull SFTP sftp){
        log.info("[SETUP] {}: Выполняется настройка nginx", server.getDomain());
        File nginxConfig = new File("setup/nginx.config");
        if(!nginxConfig.exists()){
            throw new CompletionException(new RuntimeException("Файл конфигурации nginx не найден"));
        }
        Map<String, String> userInput = new HashMap<>(Map.of(SecureShellService.sudoPasswordTrigger, server.getPassword()));

        return secureShellService.execute(shell, String.format("sudo chown root:%s /etc/nginx/sites-available/default", server.getUsername()), userInput, Integer.class, jschProperties.isUseLogger())
                .thenCompose(chownDoneCapture -> {
                    if(!(chownDoneCapture instanceof Integer chownDone)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(chownDone != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось сменить владельца конфигурации nginx. ExitCode: %s", chownDone)));
                    }
                    return secureShellService.execute(shell, "sudo chmod 664 /etc/nginx/sites-available/default", userInput, Integer.class, jschProperties.isUseLogger())
                            .thenCompose(chmodDoneCapture -> {
                                if(!(chmodDoneCapture instanceof Integer chmodDone)){
                                    throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                                }
                                if(chmodDone != 0){
                                    throw new CompletionException(new RuntimeException(String.format("Не удалось передать права на редактирование конфигурации nginx. ExitCode: %s", chmodDone)));
                                }

                                File modifiedNginxConfig;
                                try {
                                    String nginxConfigString = Files.readString(nginxConfig.toPath());
                                    String modifiedNginxConfigString = nginxConfigString
                                            .replace("$fullchainpath", String.format("/etc/letsencrypt/live/%s/fullchain.pem", server.getDomain()))
                                            .replace("$privkeypath", String.format("/etc/letsencrypt/live/%s/privkey.pem", server.getDomain()))
                                            .replace("$secretpath", String.format("/api/v1/user/%s", Base64.getEncoder().encodeToString(server.getUsername().getBytes()).substring(0, 10)));
                                    modifiedNginxConfig = File.createTempFile(UUID.randomUUID().toString(), null);
                                    FileUtils.writeStringToFile(modifiedNginxConfig, modifiedNginxConfigString, StandardCharsets.UTF_8);
                                }
                                catch (Exception e) {
                                    throw new CompletionException(e);
                                }
                                return secureFTPService.sendFile(sftp, modifiedNginxConfig, "/etc/nginx/sites-available/default")
                                        .thenApply(configSent -> {
                                            if(!configSent){
                                                throw new CompletionException(new RuntimeException("Не удалось выполнить настройку nginx"));
                                            }
                                            return ServerSetupState.NGINX_CONFIGURED;
                                        });

                            });
                });
    }

    /**
     * Загрузка сайта заглушки (index.html) на сервер
     * @param server сервер для обновления
     * @param sftp sftp для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerNginxHtml(@Nonnull Server server, @Nonnull Shell shell,  @Nonnull SFTP sftp){
        log.info("[SETUP] {}: Выполняется настройка сайта-заглушки", server.getDomain());
        File html = new File("setup/index.html");
        if(!html.exists()){
            throw new CompletionException(new RuntimeException("Сайт-заглушка index.html не найден"));
        }
        Map<String, String> userInput = new HashMap<>(Map.of(SecureShellService.sudoPasswordTrigger, server.getPassword()));

        return secureShellService.execute(shell, String.format("sudo chown root:%s /var/www/html", server.getUsername()), userInput, Integer.class, jschProperties.isUseLogger())
                .thenCompose(chownDoneCapture -> {
                    if(!(chownDoneCapture instanceof Integer chownDone)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(chownDone != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось сменить владельца сайта-заглушки. ExitCode: %s", chownDone)));
                    }
                    return secureShellService.execute(shell, "sudo chmod 775 /var/www/html", userInput, Integer.class, jschProperties.isUseLogger())
                            .thenCompose(chmodDoneCapture -> {
                                if(!(chmodDoneCapture instanceof Integer chmodDone)){
                                    throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                                }
                                if(chmodDone != 0){
                                    throw new CompletionException(new RuntimeException(String.format("Не удалось передать права на редактирование сайта-заглушки. ExitCode: %s", chmodDone)));
                                }
                                return secureFTPService.sendFile(sftp, html, "/var/www/html/index.html")
                                        .thenApply(htmlSent -> {
                                            if(!htmlSent){
                                                throw new CompletionException(new RuntimeException("Не удалось выполнить настройку сайта-заглушки"));
                                            }
                                            return ServerSetupState.NGINX_HTML_ADDED;
                                        });

                            });
                });
    }

    /**
     * Перезагрузка nginx на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerRestartNginx(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется перезагрузка nginx", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.put("Password:", server.getPassword());
        return secureShellService.execute(shell, "sudo systemctl restart nginx", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось перезагрузить nginx. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.NGINX_RESTARTED;
                });
    }

    /**
     * Установка панели 3x-ui на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerInstallXUI(@Nonnull Server server, @Nonnull Shell shell){
        log.info("[SETUP] {}: Выполняется установка панели 3x-ui", server.getDomain());
        Map<String, String> userInput = new HashMap<>();
        userInput.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        userInput.putAll(SecureShellService.serviceInstallTrigger);
        return secureShellService.execute(shell, "curl -Ls https://raw.githubusercontent.com/mhsanaei/3x-ui/master/install.sh | sudo bash", userInput, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось установить панель 3x-ui. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.XUI_INSTALLED;
                });
    }

    /**
     * Установка ssl сертификата для панели 3x-ui на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerConfigureSslXUI(@Nonnull Server server, @Nonnull Shell shell) {
        log.info("[SETUP] {}: Выполняется настройка ssl сертификата для панели 3x-ui", server.getDomain());
        Map<String, String> installSslCert = new HashMap<>();
        installSslCert.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        installSslCert.put("Please enter your selection [0-28]:", "20");
        installSslCert.put("Set Cert paths for the panel", "5");
        installSslCert.put("Enter custom certificate file paths (e.g. certbot, /etc/letsencrypt/...)", "2");
        installSslCert.put("Certificate file path (fullchain):", String.format("/etc/letsencrypt/live/%s/fullchain.pem", server.getDomain()));
        installSslCert.put("Private key file path:", String.format("/etc/letsencrypt/live/%s/privkey.pem", server.getDomain()));
        installSslCert.put("Press enter to return to the main menu:", "\n0");
        return secureShellService.execute(shell, "sudo x-ui", installSslCert, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось установить SSL сертификат панели 3x-ui. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.XUI_SSL_CONFIGURED;
                });
    }

    /**
     * Создание админа для панели 3x-ui на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerConfigureAdminXUI(@Nonnull Server server, @Nonnull Shell shell) {
        log.info("[SETUP] {}: Выполняется настройка администратора панели 3x-ui", server.getDomain());
        Map<String, String> changePanelUser = new HashMap<>();
        changePanelUser.put(SecureShellService.sudoPasswordTrigger, server.getPassword());
        changePanelUser.put("Please enter your selection [0-28]:", "7");
        changePanelUser.put("Are you sure to reset the username and password of the panel?", "y");
        changePanelUser.put("Please set the login username [default is a random username]:", server.getUsername());
        changePanelUser.put("Please set the login password [default is a random password]:", server.getPassword());
        changePanelUser.put("Do you want to disable currently configured two-factor authentication? (y/n):", "n");
        changePanelUser.put("Restarting the panel will also restart xray [Default y]:", "");
        changePanelUser.put("Press enter to return to the main menu:", "\n0");
        return secureShellService.execute(shell, "sudo x-ui", changePanelUser, Integer.class, jschProperties.isUseLogger())
                .thenApply(exitCodeCapture -> {
                    if(!(exitCodeCapture instanceof Integer exitCode)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(exitCode != 0){
                        throw new CompletionException(new RuntimeException(String.format("Не удалось задать admin пользователя для панели 3x-ui. ExitCode: %s", exitCode)));
                    }
                    return ServerSetupState.XUI_ADMIN_ADDED;
                });
    }

    /**
     * Получение настроек для подключения к панели 3x-ui на сервере
     * @param server сервер для обновления
     * @param shell shell для подключения
     * @return CompletableFuture с новым статусом настройки сервера
     */
    private CompletableFuture<ServerSetupState> setupServerLoadSettingsXUI(@Nonnull Server server, @Nonnull Shell shell) {
        log.info("[SETUP] {}: Выполняется загрузка настроек панели 3x-ui", server.getDomain());
        shell.setOutputBufferSize(512);
        return secureShellService.execute(shell, "sudo x-ui settings", new HashMap<>(Map.of(SecureShellService.sudoPasswordTrigger, server.getPassword())), String.class, jschProperties.isUseLogger())
                .thenApply(shellResponseCapture -> {
                    if(!(shellResponseCapture instanceof String shellResponse)){
                        throw new CompletionException(new ClassCastException("Возвращенный ответ исполнителя команды не поддерживается в текущем методе"));
                    }
                    if(StringUtils.isEmpty(shellResponse)){
                        throw new CompletionException(new RuntimeException("Не были получены текущие настройки панели 3x-ui"));
                    }
                    String[] responseLines = shellResponse.split("\n");
                    Integer xuiPort = null;
                    String xuiPath = null;
                    for(String responseLine: responseLines){
                        if(responseLine.contains("port")){
                            String portString = StringUtils.deleteWhitespace(responseLine.substring(responseLine.indexOf(":") + 1));
                            xuiPort = Integer.parseInt(portString);
                        }
                        else if(responseLine.contains("webBasePath")){
                            xuiPath = StringUtils.deleteWhitespace(responseLine.substring(responseLine.indexOf(":") + 1).replace("/", ""));
                            //Формат вывода bash скрипта 3x-ui для окрашивания текста в цвет
                            if(xuiPath.contains("\u001b")){
                                xuiPath = xuiPath.substring(0, xuiPath.indexOf("\u001b"));
                            }
                        }
                    }
                    if(xuiPort == null){
                        throw new CompletionException(new RuntimeException("Не удалось получить порт панели 3x-ui"));
                    }
                    if(StringUtils.isEmpty(xuiPath)){
                        throw new CompletionException(new RuntimeException("Не удалось получить путь панели 3x-ui"));
                    }
                    server.setXuiPort(xuiPort);
                    server.setXuiPath(xuiPath);
                    serverRepository.save(server);
                    return ServerSetupState.XUI_SETTING_LOADED;
                });
    }

    private CompletableFuture<ServerSetupState> setupServerIssueXUIToken(@Nonnull Server server) {
        log.info("[SETUP] {}: Выполняется выпуск api токена для панели 3x-ui", server.getDomain());
        if(StringUtils.isNotEmpty(server.getXuiToken())){
            userService.addAllUsersOnServer(server);
            return CompletableFuture.completedFuture(ServerSetupState.XUI_TOKEN_ISSUED);
        }
        AtomicReference<List<String>> csrfCookies = new AtomicReference<>(null);
        Consumer<HttpHeaders> csrfCookiesConsumer = httpHeaders -> csrfCookies.set(httpHeaders.get(HttpHeaders.SET_COOKIE));
        return xuiAuthService.getCsrfToken(server, csrfCookiesConsumer)
                .thenCompose(csrfResponse -> {
                    if(!csrfResponse.isSuccess()){
                        throw new CompletionException(new RuntimeException("Не удалось получить csrf токен"));
                    }
                    Consumer<HttpHeaders> loginHeaders = httpHeaders -> {
                        httpHeaders.add("X-CSRF-Token", csrfResponse.getObj());
                        httpHeaders.add("Cookie", csrfCookies.get().get(0));
                    };
                    AtomicReference<List<String>> loginCookies = new AtomicReference<>(null);
                    Consumer<HttpHeaders> loginCookiesConsumer = httpHeaders -> loginCookies.set(httpHeaders.get(HttpHeaders.SET_COOKIE));
                    return xuiAuthService.login(server, loginHeaders, loginCookiesConsumer)
                            .thenCompose(loginResponse -> {
                                if(!loginResponse.isSuccess()){
                                    throw new CompletionException(new RuntimeException("Не удалось авторизоваться в панели 3x-ui"));
                                }
                                Consumer<HttpHeaders> createTokenHeaders = httpHeaders -> {
                                    httpHeaders.add("X-CSRF-Token", csrfResponse.getObj());
                                    httpHeaders.add("Cookie", loginCookies.get().get(0));
                                };
                                return xuiAuthService.createApiToken(server, createTokenHeaders)
                                        .thenApply(tokenIssued -> {
                                            if(!tokenIssued.isSuccess()){
                                                throw new CompletionException(new RuntimeException("Не удалось выпустить токен для панели 3x-ui"));
                                            }
                                            String token = tokenIssued.getObj().get("token");
                                            server.setXuiToken(token);
                                            serverRepository.save(server);
                                            userService.addAllUsersOnServer(server);
                                            return ServerSetupState.XUI_TOKEN_ISSUED;
                                        });
                            });
                });
    }
}
