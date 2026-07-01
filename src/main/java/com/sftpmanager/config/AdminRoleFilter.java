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

    // Paths that don't need role check
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

        // Check if session has ADMIN_VERIFIED flag
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("ADMIN_VERIFIED"))) {
            chain.doFilter(request, response); return;
        }

        // Not verified — check role from OAuth principal
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User principal) {
            String email = principal.getAttribute("email");
            boolean isAdmin = userRepository.findByEmail(email)
                .map(user -> user.getRole() != null && user.getRole() == 10)
                .orElse(false);

            if (isAdmin) {
                request.getSession().setAttribute("ADMIN_VERIFIED", true);
                chain.doFilter(request, response);
                return;
            }
        }

        // Not admin — clear session and redirect to denied
        SecurityContextHolder.clearContext();
        if (session != null) session.invalidate();
        response.sendRedirect("/admin-denied.html");
    }
}
