package ru.dimangan.easy_ui.exceptions;

public class SecuredKeyExistsException extends RuntimeException {
    public SecuredKeyExistsException() {
        super("Пара ключей уже существует");
    }
}
