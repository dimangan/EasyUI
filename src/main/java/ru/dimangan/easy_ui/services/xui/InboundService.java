package ru.dimangan.easy_ui.services.xui;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.config.JschProperties;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.dto.CreateInboundRequest;
import ru.dimangan.easy_ui.dto.DeleteInboundRequest;
import ru.dimangan.easy_ui.dto.internal.Shell;
import ru.dimangan.easy_ui.dto.inbound.Inbound;
import ru.dimangan.easy_ui.dto.inbound.security.InboundCertificate;
import ru.dimangan.easy_ui.dto.xui_response.*;
import ru.dimangan.easy_ui.enums.XraySecurity;
import ru.dimangan.easy_ui.exceptions.ServerNotFoundException;
import ru.dimangan.easy_ui.services.WebService;
import ru.dimangan.easy_ui.services.secured.SecureShellService;
import ru.dimangan.easy_ui.services.server.ServerSetupService;
import ru.dimangan.easy_ui.utils.ServerApiUtils;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundService {
    //TODO: Возможно сделаю загрузку через файл или типа того, но пока впадлу
    private final List<String> sniList = List.of("max.ru", "ozon.ru", "vk.ru", "www.cloudflare.com",
            "www.amazon.com", "aws.amazon.com", "www.amd.com", "dl.google.com", "www.samsung.com", "www.microsoft.com", "www.nvidia.com", "www.intel.com", "www.sony.com");
    private final ServerRepository serverRepository;
    private final WebService webService;
    private final ServerSetupService serverSetupService;
    private final SecureShellService secureShellService;
    private final JschProperties jschProperties;

    /**
     * Создание нового инбаунда
     * @param createInboundRequest параметры для создания инбаунда
     */
    public Inbound createInbound(CreateInboundRequest createInboundRequest){
        Server server = serverRepository.findById(createInboundRequest.getServerId()).orElseThrow(ServerNotFoundException::new);
        int port;
        if(createInboundRequest.getPort() != null){
            port = createInboundRequest.getPort();
            List<Integer> occupiedPorts = getOccupiedPorts(server);
            if(occupiedPorts.contains(port)){
                throw new RuntimeException("Инбаунд на указанном порту уже существует");
            }
        }
        else{
            port = getFreePort(server);
        }
        Inbound inbound = switch (createInboundRequest.getProtocol()){
            case HYSTERIA -> Inbound.hysteria(createInboundRequest.getName(), port);
            case VLESS -> switch (createInboundRequest.getTransport()){
                case TCP -> Inbound.vlessTcp(createInboundRequest.getName(), port);
                case GRPC -> Inbound.vlessGrpc(createInboundRequest.getName(), port, sniList.get(new Random(System.currentTimeMillis()).nextInt(0, sniList.size())));
                case XHTTP -> Inbound.vlessXhttp(createInboundRequest.getName(), port, createInboundRequest.getSecurity() == XraySecurity.NONE?
                    String.format("/api/v1/user/%s", Base64.getEncoder().encodeToString(server.getUsername().getBytes()).substring(0, 10)):
                    String.format("/api/%s", RandomStringUtils.secure().next(6, true, false)));
                default -> throw new RuntimeException(String.format("Для VLESS протокола не поддерживается тип транспорта %s", createInboundRequest.getTransport()));
            };
        };
        switch (createInboundRequest.getSecurity()){
            case TLS -> inbound.setTlsSecurity(server.getDomain(), List.of(getPanelCertificate(server)));
            case REALITY -> {
                Pair<String, String> x25519Cert = getx25519Cert(server);
                Pair<String, String> mldsa65 = getmldsa65Key(server);
                inbound.setRealitySecurity(sniList.get(new Random(System.currentTimeMillis()).nextInt(0, sniList.size())),
                        x25519Cert.getLeft(), x25519Cert.getRight(), mldsa65.getLeft(), mldsa65.getRight());
            }
            case NONE -> inbound.setListen("127.0.0.1");
        }

        String addInboundUri = String.format("%s/panel/api/inbounds/add", ServerApiUtils.uri(server));
        InboundResponse response = new InboundResponse();
        try(Shell shell = secureShellService.openShell(server, jschProperties.isUseLogger())) {
            if(!List.of(22, server.getXuiPort()).contains(createInboundRequest.getPort())){
                response = serverSetupService.setupServerUfwConfigurePort(server, shell, port, true).orTimeout(20, TimeUnit.SECONDS)
                        .thenCompose(openPort -> webService.post(addInboundUri, inbound, ServerApiUtils.authHeader(server), InboundResponse.class)).join();
            }
            else{
                throw new RuntimeException("Указан невалидный порт");
            }

        }
        catch (Exception e){
            log.error("[INBOUND] Ошибка при создании нового inbound:", e);
        }
        finally {
            if(response.isSuccess()){
                log.info("[INBOUND] Создан новый inbound: {}", inbound);
            }
            else{
                log.error("[INBOUND] Не удалось создать новый inbound: {}", inbound);
            }
        }
        return response.isSuccess()? response.getObj(): null;
    }

    /**
     * Удаление ибаунда на сервере
     * @param deleteInboundRequest параметры удаления инбаунда
     */
    public boolean deleteInbound(DeleteInboundRequest deleteInboundRequest){
        Server server = serverRepository.findById(deleteInboundRequest.getServerId()).orElseThrow(ServerNotFoundException::new);
        Inbound inboundForRemoval = list(server).stream().filter(inbound -> Objects.equals(inbound.getId(), deleteInboundRequest.getInboundId())).findFirst().orElse(null);
        IntegerResponse response = new IntegerResponse();
        try(Shell shell = secureShellService.openShell(server, jschProperties.isUseLogger())) {
            String deleteInboundUri = String.format("%s/panel/api/inbounds/del/%s", ServerApiUtils.uri(server), deleteInboundRequest.getInboundId());
            if(!List.of(22, 80, 443, server.getXuiPort()).contains(deleteInboundRequest.getPort())){
                response = serverSetupService.setupServerUfwConfigurePort(server, shell, deleteInboundRequest.getPort(), false).orTimeout(20, TimeUnit.SECONDS)
                        .thenCompose(closePort -> webService.post(deleteInboundUri, null, ServerApiUtils.authHeader(server), IntegerResponse.class)).join();
            }
            else {
                response = webService.post(deleteInboundUri, null, ServerApiUtils.authHeader(server), IntegerResponse.class).join();
            }
        }
        catch (Exception e){
            log.error("[INBOUND] Ошибка при удалении inbound:", e);
        }
        finally {
            if(response.isSuccess()){
                log.info("[INBOUND] Удален inbound: {}", inboundForRemoval);
            }
            else{
                log.error("[INBOUND] Не удалось удалить inbound: {}", inboundForRemoval);
            }
        }
        return response.isSuccess();
    }

    /**
     * Получение списка инбаундов на заданном сервере
     * @param serverId идентификатор сервера для подключения
     * @return Список созданных инбаундов
     */
    public List<Inbound> list(Long serverId){
        Server server = serverRepository.findById(serverId).orElseThrow(ServerNotFoundException::new);
        return list(server);
    }

    /**
     * Получение списка инбаундов на сервере
     * @param server сервер для подключения
     * @return список Inbound
     */
    public List<Inbound> list(Server server){
        return webService.get(String.format("%s/panel/api/inbounds/list", ServerApiUtils.uri(server)),
                ServerApiUtils.authHeader(server), new ParameterizedTypeReference<ListResponse<Inbound>>() {}).join().getObj();
    }

    /**
     * Получение рандомного свободного порта для инбаунда на сервере
     * @param server сервер для подключения
     * @return новый свободный порт
     */
    private int getFreePort(Server server){
        int freePort;
        Random random = new Random(System.currentTimeMillis());
        List<Integer> occupiedPorts = getOccupiedPorts(server);
        do{
            freePort = random.nextInt(8000, 40000);
        }
        while (occupiedPorts.contains(freePort) || freePort == server.getPort() || freePort == server.getXuiPort());
        return freePort;
    }

    /**
     * Получение списка занятых портов для инбаундов
     * @param server сервер для подключения
     * @return Список Integer с занятыми портами
     */
    private List<Integer> getOccupiedPorts(Server server){
        List<Inbound> inbounds = list(server);
        return inbounds.stream().map(Inbound::getPort).toList();
    }

    /**
     * Получение SSL сертификата панели
     * @param server сервер для получения сертификата
     * @return InboundCertificate
     */
    private InboundCertificate getPanelCertificate(Server server){
        return new InboundCertificate(String.format("/etc/letsencrypt/live/%s/fullchain.pem", server.getDomain()),
                String.format("/etc/letsencrypt/live/%s/privkey.pem", server.getDomain()));
    }

    /**
     * Получение x25519 сертификатов
     * @param server сервер для подключения
     * @return пара privateKey и publicKey
     */
    private Pair<String, String> getx25519Cert(Server server){
        String mldsa65Uri = String.format("%s/panel/api/server/getNewX25519Cert", ServerApiUtils.uri(server));
        MapStringResponse response = webService.get(mldsa65Uri, ServerApiUtils.authHeader(server), MapStringResponse.class).join();
        if(!response.isSuccess()){
            throw new RuntimeException("Не удалось получить x25519 сертификат");
        }
        return Pair.of(response.getObj().get("privateKey"), response.getObj().get("publicKey"));
    }

    /**
     * Получение mldsa65 ключей
     * @param server сервер для подключения
     * @return пара seed и verify
     */
    private Pair<String, String> getmldsa65Key(Server server){
        String mldsa65Uri = String.format("%s/panel/api/server/getNewmldsa65", ServerApiUtils.uri(server));
        MapStringResponse response = webService.get(mldsa65Uri, ServerApiUtils.authHeader(server), MapStringResponse.class).join();
        if(!response.isSuccess()){
            throw new RuntimeException("Не удалось получить mldsa65 ключи");
        }
        return Pair.of(response.getObj().get("seed"), response.getObj().get("verify"));
    }
}
