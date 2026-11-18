package ru.dimangan.easy_ui.services;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.db.entities.User;
import ru.dimangan.easy_ui.db.repositories.ServerRepository;
import ru.dimangan.easy_ui.db.repositories.UserRepository;
import ru.dimangan.easy_ui.dto.xui_response.ListResponse;
import ru.dimangan.easy_ui.utils.QRGenerator;
import ru.dimangan.easy_ui.utils.ServerApiUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    private final UserRepository userRepository;
    private final ServerRepository serverRepository;
    private final WebService webService;

    /**
     * Получение ссылки для подключения и qr код
     * @param userId идентификатор пользователя для которого надо получить ссылку'
     * @param request http запрос
     * @return Map с данными
     */
    public Map<String, String> getSubscriptionLink(Long userId, HttpServletRequest request) {
        Map<String, String> linkQr = new HashMap<>(Map.of("link", "", "qr", ""));
        userRepository.findById(userId).ifPresent(user -> {
                    String origin = request.getScheme() + "://" + request.getServerName();
                    origin = origin.replaceAll("/+$", "");
                    String link = String.format("%s/sub/%s", origin, user.getSubId());
                    linkQr.put("link", link);
                    linkQr.put("qr", QRGenerator.generateQrCodeBase64(link));
                });
        return linkQr;
    }

    /**
     * Получение подписки для пользователя
     * @param userId идентификатор пользователя
     * @return Base64 строка с подпиской
     */
    public String getSubscriptionForUser(Long userId) {
        AtomicReference<String> subscription = new AtomicReference<>("");
        userRepository.findById(userId).ifPresent(user -> subscription.set(getSubscription(user)));
        return subscription.get();
    }

    /**
     * Получение подписки для пользователя
     * @param subId идентификатор подписки
     * @return Base64 строка с подпиской
     */
    public String getSubscriptionById(String subId){
        AtomicReference<String> subscription = new AtomicReference<>("");
        userRepository.findBySubId(subId).ifPresent(user -> subscription.set(getSubscription(user)));
        return subscription.get();
    }

    /**
     * Получение подписки для пользователя
     * @param user пользователь для получения подписки
     * @return Base64 строка с подпиской
     */
    private String getSubscription(User user){
        String subscription = "";
        List<String> userLinks = new ArrayList<>();
        for(Server server: serverRepository.findAll()){
            try{
                ListResponse<String> response = webService.get(String.format("%s/panel/api/clients/links/%s",
                        ServerApiUtils.uri(server), user.getName()), ServerApiUtils.authHeader(server), new ParameterizedTypeReference<ListResponse<String>>(){}).join();
                if(response.isSuccess() && response.getObj() != null){
                    userLinks.addAll(modifyConnectionLinks(server, response.getObj()));
                }
            }
            catch (Exception e){
                log.error("[SUB] Ошибка при получении подписки:", e);
            }
        }
        return userLinks.isEmpty()? subscription: Base64.getEncoder().encodeToString(String.join("\n", userLinks).getBytes());
    }

    /**
     * Преобразование ссылок для подключений для конфигов у которых отключена защита, потому что она идет через nginx
     * @param server сервер с которого получена подписка
     * @param rawConnectionLinks Список ссылок для подключения полученные из 3x-ui
     * @return Список строк измененный
     */
    private List<String> modifyConnectionLinks(Server server, List<String> rawConnectionLinks){
        List<String> modifiedLinks = new ArrayList<>();
        for(String rawLink: rawConnectionLinks){
            if(rawLink.contains("security=none")){
                //Замена порта на 443 для nginx
                String hostForPort = String.format("%s:", server.getDomain());
                String port = rawLink.substring(rawLink.indexOf(hostForPort) + hostForPort.length(), rawLink.indexOf("?encryption"));
                rawLink = rawLink.replace(String.format("%s%s", hostForPort, port), String.format("%s:443", server.getDomain()));

                //Добавлени TLS
                rawLink = rawLink.replace("security=none", String.format("security=tls&sni=%s&fp=firefox", server.getDomain()));
            }
            modifiedLinks.add(rawLink);
        }
        return modifiedLinks;
    }

}
