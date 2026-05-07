package dev.inventivetalent.mcp.paper.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds live object references so that non-serialisable results from reflection
 * calls can be passed back as targets or arguments in subsequent calls.
 *
 * Two kinds of entries:
 *   - anonymous handles  ("obj_123") – auto-generated IDs
 *   - named variables    ("$name")   – explicitly assigned by the caller
 */
public class ObjectRegistry {

    private final Map<String, Object> handles = new HashMap<>();
    private final Map<String, Object> variables = new HashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final int maxRefs;

    public ObjectRegistry(int maxRefs) {
        this.maxRefs = maxRefs;
    }

    /** Store an object with an auto-generated ID and return that ID. */
    public String store(Object obj) {
        if (handles.size() >= maxRefs) {
            throw new IllegalStateException("Object registry full (max " + maxRefs + ")");
        }
        String id = "obj_" + counter.incrementAndGet();
        handles.put(id, obj);
        return id;
    }

    /** Assign a named variable. */
    public void assign(String name, Object value) {
        variables.put(name, value);
    }

    /**
     * Resolve a reference string.
     * - "$name"  → named variable
     * - "obj_N"  → anonymous handle
     */
    public Object resolve(String ref) {
        if (ref == null) return null;
        if (ref.startsWith("$")) return variables.get(ref.substring(1));
        return handles.get(ref);
    }

    public boolean has(String ref) {
        if (ref.startsWith("$")) return variables.containsKey(ref.substring(1));
        return handles.containsKey(ref);
    }

    public void delete(String name) {
        variables.remove(name);
    }

    public void clear() {
        handles.clear();
        variables.clear();
    }

    public Map<String, Object> getVariables() {
        return Map.copyOf(variables);
    }

    public Set<String> getHandleIds() {
        return Set.copyOf(handles.keySet());
    }
}
