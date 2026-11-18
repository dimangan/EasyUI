package ru.dimangan.easy_ui.dto;

import lombok.Data;

@Data
public class DeleteInboundRequest {
    private Long serverId;
    private Integer inboundId;
    private int port;
}
