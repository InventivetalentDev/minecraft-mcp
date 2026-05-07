package dev.inventivetalent.mcp.paper;

import com.google.gson.*;
import dev.inventivetalent.mcp.paper.handler.RequestHandler;
import dev.inventivetalent.mcp.paper.util.PackageFilter;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server embedded in the Paper plugin.
 * Each connected MCP-server client gets its own {@link RequestHandler}.
 */
public class McpWebSocketServer extends WebSocketServer {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final McpPlugin plugin;
    private final PackageFilter filter;
    private final int maxRefs;

    // Per-connection state
    private final Map<WebSocket, RequestHandler> handlers = new ConcurrentHashMap<>();

    public McpWebSocketServer(InetSocketAddress address, McpPlugin plugin,
                               PackageFilter filter, int maxRefs) {
        super(address);
        this.plugin  = plugin;
        this.filter  = filter;
        this.maxRefs = maxRefs;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        handlers.put(conn, new RequestHandler(plugin, filter, maxRefs));
        plugin.getLogger().info("[MCP] Client connected: " + conn.getRemoteSocketAddress());

        // Wire up log event forwarding for this client
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            RequestHandler h = handlers.get(conn);
            if (h != null && h.isLogSubscribed() && conn.isOpen()) {
                // Log tailing is handled on demand via logs.get; subscription just keeps alive
            }
        }, 20L, 20L);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        handlers.remove(conn);
        plugin.getLogger().info("[MCP] Client disconnected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        JsonObject req;
        try {
            req = JsonParser.parseString(message).getAsJsonObject();
        } catch (Exception e) {
            sendError(conn, null, -32700, "Parse error: " + e.getMessage());
            return;
        }

        String id = req.has("id") ? req.get("id").getAsString() : null;
        String method = req.has("method") ? req.get("method").getAsString() : null;
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();

        if (method == null) {
            sendError(conn, id, -32600, "Missing 'method' field");
            return;
        }

        RequestHandler handler = handlers.get(conn);
        if (handler == null) {
            sendError(conn, id, -32603, "Internal error: no handler");
            return;
        }

        try {
            JsonElement result = handler.handle(method, params);
            sendResult(conn, id, result);
        } catch (SecurityException e) {
            sendError(conn, id, -32001, "Security: " + e.getMessage());
        } catch (ClassNotFoundException e) {
            sendError(conn, id, -32002, "Class not found: " + e.getMessage());
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            sendError(conn, id, -32003, e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("[MCP] Error handling " + method + ": " + e);
            sendError(conn, id, -32000, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        plugin.getLogger().warning("[MCP] WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        plugin.getLogger().info("[MCP] WebSocket server ready on port " + getPort());
    }

    /** Push a log line to all subscribed clients. */
    public void broadcastLogLine(String line) {
        for (Map.Entry<WebSocket, RequestHandler> e : handlers.entrySet()) {
            RequestHandler h = e.getValue();
            if (!h.isLogSubscribed()) continue;
            String f = h.getLogFilter();
            if (f != null && !line.toLowerCase().contains(f.toLowerCase())) continue;
            JsonObject event = new JsonObject();
            event.addProperty("event", "log");
            JsonObject data = new JsonObject();
            data.addProperty("line", line);
            data.addProperty("timestamp", System.currentTimeMillis());
            event.add("data", data);
            try {
                e.getKey().send(GSON.toJson(event));
            } catch (Exception ignored) {}
        }
    }

    private void sendResult(WebSocket conn, String id, JsonElement result) {
        JsonObject res = new JsonObject();
        res.addProperty("id", id);
        res.add("result", result);
        conn.send(GSON.toJson(res));
    }

    private void sendError(WebSocket conn, String id, int code, String msg) {
        JsonObject res = new JsonObject();
        if (id != null) res.addProperty("id", id);
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", msg);
        res.add("error", error);
        conn.send(GSON.toJson(res));
    }
}
