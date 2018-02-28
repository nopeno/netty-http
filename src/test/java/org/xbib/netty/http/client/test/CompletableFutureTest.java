package org.xbib.netty.http.client.test;

import io.netty.handler.codec.http.FullHttpResponse;
import org.junit.Test;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CompletableFutureTest {

    private static final Logger logger = Logger.getLogger(ElasticsearchTest.class.getName());

    /**
     * Get some weird content from one URL and post it to another URL, by composing completable futures.
     */
    @Test
    public void testComposeCompletableFutures() {
        Client client = new Client();
        try {
            final Function<FullHttpResponse, String> httpResponseStringFunction = response ->
                    response.content().toString(StandardCharsets.UTF_8);
            Request request = Request.get()
                    .setURL("http://alkmene.hbz-nrw.de/repository/org/xbib/content/2.0.0-SNAPSHOT/maven-metadata-local.xml")
                    .build();
            CompletableFuture<String> completableFuture = client.execute(request, httpResponseStringFunction)
                    .exceptionally(Throwable::getMessage)
                    .thenCompose(content -> {
                        logger.log(Level.INFO, content);
                        // POST is not allowed, we don't care
                        return client.execute(Request.post()
                                .setURL("http://google.com/")
                                .addParam("query", content)
                                .build(), httpResponseStringFunction);
                    });
            String result = completableFuture.join();
            logger.log(Level.INFO, "completablefuture result = " + result);
        } finally {
            client.shutdownGracefully();
        }
    }
}