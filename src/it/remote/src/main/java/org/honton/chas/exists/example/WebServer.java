package org.honton.chas.exists.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class WebServer implements HttpHandler {

    private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

    private static final Map<String, String> TYPES = new HashMap<>();
    public static final int UNAUTHORIZED = 401;
    public static final int NOT_FOUND = 404;
    public static final int OK = 200;
    public static final int NOT_IMPLEMENTED = 501;

    static {
        TYPES.put("jar", "application/java-archive");
        TYPES.put("asc", "text/plain");
        TYPES.put("pom", "text/xml");
    }

    private final Map<String, byte[]> storage = new HashMap<>();

    public WebServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this);
        server.start();
        LOG.fine("Server running on port " + port);
    }

    public static void main(String[] args) throws IOException {
        new WebServer(Integer.parseInt(args[0]));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        LOG.fine(exchange.getRequestMethod() + " " + exchange.getRequestURI());
        int statusCode = generateResponse(exchange);
        LOG.fine("Response: " + statusCode);
    }

    private int generateResponse(HttpExchange exchange) throws IOException {
        String path = authorize(exchange);
        if (path == null) {
            return UNAUTHORIZED;
        }

        switch (exchange.getRequestMethod()) {
            case "HEAD": {
                int statusCode = !storage.containsKey(path) ? NOT_FOUND : OK;
                exchange.sendResponseHeaders(statusCode, -1);
                return statusCode;
            }
            case "GET": {
                byte[] file = storage.get(path);
                if (file == null) {
                    exchange.sendResponseHeaders(NOT_FOUND, -1);
                    return NOT_FOUND;
                }
                String type = getType(path);
                exchange.getResponseHeaders().set("Content-Type", type);
                exchange.sendResponseHeaders(OK, file.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(file);
                }
            }
            case "PUT": {
                try (InputStream is = exchange.getRequestBody()) {
                    storage.put(path, is.readAllBytes());
                    exchange.sendResponseHeaders(OK, -1);
                }
            }
            default:
                exchange.sendResponseHeaders(NOT_IMPLEMENTED, -1);
                return NOT_IMPLEMENTED;
        }
    }

    private static String authorize(HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().getPath();
        if (uri.startsWith("/auth")) {
            String authorization = exchange.getRequestHeaders().getFirst("authorization");
            if (authorization == null || !authorization.equals("Basic dXNlcjE6cGFzc3dvcmQxMjM=")) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"Authentication needed\"");
                exchange.sendResponseHeaders(UNAUTHORIZED, -1);
                return null;
            }
            return uri.substring("/auth".length());
        } else if (uri.startsWith("/header-auth")) {
            String authorization = exchange.getRequestHeaders().getFirst("job-token");
            if (authorization == null) {
                exchange.sendResponseHeaders(UNAUTHORIZED, -1);
                return null;
            }
            return uri.substring("/header-auth".length());
        }
        return uri;
    }

    private static String getType(String uri) {
        int dot = uri.lastIndexOf('.');
        if (dot == -1) {
            return "text/plain";
        } else {
            String suffix = uri.substring(dot + 1);
            String type = TYPES.get(suffix);
            return type != null ? type : "text/" + suffix;
        }
    }
}
