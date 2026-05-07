package dev.inventivetalent.mcp.fabric.util;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Array;
import java.util.*;

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

        // Well-known Minecraft types
        if (value instanceof Vec3 v) return serializeVec3(v);
        if (value instanceof BlockPos bp) return serializeBlockPos(bp);
        if (value instanceof BlockState bs) return serializeBlockState(bs);
        if (value instanceof ItemStack is) return serializeItemStack(is);
        if (value instanceof LocalPlayer p) return serializePlayer(p);
        if (value instanceof Entity e) return serializeEntity(e);

        // Opaque reference
        String id = registry.store(value);
        JsonObject ref = new JsonObject();
        ref.addProperty("__type", "object_ref");
        ref.addProperty("__id", id);
        ref.addProperty("__class", value.getClass().getName());
        return ref;
    }

    private JsonObject serializeVec3(Vec3 v) {
        JsonObject o = new JsonObject();
        o.addProperty("x", v.x);
        o.addProperty("y", v.y);
        o.addProperty("z", v.z);
        return o;
    }

    private JsonObject serializeBlockPos(BlockPos bp) {
        JsonObject o = new JsonObject();
        o.addProperty("x", bp.getX());
        o.addProperty("y", bp.getY());
        o.addProperty("z", bp.getZ());
        return o;
    }

    private JsonObject serializeBlockState(BlockState bs) {
        JsonObject o = new JsonObject();
        o.addProperty("block", bs.getBlock().toString());
        return o;
    }

    private JsonObject serializeItemStack(ItemStack is) {
        JsonObject o = new JsonObject();
        o.addProperty("item", is.getItem().toString());
        o.addProperty("count", is.getCount());
        o.addProperty("empty", is.isEmpty());
        return o;
    }

    private JsonObject serializePlayer(LocalPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("name", p.getName().getString());
        o.addProperty("uuid", p.getStringUUID());
        o.addProperty("health", p.getHealth());
        o.addProperty("hunger", p.getFoodData().getFoodLevel());
        o.add("position", serializeVec3(p.position()));
        o.addProperty("yaw", p.getYRot());
        o.addProperty("pitch", p.getXRot());
        var mgr = Minecraft.getInstance().gameMode;
        if (mgr != null) {
            o.addProperty("gameMode", mgr.getPlayerMode().getSerializedName());
        }
        return o;
    }

    private JsonObject serializeEntity(Entity e) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", e.getStringUUID());
        o.addProperty("type", e.getType().toString());
        o.add("position", serializeVec3(e.position()));
        return o;
    }

    public Object deserializeArg(JsonElement el, String typeName) {
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("__id")) return registry.resolve(obj.get("__id").getAsString());
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
            default                              -> el.getAsString();
        };
    }
}
