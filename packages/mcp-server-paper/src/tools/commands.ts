import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerCommandTools(
  server: McpServer,
  conn: MinecraftConnection
) {
  server.tool(
    "run_console_command",
    "Execute a command on the Paper server console and return captured output. The command is run by a synthetic console sender so output is intercepted rather than going only to logs.",
    {
      command: z
        .string()
        .describe("Command to run (without leading slash), e.g. \"say Hello\""),
      timeout_ms: z
        .number()
        .int()
        .min(100)
        .max(30_000)
        .default(5_000)
        .describe("Max milliseconds to wait for output to settle"),
    },
    async ({ command, timeout_ms }) => {
      const result = await conn.call<{ output: string[] }>("command.run", {
        command,
        timeoutMs: timeout_ms,
      });
      return {
        content: [
          {
            type: "text",
            text:
              result.output.length > 0
                ? result.output.join("\n")
                : "(no output)",
          },
        ],
      };
    }
  );

  server.tool(
    "list_online_players",
    "Return a list of players currently online on the server.",
    {},
    async () => {
      const result = await conn.call<{ players: unknown[] }>(
        "players.list",
        {}
      );
      return {
        content: [
          { type: "text", text: JSON.stringify(result.players, null, 2) },
        ],
      };
    }
  );
}
