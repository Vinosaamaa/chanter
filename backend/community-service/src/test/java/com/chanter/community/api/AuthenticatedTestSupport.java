package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class AuthenticatedTestSupport {

    static final String TEST_INTERNAL_SERVICE_TOKEN = "test-internal-service-token-for-community";

    private AuthenticatedTestSupport() {
    }

    public static RequestPostProcessor asUser(UUID userId) {
        return request -> {
            request.addHeader(AuthHeaders.USER_ID, userId.toString());
            request.addHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN, TEST_INTERNAL_SERVICE_TOKEN);
            return request;
        };
    }
}
