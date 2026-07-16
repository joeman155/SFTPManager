package com.sftpmanager.util;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestIp {

    private RequestIp() {}

    /**
     * Client IP for the request. Behind a load balancer / ingress the real
     * client address arrives in X-Forwarded-For (first entry); otherwise use
     * the socket address. Note: XFF is only trustworthy when the app is not
     * directly reachable from the internet (the proxy must set it).
     */
    public static String of(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
