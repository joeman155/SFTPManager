package com.sftpmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class PortalSecurityConfig {

    private final EmailSessionFilter emailSessionFilter;

    public PortalSecurityConfig(EmailSessionFilter emailSessionFilter) {
        this.emailSessionFilter = emailSessionFilter;
    }

    // Matches only the portal's own OAuth2 callback (google-portal),
    // not the admin's (google-admin) — this is what fixes the cross-routing bug
    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                "/portal/**",
                "/portal-login.html",
                "/portal.html",
                "/reset-password.html",
                "/oauth2/authorization/google-portal",
                "/login/oauth2/code/google-portal"
            )
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/portal/login", "/portal-login.html").permitAll()
                .requestMatchers("/portal/api/auth/**").permitAll()
                .requestMatchers("/portal/reset-password", "/reset-password.html").permitAll()
                .requestMatchers("/oauth2/authorization/google-portal", "/login/oauth2/code/google-portal").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(emailSessionFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth -> oauth
                .loginPage("/portal/login")
                .defaultSuccessUrl("/portal", true)
                .failureUrl("/portal/login?error=true")
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/portal/logout"))
                .logoutSuccessUrl("/portal/login")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            );
        return http.build();
    }
}
