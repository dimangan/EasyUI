package ru.dimangan.easy_ui.dto.xui_response;

import lombok.Data;
import ru.dimangan.easy_ui.dto.inbound.Inbound;

@Data
public class InboundResponse {
    private boolean success;
    private Inbound obj;
}
