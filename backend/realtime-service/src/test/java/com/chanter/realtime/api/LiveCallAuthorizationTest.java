package com.chanter.realtime.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.realtime.application.DirectMessageCallAuthorizer;
import com.chanter.realtime.application.DirectMessageCallStore;
import com.chanter.realtime.domain.DirectMessageCall;
import com.chanter.realtime.domain.DirectMessageCallStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LiveCallAuthorizationTest {
    @LocalServerPort int port;
    WebTestClient client;
    @BeforeEach void connect() { client=WebTestClient.bindToServer().baseUrl("http://localhost:"+port).build(); }
    @MockitoBean DirectMessageCallStore calls;
    @MockitoBean DirectMessageCallAuthorizer authority;
    @org.springframework.beans.factory.annotation.Autowired com.chanter.realtime.websocket.DirectMessageCallHub hub;

    @Test void periodicRevalidationEndsTheStoredCallAfterABlock() {
        UUID id=UUID.randomUUID(),caller=UUID.randomUUID(),callee=UUID.randomUUID();
        var call=new DirectMessageCall(id,caller,callee,DirectMessageCallStatus.ACTIVE,Instant.now());
        when(calls.findActiveCallForUser(caller)).thenReturn(Optional.of(call));
        when(authority.requireCallAccess(caller,callee)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        when(calls.endIfPresent(id)).thenReturn(Optional.of(call));
        hub.reconcileUser(caller).block(java.time.Duration.ofSeconds(5));
        verify(calls).endIfPresent(id);
    }

    @Test void authorityOutageStopsMediaButIsNotReportedAsSuccessfulReconciliation() {
        UUID id=UUID.randomUUID(),caller=UUID.randomUUID(),callee=UUID.randomUUID();
        var call=new DirectMessageCall(id,caller,callee,DirectMessageCallStatus.ACTIVE,Instant.now());
        when(calls.findActiveCallForUser(caller)).thenReturn(Optional.of(call));
        when(authority.requireCallAccess(caller,callee)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));
        when(calls.endIfPresent(id)).thenReturn(Optional.of(call));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> hub.reconcileUser(caller).block(java.time.Duration.ofSeconds(5)))
                .isInstanceOf(ResponseStatusException.class);
        verify(calls).endIfPresent(id);
    }

    @Test void aSavedActiveCallDoesNotBypassANewBlockOrEndedCall() {
        UUID call=UUID.randomUUID(),caller=UUID.randomUUID(),callee=UUID.randomUUID();
        String path="/internal/v1/dm-calls/"+call+"/media-access?userId="+caller;
        when(calls.findById(call)).thenReturn(Optional.of(new DirectMessageCall(call,caller,callee,DirectMessageCallStatus.ACTIVE,Instant.now())));
        when(authority.requireCallAccess(caller,callee)).thenReturn(Mono.empty());
        client.get().uri(path).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-realtime")
                .exchange().expectStatus().isNoContent();
        when(authority.requireCallAccess(caller,callee)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
        client.get().uri(path).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-realtime")
                .exchange().expectStatus().isForbidden();
        when(calls.findById(call)).thenReturn(Optional.empty());
        client.get().uri(path).header(AuthHeaders.INTERNAL_SERVICE_TOKEN,"test-internal-service-token-for-realtime")
                .exchange().expectStatus().isNotFound();
    }

    @Test void internalCallStateCannotBeReadWithAUserHeaderAlone() {
        client.get().uri("/internal/v1/dm-calls/"+UUID.randomUUID()+"/media-access?userId="+UUID.randomUUID())
                .header(AuthHeaders.USER_ID,UUID.randomUUID().toString()).exchange().expectStatus().isUnauthorized();
    }
}
