package org.xbib.netty.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http2.HttpConversionUtil;
import io.netty.util.AsciiString;
import org.xbib.net.URL;
import org.xbib.net.URLSyntaxException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class RequestBuilder {

    private static final HttpMethod DEFAULT_METHOD = HttpMethod.GET;

    private static final HttpVersion DEFAULT_HTTP_VERSION = HttpVersion.HTTP_1_1;

    private static final String DEFAULT_USER_AGENT = UserAgent.getUserAgent();

    private static final URL DEFAULT_URL = URL.from("http://localhost");

    private static final boolean DEFAULT_GZIP = true;

    private static final boolean DEFAULT_KEEPALIVE = true;

    private static final boolean DEFAULT_FOLLOW_REDIRECT = true;

    private static final int DEFAULT_TIMEOUT_MILLIS = 5000;

    private static final int DEFAULT_MAX_REDIRECT = 10;

    private static final HttpVersion HTTP_2_0 = HttpVersion.valueOf("HTTP/2.0");

    private final List<String> removeHeaders;

    private final Collection<Cookie> cookies;

    private HttpMethod httpMethod;

    private HttpHeaders headers;

    private HttpVersion httpVersion;

    private String userAgent;

    private boolean keepalive;

    private boolean gzip;

    private URL url;

    private QueryStringEncoder queryStringEncoder;

    private ByteBuf content;

    private int timeout;

    private boolean followRedirect;

    private int maxRedirects;

    RequestBuilder() {
        httpMethod = DEFAULT_METHOD;
        httpVersion = DEFAULT_HTTP_VERSION;
        userAgent = DEFAULT_USER_AGENT;
        gzip = DEFAULT_GZIP;
        keepalive = DEFAULT_KEEPALIVE;
        url = DEFAULT_URL;
        timeout = DEFAULT_TIMEOUT_MILLIS;
        followRedirect = DEFAULT_FOLLOW_REDIRECT;
        maxRedirects = DEFAULT_MAX_REDIRECT;
        headers = new DefaultHttpHeaders();
        removeHeaders = new ArrayList<>();
        cookies = new HashSet<>();
    }

    public RequestBuilder setMethod(HttpMethod httpMethod) {
        this.httpMethod = httpMethod;
        return this;
    }

    public RequestBuilder setHttp1() {
        this.httpVersion = HttpVersion.HTTP_1_1;
        return this;
    }

    public RequestBuilder setHttp2() {
        this.httpVersion = HTTP_2_0;
        return this;
    }

    public RequestBuilder setVersion(HttpVersion httpVersion) {
        this.httpVersion = httpVersion;
        return this;
    }

    public RequestBuilder setVersion(String httpVersion) {
        this.httpVersion = HttpVersion.valueOf(httpVersion);
        return this;
    }

    public RequestBuilder setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public RequestBuilder setURL(String url) {
        return setURL(URL.from(url));
    }

    public RequestBuilder setURL(URL url) {
        this.url = url;
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(URI.create(url.toString()), StandardCharsets.UTF_8);
        this.queryStringEncoder = new QueryStringEncoder(queryStringDecoder.path());
        for (Map.Entry<String, List<String>> entry : queryStringDecoder.parameters().entrySet()) {
            for (String value : entry.getValue()) {
                queryStringEncoder.addParam(entry.getKey(), value);
            }
        }
        return this;
    }

    public RequestBuilder path(String path) {
        if (this.url != null) {
            try {
                setURL(URL.base(url).resolve(path).toString());
            } catch (URLSyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            setURL(path);
        }
        return this;
    }

    public RequestBuilder setHeaders(HttpHeaders headers) {
        this.headers = headers;
        return this;
    }

    public RequestBuilder addHeader(String name, Object value) {
        this.headers.add(name, value);
        return this;
    }

    public RequestBuilder setHeader(String name, Object value) {
        this.headers.set(name, value);
        return this;
    }

    public RequestBuilder removeHeader(String name) {
        removeHeaders.add(name);
        return this;
    }

    public RequestBuilder addParam(String name, String value) {
        if (queryStringEncoder != null) {
            queryStringEncoder.addParam(name, value);
        }
        return this;
    }

    public RequestBuilder addCookie(Cookie cookie) {
        cookies.add(cookie);
        return this;
    }

    public RequestBuilder contentType(String contentType) {
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
        return this;
    }

    public RequestBuilder acceptGzip(boolean gzip) {
        this.gzip = gzip;
        return this;
    }

    public RequestBuilder keepAlive(boolean keepalive) {
        this.keepalive = keepalive;
        return this;
    }

    public RequestBuilder setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect;
        return this;
    }

    public RequestBuilder setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
        return this;
    }

    public RequestBuilder setUserAgent(String userAgent) {
        this.userAgent = userAgent;
        return this;
    }

    public RequestBuilder setContent(ByteBuf byteBuf) {
        this.content = byteBuf;
        return this;
    }

    public RequestBuilder text(String text) {
        content(text, HttpHeaderValues.TEXT_PLAIN);
        return this;
    }

    public RequestBuilder json(String json) {
        content(json, HttpHeaderValues.APPLICATION_JSON);
        return this;
    }

    public RequestBuilder xml(String xml) {
        content(xml, "application/xml");
        return this;
    }

    public RequestBuilder content(CharSequence charSequence, String contentType) {
        content(charSequence.toString().getBytes(StandardCharsets.UTF_8), AsciiString.of(contentType));
        return this;
    }

    public RequestBuilder content(byte[] buf, String contentType) {
        content(buf, AsciiString.of(contentType));
        return this;
    }

    public RequestBuilder content(ByteBuf body, String contentType)  {
        content(body, AsciiString.of(contentType));
        return this;
    }

    public Request build() {
        if (url == null) {
            throw new IllegalStateException("URL not set");
        }
        if (url.getHost() == null) {
            throw new IllegalStateException("URL host not set: " + url);
        }
        DefaultHttpHeaders validatedHeaders = new DefaultHttpHeaders(true);
        validatedHeaders.set(headers);
        String scheme = url.getScheme();
        if (httpVersion.majorVersion() == 2) {
            validatedHeaders.set(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme);
        }
        validatedHeaders.set(HttpHeaderNames.HOST, url.getHostInfo());
        validatedHeaders.set(HttpHeaderNames.DATE, DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));
        if (userAgent != null) {
            validatedHeaders.set(HttpHeaderNames.USER_AGENT, userAgent);
        }
        if (gzip) {
            validatedHeaders.set(HttpHeaderNames.ACCEPT_ENCODING, "gzip");
        }
        int length = content != null ? content.capacity() : 0;
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
        // create origin form from query string encoder
        String uri = toOriginForm();
        return new Request(url, httpVersion, httpMethod, validatedHeaders, cookies, uri, content,
                timeout, followRedirect, maxRedirects, 0);
    }

    private String toOriginForm() {
        StringBuilder sb = new StringBuilder();
        String pathAndQuery = queryStringEncoder.toString();
        sb.append(pathAndQuery.isEmpty() ? "/" : pathAndQuery);
        String ref = url.getFragment();
        if (ref != null && !ref.isEmpty()) {
            sb.append('#').append(ref);
        }
        return sb.toString();
    }

    private void addHeader(AsciiString name, Object value) {
        if (!headers.contains(name)) {
            headers.add(name, value);
        }
    }

    private void content(CharSequence charSequence, AsciiString contentType)  {
        content(charSequence.toString().getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void content(byte[] buf, AsciiString contentType) {
        content(PooledByteBufAllocator.DEFAULT.buffer(buf.length).writeBytes(buf), contentType);
    }

    private void content(ByteBuf body, AsciiString contentType) {
        this.content = body;
        addHeader(HttpHeaderNames.CONTENT_LENGTH, (long) body.readableBytes());
        addHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
    }
}