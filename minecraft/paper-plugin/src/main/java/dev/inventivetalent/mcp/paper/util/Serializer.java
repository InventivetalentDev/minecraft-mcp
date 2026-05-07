package dev.inventivetalent.mcp.paper.util;

import com.google.gson.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.util.*;

/**
 * Converts Java values to JSON for transport over the WebSocket protocol.
 * Non-serialisable objects are stored in the registry as opaque handles.
 */
public class Serializer {

    private final ObjectRegistry registry;

    public Serializer(ObjectRegistry registry) {
        this.registry = registry;
    }

    public JsonElement serialize(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof Boolean b) return new JsonPrimitive(b);
        if (value instanceof Number n) return new JsonPrimitive(n);
        if (value instanceof String s) return new JsonPrimitive(s);
        if (value instanceof Character c) return new JsonPrimitive(c);
        if (value instanceof Enum<?> e) return new JsonPrimitive(e.name());

        if (value instanceof Collection<?> col) {
            JsonArray arr = new JsonArray();
            for (Object item : col) arr.add(serialize(item));
            return arr;
        }

        if (value instanceof Map<?, ?> map) {
            JsonObject obj = new JsonObject();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                obj.add(String.valueOf(e.getKey()), serialize(e.getValue()));
            }
            return obj;
        }

        if (value.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) arr.add(serialize(Array.get(value, i)));
            return arr;
        }

        // Well-known Bukkit types get a rich serialisation
        if (value instanceof Location loc) return serializeLocation(loc);
        if (value instanceof Player p) return serializePlayer(p);
        if (value instanceof Entity e) return serializeEntity(e);
        if (value instanceof World w) return serializeWorld(w);

        // Fall back to opaque object reference
        String id = registry.store(value);
        JsonObject ref = new JsonObject();
        ref.addProperty("__type", "object_ref");
        ref.addProperty("__id", id);
        ref.addProperty("__class", value.getClass().getName());
        return ref;
    }

    private JsonObject serializeLocation(Location loc) {
        JsonObject o = new JsonObject();
        o.addProperty("world", loc.getWorld() != null ? loc.getWorld().getName() : null);
        o.addProperty("x", loc.getX());
        o.addProperty("y", loc.getY());
        o.addProperty("z", loc.getZ());
        o.addProperty("yaw", loc.getYaw());
        o.addProperty("pitch", loc.getPitch());
        return o;
    }

    private JsonObject serializePlayer(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.getName());
        o.addProperty("uuid", p.getUniqueId().toString());
        o.addProperty("health", p.getHealth());
        o.addProperty("gameMode", p.getGameMode().name());
        o.add("location", serializeLocation(p.getLocation()));
        return o;
    }

    private JsonObject serializeEntity(Entity e) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", e.getUniqueId().toString());
        o.addProperty("type", e.getType().name());
        o.add("location", serializeLocation(e.getLocation()));
        return o;
    }

    private JsonObject serializeWorld(World w) {
        JsonObject o = new JsonObject();
        o.addProperty("name", w.getName());
        o.addProperty("uuid", w.getUID().toString());
        o.addProperty("environment", w.getEnvironment().name());
        return o;
    }

    // ── Deserialise arguments for reflection calls ──────────────────────────

    /**
     * Convert a JSON value + type hint into a Java object suitable as a
     * reflection argument. Resolves "$varName" strings and object_ref handles.
     */
    public Object deserializeArg(JsonElement el, String typeName) {
        if (el == null || el.isJsonNull()) return null;

        // Object reference or variable
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("__id")) {
                return registry.resolve(obj.get("__id").getAsString());
            }
        }
        if (el.isJsonPrimitive() && el.getAsString().startsWith("$")) {
            return registry.resolve(el.getAsString());
        }

        return switch (typeName) {
            case "byte",   "java.lang.Byte"      -> el.getAsByte();
            case "short",  "java.lang.Short"     -> el.getAsShort();
            case "int",    "java.lang.Integer"   -> el.getAsInt();
            case "long",   "java.lang.Long"      -> el.getAsLong();
            case "float",  "java.lang.Float"     -> el.getAsFloat();
            case "double", "java.lang.Double"    -> el.getAsDouble();
            case "boolean","java.lang.Boolean"   -> el.getAsBoolean();
            case "char",   "java.lang.Character" -> el.getAsCharacter();
            case "java.lang.String"              -> el.getAsString();
            default                              -> el.getAsString(); // best effort
        };
    }
}
