import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerScreenshotTools(
  server: McpServer,
  conn: MinecraftConnection
) {
  server.tool(
    "take_screenshot",
    "Capture the current Minecraft client view and return it as a base64-encoded PNG image. Useful for visually inspecting in-game state.",
    {},
    async () => {
      const result = await conn.call<{ png: string }>("screenshot.take", {});
      return {
        content: [
          {
            type: "image",
            data: result.png,
            mimeType: "image/png",
          },
        ],
      };
    }
  );
}
