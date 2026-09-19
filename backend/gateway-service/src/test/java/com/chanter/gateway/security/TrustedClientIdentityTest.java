package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrustedClientIdentityTest {
    private final TrustedClientIdentity identities = new TrustedClientIdentity(List.of("172.30.45.2", "2001:db8::2"));

    @Test void anUntrustedPeerCannotChooseItsAddressThroughHeaders() {
        assertThat(identities.resolve(new InetSocketAddress("192.0.2.8", 1234), List.of("198.51.100.9")))
                .isEqualTo("192.0.2.8");
        assertThat(identities.resolve(new InetSocketAddress("192.0.2.8", 1234), List.of("spoofed, chain")))
                .isEqualTo("192.0.2.8");
    }

    @Test void onlyAnExactTrustedPeerMaySupplyOneLiteralIp() {
        assertThat(identities.resolve(new InetSocketAddress("172.30.45.2", 1234), List.of("198.51.100.9")))
                .isEqualTo("198.51.100.9");
        assertThat(identities.resolve(new InetSocketAddress("172.30.45.3", 1234), List.of("198.51.100.9")))
                .isEqualTo("172.30.45.3");
        assertThat(identities.resolve(new InetSocketAddress("2001:db8::2", 1234), List.of("2001:db8::9")))
                .isEqualTo("2001:db8:0:0:0:0:0:9");
    }

    @Test void malformedAmbiguousAndDnsValuesFailClosed() {
        var proxy = new InetSocketAddress("172.30.45.2", 1234);
        for (var values : List.of(List.of("198.51.100.9, 192.0.2.3"), List.of("198.51.100.9", "192.0.2.3"),
                List.of("localhost"), List.of("127.1"), List.of("fe80::1%eth0"), List.of("198.51.100.9:1234"))) {
            assertThatThrownBy(() -> identities.resolve(proxy, values)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new TrustedClientIdentity(List.of("proxy.internal")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void mappedIpv6SocketsAndLiteralProxyAddressesUseTheSameIdentity() throws Exception {
        byte[] mapped = io.netty.util.NetUtil.createByteArrayFromIpAddressString("::ffff:172.30.45.2");
        var socket = new InetSocketAddress(java.net.Inet6Address.getByAddress(null, mapped, -1), 1234);
        assertThat(identities.resolve(socket, List.of("198.51.100.9"))).isEqualTo("198.51.100.9");
        assertThat(new TrustedClientIdentity(List.of("::ffff:172.30.45.2"))
                .resolve(new InetSocketAddress("172.30.45.2", 1234), List.of("198.51.100.9"))).isEqualTo("198.51.100.9");
    }
}
