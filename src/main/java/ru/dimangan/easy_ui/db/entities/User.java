package ru.dimangan.easy_ui.db.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.UUID;

@Entity
@Table(name = "users")
@Setter
@Getter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Имя пользователя
     */
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    /**
     * Идентификатор пользователя в панели 3x-ui
     */
    @Column(name = "uuid", nullable = false, unique = true)
    private String uuid = UUID.randomUUID().toString();

    /**
     * Идентификатор подписки в панели 3x-ui
     */
    @Column(name = "sub_id", nullable = false, unique = true)
    private String subId = RandomStringUtils.secure().next(16, true, true);

    /**
     * Данные авторизации hysteria
     */
    @Column(name = "hys_auth", nullable = false, unique = true)
    private String hysAuth = RandomStringUtils.secure().next(16, true, true);

    /**
     * Пароль для авторизации подключения
     */
    @Column(name = "password", nullable = false, unique = true)
    private String password = RandomStringUtils.secure().next(16, true, true);

    @Transient
    private String totalTraffic;
}
