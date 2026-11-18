package ru.dimangan.easy_ui.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.entities.User;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.db.repositories.UserRepository;
import ru.dimangan.easy_ui.dto.xui_response.ListResponse;
import ru.dimangan.easy_ui.dto.xui_response.MapObjectResponse;
import ru.dimangan.easy_ui.dto.xui_response.StringResponse;
import ru.dimangan.easy_ui.exceptions.UserExistsException;
import ru.dimangan.easy_ui.exceptions.UserNotFoundException;
import ru.dimangan.easy_ui.utils.ServerApiUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final WebService webService;

    @Transactional
    public User createUser(User user){
        if(userRepository.existsByName(user.getName())){
            throw new UserExistsException();
        }
        try {
            addUserOnAllServers(user);
        }
        catch (Exception e) {
            log.error("[USER] Ошибка при добавлении пользователя на сервера:", e);
        }
        log.info("[USER] Добавлен пользователь {}", user);
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long userId){
        User user = userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        userRepository.delete(user);
        try {
            deleteUserFromServers(user);
        }
        catch (Exception e) {
            log.error("[USER] Ошибка при удалении пользователя с серверов");
        }
        log.info("[USER] Удален пользователь {}", user);
    }

    public List<User> list(){
        List<User> users = userRepository.findAll();
        Map<String, BigDecimal> usernameTotalTraffic = new HashMap<>();
        for(Server server: serverRepository.findAll()){
            try{
                ListResponse<Map<String, Object>> response = webService.get(String.format("%s/panel/api/clients/list", ServerApiUtils.uri(server)), ServerApiUtils.authHeader(server),
                        new ParameterizedTypeReference<ListResponse<Map<String, Object>>>(){}).join();
                if(!response.isSuccess()){
                    throw new RuntimeException(response.getMsg());
                }
                for(Map<String, Object> clientData: response.getObj()){
                    String username = (String)clientData.get("email");
                    Map<String, String> traffic = new ObjectMapper().convertValue(clientData.get("traffic"), new TypeReference<>() {});
                    BigDecimal userTotalTraffic = new BigDecimal(traffic.get("up")).add(new BigDecimal(traffic.get("down")));
                    if(!usernameTotalTraffic.containsKey(username)){
                        usernameTotalTraffic.put(username, new BigDecimal(0));
                    }
                    usernameTotalTraffic.put(username, usernameTotalTraffic.get(username).add(userTotalTraffic));
                }
            }
            catch (Exception e){
                log.error("[USER] Не удалось получить суммарный трафик с сервера {}:", server.getName(), e);
            }
        }
        users.forEach(user -> {
            if(usernameTotalTraffic.containsKey(user.getName())){
                BigDecimal userTotalTrafficBytes = usernameTotalTraffic.get(user.getName());
                int measure = 0;
                while(userTotalTrafficBytes.doubleValue() >= 1000){
                    measure++;
                    userTotalTrafficBytes = userTotalTrafficBytes.divide(BigDecimal.valueOf(1024), 2, RoundingMode.CEILING);
                }
                String measureStr = switch (measure){
                    case 0 -> "B";
                    case 1 -> "KB";
                    case 2 -> "MB";
                    case 3 -> "GB";
                    case 4 -> "TB";
                    default -> throw new IllegalArgumentException("Слишком много трафика");
                };
                user.setTotalTraffic(String.format("%s %s", userTotalTrafficBytes, measureStr));
            }
        });
        return users;
    }

    /**
     * Добавление пользователя на существующие сервера
     * @param user пользователь для добавления
     * @throws RuntimeException если не удалось добавить пользователя на какой-либо сервер
     */
    private void addUserOnAllServers(User user) throws RuntimeException{
        List<String> errors = new ArrayList<>();
        for(Server server: serverRepository.findAll()){
            try {
                addUserOnServer(server, user);
            }
            catch (RuntimeException e) {
                errors.add(e.getMessage());
            }
        }
        if(!errors.isEmpty()){
            throw new RuntimeException(String.join("; ", errors));
        }
    }

    /**
     * Добавление всех пользователей на сервер
     * @param server сервер для добавления пользователей
     */
    public void addAllUsersOnServer(Server server){
        try{
            List<Object> clientList = new ArrayList<>();
            for(User user: userRepository.findAll()){
                clientList.add(Map.of("client", getClient(user)));
            }
            String clientsData = new ObjectMapper().writeValueAsString(clientList);
            var response = webService.post(String.format("%s/panel/api/clients/import", ServerApiUtils.uri(server)),
                    Map.of("data", clientsData),
                    ServerApiUtils.authHeader(server), MapObjectResponse.class).join();
            if(!response.isSuccess()){
                throw new RuntimeException(response.getMsg());
            }
        }
        catch (Exception e){
            log.error("[USER] Ошибка при добавлении пользователей на сервер:", e);
        }
    }

    /**
     * Добавление пользователя на конкретный сервер
     * @param server сервер для добавления пользователя
     * @param user пользователь для добавления
     * @throws RuntimeException если не удалось добавить пользователя
     */
    private void addUserOnServer(Server server, User user) throws RuntimeException{
        try{
            String clientData = new ObjectMapper().writeValueAsString(List.of(Map.of("client", getClient(user))));
            var response = webService.post(String.format("%s/panel/api/clients/import", ServerApiUtils.uri(server)),
                    Map.of("data", clientData),
                    ServerApiUtils.authHeader(server), MapObjectResponse.class).join();
            if(!response.isSuccess()){
                throw new RuntimeException(response.getMsg());
            }
        }
        catch (Exception e){
            throw new RuntimeException(String.format("Не удалось добавить пользователя %s на сервер %s", user.getName(), server.getName()), e);
        }
    }

    /**
     * Получение мапы клиент для отправки в 3x-ui
     * @param user пользователь для которого надо получить мапу
     * @return Map с данными Client
     */
    private Map<String, String> getClient(User user){
        return Map.of(
                "email", user.getName(),
                "id", user.getUuid(),
                "security", "auto",
                "password", user.getPassword(),
                "subId", user.getSubId(),
                "auth", user.getHysAuth());
    }

    /**
     * Удаление пользователя с серверов
     * @param user пользователь для удаления
     * @throws RuntimeException если не удалось удалить пользователя с какого-либо сервера
     */
    private void deleteUserFromServers(User user) throws RuntimeException{
        List<String> errors = new ArrayList<>();
        for(Server server: serverRepository.findAll()){
            try{
                var response = webService.post(String.format("%s/panel/api/clients/del/%s", ServerApiUtils.uri(server), user.getName()), null, ServerApiUtils.authHeader(server), StringResponse.class).join();
                if(!response.isSuccess()){
                    throw new RuntimeException(response.getMsg());
                }
            }
            catch (Exception e){
                errors.add(e.getMessage());
            }
        }
        if(!errors.isEmpty()){
            throw new RuntimeException(String.join("; ", errors));
        }
    }
}
