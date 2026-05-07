#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { MinecraftConnection } from "@minecraft-mcp/shared";
import { registerLogTools } from "./tools/logs.js";
import { registerCommandTools } from "./tools/commands.js";
import { registerPluginTools } from "./tools/plugins.js";
import { registerReflectionTools } from "./tools/reflection.js";
import { registerJavadocTools } from "./tools/javadocs.js";

const WS_URL = process.env.PAPER_WS_URL ?? "ws://localhost:25575";

const conn = new MinecraftConnection(WS_URL);

conn.on("connected", () => {
  process.stderr.write(`[mcp-server-paper] Connected to ${WS_URL}\n`);
});
conn.on("disconnected", () => {
  process.stderr.write(`[mcp-server-paper] Disconnected; reconnecting...\n`);
});

// Forward log events as MCP notifications (best-effort)
conn.on("mc:log", (data: unknown) => {
  process.stderr.write(`[MC LOG] ${JSON.stringify(data)}\n`);
});

const server = new McpServer({
  name: "minecraft-paper-mcp",
  version: "0.1.0",
});

registerLogTools(server, conn);
registerCommandTools(server, conn);
registerPluginTools(server, conn);
registerReflectionTools(server, conn);
registerJavadocTools(server);

const transport = new StdioServerTransport();
await server.connect(transport);
