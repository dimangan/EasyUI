package ru.dimangan.easy_ui.exceptions;

public class ServerExistsException extends RuntimeException {
    public ServerExistsException() {
        super("Сервер уже существует");
    }
}
