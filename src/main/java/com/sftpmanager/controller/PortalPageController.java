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

    @GetMapping("/verify-success")
    public String verifySuccess() { return "forward:/portal-verify.html"; }

    @GetMapping("/verify-expired")
    public String verifyExpired() { return "forward:/portal-verify.html"; }

    @GetMapping("/verify-invalid")
    public String verifyInvalid() { return "forward:/portal-verify.html"; }

    @GetMapping("/verify-already")
    public String verifyAlready() { return "forward:/portal-verify.html"; }
}
