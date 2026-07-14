package com.chanter.auth.api;

import com.chanter.auth.application.AuthSessionService;
import com.chanter.common.auth.AuthHeaders;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserDirectoryController {

    private final AuthSessionService authSessionService;
    private final byte[] internalServiceToken;

    public InternalUserDirectoryController(
            AuthSessionService authSessionService,
            @Value("${chanter.internal-service-token}") String internalServiceToken
    ) {
        this.authSessionService = authSessionService;
        this.internalServiceToken = internalServiceToken.getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/by-email")
    public InternalUserProfileResponse findByEmail(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @RequestParam String email
    ) {
        requireInternalService(serviceToken);
        return authSessionService.findProfileByEmail(email)
                .map(InternalUserProfileResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User email not found"));
    }

    @PostMapping("/profiles/query")
    public InternalUserProfileListResponse findProfiles(
            @RequestHeader(value = AuthHeaders.INTERNAL_SERVICE_TOKEN, required = false) String serviceToken,
            @Valid @RequestBody PublicProfileQueryRequest request
    ) {
        requireInternalService(serviceToken);
        return InternalUserProfileListResponse.from(
                authSessionService.findPublicProfiles(request.userIds())
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
}
