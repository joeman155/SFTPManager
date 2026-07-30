package com.sftpmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminPageController {

    @GetMapping
    public String admin() {
        return "forward:/admin.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/admin-login.html";
    }

    @GetMapping("/denied")
    public String denied() {
        return "forward:/admin-denied.html";
    }
}
