package ru.dimangan.easy_ui.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class AuthUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain chain)
            throws ServletException, IOException {
        try {
            if(!StringUtils.isEmpty(request.getHeader(JWT.HEADER_AUTHORIZATION)) && request.getHeader(JWT.HEADER_AUTHORIZATION).startsWith("Bearer ")) {
                Claims claims = JWT.extractClaims(request.getHeader(JWT.HEADER_AUTHORIZATION).substring(7));
                setUpSpringAuthentication(claims);
            }
            else{
                SecurityContextHolder.clearContext();
            }
            chain.doFilter(request, response);
        }
        catch (JwtException e){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.getMessage());
        }
        catch (Exception e) {
            log.error("Request {} failed:", request.getRequestURI(), e);
            throw e;
        }
    }

    private void setUpSpringAuthentication(Claims claims) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
