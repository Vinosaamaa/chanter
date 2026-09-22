package com.chanter.community.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.chanter.community.application.CourseRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InternalCourseScopeControllerTest {
    @Test void authoritativeScopeRequiresInternalAuthenticationAndAnExistingCourse() {
        var repository = mock(CourseRepository.class);
        var data=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DB_CLOSE_DELAY=-1","sa","");
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(data);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(data));
        jdbc.execute(com.chanter.common.lifecycle.TerminalReapplyStore.SCHEMA);
        jdbc.execute(com.chanter.common.events.DurableOutbox.SCHEMA);
        jdbc.execute(com.chanter.common.lifecycle.SourceDeletionRequests.SCHEMA);
        var deletions=new com.chanter.common.lifecycle.SourceDeletionRequests("STUDY_SERVER",jdbc,tx,
                new com.chanter.common.events.DurableOutbox(jdbc,tx,"community",java.time.Clock.systemUTC()),
                new com.chanter.common.lifecycle.AccountDeletionProtocol(new com.fasterxml.jackson.databind.ObjectMapper()));
        var controller = new InternalCourseScopeController(repository,deletions, "test-internal-service-token-for-community");
        UUID course = UUID.randomUUID(), server = UUID.randomUUID();
        when(repository.findStudyServerIdByCourseId(course)).thenReturn(Optional.of(server));
        assertThatThrownBy(() -> controller.scope(course, null)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(repository);
        assertThat(controller.scope(course, "test-internal-service-token-for-community"))
                .isEqualTo(new InternalCourseScopeController.Scope(course, server));
        deletions.request(server,UUID.randomUUID(),() -> {});
        assertThatThrownBy(() -> controller.scope(course,"test-internal-service-token-for-community"))
                .isInstanceOfSatisfying(ResponseStatusException.class,failure -> assertThat(failure.getStatusCode().value()).isEqualTo(410));
        assertThatThrownBy(() -> controller.scope(UUID.randomUUID(), "test-internal-service-token-for-community"))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
    }
}
