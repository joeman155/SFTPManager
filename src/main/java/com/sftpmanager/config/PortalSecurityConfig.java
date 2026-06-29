package com.sftpmanager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class PortalSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain portalFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/portal/**", "/oauth2/**", "/login/oauth2/**", "/portal-login.html", "/portal.html")
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/portal/login", "/portal-login.html").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .anyRequest().authenticated()
            )
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
