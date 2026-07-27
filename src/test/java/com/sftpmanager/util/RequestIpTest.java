package com.sftpmanager.util;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestIpTest {

    private HttpServletRequest requestWith(String xff, String remoteAddr) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(xff);
        when(request.getRemoteAddr()).thenReturn(remoteAddr);
        return request;
    }

    @Test
    void usesRemoteAddrWhenNoForwardedHeader() {
        assertThat(RequestIp.of(requestWith(null, "10.0.0.5"))).isEqualTo("10.0.0.5");
    }

    @Test
    void usesRemoteAddrWhenForwardedHeaderIsBlank() {
        assertThat(RequestIp.of(requestWith("   ", "10.0.0.5"))).isEqualTo("10.0.0.5");
    }

    @Test
    void usesForwardedHeaderWhenPresent() {
        assertThat(RequestIp.of(requestWith("203.0.113.7", "10.0.0.5"))).isEqualTo("203.0.113.7");
    }

    @Test
    void takesFirstEntryFromForwardedChain() {
        assertThat(RequestIp.of(requestWith("203.0.113.7, 198.51.100.2, 10.0.0.1", "10.0.0.5")))
            .isEqualTo("203.0.113.7");
    }

    @Test
    void trimsWhitespaceAroundForwardedEntry() {
        assertThat(RequestIp.of(requestWith("  203.0.113.7 , 10.0.0.1", "10.0.0.5")))
            .isEqualTo("203.0.113.7");
    }
}
