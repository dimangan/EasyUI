package ru.dimangan.easy_ui.services.secured;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.config.JschProperties;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.dto.internal.Shell;
import ru.dimangan.easy_ui.enums.ShellStatus;
import ru.dimangan.easy_ui.events.EventPublisher;
import ru.dimangan.easy_ui.events.ShellInputRequiredEvent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecureShellService {
    private final SecureSessionService secureSessionService;
    private final JschProperties jschProperties;
    private final Executor shellExecutor = Executors.newFixedThreadPool(2);

    private static final String CMD_DONE = "__CMD_DONE__";
    public static final String sudoPasswordTrigger = "[sudo] password for";
    public static final Map<String, String> serviceInstallTrigger = new HashMap<>(Map.of(
            "(Enter the items or ranges you want to select, separated by spaces.)", "",
            "Which services should be restarted?", ""));

    /**
     * Создание сессии SSH, открытие канала и подключение
     * @param server сервер для подключения
     * @param useLogger нужно ли использовать ли логгер Jsch
     * @return возвращает Shell объект, содержащий InputStream, OutputStream и ChannelShell
     * @throws JSchException В случае ошибки подключения к серверу
     */
    public Shell openShell(@NonNull Server server, boolean useLogger) throws JSchException {
        return openShell(server, useLogger, jschProperties.getOpenChannelRetryCount());
    }

    /**
     * Создание сессии SSH, открытие канала и подключение
     * @param server сервер для подключения
     * @param useLogger нужно ли использовать ли логгер Jsch
     * @param retry количество попыток при неудачном открытии
     * @return возвращает Shell объект, содержащий InputStream, OutputStream и ChannelShell
     * @throws JSchException В случае ошибки подключения к серверу
     */
    private Shell openShell(@NonNull Server server, boolean useLogger, int retry) throws JSchException {
        Session session = null;
        ChannelShell shellChannel = null;
        try{
            session = secureSessionService.session(server, useLogger);
            shellChannel = (ChannelShell) session.openChannel("shell");
            shellChannel.setPty(true);
            Shell shell = new Shell(shellChannel, shellChannel.getInputStream(), shellChannel.getOutputStream());
            shellChannel.connect(jschProperties.getChannelConnectionTimeout());
            return shell;
        }
        catch (Exception e) {
            if(shellChannel != null && shellChannel.isConnected()){
                shellChannel.disconnect();
            }
            if(session != null && session.isConnected()){
                session.disconnect();
            }
            if(retry > 0){
                return openShell(server, useLogger, retry - 1);
            }
            throw new JSchException(String.format("Не удалось открыть shell за %s попыток", jschProperties.getOpenChannelRetryCount()), e);
        }
    }

    /**
     * Выполнение команды на сервере по ssh
     * @param shell shell для подключения
     * @param command команда для выполнения
     * @param responseClass возвращаемый в объекте класс. Integer для получения exit code или String для получения всего результата вывода в shell
     * @param useLogger использовать ли логирование терминала
     * @return CompletableFuture с возвращаемым типом responseClass. Может быть String или Integer
     */
    public CompletableFuture<?> execute(@NonNull Shell shell, @NonNull String command, @NonNull Class<?> responseClass, boolean useLogger){
        return execute(shell, command, Collections.emptyMap(), responseClass, useLogger);
    }

    /**
     * Выполнение команды на сервере по ssh с аргументами вводимыми в ходе выполнения команды<br>
     * Например, при выполнении команды задается вопрос "Do you want to define server port or use default? [Y/N]",
     * Для такого случая передается аргумент для ввода на этот вопрос, например, Y
     * @param shell shell для подключения
     * @param command команда для выполнения
     * @param userInput список аргументов для ввода, ключ - строка вывода в терминал (триггер ввода), значение - ввод данных
     * @param responseClass возвращаемый в объекте класс. Integer для получения exit code или String для получения всего результата вывода в shell
     * @param useLogger использовать ли логирование терминала
     * @return CompletableFuture с возвращаемым типом responseClass. Может быть String или Integer
     */
    public CompletableFuture<?> execute(@NonNull Shell shell, @NonNull String command, @NonNull Map<String, String> userInput, @NonNull Class<?> responseClass, boolean useLogger){
        if(useLogger){
            log.info("[SETUP] Отправка команды {} с пользовательским вводом {}", command, userInput);
        }

        String uuid = UUID.randomUUID().toString().replace("-", "");
        List<String> commandParts = List.of(
                String.format("__uuid='%s';", uuid),
                String.format("%s;", command),
                "printf '\\n" + CMD_DONE + "%s:%s\\n'",
                "\"$__uuid\"",
                "\"$?\""
        );
        String modifiedCommand = String.join(" ", commandParts);

        shell.setCurrentCommandUuid(uuid);
        shell.setStatus(ShellStatus.EXECUTING);
        shell.setUserInput(userInput);
        CompletableFuture<?> response = shellResponseListener(shell, responseClass, useLogger);
        try {
            Thread.sleep(jschProperties.getShellDebounceTime());
            shell.send(modifiedCommand);
        }
        catch (Exception e) {
            shell.setStatus(ShellStatus.IDLE);
            throw new CompletionException(String.format("Ошибка при отправке команды %s в shell", modifiedCommand), e);
        }
        return response;
    }

    /**
     * Если в ходе выполнения команды на сервере нужен ручной ввод от пользователя, то вызывается обработка этого ивента
     * @param event ивент для обработки, содержит shell, в котором происходит выполнение и строку для ввода в терминал
     * @throws IOException В случае ошибки отправки ввода на сервер
     */
    @EventListener(ShellInputRequiredEvent.class)
    private void executeUserInput(@NonNull ShellInputRequiredEvent event) throws IOException{
        event.getShell().setStatus(ShellStatus.EXECUTING);
        event.getShell().send(event.getInput());
    }

    /**
     * Слушатель терминала в ходе выполнения команды, прослушивает терминал, пока не получит результат выполнения команды
     * @param shell shell, в котором происходит выполнение команды
     * @param responseClass возвращаемый в объекте класс. Integer для получения exit code или String для получения всего результата вывода в shell
     * @param useLogger нужно ли использовать логирование терминала
     * @return CompletableFuture с возвращаемым типом responseClass. Может быть String или Integer
     */
    private CompletableFuture<?> shellResponseListener(@NonNull Shell shell, @NonNull Class<?> responseClass, boolean useLogger) {
        return CompletableFuture.supplyAsync(() -> {
            if(responseClass != Integer.class && responseClass != String.class){
                throw new CompletionException(new ClassCastException("Класс ответа может быть String или Integer"));
            }
            int exitCode = -1;
            String shellResponse = null;
            if(useLogger){
                log.info("[SETUP] Запущен слушатель потока вывода для shell");
            }
            byte[] buffer = new byte[8192];
            AtomicLong lastOutputTime = new AtomicLong(Long.MAX_VALUE);
            Thread debounceThread = null;
            StringBuilder logBuilder = new StringBuilder();
            long lastLogTime = Long.MIN_VALUE;
            try {
                while(shell.getStatus().get() != ShellStatus.IDLE){
                    if(shell.getInputStream().available() > 0){
                        int c = shell.getInputStream().read(buffer);
                        String output = new String(buffer, 0, c);
                        shell.appendOutput(output);
                        lastOutputTime.set(System.currentTimeMillis());
                        if(useLogger){
                            for(int i = 0; i < output.length(); i++){
                                char l = output.charAt(i);
                                if(l == '\n'){
                                    log.info("[Jsch] {}", logBuilder);
                                    logBuilder = new StringBuilder();
                                    lastLogTime = System.currentTimeMillis();
                                }
                                else{
                                    logBuilder.append(l);
                                }
                            }
                            if(System.currentTimeMillis() - lastLogTime >= jschProperties.getShellDebounceTime()){
                                log.info("[Jsch] {}", logBuilder);
                                logBuilder = new StringBuilder();
                                lastLogTime = System.currentTimeMillis();
                            }
                        }
                        if(isCommandExecuted(shell)){
                            exitCode = getExitCode(shell);
                            shellResponse = shell.getOutput();
                            shell.reset();
                        }
                        String nextUserInput = nextUserInput(shell);
                        if(nextUserInput != null){
                            shell.setStatus(ShellStatus.INPUT_REQUIRED);
                            shell.setNextUserInput(nextUserInput);
                            shell.clearOutput();
                            debounceThread = debounceThread(shell, lastOutputTime);
                            debounceThread.start();
                        }
                    }
                }
                if(!logBuilder.isEmpty()){
                    log.info("[Jsch] {}", logBuilder);
                }
            }
            catch (Exception e) {
                shell.setStatus(ShellStatus.IDLE);
                if(!logBuilder.isEmpty()){
                    log.info("[Jsch] {}", logBuilder);
                }
                throw new CompletionException(e);
            }
            finally {
                if(debounceThread != null && debounceThread.isAlive()){
                    debounceThread.interrupt();
                }
            }
            if(useLogger){
                log.info("[SETUP] Слушатель потока вывода для shell выключен");
            }
            return responseClass == Integer.class? exitCode: shellResponse;
        }, shellExecutor);
    }

    /**
     * Создание нового потока для debounce при выводе данных в терминал при уже полученном маркере о необходимости пользовательского ввода<br>
     * При получении сигнала о том, что нужен пользовательский ввод запускается поток debounce для ожидания вывода оставшихся строк, после чего
     * будет опубликован ивент о необходимости пользовательского ввода
     * @param shell shell, в котором происходит выполнение команды
     * @param lastOutputTime время последнего вывода в терминал
     * @return новый Thread с обработкой
     */
    private Thread debounceThread(@NonNull Shell shell, @NonNull AtomicLong lastOutputTime){
        return new Thread(() -> {
            while(shell.getStatus().get() == ShellStatus.INPUT_REQUIRED){
                if(System.currentTimeMillis() - lastOutputTime.get() > jschProperties.getShellDebounceTime()){
                    EventPublisher.publishEvent(new ShellInputRequiredEvent(shell, shell.getNextUserInput()));
                    return;
                }
            }
        });
    }

    /**
     * Получение статуса выполнения команды (выполнена или еще нет)
     * @param shell shell, в котором происходит выполнение
     * @return true - команда выполнена, false - команда еще выполняется
     */
    private boolean isCommandExecuted(@NonNull Shell shell){
        return shell.getOutput().contains(String.format("%s%s", CMD_DONE, shell.getCurrentCommandUuid()));
    }

    /**
     * Получение результата выполнения команды
     * @param shell shell, в котором происходит выполнение
     * @return exit code выполнения команды, 0 - успешно выполнена, иначе error code
     */
    private int getExitCode(@NonNull Shell shell){
        int exitCode = -1;
        try {
            String shellResponse = shell.getOutput();
            String[] responseArr = shellResponse.split("\r\n");
            String cmdDone = String.format("%s%s:", CMD_DONE, shell.getCurrentCommandUuid());
            for(int i = responseArr.length - 1; i >= 0; i --){
                String line = responseArr[i];
                if(line.contains(cmdDone)){
                    String exitCodeString = line.substring(line.lastIndexOf(cmdDone) + cmdDone.length());
                    exitCode = Integer.parseInt(exitCodeString);
                    break;
                }
            }
        }
        catch (Exception e) {
            log.error("[SETUP] Не удалось получить код завершения выполнения команды:", e);
        }
        return exitCode;
    }

    /**
     * Проверка на необходимость пользовательского ввода
     * @param shell shell, в котором происходит выполнение
     * @return Строка для следующего "ручного" ввода
     */
    private String nextUserInput(@NonNull Shell shell){
        String shellOutput = shell.getOutput();
        for(Map.Entry<String, String> entry: shell.getUserInput().entrySet()){
            if(shellOutput.contains(entry.getKey())){
                return entry.getValue();
            }
        }
        return null;
    }

}
