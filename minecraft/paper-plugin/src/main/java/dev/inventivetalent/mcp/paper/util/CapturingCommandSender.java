package dev.inventivetalent.mcp.paper.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A fake console sender that buffers all output sent to it.
 * Used to capture the output of console commands.
 */
public class CapturingCommandSender implements CommandSender {

    private final Server server;
    private final List<String> lines = new ArrayList<>();

    public CapturingCommandSender(Server server) {
        this.server = server;
    }

    public List<String> getLines() {
        return List.copyOf(lines);
    }

    @Override
    public void sendMessage(@NotNull String message) {
        lines.add(message);
    }

    @Override
    public void sendMessage(String @NotNull ... messages) {
        for (String m : messages) lines.add(m);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        lines.add(message);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, String @NotNull ... messages) {
        for (String m : messages) lines.add(m);
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        lines.add(PlainTextComponentSerializer.plainText().serialize(message));
    }

    @Override
    public @NotNull Server getServer() {
        return server;
    }

    @Override
    public @NotNull String getName() {
        return "MCPConsole";
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) { return true; }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) { return true; }

    @Override
    public boolean hasPermission(@NotNull String name) { return true; }

    @Override
    public boolean hasPermission(@NotNull Permission perm) { return true; }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin,
                                                        @NotNull String name, boolean value) {
        return new PermissionAttachment(plugin, this);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin) {
        return new PermissionAttachment(plugin, this);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin,
                                                         @NotNull String name, boolean value, int ticks) {
        return null;
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull org.bukkit.plugin.Plugin plugin, int ticks) {
        return null;
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {}

    @Override
    public void recalculatePermissions() {}

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }

    @Override
    public boolean isOp() { return true; }

    @Override
    public void setOp(boolean value) {}
}
