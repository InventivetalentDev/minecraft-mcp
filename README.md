# minecraft-mcp

MCP (Model Context Protocol) servers for AI-assisted Minecraft development. Lets tools like Claude Code interact directly with a running Minecraft server and client — viewing logs, running commands, installing plugins, taking screenshots, controlling the player, and calling arbitrary Bukkit/Paper/Minecraft API methods via reflection.

---

## Repository layout

```
minecraft-mcp/
├── packages/
│   ├── shared/              TypeScript protocol types + WebSocket connection class
│   ├── mcp-server-paper/    MCP server (stdio) ↔ Paper/Spigot plugin
│   └── mcp-server-fabric/   MCP server (stdio) ↔ Fabric client mod
└── minecraft/
    ├── paper-plugin/        Paper/Spigot plugin (Java 21, Gradle)
    └── fabric-mod/          Fabric client mod (Java 21, Gradle, fabric-loom)
```

---

## Architecture

```
┌──────────────────────┐        stdio         ┌────────────────────────────┐
│  Claude Code / LLM   │ ◄──────────────────► │  mcp-server-paper (Node)   │
└──────────────────────┘                       └──────────┬─────────────────┘
                                                          │  WebSocket
                                               ┌──────────▼─────────────────┐
                                               │  Paper plugin (Java)        │
                                               │  ws://localhost:25575       │
                                               └────────────────────────────┘

┌──────────────────────┐        stdio         ┌────────────────────────────┐
│  Claude Code / LLM   │ ◄──────────────────► │  mcp-server-fabric (Node)  │
└──────────────────────┘                       └──────────┬─────────────────┘
                                                          │  WebSocket
                                               ┌──────────▼─────────────────┐
                                               │  Fabric mod (Java)          │
                                               │  ws://localhost:25576       │
                                               └────────────────────────────┘
```

The Minecraft side (plugin / mod) runs a local **WebSocket server**. The Node.js MCP servers connect to it as clients. Claude Code (or any MCP host) runs the Node.js processes and talks to them over stdio.

---

## Quick start

### Prerequisites

| Tool | Version |
|------|---------|
| Node.js | ≥ 20 |
| Java | 21 |
| Gradle | 8.8+ (for bootstrapping wrappers) |

### 1. Install Node packages

```bash
npm install
```

### 2. Build the Paper plugin

```bash
cd minecraft/paper-plugin
gradle wrapper --gradle-version 8.8   # once only
./gradlew shadowJar
# Output: build/libs/minecraft-mcp-paper-0.1.0.jar
```

Copy the jar into your Paper server's `plugins/` directory and start the server.

### 3. Build the Fabric mod

```bash
cd minecraft/fabric-mod
gradle wrapper --gradle-version 8.8   # once only
./gradlew build
# Output: build/libs/minecraft-mcp-fabric-0.1.0.jar
```

Copy the jar into your Fabric client's `mods/` directory and launch Minecraft.

### 4. Configure Claude Code (`.claude/mcp.json`)

```json
{
  "mcpServers": {
    "minecraft-paper": {
      "command": "node",
      "args": ["--import", "tsx/esm", "/path/to/packages/mcp-server-paper/src/index.ts"],
      "env": {
        "PAPER_WS_URL": "ws://localhost:25575"
      }
    },
    "minecraft-fabric": {
      "command": "node",
      "args": ["--import", "tsx/esm", "/path/to/packages/mcp-server-fabric/src/index.ts"],
      "env": {
        "FABRIC_WS_URL": "ws://localhost:25576"
      }
    }
  }
}
```

---

## Available tools

### Paper / Spigot server (`mcp-server-paper`)

| Tool | Description |
|------|-------------|
| `get_server_logs` | Tail `logs/latest.log`, optional text filter |
| `subscribe_server_logs` | Push future log lines to the MCP client |
| `run_console_command` | Run a console command, capture output |
| `list_online_players` | List online players |
| `list_plugins` | List loaded plugins |
| `install_plugin` | Copy a jar into `plugins/`, optional hot-load |
| `reload_plugin` | Disable + re-enable a named plugin |
| `restart_server` | Restart or stop the server |
| `reflect_invoke` | Call any server-side method via reflection |
| `reflect_get_field` | Read a field value via reflection |
| `reflect_set_field` | Set a field value via reflection |
| `reflect_new_instance` | Construct a new object instance |
| `get_class_info` | List declared methods and fields of a class |
| `var_get / var_list / var_delete / var_clear` | Manage named variables across calls |
| `search_javadocs` | Search Paper/Folia/Velocity javadoc index |
| `fetch_javadoc_page` | Fetch and strip HTML from a javadoc page |

### Fabric client mod (`mcp-server-fabric`)

| Tool | Description |
|------|-------------|
| `get_chat_log` | Recent chat HUD messages |
| `send_chat_message` | Send chat text or `/command` |
| `get_player_state` | Position, health, inventory, game mode |
| `player_move_to` | Teleport player to coordinates |
| `player_look_at` | Set yaw/pitch or look toward a world point |
| `player_jump` | Trigger a jump |
| `player_break_block` | Break block at coordinates |
| `player_place_block` | Place held item against a block face |
| `player_interact` | Right-click a block |
| `get_block_at` | Block type and state at coordinates |
| `get_nearby_entities` | Entities within a radius |
| `take_screenshot` | Capture client view as base64 PNG |
| `client_reflect_invoke` | Call any client-side method via reflection |
| `client_reflect_get_field` | Read a client-side field |
| `client_reflect_set_field` | Set a client-side field |
| `client_get_class_info` | List methods/fields of a Minecraft class |
| `client_var_get / client_var_list / client_var_delete / client_var_clear` | Variable management |

---

## Reflection & variable system

The reflection tools let you call any method on any allowed class and chain results together using named variables:

```
# Get the overworld World object and store it as $overworld
reflect_invoke(
  class_name="org.bukkit.Bukkit",
  method_name="getWorld",
  args=[{type:"java.lang.String", value:"world"}],
  assign_to="overworld"
)

# Use $overworld to get the player list
reflect_invoke(
  class_name="org.bukkit.World",
  target="$overworld",
  method_name="getPlayers",
  assign_to="players"
)

# Read a field on a non-serialisable object returned as an opaque handle
# (the handle ID looks like "obj_42")
reflect_get_field(
  class_name="org.bukkit.entity.Player",
  field_name="exp",
  target="obj_42"
)
```

Non-serialisable return values are stored in an in-memory registry and returned as:
```json
{"__type": "object_ref", "__id": "obj_42", "__class": "org.bukkit.World"}
```

Pass `"__id"` or `"$varName"` back as a `target` or argument `value` in subsequent calls.

---

## Configuration

### Paper plugin (`plugins/MinecraftMcp/config.yml`)

```yaml
websocket:
  host: "0.0.0.0"
  port: 25575

reflection:
  allowed-packages:
    - "org.bukkit.*"
    - "io.papermc.*"
    - "net.kyori.adventure.*"
    - "net.minecraft.*"
    - "java.util.*"
    - "java.lang.*"
  blocked-packages:
    - "java.lang.Runtime"
    - "java.lang.ProcessBuilder"
  max-object-refs: 1000
```

### Fabric mod (`.minecraft/config/mcp.json`)

```json
{
  "websocket": { "host": "127.0.0.1", "port": 25576 },
  "reflection": {
    "allowedPackages": ["net.minecraft.*", "com.mojang.*", "net.fabricmc.*"],
    "blockedPackages": ["java.lang.Runtime"],
    "maxObjectRefs": 1000
  }
}
```

---

## Security notes

- The WebSocket servers bind to `127.0.0.1` by default (Fabric) and `0.0.0.0` (Paper — change to `127.0.0.1` if the MCP server runs on the same host).
- Reflection is gated behind configurable package allow/block lists.
- `java.lang.Runtime` and `java.lang.ProcessBuilder` are blocked by default.
- The Paper plugin's command sender runs with OP-level permissions; restrict MCP bridge access on shared servers.

---

## Development

```bash
# Run Paper MCP server (connects to ws://localhost:25575)
npm run dev:paper

# Run Fabric MCP server (connects to ws://localhost:25576)
npm run dev:fabric

# Override the WebSocket URL
PAPER_WS_URL=ws://192.168.1.10:25575 npm run dev:paper
```
