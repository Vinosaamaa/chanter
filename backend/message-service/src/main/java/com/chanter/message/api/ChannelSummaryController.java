package com.chanter.message.api;

import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.AuthRequestAttributes;
import com.chanter.message.application.ChannelSummary;
import com.chanter.message.application.ChannelSummaryService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/course-channels")
public class ChannelSummaryController {

    private final ChannelSummaryService channelSummaryService;

    public ChannelSummaryController(ChannelSummaryService channelSummaryService) {
        this.channelSummaryService = channelSummaryService;
    }

    @PostMapping("/{channelId}/channel-summary")
    public ChannelSummaryResponse generateChannelSummary(
            @PathVariable UUID channelId,
            @RequestAttribute(AuthRequestAttributes.USER_ID) UUID viewerUserId,
            @Valid @RequestBody(required = false) GenerateChannelSummaryRequest request
    ) {
        int windowDays = request == null ? 7 : request.resolvedWindowDays();
        ChannelSummary summary = channelSummaryService.generateSummary(channelId, viewerUserId, windowDays);
        return ChannelSummaryResponse.from(summary);
    }
}
