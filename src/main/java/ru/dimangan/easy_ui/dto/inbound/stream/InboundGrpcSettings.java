package ru.dimangan.easy_ui.dto.inbound.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundGrpcSettings {
    private String serviceName;
    private String authority;
    private boolean multiMode = true;

    public InboundGrpcSettings(String serviceName) {
        this.serviceName = serviceName;
        this.authority = serviceName;
    }
}
