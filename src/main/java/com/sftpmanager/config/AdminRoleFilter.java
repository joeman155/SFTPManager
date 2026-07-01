package com.sftpmanager.config;

import com.sftpmanager.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class AdminRoleFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    private static final List<String> EXCLUDED = List.of(
        "/admin-login.html", "/admin-denied.html", "/healthz",
        "/portal", "/portal-login.html", "/portal.html",
        "/oauth2/", "/login/oauth2/"
    );

    public AdminRoleFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();

        // Skip excluded paths
        boolean excluded = EXCLUDED.stream().anyMatch(uri::startsWith);
        if (excluded) { chain.doFilter(request, response); return; }

        // Only check admin routes
        boolean isAdminRoute = uri.equals("/") || uri.startsWith("/api/") ||
                               uri.startsWith("/admin/") || uri.equals("/index.html");
        if (!isAdminRoute) { chain.doFilter(request, response); return; }

        // Check OAuth2 principal directly - no session attribute needed
        // Spring Security stores the principal in the session automatically and reliably
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User principal) {
            String email = principal.getAttribute("email");
            boolean isAdmin = userRepository.findByEmail(email)
                .map(user -> user.getRole() != null && user.getRole() == 10)
                .orElse(false);

            if (isAdmin) {
                chain.doFilter(request, response);
                return;
            }

            // Authenticated but not admin - clear and redirect
            SecurityContextHolder.clearContext();
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            response.sendRedirect("/admin-denied.html");
            return;
        }

        // Not authenticated at all - Spring Security will redirect to login
        chain.doFilter(request, response);
    }
}
