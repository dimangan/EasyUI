package ru.dimangan.easy_ui.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.dimangan.easy_ui.auth.AdminService;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AdminService adminService;

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> login){
        User admin = new User(login.get("username"), login.get("password"), Collections.emptyList());
        return adminService.authenticateUser(admin);
    }

    @PostMapping("/change-credentials")
    public String changeCredentials(Principal principal, @RequestBody Map<String, String> newCredentials){
        return adminService.changeCredentials(principal.getName(), newCredentials);
    }
}
