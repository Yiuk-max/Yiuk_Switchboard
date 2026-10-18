# 运维工具

`ops/` 包含 Go 运维代理和 Qt 6 Widgets 桌面客户端。代理与 relay 分开运行，并以子进程方式管理它配置的 relay 实例。

## 能力与边界

- 运维客户端可查询 relay 进程状态、启动/停止/重启进程、读取配置、校验并保存配置，以及查看 CPU/内存指标。
- 运维代理仅对 Linux 和 Windows 提供 CPU/内存指标；其他操作系统会返回不可用状态。
- 配置保存会先校验 YAML 并原子替换文件；不会热更新正在运行的 relay。保存后需从运维客户端重启 relay 才能应用。
- 管理端口采用 TLS 1.2 或更高版本。它与玩家/房主访问的 relay 控制端口分离；relay 控制协议本身仍是普通 TCP，密码没有 TLS 保护。
- 运维登录使用 relay 配置中的 `host_password`，玩家密码没有运维权限。服务端按来源 IP 限制登录尝试；密码比较使用常量时间比较。
- 运维代理不得与 relay 共用或接管自身进程。启用前先停止手工运行的 relay；之后由代理管理配置中指定的实例。

运维代理与客户端使用 4 字节大端长度前缀 + UTF-8 JSON 消息，单帧上限为 2 MiB。消息类型包括认证、进程状态、指标、配置读取/保存及进程操作。该管理协议不是 HTTP/HTTPS。

## Linux 服务器代理

从仓库根目录构建：

```sh
GOOS=linux GOARCH=amd64 go build -trimpath -o ops/server/ops-agent-linux-amd64 ./ops/server/cmd/ops-agent
```

Windows 构建：

```powershell
go build -o ops/server/ops-agent.exe ./ops/server/cmd/ops-agent
```

`ops/server/ops-agent.yaml` 展示代理需要的配置项。部署时先复制到受限权限的运行目录，并根据部署主机修改路径：

```yaml
listen: ":29564"
tls_cert: "/etc/yiuk-ops/server.crt"
tls_key: "/etc/yiuk-ops/server.key"
relay_binary: "/opt/yiuk/relay"
relay_config: "/opt/yiuk/config.yaml"
relay_working_dir: "/opt/yiuk"
```

将 Linux relay 可执行文件、`config.yaml` 和代理分别放在 `relay_binary`、`relay_config` 与代理配置指定的位置。设置 `host_password` 和 `player_password` 为不同的随机密钥；限制 relay 配置和 TLS 私钥只允许服务账户读取。

启动示例：

```sh
./ops-agent-linux-amd64 -config /etc/yiuk-ops/ops-agent.yaml
```

将云安全组和主机防火墙的 `29564/tcp`（或配置的管理端口）限制为可信运维终端访问。Minecraft relay 另需开放其控制端口及完整房间 TCP 端口范围。生产部署可由 systemd 等服务管理器托管运维代理；仓库目前没有提供 systemd unit 文件。

## Qt 桌面客户端

客户端使用 Qt 6 Widgets 和 Network 模块。使用 Qt 6 开发环境从仓库根目录构建：

```sh
cmake -S ops/client -B ops/client/build
cmake --build ops/client/build --config Release
```

客户端地址填写 `主机名:端口`，例如 `ops.example.org:29564`；登录密码是 relay 的服主密码。客户端优先校验系统信任链和主机名；首次连接遇到可固定的证书校验错误时会显示 SHA-256 指纹，用户确认后按服务器地址保存指纹。证书过期、未生效、撤销或签名错误等情况不能通过首次信任绕过。

客户端可选择记住密码。在 Windows 上使用 DPAPI 保护本机保存的密码；其他平台不提供该记住密码功能。relay 配置页中的密码只在 TLS 管理连接上传输。配置保存后需重启 relay 才能使新设置生效。

## 部署检查

1. 确认运维代理配置路径、证书路径、relay 程序路径、relay 配置路径和工作目录均与目标机一致。
2. 确认运维 TLS 证书对客户端有效，或在首次连接时核对服务器证书指纹。
3. 限制管理端口来源；不要将其无差别开放给公网。
4. 停止已有的手动 relay 进程，再由运维代理启动和管理目标 relay。
5. 配置保存后显式重启 relay；重启会中断当前房间，房间状态不会跨 relay 进程重启保存。
