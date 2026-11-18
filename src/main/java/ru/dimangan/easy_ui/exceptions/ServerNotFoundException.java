package ru.dimangan.easy_ui.exceptions;

public class ServerNotFoundException extends RuntimeException {
    public ServerNotFoundException() {
        super("Запрашиваемый сервер не найден");
    }
}
