package com.chanter.community.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chanter.community.config.LiveKitProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class LiveKitJoinGuardTest {
    static final String SECRET="livekit-join-guard-test-secret-32bytes";
    private final LiveMediaAccess access=mock(LiveMediaAccess.class);
    private final LiveKitJoinGuard guard=new LiveKitJoinGuard(new LiveKitProperties("ws://localhost:7880","test-key",SECRET),access);

    @Test void theSameSignedTokenIsDeniedOnReconnectAfterAuthorityChanges() throws Exception {
        UUID user=UUID.randomUUID();
        String room="voice-"+UUID.randomUUID();
        String token=token(user.toString(),room,true,true,60,SECRET);
        guard.requireJoin(token);
        verify(access).requireAllowed(room,user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAllowed(room,user);
        assertThatThrownBy(() -> guard.requireJoin(token)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void wrongSignatureAndExpiredTokensNeverReachProductAuthority() throws Exception {
        String user=UUID.randomUUID().toString(),room="voice-"+UUID.randomUUID();
        assertThatThrownBy(() -> guard.requireJoin(token(user,room,true,true,60,"different-signing-secret-32bytes-long")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.requireJoin(token(user,room,true,true,-1,SECRET)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(access);
    }

    @Test void releaseProbeCannotPublishSubscribeOrReachAUserRoom() throws Exception {
        String probe="release-health-"+UUID.randomUUID();
        guard.requireJoin(token(probe,"__chanter_release_health",false,false,60,SECRET));
        assertThatThrownBy(() -> guard.requireJoin(token(probe,"__chanter_release_health",true,false,60,SECRET)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.requireJoin(token(probe,"__chanter_release_health",false,true,60,SECRET)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.requireJoin(token(probe,"__chanter_release_health",false,false,600,SECRET)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> guard.requireJoin(token(probe,"voice-"+UUID.randomUUID(),false,false,60,SECRET)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(access);
    }

    static String token(String user,String room,boolean publish,boolean subscribe,int expiresIn,String secret) throws Exception {
        Instant now=Instant.now();
        var claims=new JWTClaimsSet.Builder().issuer("test-key").subject(user).notBeforeTime(Date.from(now.minusSeconds(10)))
                .expirationTime(Date.from(now.plusSeconds(expiresIn)))
                .claim("video",Map.of("roomJoin",true,"room",room,"canPublish",publish,"canSubscribe",subscribe,"canPublishData",false)).build();
        var token=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),claims);
        token.sign(new MACSigner(secret));
        return token.serialize();
    }
}
