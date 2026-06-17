package com.chanter.community.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class StudyServerCreationSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void studyServerOwnerCanCreateAndViewStudyServerWithDefaultChannels() throws Exception {
        UUID ownerUserId = UUID.randomUUID();

        MvcResult createdResult = mockMvc.perform(post("/api/v1/study-servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Java Spring Study Group",
                                "ownerUserId", ownerUserId.toString()
                        ))))
                .andExpect(status().isCreated())
                .andReturn();

        String location = createdResult.getResponse().getHeader("Location");
        StudyServerResponse created = objectMapper.readValue(
                createdResult.getResponse().getContentAsString(),
                StudyServerResponse.class
        );

        assertThat(location).isNotNull();
        assertThat(created.name()).isEqualTo("Java Spring Study Group");
        assertThat(created.ownerRole())
                .isEqualTo(new OwnerRoleResponse(ownerUserId, "STUDY_SERVER_OWNER"));
        assertThat(created.channels())
                .extracting(ChannelResponse::name)
                .containsExactly("announcements", "general", "study-room");
        assertThat(created.channels())
                .extracting(ChannelResponse::kind)
                .containsExactly("TEXT", "TEXT", "VOICE");

        MvcResult viewedResult = mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andReturn();
        StudyServerResponse viewed = objectMapper.readValue(
                viewedResult.getResponse().getContentAsString(),
                StudyServerResponse.class
        );

        assertThat(viewed).isEqualTo(created);
    }

    private record StudyServerResponse(
            UUID id,
            String name,
            OwnerRoleResponse ownerRole,
            List<ChannelResponse> channels
    ) {
    }

    private record OwnerRoleResponse(UUID userId, String role) {
    }

    private record ChannelResponse(UUID id, String name, String kind) {
    }
}
