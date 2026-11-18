package ru.dimangan.easy_ui.dto;

import lombok.Data;
import ru.dimangan.easy_ui.enums.XrayProtocol;
import ru.dimangan.easy_ui.enums.XraySecurity;
import ru.dimangan.easy_ui.enums.XrayTransport;

@Data
public class CreateInboundRequest {
    private Long serverId;
    private String name;
    private XrayProtocol protocol;
    private XrayTransport transport;
    private XraySecurity security;
    private Integer port;
}
