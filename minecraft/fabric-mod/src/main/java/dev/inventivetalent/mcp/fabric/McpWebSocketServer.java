package dev.inventivetalent.mcp.fabric;

import com.google.gson.*;
import dev.inventivetalent.mcp.fabric.handler.RequestHandler;
import dev.inventivetalent.mcp.fabric.util.PackageFilter;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class McpWebSocketServer extends WebSocketServer {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft-mcp");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final PackageFilter filter;
    private final int maxRefs;
    private final Map<WebSocket, RequestHandler> handlers = new ConcurrentHashMap<>();

    public McpWebSocketServer(InetSocketAddress address, PackageFilter filter, int maxRefs) {
        super(address);
        this.filter  = filter;
        this.maxRefs = maxRefs;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        handlers.put(conn, new RequestHandler(filter, maxRefs));
        LOGGER.info("[MCP] Client connected: {}", conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        handlers.remove(conn);
        LOGGER.info("[MCP] Client disconnected: {}", conn.getRemoteSocketAddress());
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

        String id     = req.has("id")     ? req.get("id").getAsString()     : null;
        String method = req.has("method") ? req.get("method").getAsString() : null;
        JsonObject params = req.has("params") ? req.getAsJsonObject("params") : new JsonObject();

        if (method == null) {
            sendError(conn, id, -32600, "Missing 'method'");
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
            LOGGER.warn("[MCP] Error handling {}: {}", method, e.toString());
            sendError(conn, id, -32000, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOGGER.warn("[MCP] WebSocket error: {}", ex.getMessage());
    }

    @Override
    public void onStart() {
        LOGGER.info("[MCP] WebSocket server ready on port {}", getPort());
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
