/** Sent by the MCP server → Minecraft WebSocket server */
export interface RpcRequest {
  id: string;
  method: string;
  params?: Record<string, unknown>;
}

/** Sent by Minecraft → MCP server in response to a request */
export interface RpcResponse {
  id: string;
  result?: unknown;
  error?: RpcError;
}

export interface RpcError {
  code: number;
  message: string;
}

/** Unsolicited push event from Minecraft → MCP server */
export interface RpcEvent {
  event: string;
  data?: unknown;
}

export type IncomingMessage = RpcResponse | RpcEvent;

// ── Shared result shapes ────────────────────────────────────────────────────

/** An object that could not be fully serialised on the Minecraft side.
 *  The opaque `id` can be passed back as a `target` or argument in
 *  subsequent reflect.* calls. */
export interface ObjectRef {
  __type: "object_ref";
  __id: string;
  __class: string;
}

export function isObjectRef(v: unknown): v is ObjectRef {
  return (
    typeof v === "object" &&
    v !== null &&
    (v as ObjectRef).__type === "object_ref"
  );
}

// ── Reflection argument descriptor ─────────────────────────────────────────

export interface ReflectArg {
  /** Java type name, e.g. "java.lang.String", "int", "org.bukkit.World" */
  type: string;
  /** The value. Use an ObjectRef or a "$varName" string to pass a stored handle. */
  value: unknown;
}
