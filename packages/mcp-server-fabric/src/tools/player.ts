import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

export function registerPlayerTools(server: McpServer, conn: MinecraftConnection) {
  server.tool(
    "get_player_state",
    "Return the local player's current position, rotation, health, hunger, inventory, game mode, and dimension.",
    {},
    async () => {
      const result = await conn.call("player.getState", {});
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "player_move_to",
    "Teleport the local player to the given world coordinates.",
    {
      x: z.number().describe("Target X coordinate"),
      y: z.number().describe("Target Y coordinate"),
      z: z.number().describe("Target Z coordinate"),
      yaw: z
        .number()
        .optional()
        .describe("Horizontal rotation in degrees (0 = south). Omit to keep current."),
      pitch: z
        .number()
        .optional()
        .describe("Vertical rotation in degrees. Omit to keep current."),
    },
    async ({ x, y, z: zCoord, yaw, pitch }) => {
      await conn.call("player.moveTo", { x, y, z: zCoord, yaw, pitch });
      return { content: [{ type: "text", text: `Moved to (${x}, ${y}, ${zCoord}).` }] };
    }
  );

  server.tool(
    "player_look_at",
    "Rotate the player's view toward an absolute direction or a target point in the world.",
    {
      yaw: z.number().optional().describe("Absolute yaw in degrees"),
      pitch: z.number().optional().describe("Absolute pitch in degrees"),
      target_x: z.number().optional().describe("Look toward this world X (overrides yaw/pitch)"),
      target_y: z.number().optional(),
      target_z: z.number().optional(),
    },
    async ({ yaw, pitch, target_x, target_y, target_z }) => {
      await conn.call("player.lookAt", { yaw, pitch, targetX: target_x, targetY: target_y, targetZ: target_z });
      return { content: [{ type: "text", text: "View updated." }] };
    }
  );

  server.tool(
    "player_jump",
    "Make the local player jump (applies jump velocity on the next tick).",
    {},
    async () => {
      await conn.call("player.jump", {});
      return { content: [{ type: "text", text: "Jump triggered." }] };
    }
  );

  server.tool(
    "player_break_block",
    "Instantly break the block at the given coordinates (creative-like; respects the server's permissions).",
    {
      x: z.number().int(),
      y: z.number().int(),
      z: z.number().int(),
    },
    async ({ x, y, z: zCoord }) => {
      const result = await conn.call<{ message: string }>("player.breakBlock", {
        x,
        y,
        z: zCoord,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );

  server.tool(
    "player_place_block",
    "Place the currently held item against the given block face.",
    {
      x: z.number().int().describe("Target block X"),
      y: z.number().int().describe("Target block Y"),
      z: z.number().int().describe("Target block Z"),
      face: z
        .enum(["up", "down", "north", "south", "east", "west"])
        .default("up")
        .describe("Block face to place against"),
    },
    async ({ x, y, z: zCoord, face }) => {
      const result = await conn.call<{ message: string }>("player.placeBlock", {
        x,
        y,
        z: zCoord,
        face,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );

  server.tool(
    "player_interact",
    "Right-click on the block or entity at the given coordinates (e.g. open chest, activate lever).",
    {
      x: z.number().int(),
      y: z.number().int(),
      z: z.number().int(),
    },
    async ({ x, y, z: zCoord }) => {
      const result = await conn.call<{ message: string }>("player.interact", {
        x,
        y,
        z: zCoord,
      });
      return { content: [{ type: "text", text: result.message }] };
    }
  );

  server.tool(
    "get_nearby_entities",
    "Return entities near the local player.",
    {
      radius: z
        .number()
        .min(0.5)
        .max(64)
        .default(16)
        .describe("Search radius in blocks"),
    },
    async ({ radius }) => {
      const result = await conn.call("world.nearbyEntities", { radius });
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "get_block_at",
    "Return the block type and block-state properties at the given world coordinates.",
    {
      x: z.number().int(),
      y: z.number().int(),
      z: z.number().int(),
    },
    async ({ x, y, z: zCoord }) => {
      const result = await conn.call("world.getBlock", { x, y, z: zCoord });
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );
}
