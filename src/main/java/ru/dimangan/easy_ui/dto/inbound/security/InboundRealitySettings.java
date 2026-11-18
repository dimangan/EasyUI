package ru.dimangan.easy_ui.dto.inbound.security;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InboundRealitySettings {
    private boolean show = false;
    private int xver = 0;
    private String target;
    private List<String> serverNames;
    private String privateKey;
    private List<String> shortIds = generateShortIds();
    private String mldsa65Seed;
    private Settings settings;
    private String minClientVer = "";
    private String maxClientVer = "";
    private int maxTimediff = 0;

    public InboundRealitySettings(String target, String privateKey, String publicKey, String mldsa65Seed, String mldsa65Verify) {
        this.target = String.format("%s:443", target);
        this.serverNames = List.of(target);
        this.privateKey = privateKey;
        this.mldsa65Seed = mldsa65Seed;
        this.settings = new Settings(publicKey, mldsa65Verify, target);
    }

    @Data
    public static class Settings{
        private String publicKey;
        private String fingerprint = "firefox";
        private String spiderX = String.format("/%s", RandomStringUtils.secure().next(15, true, true));
        private String mldsa65Verify;
        private String serverName;

        public Settings(String publicKey, String mldsa65Verify, String serverName) {
            this.publicKey = publicKey;
            this.mldsa65Verify = mldsa65Verify;
            this.serverName = serverName;
        }
    }

    private List<String> generateShortIds(){
        Random random = new Random(System.currentTimeMillis());
        List<String> shortIds = new ArrayList<>();
        for(int i = 0; i < 8; i ++){
            shortIds.add(UUID.randomUUID().toString().replace("-", "").substring(0, random.nextInt(1, 9) * 2));
        }
        return shortIds;
    }
}
