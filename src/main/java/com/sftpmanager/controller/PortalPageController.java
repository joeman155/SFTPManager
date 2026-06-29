package com.sftpmanager.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/portal")
public class PortalPageController {

    @GetMapping
    public String portal() {
        return "forward:/portal.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/portal-login.html";
    }
}
