package ru.dimangan.easy_ui.auth;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.dimangan.easy_ui.db.entities.Admin;
import ru.dimangan.easy_ui.db.repositories.AdminRepository;
import ru.dimangan.easy_ui.exceptions.UserNotFoundException;

import java.util.Collections;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminService implements UserDetailsService {
    private final AdminRepository adminRepository;
    @Setter
    private AuthenticationProvider authenticationProvider;

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException("Администратор панели не найден"));
        return new User(admin.getUsername(), admin.getPassword(), Collections.emptyList());
    }

    /**
     * Генерация jwt токена для админа
     * @param userDetails данные для авторизации
     * @return строка с jwt токеном
     */
    public String authenticateUser(UserDetails userDetails) {
        var auth = authenticationProvider.authenticate(new UsernamePasswordAuthenticationToken(userDetails.getUsername(), userDetails.getPassword()));
        assert auth != null;
        var principal = auth.getPrincipal();
        assert principal != null;
        return JWT.generateToken((UserDetails) principal);
    }

    /**
     * Смена логина пароля для админа
     * @param currentUsername имя текущего администратора из токена
     * @param newCredentials новые логин и пароль
     * @return строка с новым токеном
     */
    @Transactional
    public String changeCredentials(String currentUsername, Map<String, String> newCredentials){
        String login = newCredentials.get("username");
        String password = newCredentials.get("password");
        if(StringUtils.isEmpty(login) || StringUtils.isEmpty(password)){
            throw new BadCredentialsException("Логин и пароль должны быть указаны");
        }
        Admin admin = adminRepository.findWithLockByUsername(currentUsername).orElseThrow(UserNotFoundException::new);
        admin.setUsername(login);
        admin.setPassword(password);
        adminRepository.save(admin);
        return authenticateUser(new User(login, password, Collections.emptyList()));
    }
}
