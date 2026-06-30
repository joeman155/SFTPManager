package com.sftpmanager.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(Map.of(
            "name",    principal.getAttribute("name")    != null ? principal.getAttribute("name")    : "",
            "email",   principal.getAttribute("email")   != null ? principal.getAttribute("email")   : "",
            "picture", principal.getAttribute("picture") != null ? principal.getAttribute("picture") : ""
        ));
    }
}
