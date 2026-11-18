package ru.dimangan.easy_ui.exceptions;

public class UserExistsException extends RuntimeException {
    public UserExistsException() {
        super("Пользователь уже существует");
    }
}
