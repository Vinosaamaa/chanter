package com.chanter.gateway.security;

import io.netty.util.NetUtil;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Socket identity is authoritative unless the exact peer is a configured proxy. */
public final class TrustedClientIdentity {
    private final Set<String> proxies;

    public TrustedClientIdentity(List<String> addresses) {
        proxies = addresses.stream().map(TrustedClientIdentity::literal).collect(Collectors.toUnmodifiableSet());
    }

    public String resolve(InetSocketAddress remote, List<String> forwarded) {
        if (remote == null || remote.getAddress() == null) return "unknown";
        String peer = address(remote.getAddress().getAddress());
        if (!proxies.contains(peer) || forwarded == null || forwarded.isEmpty()) return peer;
        if (forwarded.size() != 1) throw new IllegalArgumentException("Ambiguous proxy identity");
        return literal(forwarded.getFirst());
    }

    private static String literal(String value) {
        if (value == null || value.length() > 64 || value.contains("%")
                || (!NetUtil.isValidIpV4Address(value) && !NetUtil.isValidIpV6Address(value))) {
            throw new IllegalArgumentException("A literal proxy IP address is required");
        }
        byte[] bytes = NetUtil.createByteArrayFromIpAddressString(value);
        if (bytes == null) throw new IllegalArgumentException("Invalid proxy IP address");
        return address(bytes);
    }

    private static String address(byte[] bytes) {
        // The JDK also collapses IPv4-mapped 16-byte addresses into the canonical IPv4 form.
        try { return InetAddress.getByAddress(bytes).getHostAddress(); }
        catch (UnknownHostException invalid) { throw new IllegalArgumentException("Invalid IP address"); }
    }
}
