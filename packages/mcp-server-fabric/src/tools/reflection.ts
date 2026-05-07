// Identical surface as the Paper reflection tools, but wired to the Fabric connection.
// Allowed classes default to: net.minecraft.*, com.mojang.*, net.fabricmc.*
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

const ReflectArgSchema = z.object({
  type: z.string(),
  value: z.unknown(),
});

const TargetSchema = z.string().optional();
const AssignToSchema = z.string().optional();

export function registerReflectionTools(
  server: McpServer,
  conn: MinecraftConnection
) {
  server.tool(
    "client_reflect_invoke",
    "Invoke a method via reflection on the Minecraft client (runs on the game thread). " +
      "Allowed packages configured in mcp.json (defaults: net.minecraft.*, com.mojang.*, net.fabricmc.*).",
    {
      class_name: z.string(),
      method_name: z.string(),
      args: z.array(ReflectArgSchema).default([]),
      target: TargetSchema,
      assign_to: AssignToSchema,
    },
    async ({ class_name, method_name, args, target, assign_to }) => {
      const result = await conn.call("reflect.invoke", {
        className: class_name,
        methodName: method_name,
        args,
        target,
        assignTo: assign_to,
      });
      return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    }
  );

  server.tool(
    "client_reflect_get_field",
    "Read a field via reflection on the Minecraft client.",
    {
      class_name: z.string(),
      field_name: z.string(),
      target: TargetSchema,
      assign_to: AssignToSchema,
    },
    async ({ class_name, field_name, target, assign_to }) => {
      const result = await conn.call("reflect.getField", {
        className: class_name,
        fieldName: field_name,
        target,
        assignTo: assign_to,
      });
      return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    }
  );

  server.tool(
    "client_reflect_set_field",
    "Set a field via reflection on the Minecraft client.",
    {
      class_name: z.string(),
      field_name: z.string(),
      value: z.unknown(),
      value_type: z.string(),
      target: TargetSchema,
    },
    async ({ class_name, field_name, value, value_type, target }) => {
      const result = await conn.call("reflect.setField", {
        className: class_name,
        fieldName: field_name,
        value,
        valueType: value_type,
        target,
      });
      return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    }
  );

  server.tool(
    "client_get_class_info",
    "Return declared methods and fields of a Minecraft client class.",
    {
      class_name: z.string(),
      include_inherited: z.boolean().default(false),
    },
    async ({ class_name, include_inherited }) => {
      const result = await conn.call("class.info", {
        className: class_name,
        includeInherited: include_inherited,
      });
      return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
    }
  );

  server.tool("client_var_get", "Get a stored client-side variable.", { name: z.string() }, async ({ name }) => {
    const result = await conn.call("vars.get", { name });
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  });

  server.tool("client_var_list", "List stored client-side variables.", {}, async () => {
    const result = await conn.call("vars.list", {});
    return { content: [{ type: "text", text: JSON.stringify(result, null, 2) }] };
  });

  server.tool("client_var_delete", "Delete a stored client-side variable.", { name: z.string() }, async ({ name }) => {
    await conn.call("vars.delete", { name });
    return { content: [{ type: "text", text: `Variable '${name}' deleted.` }] };
  });

  server.tool("client_var_clear", "Clear all stored client-side variables.", {}, async () => {
    await conn.call("vars.clear", {});
    return { content: [{ type: "text", text: "All client variables cleared." }] };
  });
}
