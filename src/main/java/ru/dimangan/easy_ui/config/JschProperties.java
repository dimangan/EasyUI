package ru.dimangan.easy_ui.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jsch")
@Data
public class JschProperties {
    private boolean useLogger = false;
    private int openChannelRetryCount = 2;
    private int sessionConnectionTimeout = 2000;
    private int channelConnectionTimeout = 2000;
    private int shellDebounceTime = 1000;
}
