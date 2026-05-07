package dev.inventivetalent.mcp.fabric.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ObjectRegistry {

    private final Map<String, Object> handles = new HashMap<>();
    private final Map<String, Object> variables = new HashMap<>();
    private final AtomicLong counter = new AtomicLong();
    private final int maxRefs;

    public ObjectRegistry(int maxRefs) {
        this.maxRefs = maxRefs;
    }

    public String store(Object obj) {
        if (handles.size() >= maxRefs) {
            throw new IllegalStateException("Object registry full (max " + maxRefs + ")");
        }
        String id = "obj_" + counter.incrementAndGet();
        handles.put(id, obj);
        return id;
    }

    public void assign(String name, Object value) {
        variables.put(name, value);
    }

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
