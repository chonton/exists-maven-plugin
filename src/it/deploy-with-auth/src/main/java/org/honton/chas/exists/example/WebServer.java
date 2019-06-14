package org.honton.chas.exists.example;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class WebServer extends NanoHTTPD {
  private static final Logger LOG = Logger.getLogger(WebServer.class.getName());

  private static final Map<String, String> TYPES = new HashMap<>();
  static {
    TYPES.put("jar", "application/java-archive");
    TYPES.put("asc", "text/plain");
    TYPES.put("pom", "text/xml");
  }

  private Map<String, byte[]> storage = new HashMap<>();

  public WebServer(int port) throws IOException {
    super(port);
    LOG.fine("Server running on port " + port);
    start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
  }

  public static void main(String[] args) throws IOException {
    new WebServer(Integer.parseInt(args[0]));
  }

  byte[] getInput(IHTTPSession session) throws IOException {
    int length = Integer.parseInt(getHeader(session, "Content-Length"));
    byte[] content = new byte[length];
    session.getInputStream().read(content);
    return content;
  }

  private String getHeader(IHTTPSession session, String key) {
    for(Map.Entry<String, String> header : session.getHeaders().entrySet()) {
      if(header.getKey().equalsIgnoreCase(key)) {
        return header.getValue();
      }
    } return null;
  }

  @Override
  public Response serve(IHTTPSession session) {
    LOG.fine(session.getMethod() + " " + session.getUri());
    Response response = generateResponse(session);
    LOG.fine("Response: " + response.getStatus());
    return response;
  }

  private Response generateResponse(IHTTPSession session) {
    String uri = session.getUri();
    if (uri.startsWith("/auth")) {
    String authorization = session.getHeaders().get("authorization");
    if (authorization == null || !authorization.equals("Basic dXNlcjE6cGFzc3dvcmQxMjM=")) {
      Response response = NanoHTTPD.newFixedLengthResponse(Status.UNAUTHORIZED, "", null);
      response.addHeader("WWW-Authenticate", "Basic realm=\"Authentication needed\"");
      return response;
    }
    uri = uri.substring("/auth".length());
    }
    String type = getType(uri);
    switch (session.getMethod()) {
      case HEAD: {
        if (!storage.containsKey(uri)) {
          return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, type, null);
        }
        return NanoHTTPD.newFixedLengthResponse(Status.OK, type, null);
      }
      case GET: {
        byte[] file = storage.get(uri);
        if (file == null) {
          return NanoHTTPD.newFixedLengthResponse(Status.NOT_FOUND, type, uri);
        }
        return newFixedLengthResponse(Status.OK, type, new ByteArrayInputStream(file), (long)file.length);
      }
      case PUT: {
        try {
          storage.put(uri, getInput(session));
          return NanoHTTPD.newFixedLengthResponse(Status.OK, "", uri);
        }
        catch (IOException e) {
          return NanoHTTPD.newFixedLengthResponse(Status.INTERNAL_ERROR, "text/plain", e.getMessage());
        }
      }
      default:
        return NanoHTTPD.newFixedLengthResponse(Status.NOT_IMPLEMENTED, "text/plain", session.getMethod() + " not supported");
    }
  }

  private String getType(String uri) {
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
