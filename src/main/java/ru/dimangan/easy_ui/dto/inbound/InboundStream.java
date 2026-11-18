package ru.dimangan.easy_ui.dto.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dimangan.easy_ui.dto.inbound.security.InboundRealitySettings;
import ru.dimangan.easy_ui.dto.inbound.security.InboundTlsSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundGrpcSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundHysteriaSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundTcpSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundXHttpSettings;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundStream {
    private String network;
    private InboundTcpSettings tcpSettings;
    private InboundGrpcSettings grpcSettings;
    private InboundXHttpSettings xhttpSettings;
    private InboundHysteriaSettings hysteriaSettings;

    private String security;
    private InboundTlsSettings tlsSettings;
    private InboundRealitySettings realitySettings;
    private InboundMask finalmask;
}
