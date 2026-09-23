package com.chanter.media.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.chanter.common.auth.AuthHeaders;
import com.chanter.media.api.ResourceRecoveryObjectsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.ByteBuffer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class ResourceRecoveryObjectsControllerTest {
    private static final String TOKEN="synthetic-object-private-token-32-chars";
    private final ResourceRecoveryObjects objects=mock(ResourceRecoveryObjects.class);
    private final ObjectMapper mapper=new ObjectMapper();
    private final ResourceRecoveryObjectsController controller=new ResourceRecoveryObjectsController(objects,mapper,TOKEN);
    private final ResourceRecoveryInventory.RestoreRequest request=new ResourceRecoveryInventory.RestoreRequest(UUID.randomUUID(),UUID.randomUUID(),
            new ResourceRecoveryInventory.Authority(0,"0".repeat(64)),1);
    @Test void bothActivationFlagsAreRequired() {
        for(String[] properties:java.util.List.of(new String[]{},new String[]{"chanter.recovery-mode=true"},
                new String[]{"chanter.media.recovery-inventory-enabled=true"})) {
            new ApplicationContextRunner().withPropertyValues(properties)
                    .withUserConfiguration(ResourceRecoveryObjects.class,ResourceRecoveryObjectsController.class)
                    .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ResourceRecoveryObjects.class)
                            .doesNotHaveBean(ResourceRecoveryObjectsController.class));
        }
    }
    @Test void authenticationPrecedesReadingAnyBodyAndMalformedRequestsNeverReachObjects() throws Exception {
        assertThatThrownBy(() -> controller.put(http(new byte[0],null))).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode().value()).isEqualTo(401));
        for(byte[] body:new byte[][] {new byte[0],ByteBuffer.allocate(4).putInt(4097).array(),new byte[]{0,0,0,2,123},
                frame("{}".getBytes(),new byte[]{1}),frame(mapper.writeValueAsBytes(request),new byte[ResourceRecoveryObjects.MAX_BYTES+1])}) {
            assertThatThrownBy(() -> controller.put(http(body,TOKEN))).isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode().value()).isEqualTo(400));
        }
        for(String json:java.util.List.of("{}","null",mapper.writeValueAsString(request)+" {}",
                mapper.writeValueAsString(request).replace("\"ordinal\":1","\"ordinal\":1,\"ordinal\":1"),
                mapper.writeValueAsString(request).replace("\"ordinal\":1","\"ordinal\":1,\"key\":\"caller-selected\""))) {
            assertThatThrownBy(() -> controller.read(http(json.getBytes(),TOKEN))).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(objects);
    }
    @Test void privateBytesAreBoundedFramedAndNeverCacheable() throws Exception {
        byte[] bytes="private-fixture".getBytes();when(objects.read(request)).thenReturn(bytes);
        var read=controller.read(http(mapper.writeValueAsBytes(request),TOKEN));
        assertThat(read.getBody()).isEqualTo(bytes);assertThat(read.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(read.getHeaders().getCacheControl()).isEqualTo("no-store");
        var receipt=new ResourceRecoveryObjects.Receipt(1,"PUT",request);when(objects.put(eq(request),any())).thenReturn(receipt);
        assertThat(controller.put(http(frame(mapper.writeValueAsBytes(request),bytes),TOKEN)).getBody()).isEqualTo(receipt);
        verify(objects).put(eq(request),org.mockito.AdditionalMatchers.aryEq(bytes));
    }
    @Test void sourceAndProviderFailuresExposeNoPrivateDetails() throws Exception {
        when(objects.read(request)).thenThrow(new java.io.IOException("private-key-or-provider-response"));
        assertThatThrownBy(() -> controller.read(http(mapper.writeValueAsBytes(request),TOKEN)))
                .isInstanceOfSatisfying(ResponseStatusException.class,error -> {
                    assertThat(error.getStatusCode().value()).isEqualTo(409);
                    assertThat(error.getReason()).isEqualTo("RESOURCE_RECOVERY_OBJECT_NOT_READY");assertThat(error.getCause()).isNull();
                });
    }
    private static byte[] frame(byte[] json,byte[] bytes) {return ByteBuffer.allocate(4+json.length+bytes.length).putInt(json.length).put(json).put(bytes).array();}
    private static MockHttpServletRequest http(byte[] bytes,String token) {
        var request=new MockHttpServletRequest();if(token!=null)request.addHeader(AuthHeaders.INTERNAL_SERVICE_TOKEN,token);
        request.setContent(bytes);return request;
    }
}
