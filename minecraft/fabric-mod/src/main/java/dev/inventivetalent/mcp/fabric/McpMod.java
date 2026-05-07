package dev.inventivetalent.mcp.fabric;

import com.google.gson.*;
import dev.inventivetalent.mcp.fabric.util.PackageFilter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Environment(EnvType.CLIENT)
public class McpMod implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("minecraft-mcp");

    private McpWebSocketServer wsServer;

    @Override
    public void onInitializeClient() {
        JsonObject config = loadConfig();

        JsonObject wsConfig    = config.has("websocket")  ? config.getAsJsonObject("websocket")  : new JsonObject();
        JsonObject reflConfig  = config.has("reflection") ? config.getAsJsonObject("reflection") : new JsonObject();

        String host = wsConfig.has("host") ? wsConfig.get("host").getAsString() : "127.0.0.1";
        int    port = wsConfig.has("port") ? wsConfig.get("port").getAsInt()    : 25576;

        List<String> allowed = jsonArrayToList(reflConfig.has("allowedPackages")
                ? reflConfig.getAsJsonArray("allowedPackages") : defaultAllowedPackages());
        List<String> blocked = jsonArrayToList(reflConfig.has("blockedPackages")
                ? reflConfig.getAsJsonArray("blockedPackages") : new JsonArray());
        int maxRefs = reflConfig.has("maxObjectRefs") ? reflConfig.get("maxObjectRefs").getAsInt() : 1000;

        PackageFilter filter = new PackageFilter(allowed, blocked);

        wsServer = new McpWebSocketServer(new InetSocketAddress(host, port), filter, maxRefs);
        try {
            wsServer.start();
            LOGGER.info("[MCP] WebSocket server started on {}:{}", host, port);
        } catch (Exception e) {
            LOGGER.error("[MCP] Failed to start WebSocket server", e);
        }
    }

    private JsonObject loadConfig() {
        Path configFile = FabricLoader.getInstance().getConfigDir().resolve("mcp.json");
        if (Files.exists(configFile)) {
            try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                LOGGER.warn("[MCP] Failed to load config, using defaults: {}", e.getMessage());
            }
        } else {
            // Write defaults
            try (InputStream is = getClass().getResourceAsStream("/mcp.json")) {
                if (is != null) {
                    Files.createDirectories(configFile.getParent());
                    Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("[MCP] Created default config at {}", configFile);
                    try (Reader r = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
                        return JsonParser.parseReader(r).getAsJsonObject();
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[MCP] Could not write default config: {}", e.getMessage());
            }
        }
        return new JsonObject();
    }

    private JsonArray defaultAllowedPackages() {
        JsonArray arr = new JsonArray();
        arr.add("net.minecraft.*");
        arr.add("com.mojang.*");
        arr.add("net.fabricmc.*");
        arr.add("java.util.*");
        arr.add("java.lang.*");
        return arr;
    }

    private List<String> jsonArrayToList(JsonArray arr) {
        List<String> list = new ArrayList<>();
        for (JsonElement el : arr) list.add(el.getAsString());
        return list;
    }
}
