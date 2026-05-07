import { EventEmitter } from "events";
import { randomUUID } from "crypto";
import WebSocket from "ws";
import type { RpcRequest, RpcResponse, RpcEvent, IncomingMessage } from "./protocol.js";

interface Pending {
  resolve: (v: unknown) => void;
  reject: (e: Error) => void;
  timer: ReturnType<typeof setTimeout>;
}

/**
 * Manages a WebSocket connection to one Minecraft-side counterpart
 * (Paper plugin or Fabric mod). Handles request/response correlation,
 * reconnection, and event forwarding.
 */
export class MinecraftConnection extends EventEmitter {
  private ws: WebSocket | null = null;
  private pending = new Map<string, Pending>();
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private _connected = false;

  constructor(
    private readonly url: string,
    private readonly timeoutMs = 30_000
  ) {
    super();
    this.connect();
  }

  get connected() {
    return this._connected;
  }

  private connect() {
    const ws = new WebSocket(this.url);
    this.ws = ws;

    ws.on("open", () => {
      this._connected = true;
      this.emit("connected");
    });

    ws.on("message", (raw: Buffer) => {
      let msg: IncomingMessage;
      try {
        msg = JSON.parse(raw.toString()) as IncomingMessage;
      } catch {
        return;
      }

      if ("id" in msg) {
        // Response to a pending call
        const p = this.pending.get(msg.id);
        if (p) {
          this.pending.delete(msg.id);
          clearTimeout(p.timer);
          if (msg.error) p.reject(new Error(msg.error.message));
          else p.resolve(msg.result);
        }
      } else if ("event" in msg) {
        this.emit("mc:event", msg as RpcEvent);
        this.emit(`mc:${msg.event}`, (msg as RpcEvent).data);
      }
    });

    ws.on("close", () => {
      this._connected = false;
      // Reject all pending calls
      for (const [id, p] of this.pending) {
        clearTimeout(p.timer);
        p.reject(new Error("WebSocket disconnected"));
        this.pending.delete(id);
      }
      this.emit("disconnected");
      this.scheduleReconnect();
    });

    ws.on("error", () => {
      // Swallow — close will fire next
    });
  }

  private scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect();
    }, 2_000);
  }

  /** Send a request and wait for the response. */
  call<T = unknown>(method: string, params?: Record<string, unknown>): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      if (!this._connected || !this.ws) {
        reject(new Error("Not connected to Minecraft"));
        return;
      }
      const id = randomUUID();
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Request timed out: ${method}`));
      }, this.timeoutMs);

      this.pending.set(id, {
        resolve: (v) => resolve(v as T),
        reject,
        timer,
      });

      const req: RpcRequest = { id, method, params };
      this.ws.send(JSON.stringify(req));
    });
  }

  close() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.ws?.close();
  }
}
