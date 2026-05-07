import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import type { MinecraftConnection } from "@minecraft-mcp/shared";

const ReflectArgSchema = z.object({
  type: z
    .string()
    .describe(
      'Java type name, e.g. "java.lang.String", "int", "org.bukkit.World". ' +
        'Use "ref" to pass an object handle or variable directly.'
    ),
  value: z
    .unknown()
    .describe(
      'The argument value. For object refs use the __id string or "$varName" syntax.'
    ),
});

const TargetSchema = z
  .string()
  .optional()
  .describe(
    'Object handle ID or "$varName" to use as the method call target (omit for static calls).'
  );

const AssignToSchema = z
  .string()
  .optional()
  .describe(
    'If provided, store the result under this variable name (accessible via $name in future calls).'
  );

export function registerReflectionTools(
  server: McpServer,
  conn: MinecraftConnection
) {
  server.tool(
    "reflect_invoke",
    "Invoke a method on a class or object instance via reflection. " +
      "Allowed packages are configured server-side (default: org.bukkit.*, io.papermc.*, net.kyori.*).",
    {
      class_name: z
        .string()
        .describe('Fully-qualified class name, e.g. "org.bukkit.Bukkit"'),
      method_name: z.string().describe("Method name to invoke"),
      args: z
        .array(ReflectArgSchema)
        .default([])
        .describe("Method arguments in order"),
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
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "reflect_get_field",
    "Read a field value from a class or object instance via reflection.",
    {
      class_name: z.string().describe("Fully-qualified class name"),
      field_name: z.string().describe("Field name"),
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
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "reflect_set_field",
    "Set a field value on a class or object instance via reflection.",
    {
      class_name: z.string().describe("Fully-qualified class name"),
      field_name: z.string().describe("Field name"),
      value: z.unknown().describe("New value (primitives and strings only; use object refs for complex types)"),
      value_type: z.string().describe('Java type of the value, e.g. "int", "java.lang.String"'),
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
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "reflect_new_instance",
    "Construct a new instance of a class via reflection.",
    {
      class_name: z.string().describe("Fully-qualified class name"),
      args: z.array(ReflectArgSchema).default([]).describe("Constructor arguments"),
      assign_to: AssignToSchema,
    },
    async ({ class_name, args, assign_to }) => {
      const result = await conn.call("reflect.newInstance", {
        className: class_name,
        args,
        assignTo: assign_to,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "get_class_info",
    "Return the declared methods and fields of a class (names, parameter types, return types, modifiers).",
    {
      class_name: z.string().describe("Fully-qualified class name"),
      include_inherited: z
        .boolean()
        .default(false)
        .describe("Include methods/fields inherited from superclasses"),
    },
    async ({ class_name, include_inherited }) => {
      const result = await conn.call("class.info", {
        className: class_name,
        includeInherited: include_inherited,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  // Variable management
  server.tool(
    "var_get",
    "Retrieve a named variable stored by a previous reflect_* call.",
    {
      name: z.string().describe('Variable name without leading $, e.g. "world0"'),
    },
    async ({ name }) => {
      const result = await conn.call("vars.get", { name });
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "var_list",
    "List all currently stored variable names and their serialised values (object refs show class name only).",
    {},
    async () => {
      const result = await conn.call("vars.list", {});
      return {
        content: [{ type: "text", text: JSON.stringify(result, null, 2) }],
      };
    }
  );

  server.tool(
    "var_delete",
    "Delete a stored variable and release the associated object reference.",
    {
      name: z.string(),
    },
    async ({ name }) => {
      await conn.call("vars.delete", { name });
      return { content: [{ type: "text", text: `Variable '${name}' deleted.` }] };
    }
  );

  server.tool(
    "var_clear",
    "Delete all stored variables and release all object references for this session.",
    {},
    async () => {
      await conn.call("vars.clear", {});
      return { content: [{ type: "text", text: "All variables cleared." }] };
    }
  );
}
