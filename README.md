# Yiuk Switchboard

Go TCP relay for the Minecraft 1.21.1 NeoForge LAN-play project.

The separate client mod project, implementation, and development plan live under [`mod/`](mod/README.md). It targets Java 21 and NeoForge 1.21.1.

## Build the mod

Install a 64-bit Java 21 JDK, then run `cd mod; .\gradlew.bat build`. Launch the development client with `cd mod; .\gradlew.bat runClient`.

See [the development environment guide](mod/docs/development-environment.md) for Java 21 setup, build commands, and troubleshooting.

## Run the server

1. Copy `configs/config.yaml` to the runtime directory as `config.yaml` and replace `password`.
2. Run `go run ./cmd/relay -config config.yaml`.
3. Allow the configured control TCP port and the full room TCP port range in both the VPS firewall and cloud security group.

Build a local executable with `go build -o bin/relay.exe ./cmd/relay` (Windows) or `go build -o bin/relay ./cmd/relay` (Linux).

The first implementation includes password authentication, length-delimited Protocol Buffers control messages (schema: `proto/switchboard.proto`), room creation/listing/closure, room mod lists, heartbeat responses, host reconnect tokens, per-room TCP listeners, one-time sid pairing and byte-transparent TCP forwarding. It keeps state in memory. Password traffic is plaintext; use only on a trusted network until TLS is added.

The relay batches file logs in memory (64 KiB threshold), then flushes and syncs them on graceful shutdown. Use Ctrl+C or the service manager's normal stop operation; forced termination can lose buffered log records.

See the [project technical documentation](docs/项目技术文档.md) for the complete architecture, protocol, lifecycle, issue history, and known limitations. The [server architecture plan](docs/server-architecture.md) retains the original Go package plan and milestones.
