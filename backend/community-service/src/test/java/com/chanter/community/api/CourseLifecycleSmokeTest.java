package com.chanter.community.api;

import static com.chanter.community.api.AuthenticatedTestSupport.asUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.community.infra.TestAuthUserDirectoryClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseLifecycleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAuthUserDirectoryClient userDirectory;

    @BeforeEach
    void resetDirectory() {
        userDirectory.reset();
    }

    @Test
    void ownerCreatesDraftCourseAddsCohortAssignsInstructorPublishesAndArchives() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        userDirectory.register(ownerUserId, "owner@spring.example", "Study Owner");
        userDirectory.register(instructorUserId, "instructor@spring.example", "Course Instructor");

        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        MvcResult draftResult = mockMvc.perform(post(
                        "/api/v1/study-servers/{studyServerId}/courses",
                        studyServer.id()
                )
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "CS 101 - Intro to CS",
                                "description", "Foundations of computer science"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.description").value("Foundations of computer science"))
                .andExpect(jsonPath("$.cohort").doesNotExist())
                .andReturn();
        CourseLifecycleResponse draftCourse = objectMapper.readValue(
                draftResult.getResponse().getContentAsString(),
                CourseLifecycleResponse.class
        );

        mockMvc.perform(post("/api/v1/courses/{courseId}/cohorts", draftCourse.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Spring cohort"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Spring cohort"));

        mockMvc.perform(patch("/api/v1/courses/{courseId}", draftCourse.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "CS 101 - Intro to Computer Science",
                                "description", "Updated foundations"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("CS 101 - Intro to Computer Science"))
                .andExpect(jsonPath("$.description").value("Updated foundations"));

        mockMvc.perform(patch("/api/v1/courses/{courseId}/instructor", draftCourse.id())
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "instructorUserId", instructorUserId.toString()
                        ))))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/course-catalog", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(0));

        mockMvc.perform(post("/api/v1/courses/{courseId}/publish", draftCourse.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/study-servers/{studyServerId}/course-catalog", studyServer.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses.length()").value(1))
                .andExpect(jsonPath("$.courses[0].title").value("CS 101 - Intro to Computer Science"));

        mockMvc.perform(get("/api/v1/courses/{courseId}", draftCourse.id())
                        .with(asUser(instructorUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instructorRole.userId").value(instructorUserId.toString()));

        mockMvc.perform(post("/api/v1/courses/{courseId}/unpublish", draftCourse.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/courses/{courseId}/archive", draftCourse.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/courses/{courseId}", draftCourse.id())
                        .with(asUser(ownerUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void nonOwnerCannotCreateCourseOnStudyServer() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID outsiderUserId = UUID.randomUUID();
        StudyServerResponse studyServer = createStudyServer(ownerUserId);

        mockMvc.perform(post("/api/v1/study-servers/{studyServerId}/courses", studyServer.id())
                        .with(asUser(outsiderUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Unauthorized course"
                        ))))
                .andExpect(status().isForbidden());
    }

    private StudyServerResponse createStudyServer(UUID ownerUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/study-servers")
                        .with(asUser(ownerUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Spring Bootcamp Hub"
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(), StudyServerResponse.class);
    }

    private record StudyServerResponse(UUID id, String name) {
    }

    private record CourseLifecycleResponse(
            UUID id,
            String title,
            String description,
            boolean published,
            boolean archived
    ) {
    }
}
