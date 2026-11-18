package ru.dimangan.easy_ui.dto.inbound.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundTlsSettings {
    private String serverName;
    private String minVersion = "1.2";
    private String maxVersion = "1.3";
    private boolean rejectUnknownSni = true;
    private List<InboundCertificate> certificates;
    private List<String> alpn = List.of("h2", "http/1.1", "h3");
    private String echServerKeys = "";
    private Map<String, Object> settings = Map.of("fingerprint", "firefox");
    private List<String> curvePreferences = List.of("X25519MLKEM768");

    public InboundTlsSettings(String serverName, List<InboundCertificate> certificates) {
        this.serverName = serverName;
        this.certificates = certificates;
    }
}
