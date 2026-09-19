package com.chanter.agent.infra;

import static org.assertj.core.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ApiEmbeddingClientTest {
    @Test void remoteModelDimensionsAndFiniteCoordinatesAreRequired() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var body=new AtomicReference<>("{\"model\":\"stable-model\",\"data\":[{\"index\":0,\"embedding\":[2,0,0,0,0,0,0,0]}]}");
        server.createContext("/v1/embeddings",exchange->{
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer fixture-key");
            var bytes=body.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(200,bytes.length);
            try(var output=exchange.getResponseBody()){output.write(bytes);}
        });
        server.start();
        try {
            var client=new ApiEmbeddingClient("api-v1",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),
                    "fixture-key","stable-model","immutable-revision",8);
            assertThat(client.embed("Evidence query")).containsExactly(1,0,0,0,0,0,0,0);
            body.set("{\"model\":\"other-model\",\"data\":[{\"index\":0,\"embedding\":[1,0,0,0,0,0,0,0]}]}");
            assertThatThrownBy(()->client.embed("Evidence query")).isInstanceOf(IllegalStateException.class);
            body.set("{\"model\":\"stable-model\",\"data\":[{\"index\":0,\"embedding\":[1,0]}]}");
            assertThatThrownBy(()->client.embed("Evidence query")).isInstanceOf(IllegalStateException.class);
            body.set("{\"model\":\"stable-model\",\"data\":[{\"index\":0,\"embedding\":[0,0,0,0,0,0,0,0]}]}");
            assertThatThrownBy(()->client.embed("Evidence query")).isInstanceOf(IllegalStateException.class);
        } finally {server.stop(0);}
    }
}
