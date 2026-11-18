package ru.dimangan.easy_ui.services.secured;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Logger;
import com.jcraft.jsch.Session;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import ru.dimangan.easy_ui.config.JschProperties;
import ru.dimangan.easy_ui.db.entities.SecuredKey;
import ru.dimangan.easy_ui.db.entities.Server;

@Service
@Slf4j
@RequiredArgsConstructor
public class SecureSessionService {
    private final JschProperties jschProperties;

    /**
     * Открытие защищенной сессии с сервером
     * @param server сервер для открытия сессии
     * @param useLogger флаг использования логирования Jsch и отправляемых команд
     * @return Session открытая Jsch сессия
     * @throws JSchException в случае ошибки при открытии сессии
     */
    public Session session(@NonNull Server server, boolean useLogger) throws JSchException{
        var jsch = new JSch();
        if(useLogger){
            jsch.setInstanceLogger(new Logger() {
                @Override
                public boolean isEnabled(int level) {
                    return true;
                }

                @Override
                public void log(int level, String message) {
                    log.info("[Jsch] {}", message);
                }
            });
        }
        SecuredKey securedKey = server.getSecuredKey();
        if(server.isKeyAdded() && StringUtils.isNotEmpty(server.getSecuredKey().getPrivateKey())){
            jsch.addIdentity(securedKey.getName(), securedKey.getPrivateKey().getBytes(), securedKey.getPublicKey().getBytes(), null);
        }
        var session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
        if(!server.isKeyAdded() && StringUtils.isNotEmpty(server.getPassword())){
            session.setPassword(server.getPassword().getBytes());
        }
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(jschProperties.getSessionConnectionTimeout());
        return session;
    }

}
