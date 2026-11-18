package ru.dimangan.easy_ui.enums;

import lombok.Getter;

public enum ServerStatus {
    WAITING_FOR_SETUP("Ожидается настройка"),
    SETUP_IN_PROGRESS("Настройка сервера в процессе"),
    SETUP_FAILED("Произошла ошибка при настройке сервера"),
    WORKING("В работе");

    @Getter
    private final String description;

    ServerStatus(String description) {
        this.description = description;
    }
}
