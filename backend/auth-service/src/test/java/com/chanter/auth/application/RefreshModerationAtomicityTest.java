package com.chanter.auth.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chanter.auth.domain.AuthUser;
import com.chanter.auth.moderation.ModerationRestrictions;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class RefreshModerationAtomicityTest {
    @Autowired AuthSessionService sessions;
    @Autowired AuthUserRepository users;
    @MockitoSpyBean ModerationRestrictions moderation;

    @Test void authorityFailureDoesNotConsumeRefreshButReplayStillRevokesItsFamily() {
        UUID user=UUID.randomUUID();
        var account=new AuthUser(user,user+"@refresh-moderation.test","unusable-test-password","Member",true,Instant.now());
        users.save(account);
        var initial=sessions.issueSessionForUser(account);
        doThrow(new DataAccessResourceFailureException("authority unavailable")).when(moderation).requireActiveAccount(user);
        assertThatThrownBy(() -> sessions.refresh(initial.refreshToken())).isInstanceOf(DataAccessResourceFailureException.class);
        doCallRealMethod().when(moderation).requireActiveAccount(user);
        var replacement=sessions.refresh(initial.refreshToken());
        assertThat(replacement.user().id()).isEqualTo(user);
        assertThatThrownBy(() -> sessions.refresh(initial.refreshToken())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sessions.refresh(replacement.refreshToken())).isInstanceOf(ResponseStatusException.class);
    }
}
