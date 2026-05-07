#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { MinecraftConnection } from "@minecraft-mcp/shared";
import { registerChatTools } from "./tools/chat.js";
import { registerPlayerTools } from "./tools/player.js";
import { registerScreenshotTools } from "./tools/screenshot.js";
import { registerReflectionTools } from "./tools/reflection.js";

const WS_URL = process.env.FABRIC_WS_URL ?? "ws://localhost:25576";

const conn = new MinecraftConnection(WS_URL);

conn.on("connected", () => {
  process.stderr.write(`[mcp-server-fabric] Connected to ${WS_URL}\n`);
});
conn.on("disconnected", () => {
  process.stderr.write(`[mcp-server-fabric] Disconnected; reconnecting...\n`);
});

const server = new McpServer({
  name: "minecraft-fabric-mcp",
  version: "0.1.0",
});

registerChatTools(server, conn);
registerPlayerTools(server, conn);
registerScreenshotTools(server, conn);
registerReflectionTools(server, conn);

const transport = new StdioServerTransport();
await server.connect(transport);
