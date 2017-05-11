package org.honton.chas.exists.example;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class WebServer extends NanoHTTPD {

  private static final Map<String, String> TYPES = new HashMap<>();
  static {
    TYPES.put("jar", "application/java-archive");
    TYPES.put("asc", "text/plain");
    TYPES.put("pom", "text/xml");
  }

  private Map<String, byte[]> storage = new HashMap<>();
  private final long start = System.currentTimeMillis();

  public WebServer(int port) throws IOException {
    super(port);
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
    String uri = session.getUri();
    String type = getType(uri);
    switch (session.getMethod()) {
      case HEAD:
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
    String suffix = uri.substring(uri.lastIndexOf('.')+1);
    String type = TYPES.get(suffix);
    return type!=null ?type :"text/"+suffix;
  }
}
