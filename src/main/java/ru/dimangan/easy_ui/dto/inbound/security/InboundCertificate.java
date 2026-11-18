package ru.dimangan.easy_ui.dto.inbound.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundCertificate {
    private boolean useFile = true;
    private String certificateFile;
    private String keyFile;
    private String usage = "encipherment";

    public InboundCertificate(String certificateFile, String keyFile) {
        this.certificateFile = certificateFile;
        this.keyFile = keyFile;
    }
}
