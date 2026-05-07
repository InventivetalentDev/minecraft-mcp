package dev.inventivetalent.mcp.paper.handler;

import com.google.gson.*;
import dev.inventivetalent.mcp.paper.McpPlugin;
import dev.inventivetalent.mcp.paper.util.CapturingCommandSender;
import dev.inventivetalent.mcp.paper.util.ObjectRegistry;
import dev.inventivetalent.mcp.paper.util.PackageFilter;
import dev.inventivetalent.mcp.paper.util.Serializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handles all incoming RPC methods for one WebSocket client session.
 * Each connected MCP server client gets its own RequestHandler with isolated
 * variable/object storage.
 */
public class RequestHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private final McpPlugin plugin;
    private final PackageFilter filter;
    private final ObjectRegistry registry;
    private final Serializer serializer;

    // Log subscription state for this client
    private volatile boolean logSubscribed = false;
    private volatile String logFilter = null;

    public RequestHandler(McpPlugin plugin, PackageFilter filter, int maxRefs) {
        this.plugin = plugin;
        this.filter = filter;
        this.registry = new ObjectRegistry(maxRefs);
        this.serializer = new Serializer(registry);
    }

    public boolean isLogSubscribed() { return logSubscribed; }
    public String getLogFilter() { return logFilter; }

    /**
     * Dispatch an RPC request and return the result as a JsonElement.
     * Throws on error (caller wraps in an error response).
     */
    public JsonElement handle(String method, JsonObject params) throws Exception {
        return switch (method) {
            // ── Logs ─────────────────────────────────────────────────────────
            case "logs.get"         -> handleLogsGet(params);
            case "logs.subscribe"   -> handleLogsSubscribe(params);
            case "logs.unsubscribe" -> { logSubscribed = false; yield success("Unsubscribed"); }

            // ── Commands ──────────────────────────────────────────────────────
            case "command.run"   -> handleCommandRun(params);
            case "players.list"  -> handlePlayersList();

            // ── Plugins ───────────────────────────────────────────────────────
            case "plugins.list"    -> handlePluginsList();
            case "plugins.install" -> handlePluginsInstall(params);
            case "plugins.reload"  -> handlePluginsReload(params);
            case "server.restart"  -> handleServerRestart(params);

            // ── Reflection ────────────────────────────────────────────────────
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

    // ── Logs ─────────────────────────────────────────────────────────────────

    private JsonElement handleLogsGet(JsonObject p) throws Exception {
        int lines = p.has("lines") ? p.get("lines").getAsInt() : 100;
        String filter = p.has("filter") ? p.get("filter").getAsString() : null;

        File logFile = new File(plugin.getServer().getWorldContainer().getParentFile(), "logs/latest.log");
        if (!logFile.exists()) {
            logFile = new File("logs/latest.log");
        }

        List<String> allLines = logFile.exists()
                ? Files.readAllLines(logFile.toPath())
                : Collections.emptyList();

        List<String> result = new ArrayList<>();
        int start = Math.max(0, allLines.size() - lines);
        for (int i = start; i < allLines.size(); i++) {
            String line = allLines.get(i);
            if (filter == null || line.toLowerCase().contains(filter.toLowerCase())) {
                result.add(line);
            }
        }

        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        result.forEach(arr::add);
        out.add("lines", arr);
        return out;
    }

    private JsonElement handleLogsSubscribe(JsonObject p) {
        logFilter = p.has("filter") ? p.get("filter").getAsString() : null;
        logSubscribed = true;
        return success("Subscribed to log events");
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    private JsonElement handleCommandRun(JsonObject p) throws Exception {
        String command = p.get("command").getAsString();
        int timeoutMs = p.has("timeoutMs") ? p.get("timeoutMs").getAsInt() : 5000;

        CapturingCommandSender sender = new CapturingCommandSender(plugin.getServer());

        // Commands must run on the main thread
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                plugin.getServer().dispatchCommand(sender, command);
            } finally {
                future.complete(null);
            }
        });

        future.get(timeoutMs, TimeUnit.MILLISECONDS);

        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        sender.getLines().forEach(arr::add);
        out.add("output", arr);
        return out;
    }

    private JsonElement handlePlayersList() {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            arr.add(serializer.serialize(p));
        }
        out.add("players", arr);
        return out;
    }

    // ── Plugins ───────────────────────────────────────────────────────────────

    private JsonElement handlePluginsList() {
        JsonObject out = new JsonObject();
        JsonArray arr = new JsonArray();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", p.getName());
            obj.addProperty("version", p.getDescription().getVersion());
            obj.addProperty("enabled", p.isEnabled());
            obj.addProperty("main", p.getDescription().getMain());
            obj.addProperty("authors", String.join(", ", p.getDescription().getAuthors()));
            arr.add(obj);
        }
        out.add("plugins", arr);
        return out;
    }

    private JsonElement handlePluginsInstall(JsonObject p) throws Exception {
        String jarPath = p.get("jarPath").getAsString();
        boolean reload = p.has("reload") && p.get("reload").getAsBoolean();

        File src = new File(jarPath);
        if (!src.exists()) throw new IllegalArgumentException("File not found: " + jarPath);
        if (!src.getName().endsWith(".jar")) throw new IllegalArgumentException("Not a jar file");

        File dest = new File(plugin.getServer().getWorldContainer().getParentFile(),
                "plugins/" + src.getName());
        Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

        if (reload) {
            CompletableFuture<String> future = new CompletableFuture<>();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    Plugin loaded = plugin.getServer().getPluginManager().loadPlugin(dest);
                    if (loaded != null) {
                        plugin.getServer().getPluginManager().enablePlugin(loaded);
                        future.complete("Installed and enabled: " + loaded.getName());
                    } else {
                        future.complete("Copied to plugins/, but hot-load returned null — restart may be required.");
                    }
                } catch (Exception e) {
                    future.complete("Copied to plugins/, hot-load failed: " + e.getMessage());
                }
            });
            return success(future.get(10, TimeUnit.SECONDS));
        }

        return success("Copied to plugins/. Restart the server to load.");
    }

    private JsonElement handlePluginsReload(JsonObject p) throws Exception {
        String name = p.get("name").getAsString();
        CompletableFuture<String> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Plugin target = Bukkit.getPluginManager().getPlugin(name);
            if (target == null) {
                future.complete("Plugin not found: " + name);
                return;
            }
            Bukkit.getPluginManager().disablePlugin(target);
            Bukkit.getPluginManager().enablePlugin(target);
            future.complete("Plugin reloaded: " + name);
        });
        return success(future.get(10, TimeUnit.SECONDS));
    }

    @SuppressWarnings("removal")
    private JsonElement handleServerRestart(JsonObject p) {
        boolean stopOnly = p.has("stopOnly") && p.get("stopOnly").getAsBoolean();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (stopOnly) {
                Bukkit.shutdown();
            } else {
                // Requires restart-script in paper.yml; otherwise behaves as shutdown.
                Bukkit.spigot().restart();
            }
        }, 20L);
        return success(stopOnly ? "Server stopping in ~1 second." : "Server restarting in ~1 second.");
    }

    // ── Reflection ────────────────────────────────────────────────────────────

    private JsonElement handleReflectInvoke(JsonObject p) throws Exception {
        String className  = p.get("className").getAsString();
        String methodName = p.get("methodName").getAsString();
        JsonArray argsArr = p.has("args") ? p.getAsJsonArray("args") : new JsonArray();
        String target     = p.has("target") ? p.get("target").getAsString() : null;
        String assignTo   = p.has("assignTo") ? p.get("assignTo").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);

        // Build arg arrays
        Object[] argValues = new Object[argsArr.size()];
        Class<?>[] argTypes = new Class<?>[argsArr.size()];
        for (int i = 0; i < argsArr.size(); i++) {
            JsonObject argObj = argsArr.get(i).getAsJsonObject();
            String type  = argObj.get("type").getAsString();
            JsonElement val = argObj.get("value");
            argValues[i] = serializer.deserializeArg(val, type);
            argTypes[i]  = resolveType(type, argValues[i]);
        }

        Object instance = target != null ? registry.resolve(target) : null;

        // Find method by name + compatible arg count (best-effort, no overload resolution)
        Method method = findMethod(cls, methodName, argTypes);
        method.setAccessible(true);

        // Must run on the main server thread for Bukkit API calls
        CompletableFuture<Object> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                future.complete(method.invoke(instance, argValues));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });

        Object result = future.get(30, TimeUnit.SECONDS);
        JsonElement serialised = serializer.serialize(result);

        if (assignTo != null) {
            // Store either the raw object or the serialised value
            registry.assign(assignTo, result);
        }

        return serialised;
    }

    private JsonElement handleReflectGetField(JsonObject p) throws Exception {
        String className = p.get("className").getAsString();
        String fieldName = p.get("fieldName").getAsString();
        String target    = p.has("target") ? p.get("target").getAsString() : null;
        String assignTo  = p.has("assignTo") ? p.get("assignTo").getAsString() : null;

        assertAllowed(className);
        Class<?> cls = Class.forName(className);
        Field field = findField(cls, fieldName);
        field.setAccessible(true);

        Object instance = target != null ? registry.resolve(target) : null;

        CompletableFuture<Object> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
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

        Constructor<?> ctor = findConstructor(cls, argTypes);
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

        // Methods
        Method[] methods = includeInherited ? cls.getMethods() : cls.getDeclaredMethods();
        JsonArray methodsArr = new JsonArray();
        for (Method m : methods) {
            JsonObject mo = new JsonObject();
            mo.addProperty("name", m.getName());
            mo.addProperty("modifiers", Modifier.toString(m.getModifiers()));
            mo.addProperty("returnType", m.getReturnType().getName());
            JsonArray paramArr = new JsonArray();
            for (Class<?> pt : m.getParameterTypes()) paramArr.add(pt.getName());
            mo.add("parameterTypes", paramArr);
            methodsArr.add(mo);
        }
        out.add("methods", methodsArr);

        // Fields
        Field[] fields = includeInherited ? cls.getFields() : cls.getDeclaredFields();
        JsonArray fieldsArr = new JsonArray();
        for (Field f : fields) {
            JsonObject fo = new JsonObject();
            fo.addProperty("name", f.getName());
            fo.addProperty("type", f.getType().getName());
            fo.addProperty("modifiers", Modifier.toString(f.getModifiers()));
            fieldsArr.add(fo);
        }
        out.add("fields", fieldsArr);

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
        String name = p.get("name").getAsString();
        registry.delete(name);
        return success("Deleted variable: " + name);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertAllowed(String className) {
        if (!filter.isAllowed(className)) {
            throw new SecurityException("Class not in allowlist: " + className);
        }
    }

    private Method findMethod(Class<?> cls, String name, Class<?>[] argTypes) throws NoSuchMethodException {
        // Try exact match first
        for (Method m : cls.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argTypes.length) {
                return m;
            }
        }
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == argTypes.length) {
                return m;
            }
        }
        throw new NoSuchMethodException(cls.getName() + "." + name + " (arity " + argTypes.length + ")");
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        Class<?> c = cls;
        while (c != null) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
            c = c.getSuperclass();
        }
        throw new NoSuchFieldException(cls.getName() + "." + name);
    }

    private Constructor<?> findConstructor(Class<?> cls, Class<?>[] argTypes) throws NoSuchMethodException {
        for (Constructor<?> c : cls.getDeclaredConstructors()) {
            if (c.getParameterCount() == argTypes.length) return c;
        }
        throw new NoSuchMethodException("No constructor with arity " + argTypes.length);
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

    private JsonElement success(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("message", message);
        return o;
    }
}
