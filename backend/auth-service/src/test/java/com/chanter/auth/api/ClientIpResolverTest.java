package com.chanter.auth.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    @Test
    void usesFirstXForwardedForHopBehindGateway() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.50, 10.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void fallsBackToRemoteAddrWhenForwardedHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void ignoresBlankForwardedHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(ClientIpResolver.resolve(request)).isEqualTo("192.0.2.10");
    }
}
