# Portal

A Minecraft Paper 1.21.1 plugin that federates multiple server instances together, enabling CDN-style distributed gameplay where all players across all instances can see and interact with each other as if they were on a single server.

## Features

- **Low-Latency Sync**: Uses Redis pub/sub and direct TCP connections for minimal latency
- **Full Player Synchronization**: Position, rotation, equipment, animations, health, effects
- **Entity Synchronization**: Sync mobs, items, projectiles across instances
- **World State Sync**: Block changes, explosions propagate to all instances
- **Chat Federation**: Cross-instance chat with full formatting support
- **Combat Sync**: Cross-instance PvP and PvE damage with knockback
- **Virtual Players**: Remote players appear as real entities with skins and animations

## Architecture example

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Node US-East  │    │   Node EU-West  │    │  Node AP-South  │
│   (Primary)     │◄──►│                 │◄──►│                 │
│   Players: 50   │    │   Players: 30   │    │   Players: 20   │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         └──────────────────────┼──────────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │        Redis          │
                    │   (Pub/Sub + State)   │
                    └───────────────────────┘
```

## Requirements

- Paper 1.21.1+
- Java 21+
- Redis 7.0+ (for pub/sub messaging)
- Network connectivity between all nodes

## Installation

1. Build the plugin:
   ```bash
   ./gradlew shadowJar
   ```

2. Copy `build/libs/Portal-1.0.0.jar` to each server's `plugins/` folder

3. Start each server once to generate the config

4. Configure each node (see Configuration section)

5. Restart all servers

## Configuration

Portal is highly configurable. All settings are in `plugins/Portal/config.yml`.

### config.yml

```yaml
# Unique identifier for this node (auto-generated if empty)
node-id: ""

# Human-readable name for this node
node-name: "node-us-east-1"

# Region identifier for latency-based routing
region: "us-east"

# Is this the primary/host node?
is-primary: true

# Network Configuration
network:
  redis:
    enabled: true
    host: "redis.example.com"
    port: 6379
    password: "your-redis-password"
  
  direct:
    enabled: true
    bind-port: 25580
    peers:
      - host: "node2.example.com"
        port: 25580
      - host: "node3.example.com"
        port: 25580
```

See the generated `config.yml` for all available options with detailed comments.

### Multi-Node Setup

Each node needs:
1. The same world files (use a shared filesystem or sync mechanism)
2. A unique `node-name` and optionally `region`
3. One node should be set as `is-primary: true`
4. Redis connection details
5. Direct peer connections for lowest latency

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/portal status` | Show federation status | - |
| `/portal info` | Show node information | - |
| `/portal stats` | Show network statistics | - |
| `/portal debug <option>` | Toggle debug options | `portal.admin.debug` |
| `/pnode list` | List connected nodes | `portal.admin.nodes` |
| `/pnode info <id>` | Show node details | `portal.admin.nodes` |
| `/pnode ping [id]` | Ping nodes | `portal.admin.nodes` |
| `/psync <type>` | Force synchronization | `portal.admin.sync` |

### Command Aliases
- `/portal` can also be used as `/p` or `/ptl`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `portal.admin` | Full admin access | op |
| `portal.admin.nodes` | Manage federation nodes | op |
| `portal.admin.sync` | Force sync operations | op |
| `portal.admin.debug` | Debug commands | op |

## How It Works

### Player Synchronization
- Player positions are broadcast every tick (50ms) using batched, compressed messages
- Each remote player is represented as a virtual entity on other nodes
- Skin data is synchronized for accurate player appearance
- Equipment, animations, and effects are synchronized in real-time

### World Synchronization
- Block changes are immediately broadcast to all nodes
- Changes are batched for efficiency when many blocks change at once
- Each node applies changes to maintain identical world state

### Combat Synchronization
- Damage events are broadcast with full knockback vectors
- Virtual players can be damaged and will relay damage to the source node
- The source node processes the damage and broadcasts health updates

### Latency Optimization
1. **Direct TCP connections** between nodes for lowest latency
2. **Binary protocol** (Protobuf) for efficient serialization
3. **LZ4 compression** for large payloads
4. **Message batching** to reduce network overhead
5. **Interpolation** on virtual player movement for smooth rendering

## Performance Considerations

- **Network**: Each player generates ~1-2 KB/s of sync traffic
- **CPU**: Minimal overhead, async processing on dedicated threads
- **Memory**: ~1 KB per remote player for state tracking
- **Recommended**: Low-latency connections (<50ms) between nodes

## Limitations

- World generation should be synchronized (use same seed)
- Redstone timing may vary between nodes
- Complex mob AI may behave differently across nodes
- Container interactions need careful handling

## Troubleshooting

### Players not visible across nodes
1. Check Redis connectivity: `/portal status`
2. Verify nodes see each other: `/pnode list`
3. Check latency: `/pnode ping`
4. Enable debug logging: `/portal debug sync`

### High latency
1. Use direct connections instead of Redis-only
2. Check network path between nodes
3. Consider regional node placement

### Desynced world state
1. Force sync: `/psync all`
2. Check block change batching settings
3. Ensure all nodes have identical world files

## Development

### Building
```bash
./gradlew build
```

### Testing
```bash
./gradlew runServer
```

### Project Structure
```
src/main/kotlin/org/mwynhad/portal/
├── Portal.kt               # Main plugin class
├── config/                 # Configuration handling
├── protocol/               # Message definitions & serialization
├── network/                # Network layer (Redis, Direct TCP)
├── node/                   # Node management
├── sync/                   # Sync managers (Player, Entity, World, Chat)
├── virtual/                # Virtual player entities
├── listener/               # Bukkit event listeners
├── command/                # Command handlers
└── metrics/                # Performance metrics
```

## License

MIT License - See LICENSE file

## Contributing

Contributions welcome! Please read CONTRIBUTING.md first.
