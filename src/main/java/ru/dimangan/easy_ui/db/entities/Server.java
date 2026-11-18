package ru.dimangan.easy_ui.db.entities;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.dimangan.easy_ui.dto.ServerDTO;
import ru.dimangan.easy_ui.enums.ServerSetupState;
import ru.dimangan.easy_ui.enums.ServerStatus;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "server")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Server {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Имя сервера для отображения в панели управления
     */
    @Column(name = "name", unique = true, nullable = false)
    private String name;

    /**
     * Статус сервера
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ServerStatus status = ServerStatus.WAITING_FOR_SETUP;

    /**
     * Текущий статус настройки сервера
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "setup_state", nullable = false)
    private ServerSetupState setupState = ServerSetupState.NOT_CONFIGURED;

    /**
     * Имя пользователя для подключения по ssh и для авторизации в панели 3x-ui
     */
    @Column(name = "username", nullable = false)
    private String username = "root";

    /**
     * Пароль для пользователя для первого подключения по ssh и для авторизации в панели 3x-ui
     */
    @Column(name = "password", nullable = false)
    private String password;

    /**
     * Хост сервера (ipv4 адрес)
     */
    @Column(name = "host", unique = true, nullable = false)
    private String host;

    /**
     * Доменное имя сервера
     */
    @Column(name = "domain", unique = true, nullable = false)
    private String domain;

    /**
     * Порт для подключения к серверу по ssh
     */
    @Column(name = "port", nullable = false)
    private int port = 22;

    /**
     * Список портов открытых фаерволом
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "open_ports", nullable = false, columnDefinition = "jsonb")
    private Set<Integer> openPorts = new HashSet<>();

    /**
     * Порт для подключения к панели 3x-ui
     */
    @Column(name = "xui_port")
    private Integer xuiPort;

    /**
     * Путь для подключения к панели 3x-ui
     */
    @Column(name = "xui_path")
    private String xuiPath;

    /**
     * Токен авторизации в панели 3x-ui
     */
    @Column(name = "xui_token")
    private String xuiToken;

    /**
     * Ключи для подключения
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JsonManagedReference
    private SecuredKey securedKey;

    /**
     * Флаг добавления ключа на сервер
     */
    @Column(name = "priv_key_added", nullable = false)
    private boolean keyAdded;

    /**
     * Получение Server entity из ServerDTO
     * @param serverDTO serverDTO из которого нужно получить entity
     * @return Server
     */
    public static Server buildFromServerDTO(ServerDTO serverDTO){
        return Server.builder()
                .name(serverDTO.getName())
                .username(serverDTO.getUsername())
                .password(serverDTO.getPassword())
                .host(serverDTO.getHost())
                .port(serverDTO.getPort())
                .domain(serverDTO.getDomain())
                .status(ServerStatus.WAITING_FOR_SETUP)
                .setupState(ServerSetupState.NOT_CONFIGURED)
                .openPorts(new HashSet<>())
                .build();
    }
}
