package com.chanter.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chanter.common.auth.AuthHeaders;
import com.chanter.media.infra.TestCourseResourceAccessClient;
import com.chanter.media.infra.TestResourceIngestionClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseResourceSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestCourseResourceAccessClient courseResourceAccessClient;

    @Autowired
    private TestResourceIngestionClient resourceIngestionClient;

    @BeforeEach
    void setUp() {
        courseResourceAccessClient.clear();
        resourceIngestionClient.clear();
    }

    @Test
    void instructorCanUploadCourseResourceAndEnrolledLearnerCanListAndDownload() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        byte[] fileContent = "# Spring Security Guide".getBytes(StandardCharsets.UTF_8);

        courseResourceAccessClient.grantInstructorUpload(courseId, instructorUserId);
        courseResourceAccessClient.grantLearnerView(courseId, learnerUserId);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", courseId)
                        .file(new MockMultipartFile(
                                "file",
                                "spring-security-guide.md",
                                "text/markdown",
                                fileContent
                        ))
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .param("title", "Spring Security Guide")
                        .param("aiApproved", "true"))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResourceResponse uploaded = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                CourseResourceResponse.class
        );

        assertThat(uploaded.courseId()).isEqualTo(courseId);
        assertThat(uploaded.title()).isEqualTo("Spring Security Guide");
        assertThat(uploaded.fileName()).isEqualTo("spring-security-guide.md");
        assertThat(uploaded.aiApproved()).isTrue();
        assertThat(uploaded.uploadedByUserId()).isEqualTo(instructorUserId);

        MvcResult listResult = mockMvc.perform(get("/api/v1/courses/{courseId}/course-resources", courseId)
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        CourseResourceListResponse listed = objectMapper.readValue(
                listResult.getResponse().getContentAsString(),
                CourseResourceListResponse.class
        );

        assertThat(listed.courseResources()).hasSize(1);
        CourseResourceResponse listedResource = listed.courseResources().getFirst();
        assertThat(listedResource)
                .usingRecursiveComparison()
                .ignoringFields("createdAt")
                .isEqualTo(uploaded);
        // Postgres stores timestamps at microsecond precision; upload responses truncate to match.
        assertThat(listedResource.createdAt()).isEqualTo(uploaded.createdAt().truncatedTo(ChronoUnit.MICROS));

        MvcResult downloadResult = mockMvc.perform(get("/api/v1/course-resources/{resourceId}/content", uploaded.id())
                        .header(AuthHeaders.USER_ID, learnerUserId.toString()))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(fileContent);
        assertThat(downloadResult.getResponse().getHeader("Content-Disposition"))
                .contains("spring-security-guide.md");

        assertThat(resourceIngestionClient.ingestCalls()).hasSize(1);
        TestResourceIngestionClient.IngestCall ingestCall = resourceIngestionClient.ingestCalls().getFirst();
        assertThat(ingestCall.courseId()).isEqualTo(courseId);
        assertThat(ingestCall.resourceId()).isEqualTo(uploaded.id());
        assertThat(ingestCall.fileName()).isEqualTo("spring-security-guide.md");
        assertThat(ingestCall.content()).isEqualTo(fileContent);
    }

    @Test
    void unauthorizedUserCannotListCourseResources() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        courseResourceAccessClient.grantInstructorUpload(courseId, instructorUserId);

        mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", courseId)
                        .file(new MockMultipartFile(
                                "file",
                                "notes.txt",
                                "text/plain",
                                "notes".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .param("aiApproved", "true"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/courses/{courseId}/course-resources", courseId)
                        .header(AuthHeaders.USER_ID, strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void learnerCannotUploadCourseResource() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();

        courseResourceAccessClient.grantLearnerView(courseId, learnerUserId);

        mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", courseId)
                        .file(new MockMultipartFile(
                                "file",
                                "notes.txt",
                                "text/plain",
                                "notes".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header(AuthHeaders.USER_ID, learnerUserId.toString())
                        .param("aiApproved", "false"))
                .andExpect(status().isForbidden());
    }

    @Test
    void instructorUploadStripsPathFromFileName() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();

        courseResourceAccessClient.grantInstructorUpload(courseId, instructorUserId);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", courseId)
                        .file(new MockMultipartFile(
                                "file",
                                "../../notes.txt",
                                "text/plain",
                                "notes".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .param("aiApproved", "true"))
                .andExpect(status().isCreated())
                .andReturn();

        CourseResourceResponse uploaded = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                CourseResourceResponse.class
        );

        assertThat(uploaded.fileName()).isEqualTo("notes.txt");
    }

    @Test
    void unauthorizedUserCannotDownloadCourseResource() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID instructorUserId = UUID.randomUUID();
        UUID strangerUserId = UUID.randomUUID();

        courseResourceAccessClient.grantInstructorUpload(courseId, instructorUserId);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", courseId)
                        .file(new MockMultipartFile(
                                "file",
                                "notes.txt",
                                "text/plain",
                                "notes".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header(AuthHeaders.USER_ID, instructorUserId.toString())
                        .param("aiApproved", "true"))
                .andExpect(status().isCreated())
                .andReturn();
        CourseResourceResponse uploaded = objectMapper.readValue(
                uploadResult.getResponse().getContentAsString(),
                CourseResourceResponse.class
        );

        mockMvc.perform(get("/api/v1/course-resources/{resourceId}/content", uploaded.id())
                        .header(AuthHeaders.USER_ID, strangerUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadingUnknownCourseResourceReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/course-resources/{resourceId}/content", UUID.randomUUID())
                        .header(AuthHeaders.USER_ID, UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadingToUnknownCourseReturnsNotFound() throws Exception {
        mockMvc.perform(multipart("/api/v1/courses/{courseId}/course-resources", UUID.randomUUID())
                        .file(new MockMultipartFile(
                                "file",
                                "notes.txt",
                                "text/plain",
                                "notes".getBytes(StandardCharsets.UTF_8)
                        ))
                        .header(AuthHeaders.USER_ID, UUID.randomUUID().toString())
                        .param("aiApproved", "true"))
                .andExpect(status().isNotFound());
    }
}
