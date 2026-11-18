package ru.dimangan.easy_ui.dto.inbound.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundHysteriaSettings {
    private int version = 2;
    private int updIdleTimeout = 60;
    private Map<String, Object> masquerade = Map.of(
            "type", "file",
            "dir", "/var/www/html",
            "url", "",
            "rewriteHost", false,
            "insecure", false,
            "content", "",
            "headers", Collections.emptyMap(),
            "statusCode", 0
    );
}
