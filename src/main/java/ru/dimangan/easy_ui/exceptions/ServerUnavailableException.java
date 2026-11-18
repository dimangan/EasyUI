package ru.dimangan.easy_ui.exceptions;

public class ServerUnavailableException extends RuntimeException {
    public ServerUnavailableException() {
        super("Сервер недоступен");
    }
}
