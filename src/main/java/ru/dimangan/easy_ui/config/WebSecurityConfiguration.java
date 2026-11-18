package ru.dimangan.easy_ui.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import ru.dimangan.easy_ui.auth.AdminService;
import ru.dimangan.easy_ui.auth.AuthUserFilter;

@EnableWebSecurity
@Configuration
@RequiredArgsConstructor
public class WebSecurityConfiguration {
    private final AuthUserFilter authUserFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity){
        return httpSecurity
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeHttpRequest -> authorizeHttpRequest
                        .requestMatchers("/login", "/sub/**", "/api/private/subscription/get/**").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/", "/index.html", "/favicon.ico",
                                "/servers", "/servers/**", "/users", "/users/**",
                                "/*.css", "/*.js", "/*.map", "/*.webmanifest",
                                "/assets/**", "/media/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(authUserFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        //noinspection deprecation
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration){
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(PasswordEncoder passwordEncoder, AdminService adminService){
        DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(adminService);
        authenticationProvider.setPasswordEncoder(passwordEncoder);
        adminService.setAuthenticationProvider(authenticationProvider);
        return authenticationProvider;
    }
}
