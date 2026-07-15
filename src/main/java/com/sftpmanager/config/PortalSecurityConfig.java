package com.sftpmanager.config;

import com.sftpmanager.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Configuration
public class PortalSecurityConfig {

    private final EmailSessionFilter emailSessionFilter;
    private final UserRepository userRepository;

    public PortalSecurityConfig(EmailSessionFilter emailSessionFilter, UserRepository userRepository) {
        this.emailSessionFilter = emailSessionFilter;
        this.userRepository = userRepository;
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
                .successHandler(portalSuccessHandler())
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

    // Blocks locked and closed accounts from completing Google sign-in,
    // mirroring the checks PortalAuthController does for email/password.
    private AuthenticationSuccessHandler portalSuccessHandler() {
        return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) -> {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            String email = principal.getAttribute("email");

            String block = userRepository.findByEmail(email)
                .map(user -> Boolean.TRUE.equals(user.getAccountClosed()) ? "closed"
                           : Boolean.TRUE.equals(user.getLocked()) ? "locked" : null)
                .orElse(null);

            if (block != null) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) session.invalidate();
                response.sendRedirect("/portal/login?error=" + block);
            } else {
                response.sendRedirect("/portal");
            }
        };
    }
}
