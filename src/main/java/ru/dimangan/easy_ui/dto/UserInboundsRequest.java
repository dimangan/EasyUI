package ru.dimangan.easy_ui.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dimangan.easy_ui.db.entities.User;
import ru.dimangan.easy_ui.dto.inbound.Inbound;

import java.util.List;

@Data
public class UserInboundsRequest {
    private User user;
    private List<ServerInbounds> changes;
    private List<ServerReference> unavailableServers;

    @Data
    @NoArgsConstructor
    public static class ServerInbounds{
        private ServerReference server;
        private List<Inbound> inbounds;
    }
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerReference {
        private Long id;
        private String name;

        public ServerReference(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
