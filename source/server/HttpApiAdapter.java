import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Local-only HTTP bridge to the existing TCP server; no changes to the TCP protocol. */
public final class HttpApiAdapter {
    private static final Gson GSON = new Gson();
    private static final Set<String> ACTIONS = Set.of(
            "REGISTER", "LOGIN", "GET_USER_LIST", "UPDATE_PROFILE",
            "SEND_PRIVATE_MESSAGE", "GET_CHAT_HISTORY", "MARK_MESSAGES_READ",
            "CREATE_GROUP", "GET_MY_GROUPS", "GET_GROUP_MEMBERS", "ADD_GROUP_MEMBER",
            "REMOVE_GROUP_MEMBER", "TRANSFER_LEADER", "LEAVE_GROUP", "SEND_GROUP_MESSAGE",
            "GET_GROUP_HISTORY", "SEND_FILE", "DOWNLOAD_FILE");
    private static final Set<String> EVENTS = Set.of("USER_STATUS_CHANGE", "RECEIVE_PRIVATE_MESSAGE",
            "RECEIVE_NOTIFICATION", "RECEIVE_GROUP_MESSAGE", "GROUP_UPDATED");
    private static final int MAX_BODY_BYTES = 30 * 1024 * 1024;
    private static final int MAX_SESSIONS = 64;
    private static final int MAX_EVENTS = 256;
    private static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(30);
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static int tcpPort;

    public static void main(String[] args) throws IOException {
        int httpPort = Integer.getInteger("chatnest.http.port", 8080);
        tcpPort = Integer.getInteger("chatnest.tcp.port", ServerMain.PORT);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", httpPort), 0);
        java.util.concurrent.ExecutorService workers = Executors.newFixedThreadPool(16);
        ScheduledExecutorService reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "http-session-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(workers);
        server.createContext("/api/", HttpApiAdapter::handle);
        reaper.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            SESSIONS.forEach((id, session) -> {
                if (session.closed.get() || now - session.lastAccess > IDLE_MILLIS) {
                    if (SESSIONS.remove(id, session)) session.close();
                }
            });
        }, 1, 1, TimeUnit.MINUTES);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            SESSIONS.values().forEach(Session::close);
            SESSIONS.clear();
            workers.shutdownNow();
            reaper.shutdownNow();
        }));
        server.start();
        System.out.println("HTTP adapter: http://127.0.0.1:" + server.getAddress().getPort());
        System.out.println("TCP upstream: 127.0.0.1:" + tcpPort);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try {
            // No browser CORS support: this adapter is for local tools such as Postman.
            if (exchange.getRequestHeaders().containsKey("Origin")) {
                throw new ApiException(403, "Browser-origin requests are disabled; use Postman locally.");
            }
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/health".equals(path)) {
                requireMethod(method, "GET");
                JsonObject data = new JsonObject();
                data.addProperty("tcpHost", "127.0.0.1");
                data.addProperty("tcpPort", tcpPort);
                data.addProperty("note", "Adapter is running; upstream is checked when a session is created.");
                reply(exchange, 200, success(data));
            } else if ("/api/sessions".equals(path) && "POST".equals(method)) {
                Session session;
                String id = UUID.randomUUID().toString();
                synchronized (SESSIONS) {
                    if (SESSIONS.size() >= MAX_SESSIONS) {
                        throw new ApiException(503, "Session limit reached; close unused sessions.");
                    }
                    try {
                        session = new Session();
                    } catch (IOException e) {
                        throw new ApiException(502, "Cannot connect to the TCP server. Start ServerMain first.");
                    }
                    SESSIONS.put(id, session);
                }
                JsonObject data = new JsonObject();
                data.addProperty("sessionId", id);
                data.addProperty("idleTimeoutSeconds", IDLE_MILLIS / 1000);
                try {
                    reply(exchange, 201, success(data));
                } catch (IOException e) {
                    SESSIONS.remove(id, session);
                    session.close();
                    throw e;
                }
            } else if ("/api/sessions".equals(path)) {
                requireMethod(method, "DELETE");
                Session session = requireSession(exchange);
                SESSIONS.remove(exchange.getRequestHeaders().getFirst("X-Session-Id"), session);
                session.close();
                reply(exchange, 200, success(null));
            } else if ("/api/actions".equals(path)) {
                requireMethod(method, "POST");
                Session session = requireSession(exchange);
                JsonObject request = readRequest(exchange);
                validate(request);
                try {
                    // Business failures remain HTTP 200; preserve the TCP response exactly.
                    reply(exchange, 200, session.request(request));
                } catch (ApiException e) {
                    if (session.closed.get()) {
                        SESSIONS.remove(exchange.getRequestHeaders().getFirst("X-Session-Id"), session);
                    }
                    throw e;
                }
            } else if ("/api/events".equals(path)) {
                requireMethod(method, "GET");
                Session session = requireSession(exchange);
                reply(exchange, 200, success(session.drainEvents()));
            } else {
                throw new ApiException(404, "Endpoint not found.");
            }
        } catch (ApiException e) {
            reply(exchange, e.status, error(e.getMessage()));
        } catch (RuntimeException e) {
            reply(exchange, 500, error("Internal adapter error."));
        } finally {
            exchange.close();
        }
    }

    private static void requireMethod(String actual, String expected) throws ApiException {
        if (!expected.equals(actual)) throw new ApiException(405, "Use HTTP " + expected + " for this endpoint.");
    }

    private static Session requireSession(HttpExchange exchange) throws ApiException {
        String id = exchange.getRequestHeaders().getFirst("X-Session-Id");
        Session session = id == null ? null : SESSIONS.get(id);
        if (session == null) throw new ApiException(401, "Missing or unknown X-Session-Id; create a session first.");
        if (session.closed.get() || System.currentTimeMillis() - session.lastAccess > IDLE_MILLIS) {
            SESSIONS.remove(id, session);
            session.close();
            throw new ApiException(401, "Session closed or expired; create a new session and log in again.");
        }
        session.lastAccess = System.currentTimeMillis();
        return session;
    }

    private static JsonObject readRequest(HttpExchange exchange) throws IOException, ApiException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !"application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim())) {
            throw new ApiException(415, "Content-Type must be application/json.");
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (bytes.length > MAX_BODY_BYTES) throw new ApiException(413, "JSON body exceeds 30 MiB.");
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) throw new IllegalArgumentException();
            return parsed.getAsJsonObject();
        } catch (RuntimeException e) {
            throw new ApiException(400, "Body must be a valid JSON object.");
        }
    }

    /** Validate required fields to avoid the TCP handlers' silent failures on malformed input. */
    private static void validate(JsonObject request) throws ApiException {
        stringField(request, "action", true);
        String action = request.get("action").getAsString();
        if (!ACTIONS.contains(action)) throw new ApiException(400, "Unsupported action; use an exact uppercase action name.");
        JsonObject data = new JsonObject();
        if (request.has("data") && !request.get("data").isJsonNull()) {
            if (!request.get("data").isJsonObject()) throw new ApiException(400, "data must be an object.");
            data = request.getAsJsonObject("data");
        }
        switch (action) {
            case "REGISTER":
                stringField(data, "username", true);
                stringField(data, "password", true);
                stringField(data, "fullName", true);
                break;
            case "LOGIN":
                stringField(data, "username", true);
                stringField(data, "password", true);
                break;
            case "UPDATE_PROFILE":
                stringField(data, "fullName", true);
                stringField(data, "avatar", false);
                break;
            case "SEND_PRIVATE_MESSAGE":
                stringField(data, "receiver", true);
                stringField(data, "content", true);
                break;
            case "GET_CHAT_HISTORY":
                stringField(data, "targetUser", true);
                intField(data, "limit", false, 1);
                break;
            case "MARK_MESSAGES_READ":
                intField(data, "conversationId", true, 1);
                break;
            case "CREATE_GROUP":
                stringField(data, "name", true);
                if (!data.has("members") || !data.get("members").isJsonArray()) {
                    throw new ApiException(400, "members must be an array of strings.");
                }
                for (JsonElement member : data.getAsJsonArray("members")) {
                    if (!member.isJsonPrimitive() || !member.getAsJsonPrimitive().isString()) {
                        throw new ApiException(400, "members must be an array of strings.");
                    }
                }
                break;
            case "ADD_GROUP_MEMBER":
            case "REMOVE_GROUP_MEMBER":
            case "TRANSFER_LEADER":
                intField(data, "groupId", true, 1);
                stringField(data, "username", true);
                break;
            case "GET_GROUP_MEMBERS":
            case "LEAVE_GROUP":
                intField(data, "groupId", true, 1);
                break;
            case "SEND_GROUP_MESSAGE":
                intField(data, "groupId", true, 1);
                stringField(data, "content", true);
                break;
            case "GET_GROUP_HISTORY":
                intField(data, "groupId", true, 1);
                intField(data, "limit", false, 1);
                break;
            case "SEND_FILE":
                stringField(data, "receiver", true);
                stringField(data, "fileName", true);
                stringField(data, "base64Data", true);
                stringField(data, "fileType", false);
                stringField(data, "caption", false);
                intField(data, "duration", false, 0);
                break;
            case "DOWNLOAD_FILE":
                intField(data, "fileId", true, 1);
                break;
            default:
                break;
        }
    }

    private static void stringField(JsonObject data, String name, boolean required) throws ApiException {
        JsonElement value = data.get(name);
        if (value == null && !required) return;
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new ApiException(400, name + " must be a string.");
        }
    }

    private static void intField(JsonObject data, String name, boolean required, int minimum) throws ApiException {
        JsonElement value = data.get(name);
        if (value == null && !required) return;
        try {
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException();
            }
            int number = value.getAsBigDecimal().intValueExact();
            if (number < minimum) throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            throw new ApiException(400, name + " must be an integer >= " + minimum + " (32-bit).");
        }
    }

    private static JsonObject success(JsonObject data) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "SUCCESS");
        if (data != null) result.add("data", data);
        return result;
    }

    private static JsonObject error(String message) {
        JsonObject result = new JsonObject();
        result.addProperty("status", "FAILED");
        result.addProperty("message", message);
        return result;
    }

    private static void reply(HttpExchange exchange, int code, JsonObject body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final class ApiException extends Exception {
        final int status;
        ApiException(int status, String message) { super(message); this.status = status; }
    }

    private static final class Pending {
        final String expectedAction;
        final CompletableFuture<JsonObject> response = new CompletableFuture<>();
        Pending(String action) { expectedAction = action + "_RESPONSE"; }
    }

    private static final class Session {
        final Socket socket = new Socket();
        final PrintWriter writer;
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicReference<Pending> pending = new AtomicReference<>();
        final Object requestLock = new Object();
        final Deque<JsonObject> events = new ArrayDeque<>();
        volatile long lastAccess = System.currentTimeMillis();
        long droppedEvents;
        boolean loggedIn;

        Session() throws IOException {
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), tcpPort), 3000);
                socket.setTcpNoDelay(true);
                writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                Thread thread = new Thread(() -> readLoop(reader), "http-tcp-session-reader");
                thread.setDaemon(true);
                thread.start();
            } catch (IOException e) {
                socket.close();
                throw e;
            }
        }

        JsonObject request(JsonObject request) throws ApiException {
            // TCP has no correlation ID. Exactly one in-flight action per session.
            synchronized (requestLock) {
                if (closed.get()) throw new ApiException(502, "TCP session is closed; create a new session.");
                String action = request.get("action").getAsString();
                if ("LOGIN".equals(action) && loggedIn) {
                    throw new ApiException(409, "Already logged in; close this session before switching accounts.");
                }
                Pending call = new Pending(action);
                pending.set(call);
                try {
                    writer.println(GSON.toJson(request));
                    if (writer.checkError()) throw new IOException("TCP write failed");
                    JsonObject result = call.response.get(15, TimeUnit.SECONDS);
                    if ("LOGIN".equals(action) && result.has("status")
                            && "SUCCESS".equals(result.get("status").getAsString())) loggedIn = true;
                    return result;
                } catch (TimeoutException e) {
                    close(); // Never reuse a stream whose delayed response could match a later request.
                    throw new ApiException(504, "TCP server did not respond within 15 seconds; session closed. Do not blindly retry writes.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    close();
                    throw new ApiException(502, "Request interrupted; session closed.");
                } catch (ExecutionException | IOException e) {
                    close();
                    throw new ApiException(502, "TCP connection failed; create a new session and log in again.");
                } finally {
                    pending.compareAndSet(call, null);
                    lastAccess = System.currentTimeMillis();
                }
            }
        }

        private void readLoop(BufferedReader reader) {
            try (BufferedReader input = reader) {
                String line;
                while ((line = input.readLine()) != null) {
                    JsonObject packet = JsonParser.parseString(line).getAsJsonObject();
                    String action = packet.get("action").getAsString();
                    if (EVENTS.contains(action)) {
                        synchronized (events) {
                            if (events.size() == MAX_EVENTS) { events.removeFirst(); droppedEvents++; }
                            events.addLast(packet);
                        }
                    } else {
                        Pending call = pending.get();
                        if (call == null || !(call.expectedAction.equals(action) || "ERROR".equals(action))
                                || !call.response.complete(packet)) {
                            break; // Unsolicited or duplicate response: stream can no longer be correlated safely.
                        }
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // Do not log payloads: they may contain account information or file contents.
            } finally {
                close();
            }
        }

        JsonObject drainEvents() {
            JsonObject data = new JsonObject();
            JsonArray packets = new JsonArray();
            synchronized (events) {
                while (!events.isEmpty()) packets.add(events.removeFirst());
                data.addProperty("droppedCount", droppedEvents);
                droppedEvents = 0;
            }
            data.add("events", packets);
            return data;
        }

        void close() {
            if (closed.compareAndSet(false, true)) {
                try { socket.close(); } catch (IOException ignored) { }
                Pending call = pending.get();
                if (call != null) call.response.completeExceptionally(new IOException("TCP session closed"));
            }
        }
    }
}
