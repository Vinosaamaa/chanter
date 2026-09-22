package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.media.api.ResourceRecoveryInventoryController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ResourceRecoveryInventoryControllerTest {
    private static final String TOKEN="synthetic-inventory-private-token-32-chars";
    private final StorageMutationStore mutations=mock(StorageMutationStore.class);
    private final ResourceRecoveryInventory inventory=mock(ResourceRecoveryInventory.class);
    private final ResourceRecoveryInventoryController controller=new ResourceRecoveryInventoryController(mutations,inventory,new ObjectMapper(),TOKEN);

    @Test void neitherOrdinaryRuntimeNorRecoveryFlagAloneConstructsTheInventoryApi() {
        for(String[] properties:java.util.List.of(new String[]{},new String[]{"chanter.recovery-mode=true"},
                new String[]{"chanter.media.recovery-inventory-enabled=true"})) {
            new ApplicationContextRunner().withPropertyValues(properties)
                    .withUserConfiguration(ResourceRecoveryInventory.class,ResourceRecoveryInventoryController.class)
                    .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResourceRecoveryInventory.class)
                            .doesNotHaveBean(ResourceRecoveryInventoryController.class));
        }
    }
    @Test void authenticationPrecedesParsingAndMaintenanceHasNoQuiescenceClaim() {
        UUID id=UUID.randomUUID();
        assertThatThrownBy(() -> controller.fence(request("{",null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(401));
        verifyNoInteractions(mutations,inventory);
        when(mutations.fence(id)).thenReturn(new StorageMutationStore.Fence(id,Instant.EPOCH,null,1));
        var response=controller.fence(request("{\"inventoryId\":\""+id+"\"}",TOKEN));
        assertThat(response.getBody().unsettledMutations()).isEqualTo(1);
        assertThat(response.getBody().storageNamespaceSha256()).isNull();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    }
    @Test void missingDuplicateUnknownTrailingAndOversizedInputNeverReachesStorage() {
        String id=UUID.randomUUID().toString();
        for(String body:java.util.List.of("{}","null","{\"inventoryId\":\""+id+"\",\"inventoryId\":\""+id+"\"}",
                "{\"inventoryId\":\""+id+"\",\"writers\":\"QUIESCENT\"}","{\"inventoryId\":\""+id+"\"} {}"," ".repeat(4097))) {
            assertThatThrownBy(() -> controller.fence(request(body,TOKEN)))
                    .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(400));
        }
        verifyNoInteractions(mutations,inventory);
    }
    @Test void unavailableSourceContractsReturnOnlyABoundedUnreadyCode() {
        UUID id=UUID.randomUUID(); when(mutations.fence(id)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("fixture private database detail"));
        assertThatThrownBy(() -> controller.fence(request("{\"inventoryId\":\""+id+"\"}",TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> {
                    assertThat(e.getStatusCode().value()).isEqualTo(409);
                    assertThat(e.getReason()).isEqualTo("RESOURCE_RECOVERY_SOURCE_NOT_READY");
                    assertThat(e.getCause()).isNull();
                });
    }
    private static MockHttpServletRequest request(String body,String token) {
        var request=new MockHttpServletRequest();
        if(token!=null) request.addHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN,token);
        request.setContent(body.getBytes(StandardCharsets.UTF_8)); return request;
    }
}
