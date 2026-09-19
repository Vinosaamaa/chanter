package com.chanter.gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RequestBudgetPolicyTest {
    @Test void expensiveAndSensitiveOperationsHaveDedicatedBudgets() {
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/auth/register")).isEqualTo(RequestBudgetPolicy.REGISTRATION);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/auth/forgot-password")).isEqualTo(RequestBudgetPolicy.RECOVERY);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/auth/logout")).isEqualTo(RequestBudgetPolicy.LOGOUT);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.GET, "/api/v1/realtime/connect")).isEqualTo(RequestBudgetPolicy.RECONNECT);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/course-channels/abc/support-questions/def/assistant-answer")).isEqualTo(RequestBudgetPolicy.AI);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/courses/abc/course-resources")).isEqualTo(RequestBudgetPolicy.UPLOAD);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.GET, "/api/v1/course-resources/abc/download")).isEqualTo(RequestBudgetPolicy.DOWNLOAD);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.GET, "/api/v1/study-servers/abc/search")).isEqualTo(RequestBudgetPolicy.SEARCH);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/study-server-invitations")).isEqualTo(RequestBudgetPolicy.SENSITIVE);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/direct-messages/abc/messages")).isEqualTo(RequestBudgetPolicy.MESSAGE);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.DELETE, "/api/v1/admin/users/abc")).isEqualTo(RequestBudgetPolicy.SENSITIVE);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.POST, "/api/v1/new-future-write")).isEqualTo(RequestBudgetPolicy.WRITE);
        assertThat(RequestBudgetPolicy.classify(HttpMethod.GET, "/api/v1/me/home-summary")).isEqualTo(RequestBudgetPolicy.READ);
    }

    @Test void tenantHintsAreBoundedAndNeverAcceptedAsAuthority() {
        String tenant = "11111111-1111-1111-1111-111111111111";
        assertThat(RequestBudgetPolicy.tenantHint("/api/v1/study-servers/" + tenant + "/search")).isEqualTo(tenant);
        assertThat(RequestBudgetPolicy.tenantHint("/api/v1/study-servers/arbitrary/search")).isNull();
        assertThat(RequestBudgetPolicy.tenantHint("/api/v1/courses/" + tenant)).isNull();
    }
}
