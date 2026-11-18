package ru.dimangan.easy_ui.enums;

import lombok.Getter;

/**
 * Текущее состояние настройки сервера
 */
public enum ServerSetupState {
    NOT_CONFIGURED("Сервер не настроен"),
    SUDO_USER_ADDED("Добавлен sudo пользователь"),
    AUTHORIZED_KEYS_ADDED("Добавлен public key в список authorized_keys"),
    FIREWALL_ENABLED("Фаервол запущен"),
    UPDATED("Пакеты сервера обновлены"),
    NGINX_INSTALLED("Nginx установлен"),
    SNAPD_INSTALLED("Snapd установлен"),
    CERTBOT_INSTALLED("Certbot установлен"),
    CERTBOT_LINKED("Создана ссылка на certbot"),
    CERTIFICATE_ISSUED("Сертификат выпущен"),
    NGINX_CONFIGURED("Nginx настроен"),
    NGINX_HTML_ADDED("Добавлен сайт заглушка"),
    NGINX_RESTARTED("Nginx перезапущен"),
    XUI_INSTALLED("Панель 3x-ui установлена"),
    XUI_SSL_CONFIGURED("Установлен SSL сертификат панели 3x-ui"),
    XUI_ADMIN_ADDED("Добавлен администратор панели 3x-ui"),
    XUI_SETTING_LOADED("Загружены текущие настройки панели 3x-ui"),
    XUI_TOKEN_ISSUED("Получен API токен панели 3x-ui"),
    DEFAULT_PORTS_OPENED("Открыты дефолтные порты в фаерволе"),
    CONFIGURED("Сервер настроен");

    @Getter
    private final String description;

    ServerSetupState(String description) {
        this.description = description;
    }
}
