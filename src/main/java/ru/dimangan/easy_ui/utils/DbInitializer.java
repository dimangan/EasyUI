package ru.dimangan.easy_ui.utils;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.dimangan.easy_ui.db.entities.Admin;
import ru.dimangan.easy_ui.db.entities.SecuredKey;
import ru.dimangan.easy_ui.db.repositories.AdminRepository;
import ru.dimangan.easy_ui.db.repositories.SecuredKeyRepository;

import java.io.ByteArrayOutputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbInitializer implements CommandLineRunner {
    private final SecuredKeyRepository securedKeyRepository;
    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(@Nullable String... args) {
        initializeAdminUser(args);
        initializeKeyPair();
    }

    /**
     * Создание дефолтного админ-пользователя в бд
     * @param adminArgs аргументы для создания<br>
     * Указываются в формате -u=username для задания логина и -p=password для задания пароля<br>
     * Если аргументы отсутствуют, то создается admin:admin
     */
    private void initializeAdminUser(@Nullable String... adminArgs){
        String username = "admin";
        String passwordRaw = "admin";
        if(adminArgs != null){
            boolean gotUsername = false;
            boolean gotPassword = false;
            for(String arg: adminArgs){
                if(!gotUsername && arg.contains("-u=")){
                    String usernameArg = arg.substring(arg.indexOf("-u=") + 3);
                    if(StringUtils.isNotEmpty(usernameArg)){
                        username = usernameArg;
                        gotUsername = true;
                    }
                }
                else if(!gotPassword && arg.contains("-p=")){
                    String passwordArg = arg.substring(arg.indexOf("-p=") + 3);
                    if(StringUtils.isNotEmpty(passwordArg)){
                        passwordRaw = passwordArg;
                        gotPassword = true;
                    }
                }
                if(gotUsername && gotPassword){
                    break;
                }
            }
        }
        if(adminRepository.findByUsername(username).isEmpty()){
            Admin admin = new Admin();
            admin.setUsername(username);
            admin.setPassword(passwordEncoder.encode(passwordRaw));
            adminRepository.save(admin);
            log.info("[SETUP] Добавлен админ {}:{}", username, passwordRaw);
        }
    }

    /**
     * Создание дефолтной пары ключей public и private для дальнейшего подключения к серверам
     */
    private void initializeKeyPair(){
        if(securedKeyRepository.findOneSecuredKeyByIsDefaultIsTrue().isEmpty()){
            try {
                JSch jSch = new JSch();
                KeyPair keyPair = KeyPair.genKeyPair(jSch, KeyPair.RSA, 2048);

                ByteArrayOutputStream privateKeyOut = new ByteArrayOutputStream();
                keyPair.writePrivateKey(privateKeyOut);
                String privateKey = privateKeyOut.toString();

                ByteArrayOutputStream publicKeyOut = new ByteArrayOutputStream();
                keyPair.writePublicKey(publicKeyOut, null);
                String publicKey = publicKeyOut.toString();

                SecuredKey securedKey = new SecuredKey();
                securedKey.setPrivateKey(privateKey);
                securedKey.setPublicKey(publicKey);
                securedKey.setName("Default");
                securedKey.setDefault(true);
                securedKeyRepository.save(securedKey);
                log.info("[SETUP] Добавлена пара дефолтных ключей");
            }
            catch (Exception e) {
                log.error("[SETUP] Не удалось сгенерировать пару ключей по умолчанию:", e);
            }
        }
    }
}
