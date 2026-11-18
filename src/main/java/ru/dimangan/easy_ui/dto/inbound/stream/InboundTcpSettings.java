package ru.dimangan.easy_ui.dto.inbound.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundTcpSettings {
    private boolean acceptProxyProtocol = false;
    private Map<String, Object> header = Map.of(
            "type", "http",
            "request", Map.of(
                    "version", "1.1",
                    "method", "GET",
                    "path", List.of(String.format("/%s", UUID.randomUUID().toString().replace("-", "").substring(0, 10))),
                    "headers", Collections.emptyMap()
            ),
            "response", Map.of(
                    "version", "1.1",
                    "status", "200",
                    "reason", "OK",
                    "headers", Collections.emptyMap()
            )
    );
}
