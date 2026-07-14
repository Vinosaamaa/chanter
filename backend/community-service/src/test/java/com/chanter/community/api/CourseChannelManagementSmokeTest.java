package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
import com.chanter.community.application.CourseService;
import com.chanter.community.domain.ChannelKind;
import com.chanter.community.domain.CourseChannel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseChannelManagementSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthUserDirectoryClient userDirectory;

    @Autowired
    private CourseService courseService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void resetDirectory() {
        userDirectory.reset();
    }

    @Test
    void instructorCreatesCohortTextChannelVisibleToEnrolledLearner() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "channel-instructor@spring.example", "Channel Instructor");
        userDirectory.register(learnerUserId, "channel-learner@spring.example", "Channel Learner");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "channel-learner@spring.example");

        MvcResult createResult = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "study-group",
                                "kind", "TEXT"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cohortId").value(course.cohortId().toString()))
                .andExpect(jsonPath("$.name").value("study-group"))
                .andExpect(jsonPath("$.kind").value("TEXT"))
                .andReturn();
        UUID channelId = UUID.fromString(
                objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
        );
        assertThat(createResult.getResponse().getHeader("Location"))
                .isEqualTo("http://localhost/api/v1/course-channels/" + channelId);

        MvcResult navigationResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        course.studyServerId()
                ).with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode navigation = objectMapper.readTree(navigationResult.getResponse().getContentAsString());
        assertThat(navigation.get("courses").get(0).get("channels").toString())
                .contains(channelId.toString(), "study-group", course.cohortId().toString());
    }

    @Test
    void instructorRenamesAndArchivesCohortChannel() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "lifecycle-instructor@spring.example", "Lifecycle Instructor");
        CourseFixture course = createCourse(instructorUserId);
        UUID channelId = createChannel(course.cohortId(), instructorUserId, "project-room", "TEXT");

        mockMvc.perform(patch("/api/v1/course-channels/{channelId}", channelId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Project Lounge"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("project-lounge"));

        mockMvc.perform(delete("/api/v1/course-channels/{channelId}", channelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        MvcResult navigationResult = mockMvc.perform(get(
                        "/api/v1/study-servers/{studyServerId}/navigation",
                        course.studyServerId()
                ).with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(navigationResult.getResponse().getContentAsString()).doesNotContain(channelId.toString());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}/channel-message-access", channelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void enrolledLearnerCannotManageCohortChannels() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "manager@spring.example", "Channel Manager");
        userDirectory.register(learnerUserId, "member@spring.example", "Channel Member");
        CourseFixture course = createCourse(instructorUserId);
        enrollByEmail(course.cohortId(), instructorUserId, "member@spring.example");
        UUID channelId = createChannel(course.cohortId(), instructorUserId, "staff-room", "TEXT");

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", course.cohortId())
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "learner-room",
                                "kind", "TEXT"
                        ))))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/course-channels/{channelId}", channelId)
                        .with(asUser(learnerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "renamed-by-learner"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/course-channels/{channelId}", channelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/course-channels/{channelId}", channelId)
                        .with(asUser(learnerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("staff-room"));
    }

    @Test
    void channelNameMustContainAReadableSlug() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "slug@spring.example", "Slug Instructor");
        CourseFixture course = createCourse(instructorUserId);

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "!!!",
                                "kind", "TEXT"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void activeChannelNamesAreUniqueWithinACohortAndReusableAfterArchive() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "unique@spring.example", "Unique Instructor");
        CourseFixture course = createCourse(instructorUserId);
        UUID originalChannelId = createChannel(course.cohortId(), instructorUserId, "exam-review", "TEXT");

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Exam Review",
                                "kind", "TEXT"
                        ))))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/course-channels/{channelId}", originalChannelId)
                        .with(asUser(instructorUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", course.cohortId())
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Exam Review",
                                "kind", "TEXT"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("exam-review"));
    }

    @Test
    void identicalChannelNamesAreAllowedInDifferentCohorts() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "multi-cohort@spring.example", "Multi Cohort Instructor");
        CourseFixture course = createCourse(instructorUserId);
        UUID secondCohortId = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO cohorts (id, course_id, name, invite_code)
                        VALUES (:id, :courseId, :name, :inviteCode)
                        """)
                .param("id", secondCohortId)
                .param("courseId", course.courseId())
                .param("name", "Fall 2026")
                .param("inviteCode", UUID.randomUUID())
                .update();

        createChannel(course.cohortId(), instructorUserId, "cohort-lounge", "TEXT");
        createChannel(secondCohortId, instructorUserId, "cohort-lounge", "TEXT");
    }

    @Test
    void concurrentChannelCreationAllocatesDistinctPositions() throws Exception {
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(instructorUserId, "concurrent@spring.example", "Concurrent Instructor");
        CourseFixture course = createCourse(instructorUserId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<CourseChannel> first = executor.submit(() -> createConcurrentChannel(
                    ready,
                    start,
                    course.cohortId(),
                    instructorUserId,
                    "project-alpha"
            ));
            Future<CourseChannel> second = executor.submit(() -> createConcurrentChannel(
                    ready,
                    start,
                    course.cohortId(),
                    instructorUserId,
                    "project-beta"
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(List.of(
                    first.get(10, TimeUnit.SECONDS).position(),
                    second.get(10, TimeUnit.SECONDS).position()
            )).containsExactlyInAnyOrder(4, 5);
        } finally {
            executor.shutdownNow();
        }
    }

    private CourseChannel createConcurrentChannel(
            CountDownLatch ready,
            CountDownLatch start,
            UUID cohortId,
            UUID actorUserId,
            String name
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent channel start gate timed out");
        }
        return courseService.createCohortChannel(cohortId, actorUserId, name, ChannelKind.TEXT);
    }

    private UUID createChannel(UUID cohortId, UUID actorUserId, String name, String kind) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/cohorts/{cohortId}/channels", cohortId)
                        .with(asUser(actorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "kind", kind
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void enrollByEmail(UUID cohortId, UUID instructorUserId, String email) throws Exception {
        mockMvc.perform(post("/api/v1/cohorts/{cohortId}/enrollments", cohortId)
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());
    }

    private CourseFixture createCourse(UUID instructorUserId) throws Exception {
        MvcResult serverResult = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Channel Study Hub"))))
                .andExpect(status().isCreated())
                .andReturn();
        UUID studyServerId = UUID.fromString(
                objectMapper.readTree(serverResult.getResponse().getContentAsString()).get("id").asText()
        );

        MvcResult courseResult = mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/courses",
                        studyServerId
                )
                        .with(asUser(instructorUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Channel Operations",
                                "cohortName", "Spring 2026"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode course = objectMapper.readTree(courseResult.getResponse().getContentAsString());
        return new CourseFixture(
                studyServerId,
                UUID.fromString(course.get("id").asText()),
                UUID.fromString(course.get("cohort").get("id").asText())
        );
    }

    private record CourseFixture(UUID studyServerId, UUID courseId, UUID cohortId) {
    }
}
