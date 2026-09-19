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
        var controller = new InternalCourseScopeController(repository, "test-internal-service-token-for-community");
        UUID course = UUID.randomUUID(), server = UUID.randomUUID();
        when(repository.findStudyServerIdByCourseId(course)).thenReturn(Optional.of(server));
        assertThatThrownBy(() -> controller.scope(course, null)).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(repository);
        assertThat(controller.scope(course, "test-internal-service-token-for-community"))
                .isEqualTo(new InternalCourseScopeController.Scope(course, server));
        assertThatThrownBy(() -> controller.scope(UUID.randomUUID(), "test-internal-service-token-for-community"))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
    }
}
