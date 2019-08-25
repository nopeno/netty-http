package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;

import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import org.xbib.net.PercentEncoder;
import org.xbib.net.PercentEncoders;
import org.xbib.net.URL;
import org.xbib.netty.http.client.listener.CookieListener;
import org.xbib.netty.http.client.listener.ResponseListener;
import org.xbib.netty.http.client.listener.StatusListener;
import org.xbib.netty.http.client.retry.BackOff;
import org.xbib.netty.http.common.HttpAddress;
import org.xbib.netty.http.common.HttpParameters;
import org.xbib.netty.http.common.HttpResponse;
import org.xbib.netty.http.common.cookie.Cookie;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnmappableCharacterException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP client request.
 */
public class Request {

    private final URL url;

    private final String uri;

    private final HttpVersion httpVersion;

    private final HttpMethod httpMethod;

    private final HttpHeaders headers;

    private final Collection<Cookie> cookies;

    private final ByteBuf content;

    private final long timeoutInMillis;

    private final boolean followRedirect;

    private final int maxRedirects;

    private int redirectCount;

    private final boolean isBackOff;

    private final BackOff backOff;

    private CompletableFuture<?> completableFuture;

    private ResponseListener<HttpResponse> responseListener;

    private CookieListener cookieListener;

    private StatusListener statusListener;

    private Request(URL url, String uri, HttpVersion httpVersion, HttpMethod httpMethod,
                    HttpHeaders headers, Collection<Cookie> cookies, ByteBuf content,
                    long timeoutInMillis, boolean followRedirect, int maxRedirect, int redirectCount,
                    boolean isBackOff, BackOff backOff) {
        this.url = url;
        this.uri = uri;
        this.httpVersion = httpVersion;
        this.httpMethod = httpMethod;
        this.headers = headers;
        this.cookies = cookies;
        this.content = content;
        this.timeoutInMillis = timeoutInMillis;
        this.followRedirect = followRedirect;
        this.maxRedirects = maxRedirect;
        this.redirectCount = redirectCount;
        this.isBackOff = isBackOff;
        this.backOff = backOff;
    }

    public URL url() {
        return url;
    }

    public String absolute() {
        return url.toExternalForm();
    }

    public String relative() {
        // is already in external form
        return uri;
    }

    public HttpVersion httpVersion() {
        return httpVersion;
    }

    public HttpMethod httpMethod() {
        return httpMethod;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public Collection<Cookie> cookies() {
        return cookies;
    }

    public ByteBuf content() {
        return content;
    }

    /**
     * Return the timeout in milliseconds per request. This overrides the read timeout of the client.
     * @return timeout timeout in milliseconds
     */
    public long getTimeoutInMillis() {
        return timeoutInMillis;
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public boolean isBackOff() {
        return isBackOff;
    }

    public BackOff getBackOff() {
        return backOff;
    }

    public boolean canRedirect() {
        if (!followRedirect) {
            return false;
        }
        if (redirectCount >= maxRedirects) {
            return false;
        }
        redirectCount = redirectCount + 1;
        return true;
    }

    public void release() {
        if (content != null) {
            content.release();
        }
    }

    @Override
    public String toString() {
        return "Request[url='" + url +
                "',version=" + httpVersion +
                ",method=" + httpMethod +
                ",headers=" + headers.entries() +
                ",content=" + (content != null && content.readableBytes() >= 16 ?
                content.copy(0, 16).toString(StandardCharsets.UTF_8) + "..." :
                content != null ? content.toString(StandardCharsets.UTF_8) : "") +
                "]";
    }

    public Request setCompletableFuture(CompletableFuture<?> completableFuture) {
        this.completableFuture = completableFuture;
        return this;
    }

    public CompletableFuture<?> getCompletableFuture() {
        return completableFuture;
    }


    public Request setCookieListener(CookieListener cookieListener) {
        this.cookieListener = cookieListener;
        return this;
    }

    public CookieListener getCookieListener() {
        return cookieListener;
    }

    public Request setStatusListener(StatusListener statusListener) {
        this.statusListener = statusListener;
        return this;
    }

    public StatusListener getStatusListener() {
        return statusListener;
    }

    public Request setResponseListener(ResponseListener<HttpResponse> responseListener) {
        this.responseListener = responseListener;
        return this;
    }

    public ResponseListener<HttpResponse> getResponseListener() {
        return responseListener;
    }

    public static Builder get() {
        return builder(HttpMethod.GET);
    }

    public static Builder put() {
        return builder(HttpMethod.PUT);
    }

    public static Builder post() {
        return builder(HttpMethod.POST);
    }

    public static Builder delete() {
        return builder(HttpMethod.DELETE);
    }

    public static Builder head() {
        return builder(HttpMethod.HEAD);
    }

    public static Builder patch() {
        return builder(HttpMethod.PATCH);
    }

    public static Builder trace() {
        return builder(HttpMethod.TRACE);
    }

    public static Builder options() {
        return builder(HttpMethod.OPTIONS);
    }

    public static Builder connect() {
        return builder(HttpMethod.CONNECT);
    }

    public static Builder builder(HttpMethod httpMethod) {
        return builder(PooledByteBufAllocator.DEFAULT, httpMethod);
    }

    public static Builder builder(ByteBufAllocator allocator, HttpMethod httpMethod) {
        return new Builder(allocator).setMethod(httpMethod);
    }

    public static class Builder {

        private static final HttpMethod DEFAULT_METHOD = HttpMethod.GET;

        private static final HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_1_1;

        private static final String DEFAULT_USER_AGENT = UserAgent.getUserAgent();

        private static final URL DEFAULT_URL = URL.from("http://localhost");

        private static final boolean DEFAULT_GZIP = true;

        private static final boolean DEFAULT_KEEPALIVE = true;

        private static final boolean DEFAULT_FOLLOW_REDIRECT = true;

        private static final long DEFAULT_TIMEOUT_MILLIS = -1L;

        private static final int DEFAULT_MAX_REDIRECT = 10;

        private static final HttpVersion HTTP_2_0 = HttpVersion.valueOf("HTTP/2.0");

        private static final String DEFAULT_FORM_CONTENT_TYPE = "application/x-www-form-urlencoded; charset=utf-8";

        private final ByteBufAllocator allocator;

        private final List<String> removeHeaders;

        private final Collection<Cookie> cookies;

        private final PercentEncoder encoder;

        private HttpMethod httpMethod;

        private HttpHeaders headers;

        private HttpVersion httpVersion;

        private String userAgent;

        private boolean keepalive;

        private boolean gzip;

        private URL url;

        private String uri;

        private HttpParameters uriParameters;

        private HttpParameters formParameters;

        private ByteBuf content;

        private long timeoutInMillis;

        private boolean followRedirect;

        private int maxRedirects;

        private boolean enableBackOff;

        private BackOff backOff;

        Builder(ByteBufAllocator allocator) {
            this.allocator = allocator;
            this.httpMethod = DEFAULT_METHOD;
            this.httpVersion = DEFAULT_HTTP_VERSION;
            this.userAgent = DEFAULT_USER_AGENT;
            this.gzip = DEFAULT_GZIP;
            this.keepalive = DEFAULT_KEEPALIVE;
            this.url = DEFAULT_URL;
            this.timeoutInMillis = DEFAULT_TIMEOUT_MILLIS;
            this.followRedirect = DEFAULT_FOLLOW_REDIRECT;
            this.maxRedirects = DEFAULT_MAX_REDIRECT;
            this.headers = new DefaultHttpHeaders();
            this.removeHeaders = new ArrayList<>();
            this.cookies = new HashSet<>();
            this.encoder = PercentEncoders.getQueryEncoder(StandardCharsets.UTF_8);
            this.uriParameters = new HttpParameters();
            this.formParameters = new HttpParameters(DEFAULT_FORM_CONTENT_TYPE);
        }

        public Builder setMethod(HttpMethod httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder enableHttp1() {
            this.httpVersion = HttpVersion.HTTP_1_1;
            return this;
        }

        public Builder enableHttp2() {
            this.httpVersion = HTTP_2_0;
            return this;
        }

        public Builder setVersion(HttpVersion httpVersion) {
            this.httpVersion = httpVersion;
            return this;
        }

        public Builder setVersion(String httpVersion) {
            this.httpVersion = HttpVersion.valueOf(httpVersion);
            return this;
        }

        public Builder setTimeoutInMillis(long timeoutInMillis) {
            this.timeoutInMillis = timeoutInMillis;
            return this;
        }

        public Builder remoteAddress(HttpAddress httpAddress) {
            this.url = URL.builder()
                    .scheme(httpAddress.isSecure() ? "https" : "http")
                    .host(httpAddress.getInetSocketAddress().getHostString())
                    .port(httpAddress.getInetSocketAddress().getPort())
                    .build();
            this.httpVersion = httpAddress.getVersion();
            return this;
        }

        public Builder url(String url) {
            return url(URL.from(url));
        }

        public Builder url(URL url) {
            this.url = url;
            return this;
        }

        public Builder uri(String uri) {
            this.uri = uri;
            return this;
        }

        public Builder setHeaders(HttpHeaders headers) {
            this.headers = headers;
            return this;
        }

        public Builder addHeader(String name, Object value) {
            this.headers.add(name, value);
            return this;
        }

        public Builder setHeader(String name, Object value) {
            this.headers.set(name, value);
            return this;
        }

        public Builder removeHeader(String name) {
            removeHeaders.add(name);
            return this;
        }

        public Builder addParameter(String name, String value) {
            try {
                uriParameters.add(encoder.encode(name), encoder.encode(value));
            } catch (MalformedInputException | UnmappableCharacterException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }

        public Builder addFormParameter(String name, String value) {
            try {
                formParameters.add(encoder.encode(name), encoder.encode(value));
            } catch (MalformedInputException | UnmappableCharacterException e) {
                throw new IllegalArgumentException(e);
            }
            return this;
        }

        public Builder addCookie(Cookie cookie) {
            cookies.add(cookie);
            return this;
        }

        public Builder contentType(String contentType) {
            addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
            return this;
        }

        public Builder acceptGzip(boolean gzip) {
            this.gzip = gzip;
            return this;
        }

        public Builder keepAlive(boolean keepalive) {
            this.keepalive = keepalive;
            return this;
        }

        public Builder setFollowRedirect(boolean followRedirect) {
            this.followRedirect = followRedirect;
            return this;
        }

        public Builder setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
            return this;
        }

        public Builder enableBackOff(boolean enableBackOff) {
            this.enableBackOff = enableBackOff;
            return this;
        }

        public Builder setBackOff(BackOff backOff) {
            this.backOff = backOff;
            return this;
        }

        public Builder setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder text(String text) {
            ByteBuf byteBuf = ByteBufUtil.writeUtf8(allocator, text);
            try {
                content(byteBuf, HttpHeaderValues.TEXT_PLAIN);
            } finally {
                byteBuf.release();
            }
            return this;
        }

        public Builder json(String json) {
            ByteBuf byteBuf = ByteBufUtil.writeUtf8(allocator, json);
            try {
                content(byteBuf, HttpHeaderValues.APPLICATION_JSON);
            } finally {
                byteBuf.release();
            }
            return this;
        }

        public Builder xml(String xml) {
            content(xml, "application/xml");
            return this;
        }

        public Builder content(ByteBuf byteBuf) {
            this.content = byteBuf;
            return this;
        }

        public Builder content(CharSequence charSequence, String contentType) {
            content(charSequence.toString().getBytes(HttpUtil.getCharset(contentType, StandardCharsets.UTF_8)),
                    AsciiString.of(contentType));
            return this;
        }

        public Builder content(byte[] buf, String contentType) {
            content(buf, AsciiString.of(contentType));
            return this;
        }

        public Builder content(ByteBuf body, String contentType)  {
            content(body, AsciiString.of(contentType));
            return this;
        }

        public Request build() {
            DefaultHttpHeaders validatedHeaders = new DefaultHttpHeaders(true);
            if (url != null) {
                // attach user query parameters to URL
                URL.Builder mutator = url.mutator();
                uriParameters.forEach((k, v) -> v.forEach(value -> mutator.queryParam(k, value)));
                url = mutator.build();
                // let Netty's query string decoder/encoder work over the URL to add parameters given implicitly in url()
                String path = url.getPath();
                String query = url.getQuery();
                QueryStringDecoder queryStringDecoder = new QueryStringDecoder(query != null ? path + "?" + query : path, StandardCharsets.UTF_8);
                QueryStringEncoder queryStringEncoder = new QueryStringEncoder(queryStringDecoder.path());
                for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
                    for (String value : entry.getValue()) {
                        queryStringEncoder.addParam(entry.getKey(), value);
                    }
                }
                // build uri from QueryStringDecoder
                String pathAndQuery = queryStringEncoder.toString();
                StringBuilder sb = new StringBuilder();
                if (!pathAndQuery.isEmpty()) {
                    sb.append(pathAndQuery);
                }
                String fragment = url.getFragment();
                if (fragment != null && !fragment.isEmpty()) {
                    sb.append('#').append(fragment);
                }
                this.uri = sb.toString(); // the encoded form of path/query/fragment
                validatedHeaders.set(headers);
                String scheme = url.getScheme();
                if (httpVersion.majorVersion() == 2) {
                    validatedHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme);
                }
                validatedHeaders.set(HttpHeaderNames.HOST, url.getHostInfo());
            }
            validatedHeaders.set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
            if (userAgent != null) {
                validatedHeaders.set(HttpHeaderNames.USER_AGENT, userAgent);
            }
            if (gzip) {
                validatedHeaders.set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
            }
            // form parameters
            if (!formParameters.isEmpty()) {
                try {
                    // formParameters is already percent encoded
                    content(formParameters.getAsQueryString(false), formParameters.getContentType());
                } catch (MalformedInputException | UnmappableCharacterException e) {
                    throw new IllegalArgumentException();
                }
            }
            int length = content != null ? content.readableBytes() : 0;
            if (!validatedHeaders.contains(HttpHeaderNames.CONTENT_LENGTH) && !validatedHeaders.contains(HttpHeaderNames.TRANSFER_ENCODING)) {
                if (length < 0) {
                    validatedHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, "chunked");
                } else {
                    validatedHeaders.set(HttpHeaderNames.CONTENT_LENGTH, Long.toString(length));
                }
            }
            if (!validatedHeaders.contains(HttpHeaderNames.ACCEPT)) {
                validatedHeaders.set(HttpHeaderNames.ACCEPT, "*/*");
            }
            // RFC 2616 Section 14.10
            // "An HTTP/1.1 client that does not support persistent connections MUST include the "close" connection
            // option in every request message."
            if (httpVersion.majorVersion() == 1 && !keepalive) {
                validatedHeaders.set(HttpHeaderNames.CONNECTION, "close");
            }
            // at last, forced removal of unwanted headers
            for (String headerName : removeHeaders) {
                validatedHeaders.remove(headerName);
            }
            return new Request(url, uri, httpVersion, httpMethod, validatedHeaders, cookies, content,
                    timeoutInMillis, followRedirect, maxRedirects, 0, enableBackOff, backOff);
        }

        private void addHeader(AsciiString name, Object value) {
            if (!headers.contains(name)) {
                headers.add(name, value);
            }
        }

        private void content(byte[] buf, AsciiString contentType) {
            content(allocator.buffer().writeBytes(buf), contentType);
        }

        private void content(ByteBuf body, AsciiString contentType) {
            this.content = body;
            addHeader(HttpHeaderNames.CONTENT_LENGTH, (long) body.readableBytes());
            addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
        }
    }
}
