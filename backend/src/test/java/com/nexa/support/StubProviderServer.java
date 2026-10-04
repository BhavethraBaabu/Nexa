package com.nexa.support;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A local stand-in for Atlassian and Slack APIs. Tests script responses per path (queued, with the
 * last one repeating) and inspect the requests Nexa sent. Unscripted paths return 404.
 */
public final class StubProviderServer {

    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
        public String header(String name) {
            return headers.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(name))
                    .map(e -> e.getValue().getFirst()).findFirst().orElse(null);
        }
    }

    private record Response(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Deque<Response>> responses = new ConcurrentHashMap<>();
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

    public StubProviderServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            Response response = next(exchange.getRequestMethod() + " " + path);
            byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(response.status(), bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Queues a response for "METHOD /path". The last queued response repeats. */
    public StubProviderServer on(String methodAndPath, int status, String body) {
        responses.computeIfAbsent(methodAndPath, k -> new ArrayDeque<>()).add(new Response(status, body));
        return this;
    }

    public List<Request> requests(String methodAndPath) {
        synchronized (requests) {
            return requests.stream().filter(r -> (r.method() + " " + r.path()).equals(methodAndPath)).toList();
        }
    }

    public void reset() {
        responses.clear();
        requests.clear();
    }

    public void stop() {
        server.stop(0);
    }

    private Response next(String key) {
        Deque<Response> queue = responses.get(key);
        if (queue == null || queue.isEmpty()) {
            return new Response(404, "{\"error\":\"not stubbed: " + key + "\"}");
        }
        synchronized (queue) {
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
    }
}
