package ru.dimangan.easy_ui.exceptions;

public class SecuredKeyNotFoundException extends RuntimeException {
    public SecuredKeyNotFoundException() {
        super("Пара ключей не найдена");
    }
}
