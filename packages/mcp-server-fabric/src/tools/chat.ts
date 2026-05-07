import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerChatTools(server: McpServer, conn: MinecraftConnection) {
  server.tool(
    "get_chat_log",
    "Return recent messages from the client's chat HUD history.",
    {
      lines: z
        .number()
        .int()
        .min(1)
        .max(500)
        .default(50)
        .describe("Maximum number of recent messages to return"),
    },
    async ({ lines }) => {
      const result = await conn.call<{ messages: unknown[] }>("chat.get", { lines });
      return {
        content: [{ type: "text", text: JSON.stringify(result.messages, null, 2) }],
      };
    }
  );

  server.tool(
    "send_chat_message",
    "Send a chat message or slash command as the local player.",
    {
      message: z
        .string()
        .describe(
          "Text to send. Use a leading / for commands, e.g. \"/gamemode creative\""
        ),
    },
    async ({ message }) => {
      await conn.call("chat.send", { message });
      return { content: [{ type: "text", text: "Message sent." }] };
    }
  );
}
