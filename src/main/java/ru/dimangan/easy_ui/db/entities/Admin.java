package ru.dimangan.easy_ui.db.entities;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "admin")
@Data
public class Admin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Логин от админ-пользователя
     */
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    /**
     * Пароль от админ-пользователя
     */
    @Column(name = "password", nullable = false)
    private String password;
}
