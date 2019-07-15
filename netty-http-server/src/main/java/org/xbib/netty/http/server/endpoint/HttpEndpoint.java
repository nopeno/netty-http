package org.xbib.netty.http.server.endpoint;

import org.xbib.net.Pair;
import org.xbib.net.QueryParameters;
import org.xbib.net.path.PathMatcher;
import org.xbib.net.path.PathNormalizer;
import org.xbib.netty.http.server.ServerRequest;
import org.xbib.netty.http.server.ServerResponse;
import org.xbib.netty.http.server.endpoint.service.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class HttpEndpoint implements Endpoint<HttpEndpointDescriptor> {

    private static final PathMatcher pathMatcher = new PathMatcher();

    private static final List<String> DEFAULT_METHODS = Arrays.asList("GET", "HEAD");

    private final String prefix;

    private final String path;

    private final List<String> methods;

    private final List<String> contentTypes;

    private final List<Service> filters;

    private HttpEndpoint(String prefix, String path, List<String> methods, List<String> contentTypes, List<Service> filters) {
        this.prefix = PathNormalizer.normalize(prefix);
        this.path = PathNormalizer.normalize(path);
        this.methods = methods;
        this.contentTypes = contentTypes;
        this.filters = filters;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(HttpEndpoint endpoint) {
        return new Builder()
                .setPrefix(endpoint.prefix)
                .setPath(endpoint.path)
                .setMethods(endpoint.methods)
                .setContentTypes(endpoint.contentTypes)
                .setFilters(endpoint.filters);
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public boolean matches(HttpEndpointDescriptor info) {
        return pathMatcher.match(prefix + path, info.getPath()) &&
                (methods == null || methods.isEmpty() || (methods.contains(info.getMethod()))) &&
                (contentTypes == null || contentTypes.isEmpty() || info.getContentType() == null ||
                contentTypes.stream().anyMatch(info.getContentType()::startsWith));
    }

    @Override
    public void resolveUriTemplate(ServerRequest serverRequest) throws IOException {
        if (pathMatcher.match(prefix + path,  serverRequest.getEffectiveRequestPath())) {
            QueryParameters queryParameters = pathMatcher.extractUriTemplateVariables(prefix + path,
                    serverRequest.getEffectiveRequestPath());
            for (Pair<String, String> pair : queryParameters) {
                serverRequest.addPathParameter(pair.getFirst(), pair.getSecond());
            }
        }
    }

    public void handle(ServerRequest serverRequest, ServerResponse serverResponse) throws IOException {
        serverRequest.setContext(pathMatcher.tokenizePath(getPrefix()));
        for (Service service : filters) {
            service.handle(serverRequest, serverResponse);
            if (serverResponse.getStatus() != null) {
                break;
            }
        }
    }

    @Override
    public String toString() {
        return "Endpoint[prefix=" + prefix + ",path=" + path + ",methods=" + methods + ",contentTypes=" + contentTypes + " --> " + filters +"]";
    }

    static class EndpointPathComparator implements Comparator<HttpEndpoint> {

        private final Comparator<String> pathComparator;

        EndpointPathComparator(String path) {
            this.pathComparator = pathMatcher.getPatternComparator(path);
        }

        @Override
        public int compare(HttpEndpoint endpoint1, HttpEndpoint endpoint2) {
            return pathComparator.compare(endpoint1.path, endpoint2.path);
        }
    }

    public static class Builder {

        private String prefix;

        private String path;

        private List<String> methods;

        private List<String> contentTypes;

        private List<Service> filters;

        Builder() {
            this.prefix = "/";
            this.path = "/**";
            this.methods = new ArrayList<>();
            this.contentTypes = new ArrayList<>();
            this.filters = new ArrayList<>();
        }

        public Builder setPrefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setMethods(List<String> methods) {
            this.methods = methods;
            return this;
        }

        public Builder addMethod(String method) {
            methods.add(method);
            return this;
        }

        public Builder setContentTypes(List<String> contentTypes) {
            this.contentTypes = contentTypes;
            return this;
        }

        public Builder addContentType(String contentType) {
            this.contentTypes.add(contentType);
            return this;
        }

        public Builder setFilters(List<Service> filters) {
            this.filters = filters;
            return this;
        }

        public Builder addFilter(Service filter) {
            this.filters.add(filter);
            return this;
        }

        public HttpEndpoint build() {
            if (methods.isEmpty()) {
                methods = DEFAULT_METHODS;
            }
            return new HttpEndpoint(prefix, path, methods, contentTypes, filters);
        }
    }
}
