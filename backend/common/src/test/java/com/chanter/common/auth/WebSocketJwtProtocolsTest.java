package com.chanter.common.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WebSocketJwtProtocolsTest {

    @Test
    void extractsTwoValueForm() {
        assertThat(WebSocketJwtProtocols.extractToken(List.of("chanter-jwt, mytoken123")))
                .isEqualTo("mytoken123");
    }

    @Test
    void extractsDotForm() {
        assertThat(WebSocketJwtProtocols.extractToken(List.of("chanter-jwt.mytoken123")))
                .isEqualTo("mytoken123");
    }

    @Test
    void returnsNullWhenAbsent() {
        assertThat(WebSocketJwtProtocols.extractToken(List.of())).isNull();
        assertThat(WebSocketJwtProtocols.extractToken(null)).isNull();
        assertThat(WebSocketJwtProtocols.extractToken(List.of("other-protocol"))).isNull();
    }
}
