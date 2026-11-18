package ru.dimangan.easy_ui.dto.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundMask {
    private List<Object> tcp;
    private List<Object> udp;


    public static InboundMask tcpMask(){
        InboundMask mask = new InboundMask();
        mask.setTcp(List.of(Map.of(
                "type", "fragment",
                "settings", Map.of(
                        "packets", "1-3",
                        "lengths", List.of("100-200"),
                        "maxSplit", "",
                        "delays", Collections.emptyList()
                )
        )));
        return mask;
    }

    public static InboundMask udpMask(){
        InboundMask mask = new InboundMask();
        mask.setUdp(List.of(Map.of(
                "type", "salamander",
                "settings", Map.of(
                        "password", RandomStringUtils.secure().next(16, true, true)
                ))));
        return mask;
    }
}
