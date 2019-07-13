package org.xbib.netty.http.server.test;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xbib.netty.http.client.Client;
import org.xbib.netty.http.client.Request;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.server.Server;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.HttpEndpoint;
import org.xbib.netty.http.server.endpoint.EndpointResolver;
import org.xbib.netty.http.server.Domain;
import org.xbib.netty.http.server.endpoint.service.FileService;
import org.xbib.netty.http.server.endpoint.service.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(NettyHttpExtension.class)
class EndpointTest {

    private static final Logger logger = Logger.getLogger(EndpointTest.class.getName());

    @Test
    void testEmptyPrefixEndpoint() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        Service service = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        EndpointResolver endpointResolver = EndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPath("/**").build())
                .setDispatcher((endpoint, req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint=" + endpoint + " req=" + req);
                    service.handle(req, resp);
                })
                .build();
        Domain domain = Domain.builder(httpAddress)
                .addEndpointResolver(endpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/test.txt"))
                    .build()
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            client.execute(request).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }

    @Test
    void testPlainPrefixEndpoint() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        Service service = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        EndpointResolver endpointResolver = EndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/").setPath("/**").build())
                .setDispatcher((endpoint, req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint=" + endpoint + " req=" + req);
                    service.handle(req, resp);
                })
                .build();
        Domain domain = Domain.builder(httpAddress)
                .addEndpointResolver(endpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/test.txt"))
                    .build()
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            client.execute(request).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
    }


    @Test
    void testSimplePathEndpoints() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        Service service = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        EndpointResolver endpointResolver = EndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static1").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static2").setPath("/**").build())
                .setDispatcher((endpoint, req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint=" + endpoint + " req=" + req);
                    service.handle(req, resp);
                })
                .build();
        Domain domain = Domain.builder(httpAddress)
                .addEndpointResolver(endpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test1.txt"), "Hello Jörg 1".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test2.txt"), "Hello Jörg 2".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static/test.txt"))
                    .build()
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                        success.set(true);
                    });
            client.execute(request).get();
            Request request1 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static1/test1.txt"))
                    .build()
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg 1", r.content().toString(StandardCharsets.UTF_8));
                        success1.set(true);
                    });
            client.execute(request1).get();
            Request request2 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static2/test2.txt"))
                    .build()
                    .setResponseListener(r -> {
                        assertEquals("Hello Jörg 2", r.content().toString(StandardCharsets.UTF_8));
                        success2.set(true);
                    });
            client.execute(request2).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            Files.delete(vartmp.resolve("test1.txt"));
            Files.delete(vartmp.resolve("test2.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
        assertTrue(success1.get());
        assertTrue(success2.get());
    }

    @Test
    void testQueryAndFragmentEndpoints() throws Exception {
        Path vartmp = Paths.get("/var/tmp/");
        Service service = new FileService(vartmp);
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        EndpointResolver endpointResolver = EndpointResolver.builder()
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static1").setPath("/**").build())
                .addEndpoint(HttpEndpoint.builder().setPrefix("/static2").setPath("/**").build())
                .setDispatcher((endpoint, req, resp) -> {
                    logger.log(Level.FINE, "dispatching endpoint=" + endpoint + " req=" + req +
                            " fragment=" + req.getURL().getFragment());
                    service.handle(req, resp);
                })
                .build();
        Domain domain = Domain.builder(httpAddress)
                .addEndpointResolver(endpointResolver)
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicBoolean success = new AtomicBoolean(false);
        final AtomicBoolean success1 = new AtomicBoolean(false);
        final AtomicBoolean success2 = new AtomicBoolean(false);
        try {
            Files.write(vartmp.resolve("test.txt"), "Hello Jörg".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test1.txt"), "Hello Jörg 1".getBytes(StandardCharsets.UTF_8));
            Files.write(vartmp.resolve("test2.txt"), "Hello Jörg 2".getBytes(StandardCharsets.UTF_8));
            server.accept();
            Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static/test.txt"))
                    .addParameter("a", "b")
                    .build()
                    .setResponseListener(r -> {
                        if (r.status().equals(HttpResponseStatus.OK)) {
                            assertEquals("Hello Jörg", r.content().toString(StandardCharsets.UTF_8));
                            success.set(true);
                        } else {
                            logger.log(Level.WARNING, r.toString());
                        }
                    });
            client.execute(request).get();
            Request request1 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base()
                            .resolve("/static1/test1.txt").newBuilder().fragment("frag").build())
                    .build()
                    .setResponseListener(r -> {
                        if (r.status().equals(HttpResponseStatus.OK)) {
                            assertEquals("Hello Jörg 1", r.content().toString(StandardCharsets.UTF_8));
                            success1.set(true);
                        } else {
                            logger.log(Level.WARNING, r.toString());
                        }
                    });
            client.execute(request1).get();
            Request request2 = Request.get().setVersion(HttpVersion.HTTP_1_1)
                    .url(server.getServerConfig().getAddress().base().resolve("/static2/test2.txt"))
                    .content("{\"a\":\"b\"}","application/json")
                    .build()
                    .setResponseListener(r -> {
                        if (r.status().equals(HttpResponseStatus.OK)) {
                            assertEquals("Hello Jörg 2", r.content().toString(StandardCharsets.UTF_8));
                            success2.set(true);
                        } else {
                            logger.log(Level.WARNING, r.toString());
                        }
                    });
            client.execute(request2).get();
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            Files.delete(vartmp.resolve("test.txt"));
            Files.delete(vartmp.resolve("test1.txt"));
            Files.delete(vartmp.resolve("test2.txt"));
            logger.log(Level.INFO, "server and client shut down");
        }
        assertTrue(success.get());
        assertTrue(success1.get());
        assertTrue(success2.get());
    }

    @Test
    void testMassiveEndpoints() throws IOException {
        int max = 2; // more than 1024
        HttpAddress httpAddress = HttpAddress.http1("localhost", 8008);
        EndpointResolver.Builder endpointResolverBuilder = EndpointResolver.builder()
                .setPrefix("/static");
        for (int i = 0; i < max; i++) {
            endpointResolverBuilder.addEndpoint(HttpEndpoint.builder()
                    .setPath("/" + i + "/**")
                    .addFilter((req, resp) -> ServerResponse.write(resp, HttpResponseStatus.OK))
                    .build());
        }
        Domain domain = Domain.builder(httpAddress)
                .addEndpointResolver(endpointResolverBuilder.build())
                .build();
        Server server = Server.builder(domain)
                .build();
        Client client = Client.builder()
                .build();
        final AtomicInteger count = new AtomicInteger(0);
        try {
            server.accept();
            for (int i = 0; i < max; i++) {
                Request request = Request.get().setVersion(HttpVersion.HTTP_1_1)
                        .url(server.getServerConfig().getAddress().base().resolve("/static/" + i + "/test.txt"))
                        .build()
                        .setResponseListener(r -> {
                            if (r.status().equals(HttpResponseStatus.OK)) {
                                count.incrementAndGet();
                            } else {
                                logger.log(Level.WARNING, r.status().reasonPhrase());
                            }
                        });
                client.execute(request).get();
            }
        } finally {
            server.shutdownGracefully();
            client.shutdownGracefully();
            logger.log(Level.INFO, "server and client shut down");
        }
        assertEquals(max, count.get());
    }
}