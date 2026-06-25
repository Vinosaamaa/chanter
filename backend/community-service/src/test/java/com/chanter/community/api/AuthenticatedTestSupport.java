package com.chanter.community.api;

import com.chanter.common.auth.AuthHeaders;
import java.util.UUID;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

public final class AuthenticatedTestSupport {

    private AuthenticatedTestSupport() {
    }

    public static RequestPostProcessor asUser(UUID userId) {
        return request -> {
            request.addHeader(AuthHeaders.USER_ID, userId.toString());
            return request;
        };
    }
}
