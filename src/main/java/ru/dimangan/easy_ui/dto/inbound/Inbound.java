package ru.dimangan.easy_ui.dto.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.dimangan.easy_ui.dto.inbound.security.InboundCertificate;
import ru.dimangan.easy_ui.dto.inbound.security.InboundRealitySettings;
import ru.dimangan.easy_ui.dto.inbound.security.InboundTlsSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundGrpcSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundHysteriaSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundTcpSettings;
import ru.dimangan.easy_ui.dto.inbound.stream.InboundXHttpSettings;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Inbound {
    private Integer id;
    private boolean enable = true;
    private String remark;
    private String tag;
    private String protocol;
    private String listen = "";
    private int port;
    private InboundStream streamSettings = new InboundStream();
    private InboundSniffing sniffing = new InboundSniffing();
    private Map<String, Object> settings = Map.of("decryption", "none", "encryption", "none");

    //Только для фронта - показывает, что инбаунд добавлен к пользователю
    private boolean peeked;
    //Только для фронта - новое значение для peeked
    private boolean newPeeked;


    public static Inbound hysteria(String name, int port){
        Inbound hysteria = new Inbound(name, port);
        hysteria.streamSettings.setHysteriaSettings(new InboundHysteriaSettings());
        hysteria.streamSettings.setFinalmask(InboundMask.udpMask());
        hysteria.protocol = "hysteria";
        hysteria.streamSettings.setNetwork("hysteria");
        return hysteria;
    }

    public static Inbound vlessTcp(String name, int port){
        Inbound tcp = new Inbound(name, port);
        tcp.streamSettings.setTcpSettings(new InboundTcpSettings());
        tcp.protocol = "vless";
        tcp.streamSettings.setNetwork("tcp");
        return tcp;
    }

    public static Inbound vlessGrpc(String name, int port, String sni){
        Inbound grpc = new Inbound(name, port);
        grpc.streamSettings.setGrpcSettings(new InboundGrpcSettings(sni));
        grpc.protocol = "vless";
        grpc.streamSettings.setNetwork("grpc");
        return grpc;
    }

    public static Inbound vlessXhttp(String name, int port, String path){
        Inbound xhttp = new Inbound(name, port);
        xhttp.streamSettings.setXhttpSettings(new InboundXHttpSettings(path));
        xhttp.protocol = "vless";
        xhttp.streamSettings.setNetwork("xhttp");
        return xhttp;
    }

    private Inbound(String name, int port){
        this.remark = name;
        this.port = port;
    }

    public void setTlsSecurity(String sni, List<InboundCertificate> certificates){
        this.streamSettings.setSecurity("tls");
        this.streamSettings.setTlsSettings(new InboundTlsSettings(sni, certificates));
        if(this.protocol.equals("vless") && !this.streamSettings.getSecurity().equals("reality")){
            this.streamSettings.setFinalmask(InboundMask.tcpMask());
        }
    }

    public void setRealitySecurity(String sni, String privateKey, String publicKey, String mldsa65Seed, String mldsa65Verify){
        this.streamSettings.setSecurity("reality");
        this.streamSettings.setRealitySettings(new InboundRealitySettings(sni, privateKey, publicKey, mldsa65Seed, mldsa65Verify));
    }
}
