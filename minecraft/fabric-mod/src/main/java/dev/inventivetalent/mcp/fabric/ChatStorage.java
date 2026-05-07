package dev.inventivetalent.mcp.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe ring buffer for received chat messages.
 * Filled by the ChatHudMixin; read by the request handler.
 */
public class ChatStorage {

    private static final int MAX_MESSAGES = 500;

    private static final CopyOnWriteArrayList<Map<String, Object>> messages =
            new CopyOnWriteArrayList<>();

    public static void addMessage(String text, String sender) {
        Map<String, Object> entry = Map.of(
                "text", text,
                "sender", sender != null ? sender : "",
                "timestamp", System.currentTimeMillis()
        );
        messages.add(entry);
        while (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
    }

    public static List<Map<String, Object>> getRecent(int count) {
        List<Map<String, Object>> copy = new ArrayList<>(messages);
        int start = Math.max(0, copy.size() - count);
        return Collections.unmodifiableList(copy.subList(start, copy.size()));
    }
}
