package ru.dimangan.easy_ui.dto.inbound.stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundXHttpSettings {
    private String host = "";
    private String path = "/";
    private String mode = "packet-up";
    private String xPaddingBytes = "100-1000";
    private boolean xPaddingObfsMode = true;
    private String xPaddingKey = "_dc";
    private String  xPaddingHeader = "X-Cache";
    private String  xPaddingPlacement = "queryInHeader";
    private String  xPaddingMethod = "tokenish";
    private String  sessionIDPlacement = "";
    private String  sessionIDKey = "";
    private String  sessionIDTable = "";
    private String  sessionIDLength = "";
    private String  seqPlacement = "";
    private String  seqKey = "";
    private String  uplinkDataPlacement = "";
    private String  uplinkDataKey = "";
    private String  scMaxEachPostBytes = "";
    private boolean  noSSEHeader = false;
    private int  scMaxBufferedPosts = 30;
    private String  scStreamUpServerSecs = "20-80";
    private int  serverMaxHeaderBytes = 0;
    private String  uplinkHTTPMethod = "GET";
    private Map<Object, Object> headers = Collections.emptyMap();
    private String scMinPostsIntervalMs = "";
    private int uplinkChunkSize = 0;
    private boolean noGRPCHeader = false;
    private Map<String, Object> xmux = Map.of(
            "maxConcurrency", "0",
            "maxConnections", 2,
            "cMaxReuseTimes", 0,
            "hMaxRequestTimes", "100-200",
            "hMaxReusableSecs", "300-600",
            "hKeepAlivePeriod", 0
    );
    private boolean enableXmux = true;

    public InboundXHttpSettings(String path) {
        if(StringUtils.isNotEmpty(path)){
            this.path = path;
        }
    }
}
