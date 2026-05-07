package dev.inventivetalent.mcp.paper;

import dev.inventivetalent.mcp.paper.util.PackageFilter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;

public class McpPlugin extends JavaPlugin {

    private McpWebSocketServer wsServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        int port = getConfig().getInt("websocket.port", 25575);
        String host = getConfig().getString("websocket.host", "0.0.0.0");

        List<String> allowed = getConfig().getStringList("reflection.allowed-packages");
        List<String> blocked = getConfig().getStringList("reflection.blocked-packages");
        int maxRefs = getConfig().getInt("reflection.max-object-refs", 1000);

        PackageFilter filter = new PackageFilter(allowed, blocked);

        wsServer = new McpWebSocketServer(
                new InetSocketAddress(host, port),
                this,
                filter,
                maxRefs
        );

        try {
            wsServer.start();
            getLogger().info("MCP WebSocket server started on " + host + ":" + port);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to start MCP WebSocket server", e);
        }
    }

    @Override
    public void onDisable() {
        if (wsServer != null) {
            try {
                wsServer.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args) {
        if (!command.getName().equalsIgnoreCase("mcp")) return false;

        if (args.length == 0) {
            sender.sendMessage("[MCP] Usage: /mcp <status|reload>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> {
                int connections = wsServer != null ? wsServer.getConnections().size() : 0;
                sender.sendMessage("[MCP] WebSocket server running. Active connections: " + connections);
            }
            case "reload" -> {
                reloadConfig();
                sender.sendMessage("[MCP] Config reloaded. Restart the server to apply WebSocket changes.");
            }
            default -> sender.sendMessage("[MCP] Unknown subcommand.");
        }
        return true;
    }
}
