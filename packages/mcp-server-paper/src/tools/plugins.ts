import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerPluginTools(
  server: McpServer,
  conn: MinecraftConnection
) {
  server.tool(
    "list_plugins",
    "Return all loaded plugins with their name, version, enabled status, and main class.",
    {},
    async () => {
      const result = await conn.call<{ plugins: unknown[] }>("plugins.list", {});
      return {
        content: [
          { type: "text", text: JSON.stringify(result.plugins, null, 2) },
        ],
      };
    }
  );

  server.tool(
    "install_plugin",
    "Copy a plugin jar into the server's plugins/ directory. The jar must already be accessible on the server's filesystem. Optionally enable without a full restart by using the PluginManager (where safe).",
    {
      jar_path: z
        .string()
        .describe(
          "Absolute path on the server filesystem to the jar to install"
        ),
      reload: z
        .boolean()
        .default(false)
        .describe(
          "If true, attempt a hot-load via PluginManager after copying. A full restart is safer for plugins with static state."
        ),
    },
    async ({ jar_path, reload }) => {
      const result = await conn.call<{ message: string }>("plugins.install", {
        jarPath: jar_path,
        reload,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );

  server.tool(
    "reload_plugin",
    "Reload a specific plugin (disable then enable). This is a best-effort operation; plugins that hold static resources may not reload cleanly — prefer a full server restart in those cases.",
    {
      name: z
        .string()
        .describe("Exact plugin name as shown in /plugins"),
    },
    async ({ name }) => {
      const result = await conn.call<{ message: string }>("plugins.reload", {
        name,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );

  server.tool(
    "restart_server",
    "Restart the Paper server. Requires Paper's restart script to be configured (restart-script in paper.yml), or falls back to `stop`.",
    {
      stop_only: z
        .boolean()
        .default(false)
        .describe(
          "If true, only stop the server without restarting (useful when a process manager handles restarts)"
        ),
    },
    async ({ stop_only }) => {
      const result = await conn.call<{ message: string }>("server.restart", {
        stopOnly: stop_only,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );
}
