package org.xbib.netty.http.server.test.http2;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.api.Request;
import org.xbib.netty.http.client.api.ResponseListener;
import org.xbib.netty.http.client.api.Transport;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.api.ServerResponse;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.test.NettyHttpTestExtension;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(NettyHttpTestExtension.class)
class CleartextTest {

    private static final Logger logger = Logger.getLogger(CleartextTest.class.getName());

    @Test
    void testSimpleCleartextHttp2() throws Exception {
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text.plain",
                                request.getContent().toString(StandardCharsets.UTF_8)))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            String payload = 0 + "/" + 0;
            Request request = Request.get().setVersion("HTTP/2.0")
                    .url(server.getServerConfig().getAddress().base())
                    .content(payload, "text/plain")
                    .setResponseListener(responseListener)
                    .build();
            Transport transport = client.newTransport(httpAddress);
            transport.execute(request);
            if (transport.isFailed()) {
                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
            }
            transport.get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + 1 + " counter=" + counter.get());
        assertEquals(1, counter.get());
    }

    @Test
    void testPooledClearTextHttp2() throws Exception {
        int loop = 1000;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        response.withStatus(HttpResponseStatus.OK)
                                .withContentType("text/plain")
                                .write(request.getContent().retain()))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(2)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            // single transport, single thread
            Transport transport = client.newTransport();
            for (int i = 0; i < loop; i++) {
                String payload = 0 + "/" + i;
                Request request = Request.get().setVersion("HTTP/2.0")
                        .url(server.getServerConfig().getAddress().base())
                        .content(payload, "text/plain")
                        .setResponseListener(responseListener)
                        .build();
                transport.execute(request);
                if (transport.isFailed()) {
                    logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                    break;
                }
            }
            transport.get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
        }
        logger.log(Level.INFO, "expecting=" + loop + " counter=" + counter.get());
        assertEquals(loop, counter.get());
    }

    @Test
    void testMultithreadPooledClearTextHttp2() throws Exception {
        int threads = 2;
        int loop = 1000;
        HttpAddress httpAddress = HttpAddress.http2("localhost", 8008);
        Domain domain = Domain.builder(httpAddress)
                .singleEndpoint("/", (request, response) ->
                        ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                                request.getContent().retain()))
                .build();
        Server server = Server.builder(domain)
                .build();
        server.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a HTTP/2 listener always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() ==  HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            // note: for HTTP/2 only, we use a single shared transport
            final Transport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            Request request = Request.get().setVersion("HTTP/2.0")
                                    .url(server.getServerConfig().getAddress().base())
                                    .content(payload, "text/plain")
                                    .setResponseListener(responseListener)
                                    .build();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(10L, TimeUnit.SECONDS);
            executorService.shutdownNow();
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(10L, TimeUnit.SECONDS);
        } finally {
            server.shutdownGracefully(10L, TimeUnit.SECONDS);
            client.shutdownGracefully(10L, TimeUnit.SECONDS);
        }
        logger.log(Level.INFO, "server requests = " + server.getRequestCounter() +
                " server responses = " + server.getResponseCounter());
        logger.log(Level.INFO, "client requests = " + client.getRequestCounter() +
                " client responses = " + client.getResponseCounter());
        logger.log(Level.INFO, "expected=" + (threads * loop) + " counter=" + counter.get());
        assertEquals(threads * loop , counter.get());
    }

    @Test
    void testTwoPooledClearTextHttp2() throws Exception {
        int threads = 2;
        int loop = 1000;
        HttpAddress httpAddress1 = HttpAddress.http2("localhost", 8008);
        AtomicInteger counter1 = new AtomicInteger();
        Domain domain1 = Domain.builder(httpAddress1)
                .singleEndpoint("/", (request, response) -> {
                    ServerResponse.write(response, HttpResponseStatus.OK, "text.plain",
                          request.getContent().toString(StandardCharsets.UTF_8));
                    counter1.incrementAndGet();
                })
                .build();
        Server server1 = Server.builder(domain1)
                .build();
        server1.accept();
        HttpAddress httpAddress2 = HttpAddress.http2("localhost", 8009);
        AtomicInteger counter2 = new AtomicInteger();
        Domain domain2 = Domain.builder(httpAddress2)
                .singleEndpoint("/", (request, response) -> {
                    ServerResponse.write(response, HttpResponseStatus.OK, "text/plain",
                            request.getContent().toString(StandardCharsets.UTF_8));
                    counter2.incrementAndGet();
                })
                .build();
        Server server2 = Server.builder(domain2)
                .build();
        server2.accept();
        Client client = Client.builder()
                .addPoolNode(httpAddress1)
                .addPoolNode(httpAddress2)
                .setPoolNodeConnectionLimit(threads)
                .build();
        AtomicInteger counter = new AtomicInteger();
        // a single instance of HTTP/2 response listener, always receives responses out-of-order
        final ResponseListener<HttpResponse> responseListener = resp -> {
            if (resp.getStatus().getCode() == HttpResponseStatus.OK.code()) {
                counter.incrementAndGet();
            } else {
                logger.log(Level.INFO, "response listener: headers = " + resp.getHeaders() +
                        " response body = " + resp.getBodyAsString(StandardCharsets.UTF_8));
            }
        };
        try {
            // note: for HTTP/2 only, we can use a single shared transport
            final Transport transport = client.newTransport();
            ExecutorService executorService = Executors.newFixedThreadPool(threads);
            for (int n = 0; n < threads; n++) {
                final int t = n;
                executorService.submit(() -> {
                    try {
                        for (int i = 0; i < loop; i++) {
                            String payload = t + "/" + i;
                            // note  that we do not set url() in the request
                            Request request = Request.get()
                                    .setVersion("HTTP/2.0")
                                    .content(payload, "text/plain")
                                    .setResponseListener(responseListener)
                                    .build();
                            transport.execute(request);
                            if (transport.isFailed()) {
                                logger.log(Level.WARNING, transport.getFailure().getMessage(), transport.getFailure());
                                break;
                            }
                        }
                    } catch (Throwable e) {
                        logger.log(Level.WARNING, e.getMessage(), e);
                    }
                });
            }
            executorService.shutdown();
            boolean terminated = executorService.awaitTermination(10L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "terminated = " + terminated + ", now waiting for transport to complete");
            transport.get(10L, TimeUnit.SECONDS);
            logger.log(Level.INFO, "transport complete");
        } finally {
            server1.shutdownGracefully(10L, TimeUnit.SECONDS);
            server2.shutdownGracefully(10L, TimeUnit.SECONDS);
            client.shutdownGracefully(10L, TimeUnit.SECONDS);
        }
        logger.log(Level.INFO, "server1 requests = " + server1.getRequestCounter() +
                " server1 responses = " + server1.getResponseCounter());
        logger.log(Level.INFO, "server2 requests = " + server1.getRequestCounter() +
                " server2 responses = " + server1.getResponseCounter());
        logger.log(Level.INFO, "client requests = " + client.getRequestCounter() +
                " client responses = " + client.getResponseCounter());
        logger.log(Level.INFO, "counter1=" + counter1.get() + " counter2=" + counter2.get());
        logger.log(Level.INFO, "expecting=" + threads * loop + " counter=" + counter.get());
        assertEquals(threads * loop, counter.get());
    }
}
