package com.chanter.analytics.infra;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

final class DownstreamRestClientErrors {

    private DownstreamRestClientErrors() {
    }

    static ResponseStatusException mapResourceAccess(
            ResourceAccessException exception,
            String timeoutMessage,
            String unreachableMessage
    ) {
        if (isTimeout(exception)) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, timeoutMessage, exception);
        }

        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, unreachableMessage, exception);
    }

    static ResponseStatusException mapRestClient(RestClientException exception, String unreachableMessage) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, unreachableMessage, exception);
    }

    private static boolean isTimeout(ResourceAccessException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpConnectTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
