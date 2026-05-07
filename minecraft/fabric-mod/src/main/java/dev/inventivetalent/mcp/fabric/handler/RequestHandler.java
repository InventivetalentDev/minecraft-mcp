package dev.inventivetalent.mcp.fabric.handler;

import com.google.gson.*;
import dev.inventivetalent.mcp.fabric.ChatStorage;
import dev.inventivetalent.mcp.fabric.util.ObjectRegistry;
import dev.inventivetalent.mcp.fabric.util.PackageFilter;
import dev.inventivetalent.mcp.fabric.util.Serializer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

public class RequestHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final PackageFilter filter;
    private final ObjectRegistry registry;
    private final Serializer serializer;

    public RequestHandler(PackageFilter filter, int maxRefs) {
        this.filter     = filter;
        this.registry   = new ObjectRegistry(maxRefs);
        this.serializer = new Serializer(registry);
    }

    public JsonElement handle(String method, JsonObject params) throws Exception {
        return switch (method) {
            // ── Chat ─────────────────────────────────────────────────────────
            case "chat.get"  -> handleChatGet(params);
            case "chat.send" -> handleChatSend(params);

            // ── Player ───────────────────────────────────────────────────────
            case "player.getState"  -> handlePlayerGetState();
            case "player.moveTo"    -> handlePlayerMoveTo(params);
            case "player.lookAt"    -> handlePlayerLookAt(params);
            case "player.jump"      -> handlePlayerJump();
            case "player.breakBlock"-> handlePlayerBreakBlock(params);
            case "player.placeBlock"-> handlePlayerPlaceBlock(params);
            case "player.interact"  -> handlePlayerInteract(params);

            // ── World ────────────────────────────────────────────────────────
            case "world.getBlock"       -> handleWorldGetBlock(params);
            case "world.nearbyEntities" -> handleWorldNearbyEntities(params);

            // ── Screenshot ───────────────────────────────────────────────────
            case "screenshot.take" -> handleScreenshot();

            // ── Reflection ───────────────────────────────────────────────────
            case "reflect.invoke"      -> handleReflectInvoke(params);
            case "reflect.getField"    -> handleReflectGetField(params);
            case "reflect.setField"    -> handleReflectSetField(params);
            case "reflect.newInstance" -> handleReflectNewInstance(params);
            case "class.info"          -> handleClassInfo(params);

            // ── Variables ────────────────────────────────────────────────────
            case "vars.get"    -> handleVarsGet(params);
            case "vars.list"   -> handleVarsList();
            case "vars.delete" -> handleVarsDelete(params);
            case "vars.clear"  -> { registry.clear(); yield success("Cleared"); }

            default -> throw new IllegalArgumentException("Unknown method: " + method);
        };
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    private JsonElement handleChatGet(JsonObject p) {
        int lines = p.has("lines") ? p.get("lines").getAsInt() : 50;
        List<Map<String, Object>> msgs = ChatStorage.getRecent(lines);
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Map<String, Object> m : msgs) {
            JsonObject entry = new JsonObject();
            entry.addProperty("text", (String) m.get("text"));
            entry.addProperty("sender", (String) m.get("sender"));
            entry.addProperty("timestamp", (Long) m.get("timestamp"));
            arr.add(entry);
        }
        out.add("messages", arr);
        return out;
    }

    private JsonElement handleChatSend(JsonObject p) throws Exception {
        String message = p.get("message").getAsString();
        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) throw new IllegalStateException("Not in a world");
                if (message.startsWith("/")) {
                    player.networkHandler.sendChatCommand(message.substring(1));
                } else {
                    player.networkHandler.sendChatMessage(message);
                }
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.get(5, TimeUnit.SECONDS);
        return success("Message sent.");
    }

    // ── Player ───────────────────────────────────────────────────────────────

    private JsonElement handlePlayerGetState() throws Exception {
        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                ClientPlayerEntity p = MinecraftClient.getInstance().player;
                if (p == null) throw new IllegalStateException("Not in a world");

                JsonObject out = new JsonObject();
                out.addProperty("name", p.getName().getString());
                out.addProperty("uuid", p.getUuidAsString());
                out.addProperty("health", p.getHealth());
                out.addProperty("maxHealth", p.getMaxHealth());
                out.addProperty("hunger", p.getHungerManager().getFoodLevel());
                out.addProperty("x", p.getX());
                out.addProperty("y", p.getY());
                out.addProperty("z", p.getZ());
                out.addProperty("yaw", p.getYaw());
                out.addProperty("pitch", p.getPitch());
                out.addProperty("onGround", p.isOnGround());
                var mgr = MinecraftClient.getInstance().interactionManager;
                if (mgr != null) {
                    out.addProperty("gameMode", mgr.getCurrentGameMode().getName());
                }
                if (p.getWorld() != null) {
                    out.addProperty("dimension", p.getWorld().getRegistryKey().getValue().toString());
                }

                // Main hand item
                out.add("mainHandItem", serializer.serialize(p.getMainHandStack()));

                // Inventory slot count
                out.addProperty("inventorySlots", p.getInventory().size());

                future.complete(out);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }

    private JsonElement handlePlayerMoveTo(JsonObject p) throws Exception {
        double x = p.get("x").getAsDouble();
        double y = p.get("y").getAsDouble();
        double z = p.get("z").getAsDouble();
        Float yaw   = p.has("yaw")   ? p.get("yaw").getAsFloat()   : null;
        Float pitch = p.has("pitch") ? p.get("pitch").getAsFloat() : null;

        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) throw new IllegalStateException("Not in a world");
                player.setPosition(x, y, z);
                if (yaw   != null) player.setYaw(yaw);
                if (pitch != null) player.setPitch(pitch);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.get(5, TimeUnit.SECONDS);
        return success("Moved to (" + x + ", " + y + ", " + z + ")");
    }

    private JsonElement handlePlayerLookAt(JsonObject p) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) throw new IllegalStateException("Not in a world");

                if (p.has("targetX")) {
                    double tx = p.get("targetX").getAsDouble();
                    double ty = p.get("targetY").getAsDouble();
                    double tz = p.get("targetZ").getAsDouble();
                    Vec3d diff = new Vec3d(tx - player.getX(), ty - (player.getY() + player.getEyeHeight(player.getPose())), tz - player.getZ()).normalize();
                    double yaw   = Math.toDegrees(Math.atan2(-diff.x, diff.z));
                    double pitch = Math.toDegrees(-Math.asin(diff.y));
                    player.setYaw((float) yaw);
                    player.setPitch((float) pitch);
                } else {
                    if (p.has("yaw"))   player.setYaw(p.get("yaw").getAsFloat());
                    if (p.has("pitch")) player.setPitch(p.get("pitch").getAsFloat());
                }
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.get(5, TimeUnit.SECONDS);
        return success("View updated.");
    }

    private JsonElement handlePlayerJump() throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                if (player == null) throw new IllegalStateException("Not in a world");
                if (player.isOnGround()) player.jump();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        future.get(5, TimeUnit.SECONDS);
        return success("Jump triggered.");
    }

    private JsonElement handlePlayerBreakBlock(JsonObject p) throws Exception {
        int x = p.get("x").getAsInt();
        int y = p.get("y").getAsInt();
        int z = p.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);

        CompletableFuture<String> future = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                ClientPlayerEntity player = client.player;
                ClientPlayerInteractionManager mgr = client.interactionManager;
                if (player == null || mgr == null) throw new IllegalStateException("Not in a world");
                mgr.attackBlock(pos, Direction.UP);
                future.complete("Block break initiated at " + pos);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return success(future.get(5, TimeUnit.SECONDS));
    }

    private JsonElement handlePlayerPlaceBlock(JsonObject p) throws Exception {
        int x = p.get("x").getAsInt();
        int y = p.get("y").getAsInt();
        int z = p.get("z").getAsInt();
        String faceName = p.has("face") ? p.get("face").getAsString() : "up";
        Direction face = Direction.byName(faceName);
        if (face == null) face = Direction.UP;
        BlockPos pos = new BlockPos(x, y, z);
        Direction finalFace = face;

        CompletableFuture<String> future = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                ClientPlayerEntity player = client.player;
                ClientPlayerInteractionManager mgr = client.interactionManager;
                if (player == null || mgr == null) throw new IllegalStateException("Not in a world");

                Vec3d hitVec = Vec3d.ofCenter(pos).add(Vec3d.of(finalFace.getVector()).multiply(0.5));
                BlockHitResult hit = new BlockHitResult(hitVec, finalFace, pos, false);
                mgr.interactBlock(player, Hand.MAIN_HAND, hit);
                future.complete("Block place attempted at " + pos + " face=" + finalFace);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return success(future.get(5, TimeUnit.SECONDS));
    }

    private JsonElement handlePlayerInteract(JsonObject p) throws Exception {
        int x = p.get("x").getAsInt();
        int y = p.get("y").getAsInt();
        int z = p.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);

        CompletableFuture<String> future = new CompletableFuture<>();
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                ClientPlayerEntity player = client.player;
                ClientPlayerInteractionManager mgr = client.interactionManager;
                if (player == null || mgr == null) throw new IllegalStateException("Not in a world");

                Vec3d hitVec = Vec3d.ofCenter(pos);
                BlockHitResult hit = new BlockHitResult(hitVec, Direction.UP, pos, false);
                mgr.interactBlock(player, Hand.MAIN_HAND, hit);
                future.complete("Interacted with block at " + pos);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return success(future.get(5, TimeUnit.SECONDS));
    }

    // ── World ────────────────────────────────────────────────────────────────

    private JsonElement handleWorldGetBlock(JsonObject p) throws Exception {
        int x = p.get("x").getAsInt();
        int y = p.get("y").getAsInt();
        int z = p.get("z").getAsInt();
        BlockPos pos = new BlockPos(x, y, z);

        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                World world = MinecraftClient.getInstance().world;
                if (world == null) throw new IllegalStateException("Not in a world");
                BlockState state = world.getBlockState(pos);
                future.complete(serializer.serialize(state));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }

    private JsonElement handleWorldNearbyEntities(JsonObject p) throws Exception {
        double radius = p.has("radius") ? p.get("radius").getAsDouble() : 16.0;

        CompletableFuture<JsonElement> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                ClientPlayerEntity player = client.player;
                if (player == null || client.world == null) throw new IllegalStateException("Not in a world");

                Box box = player.getBoundingBox().expand(radius);
                List<Entity> entities = client.world.getOtherEntities(player, box);

                JsonArray arr = new JsonArray();
                for (Entity e : entities) arr.add(serializer.serialize(e));
                JsonObject out = new JsonObject();
                out.add("entities", arr);
                future.complete(out);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }

    // ── Screenshot ───────────────────────────────────────────────────────────

    private JsonElement handleScreenshot() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();

        // Screenshots must be taken on the render thread
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            try {
                var nativeImage = ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
                byte[] bytes = nativeImage.getBytes();
                nativeImage.close();
                String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
                future.complete(base64);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        String png = future.get(10, TimeUnit.SECONDS);
        JsonObject out = new JsonObject();
        out.addProperty("png", png);
        return out;
    }

    // ── Reflection ────────────────────────────────────────────────────────────

    private JsonElement handleReflectInvoke(JsonObject p) throws Exception {
        String className  = p.get("className").getAsString();
        String methodName = p.get("methodName").getAsString();
        JsonArray argsArr = p.has("args") ? p.getAsJsonArray("args") : new JsonArray();
        String target     = p.has("target")   ? p.get("target").getAsString()   : null;
        String assignTo   = p.has("assignTo") ? p.get("assignTo").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);

        Object[] argValues = new Object[argsArr.size()];
        Class<?>[] argTypes = new Class<?>[argsArr.size()];
        for (int i = 0; i < argsArr.size(); i++) {
            JsonObject argObj = argsArr.get(i).getAsJsonObject();
            String type = argObj.get("type").getAsString();
            JsonElement val = argObj.get("value");
            argValues[i] = serializer.deserializeArg(val, type);
            argTypes[i]  = resolveType(type, argValues[i]);
        }

        Object instance = target != null ? registry.resolve(target) : null;
        Method method = findMethod(cls, methodName, argTypes.length);
        method.setAccessible(true);

        CompletableFuture<Object> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                future.complete(method.invoke(instance, argValues));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        Object result = future.get(30, TimeUnit.SECONDS);
        if (assignTo != null) registry.assign(assignTo, result);
        return serializer.serialize(result);
    }

    private JsonElement handleReflectGetField(JsonObject p) throws Exception {
        String className = p.get("className").getAsString();
        String fieldName = p.get("fieldName").getAsString();
        String target    = p.has("target")   ? p.get("target").getAsString()   : null;
        String assignTo  = p.has("assignTo") ? p.get("assignTo").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);
        Field field = findField(cls, fieldName);
        field.setAccessible(true);

        Object instance = target != null ? registry.resolve(target) : null;

        CompletableFuture<Object> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                future.complete(field.get(instance));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        Object result = future.get(10, TimeUnit.SECONDS);
        if (assignTo != null) registry.assign(assignTo, result);
        return serializer.serialize(result);
    }

    private JsonElement handleReflectSetField(JsonObject p) throws Exception {
        String className  = p.get("className").getAsString();
        String fieldName  = p.get("fieldName").getAsString();
        JsonElement value = p.get("value");
        String valueType  = p.get("valueType").getAsString();
        String target     = p.has("target") ? p.get("target").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);
        Field field = findField(cls, fieldName);
        field.setAccessible(true);

        Object val = serializer.deserializeArg(value, valueType);
        Object instance = target != null ? registry.resolve(target) : null;

        CompletableFuture<Void> future = new CompletableFuture<>();
        MinecraftClient.getInstance().execute(() -> {
            try {
                field.set(instance, val);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        future.get(10, TimeUnit.SECONDS);
        return success("Field set.");
    }

    private JsonElement handleReflectNewInstance(JsonObject p) throws Exception {
        String className = p.get("className").getAsString();
        JsonArray argsArr = p.has("args") ? p.getAsJsonArray("args") : new JsonArray();
        String assignTo  = p.has("assignTo") ? p.get("assignTo").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);

        Object[] argValues = new Object[argsArr.size()];
        Class<?>[] argTypes = new Class<?>[argsArr.size()];
        for (int i = 0; i < argsArr.size(); i++) {
            JsonObject argObj = argsArr.get(i).getAsJsonObject();
            String type = argObj.get("type").getAsString();
            JsonElement val = argObj.get("value");
            argValues[i] = serializer.deserializeArg(val, type);
            argTypes[i]  = resolveType(type, argValues[i]);
        }

        Constructor<?> ctor = findConstructor(cls, argTypes.length);
        ctor.setAccessible(true);
        Object instance = ctor.newInstance(argValues);
        if (assignTo != null) registry.assign(assignTo, instance);
        return serializer.serialize(instance);
    }

    private JsonElement handleClassInfo(JsonObject p) throws Exception {
        String className = p.get("className").getAsString();
        boolean includeInherited = p.has("includeInherited") && p.get("includeInherited").getAsBoolean();

        assertAllowed(className);
        Class<?> cls = Class.forName(className);

        JsonObject out = new JsonObject();
        out.addProperty("name", cls.getName());
        out.addProperty("superclass", cls.getSuperclass() != null ? cls.getSuperclass().getName() : null);

        Method[] methods = includeInherited ? cls.getMethods() : cls.getDeclaredMethods();
        JsonArray ma = new JsonArray();
        for (Method m : methods) {
            JsonObject mo = new JsonObject();
            mo.addProperty("name", m.getName());
            mo.addProperty("modifiers", Modifier.toString(m.getModifiers()));
            mo.addProperty("returnType", m.getReturnType().getName());
            JsonArray pt = new JsonArray();
            for (Class<?> c : m.getParameterTypes()) pt.add(c.getName());
            mo.add("parameterTypes", pt);
            ma.add(mo);
        }
        out.add("methods", ma);

        Field[] fields = includeInherited ? cls.getFields() : cls.getDeclaredFields();
        JsonArray fa = new JsonArray();
        for (Field f : fields) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f.getName());
            fo.addProperty("type", f.getType().getName());
            fo.addProperty("modifiers", Modifier.toString(f.getModifiers()));
            fa.add(fo);
        }
        out.add("fields", fa);
        return out;
    }

    // ── Variables ────────────────────────────────────────────────────────────

    private JsonElement handleVarsGet(JsonObject p) {
        String name = p.get("name").getAsString();
        Object val = registry.resolve("$" + name);
        return val == null ? JsonNull.INSTANCE : serializer.serialize(val);
    }

    private JsonElement handleVarsList() {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, Object> e : registry.getVariables().entrySet()) {
            out.add(e.getKey(), serializer.serialize(e.getValue()));
        }
        return out;
    }

    private JsonElement handleVarsDelete(JsonObject p) {
        registry.delete(p.get("name").getAsString());
        return success("Deleted.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertAllowed(String className) {
        if (!filter.isAllowed(className)) {
            throw new SecurityException("Class not in allowlist: " + className);
        }
    }

    private Method findMethod(Class<?> cls, String name, int arity) throws NoSuchMethodException {
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == arity) return m;
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + " (arity " + arity + ")");
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(cls.getName() + "." + name);
    }

    private Constructor<?> findConstructor(Class<?> cls, int arity) throws NoSuchMethodException {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == arity) return c;
        }
        throw new NoSuchMethodException("No constructor with arity " + arity);
    }

    private Class<?> resolveType(String typeName, Object value) {
        return switch (typeName) {
            case "byte"    -> byte.class;
            case "short"   -> short.class;
            case "int"     -> int.class;
            case "long"    -> long.class;
            case "float"   -> float.class;
            case "double"  -> double.class;
            case "boolean" -> boolean.class;
            case "char"    -> char.class;
            default        -> value != null ? value.getClass() : Object.class;
        };
    }

    private JsonObject success(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("message", message);
        return o;
    }
}
