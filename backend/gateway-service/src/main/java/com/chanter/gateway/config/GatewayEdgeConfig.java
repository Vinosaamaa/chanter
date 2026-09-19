package com.chanter.gateway.config;

import com.chanter.gateway.security.ProxyBoundaryFilter;
import com.chanter.gateway.security.CanonicalForwardedHeadersFilter;
import com.chanter.gateway.security.TrustedClientIdentity;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayEdgeConfig {
    @Bean TrustedClientIdentity trustedClientIdentity(@Value("${chanter.edge.trusted-proxy-addresses:}") String addresses) {
        return new TrustedClientIdentity(Arrays.stream(addresses.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList());
    }

    @Bean ProxyBoundaryFilter proxyBoundaryFilter(TrustedClientIdentity identities,
            @Value("${chanter.edge.public-origin:}") String publicOrigin) {
        return new ProxyBoundaryFilter(identities, publicOrigin);
    }

    @Bean CanonicalForwardedHeadersFilter canonicalForwardedHeadersFilter(ProxyBoundaryFilter boundary) {
        return new CanonicalForwardedHeadersFilter(boundary);
    }
}
