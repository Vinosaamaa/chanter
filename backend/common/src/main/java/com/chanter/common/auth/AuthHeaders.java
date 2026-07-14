package com.chanter.common.auth;

public final class AuthHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String INTERNAL_SERVICE_TOKEN = "X-Chanter-Internal-Service-Token";

    private AuthHeaders() {
    }
}
