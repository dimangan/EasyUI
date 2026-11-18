package ru.dimangan.easy_ui.services.xui;

import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.services.WebService;
import ru.dimangan.easy_ui.utils.ServerApiUtils;
import ru.dimangan.easy_ui.dto.xui_response.MapStringResponse;
import ru.dimangan.easy_ui.dto.xui_response.StringResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
@Slf4j
@RequiredArgsConstructor
public class XUIAuthService {
    private final WebService webService;

    /**
     * Получение csrf токена для авторизации в панели 3x-ui через логин пароль вместо api токена
     * @param server сервер для отправки запроса
     * @param responseHeadersConsumer Consumer заголовков ответа для получения куков
     * @return CompletableFuture с StringResponse формата от 3x-ui api - возвращает флаг success и строку с данными obj
     */
    public CompletableFuture<StringResponse> getCsrfToken(@Nonnull Server server, @Nonnull Consumer<HttpHeaders> responseHeadersConsumer){
        return webService.get(String.format("%s/csrf-token", ServerApiUtils.uri(server)), null, responseHeadersConsumer, StringResponse.class);
    }

    /**
     * Авторизация в панели 3x-ui через логин пароль вместо api токена
     * @param server сервер для отправки запроса
     * @param headersConsumer Consumer с заголовками запроса (должен передаваться заголовок X-CSRF-Token с csrf токеном и Cookie с куками из запроса csrf)
     * @param responseHeadersConsumer Consumer заголовков ответа для получения куков
     * @return CompletableFuture с StringResponse формата от 3x-ui api - возвращает флаг success и строку с данными obj
     */
    public CompletableFuture<StringResponse> login(@Nonnull Server server, @Nonnull Consumer<HttpHeaders> headersConsumer, @Nonnull Consumer<HttpHeaders> responseHeadersConsumer){
        Map<String, String> body = new HashMap<>(Map.of(
                "username", server.getUsername(),
                "password", server.getPassword()));
        return webService.post(String.format("%s/login", ServerApiUtils.uri(server)), body, headersConsumer, responseHeadersConsumer, StringResponse.class);
    }

    /**
     * Создание api токена для панели 3x-ui
     * @param server сервер для отправки запроса
     * @param headersConsumer Consumer для передачи заголовков запроса (должен передаваться заголовок X-CSRF-TOKEN с csrf токеном и Cookie с куками из запроса login)
     * @return CompletableFuture с MapStringResponse формата от 3x-ui api - возвращает флаг success и Map формата String-String
     */
    public CompletableFuture<MapStringResponse> createApiToken(@Nonnull Server server, @Nonnull Consumer<HttpHeaders> headersConsumer){
        Map<String, String> body = new HashMap<>(Map.of(
                "name", server.getUsername(),
                "scope", "admin"
        ));
        return webService.post(String.format("%s/panel/api/setting/apiTokens/create", ServerApiUtils.uri(server)), body, headersConsumer, MapStringResponse.class);
    }
}
