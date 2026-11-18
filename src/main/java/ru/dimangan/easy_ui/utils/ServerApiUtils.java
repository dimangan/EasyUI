package ru.dimangan.easy_ui.utils;

import jakarta.annotation.Nonnull;
import org.springframework.http.HttpHeaders;
import ru.dimangan.easy_ui.auth.JWT;
import ru.dimangan.easy_ui.db.entities.Server;

import java.util.function.Consumer;

public class ServerApiUtils {

    /**
     * Получение URI панели 3x-ui для заданного сервера
     * @param server сервер для получения пути для подключения к панели 3x-ui
     * @return Строка с uri для подключения к панели (стартовая страница)
     */
    public static String uri(@Nonnull Server server){
        return String.format("https://%s:%s/%s", server.getDomain(), server.getXuiPort(), server.getXuiPath());
    }

    /**
     * Получение заголовка авторизации в панели 3x-ui
     * @param server сервер для подключения
     * @return Consumer HttpHeaders для передачи в WebService
     */
    public static Consumer<HttpHeaders> authHeader(@Nonnull Server server){
        return httpHeaders -> httpHeaders.add(JWT.HEADER_AUTHORIZATION, String.format("Bearer %s", server.getXuiToken()));
    }
}
