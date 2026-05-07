import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerLogTools(server: McpServer, conn: MinecraftConnection) {
  server.tool(
    "get_server_logs",
    "Return recent lines from the Paper server log (logs/latest.log).",
    {
      lines: z
        .number()
        .int()
        .min(1)
        .max(10_000)
        .default(100)
        .describe("Number of tail lines to return"),
      filter: z
        .string()
        .optional()
        .describe("Case-insensitive substring filter applied to each line"),
    },
    async ({ lines, filter }) => {
      const result = await conn.call<{ lines: string[] }>("logs.get", {
        lines,
        filter,
      });
      return {
        content: [{ type: "text", text: result.lines.join("\n") }],
      };
    }
  );

  server.tool(
    "subscribe_server_logs",
    "Register to receive future log lines as server-sent events (pushed to the MCP client via notifications). The subscription persists until the MCP session ends or `unsubscribe_server_logs` is called.",
    {
      filter: z
        .string()
        .optional()
        .describe(
          "Case-insensitive substring filter; only matching lines are forwarded"
        ),
    },
    async ({ filter }) => {
      await conn.call("logs.subscribe", { filter });
      return {
        content: [
          {
            type: "text",
            text: "Subscribed to server log stream. Log lines will be emitted as resource-update notifications.",
          },
        ],
      };
    }
  );

  server.tool(
    "unsubscribe_server_logs",
    "Cancel a previously registered log subscription.",
    {},
    async () => {
      await conn.call("logs.unsubscribe", {});
      return { content: [{ type: "text", text: "Log subscription cancelled." }] };
    }
  );
}
