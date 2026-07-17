package com.chanter.agent.api;

import com.chanter.agent.application.LlmChatClient;
import com.chanter.common.ServiceInfo;
import com.chanter.common.auth.InternalServiceTokens;
import com.chanter.common.auth.AuthHeaders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(ServiceInfo.API_V1_PREFIX + "/internal/llm")
public class InternalLlmHealthController {

    private final LlmChatClient llmChatClient;
    private final byte[] internalServiceToken;

    public InternalLlmHealthController(
            LlmChatClient llmChatClient,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.llmChatClient = llmChatClient;
        this.internalServiceToken = InternalServiceTokens.requireBytes(internalServiceToken);
    }

    @GetMapping("/health")
    public LlmHealthResponse health(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken
    ) {
        requireInternalService(serviceToken);
        boolean reachable = llmChatClient.isEnabled() && llmChatClient.ping();
        return new LlmHealthResponse(
                llmChatClient.isEnabled(),
                llmChatClient.providerId(),
                llmChatClient.modelId(),
                reachable
        );
    }

    private void requireInternalService(String presentedToken) {
        byte[] presented = presentedToken == null
                ? new byte[0]
                : presentedToken.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(internalServiceToken, presented)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal service authentication required");
        }
    }

    public record LlmHealthResponse(boolean enabled, String provider, String model, boolean reachable) {
    }
}
