package ru.dimangan.easy_ui.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.entities.User;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.db.repositories.UserRepository;
import ru.dimangan.easy_ui.dto.UserInboundsRequest;
import ru.dimangan.easy_ui.dto.inbound.Inbound;
import ru.dimangan.easy_ui.dto.xui_response.MapObjectResponse;
import ru.dimangan.easy_ui.dto.xui_response.StringResponse;
import ru.dimangan.easy_ui.exceptions.ServerNotFoundException;
import ru.dimangan.easy_ui.exceptions.UserNotFoundException;
import ru.dimangan.easy_ui.services.xui.InboundService;
import ru.dimangan.easy_ui.utils.ServerApiUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserInboundService {
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final InboundService inboundService;
    private final WebService webService;

    /**
     * Получение списка инбаундов пользователя на всех серверах
     * @param userId идентификатор пользователя
     * @return список Inbound
     */
    public UserInboundsRequest getUserInbounds(Long userId){
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        List<UserInboundsRequest.ServerInbounds> userInbounds = new ArrayList<>();
        List<UserInboundsRequest.ServerReference> unavailableServers = new ArrayList<>();
        for (Server server : serverRepository.findAll()) {
            var reference = new UserInboundsRequest.ServerReference(server.getId(), server.getName());
            try {
                var client = webService.get(String.format("%s/panel/api/clients/get/%s", ServerApiUtils.uri(server), user.getName()), ServerApiUtils.authHeader(server), MapObjectResponse.class).join();
                if(!client.isSuccess()){
                    log.error("[USER.INBOUND] Пропущен сервер {}: не удалось найти клиента", server.getName());
                    continue;
                }
                List<Integer> selected = new ObjectMapper().convertValue(client.getObj().get("inboundIds"), new TypeReference<>() {});
                List<Inbound> serverInbounds = inboundService.list(server);
                serverInbounds.forEach(inbound -> {
                    boolean assigned = selected.contains(inbound.getId());
                    inbound.setPeeked(assigned);
                    inbound.setNewPeeked(assigned);
                });
                UserInboundsRequest.ServerInbounds inbounds = new UserInboundsRequest.ServerInbounds();
                inbounds.setServer(reference);
                inbounds.setInbounds(serverInbounds);
                userInbounds.add(inbounds);
            } catch (Exception e) {
                unavailableServers.add(reference);
                log.error("[USER.INBOUND] Ошибка при получении инбаундов пользователя на сервере {}:", server.getName(), e);
            }
        }
        UserInboundsRequest response = new UserInboundsRequest();
        response.setUser(user);
        response.setChanges(userInbounds);
        response.setUnavailableServers(unavailableServers);
        return response;
    }

    /**
     * Обновление инбаундов пользователя
     * @param userInbounds запрос с данными на изменение
     */
    public void setUserInbounds(UserInboundsRequest userInbounds){
        User user = userInbounds.getUser();
        for(UserInboundsRequest.ServerInbounds change: userInbounds.getChanges()){
            try{
                Server server = serverRepository.findById(change.getServer().getId()).orElseThrow(ServerNotFoundException::new);
                List<Integer> inboundsToPeek = change.getInbounds().stream()
                        .filter(inbound -> !inbound.isPeeked() && inbound.isNewPeeked())
                        .map(Inbound::getId)
                        .toList();
                List<Integer> inboundsToRemovePeek = change.getInbounds().stream()
                        .filter(inbound -> inbound.isPeeked() && !inbound.isNewPeeked())
                        .map(Inbound::getId)
                        .toList();
                if(!inboundsToPeek.isEmpty()){
                    var peek = webService.post(String.format("%s/panel/api/clients/%s/attach", ServerApiUtils.uri(server), user.getName()),
                            Map.of("inboundIds", inboundsToPeek), ServerApiUtils.authHeader(server), StringResponse.class).join();
                    if(peek.isSuccess()){
                        log.info("[USER.INBOUND] Привязаны инбаунды {} к пользователя", inboundsToPeek);
                    }
                    else{
                        log.error("[USER.INBOUND] Не удалось привязать инбаунды {} к пользователя", inboundsToPeek);
                    }
                }
                if(!inboundsToRemovePeek.isEmpty()){
                    var peek = webService.post(String.format("%s/panel/api/clients/%s/detach", ServerApiUtils.uri(server), user.getName()),
                            Map.of("inboundIds", inboundsToRemovePeek), ServerApiUtils.authHeader(server), StringResponse.class).join();
                    if(peek.isSuccess()){
                        log.info("[USER.INBOUND] Отвязаны инбаунды {} от пользователя", inboundsToRemovePeek);
                    }
                    else{
                        log.error("[USER.INBOUND] Не удалось отвязать инбаунды {} от пользователя", inboundsToRemovePeek);
                    }
                }
            }
            catch (Exception e){
                log.error("[USER.INBOUND] Пропущено обновление пользователя на сервере {} из-за ошибки:", change.getServer().getName(), e);
            }
        }
    }
}
