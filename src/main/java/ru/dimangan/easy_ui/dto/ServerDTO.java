package ru.dimangan.easy_ui.dto;

import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.IpAddress;
import ru.dimangan.easy_ui.db.entities.Server;
import ru.dimangan.easy_ui.enums.ServerSetupState;
import ru.dimangan.easy_ui.enums.ServerStatus;

@Data
@AllArgsConstructor
@Builder
public class ServerDTO {
    private Long id;
    @Nonnull
    private String name;
    @Nonnull
    private String username;
    @Nonnull
    private String password;
    @Nonnull
    @IpAddress
    private String host;
    @Min(1)
    @Max(65535)
    private int port;
    @Nonnull
    private String domain;
    private SecuredKeyDTO securedKey;
    private ServerStatus status;
    private ServerSetupState setupState;
    private Integer xuiPort;
    private String xuiPath;

    /**
     * Получение ServerDTO из Server entity
     * @param server Server из которого нужно получить DTO
     * @return ServerDTO
     */
    public static ServerDTO buildFromServer(Server server){
        return ServerDTO.builder()
                .id(server.getId())
                .name(server.getName())
                .username(server.getUsername())
                .password(server.getPassword())
                .host(server.getHost())
                .port(server.getPort())
                .domain(server.getDomain())
                .status(server.getStatus())
                .setupState(server.getSetupState())
                .xuiPort(server.getXuiPort())
                .xuiPath(server.getXuiPath())
                .build();
    }
}
