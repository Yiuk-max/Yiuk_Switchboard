# Go 中转服务器工程规划

本文记录 Go 服务端的模块边界、设计约定和实现状态。早期里程碑表保留为开发路线；当前功能状态以文末代码现状及[项目技术文档](项目技术文档.md)为准。

## 第一版技术选型

| 部分 | 选择 | 理由 |
| --- | --- | --- |
| Go 版本 | Go 1.26，项目 `go` 指令设为 1.26.0 | 与当前开发机已安装版本一致；部署构建由同一工具链完成 |
| 网络 | 标准库 `net`、`net.TCPConn`、`io.Copy` | 控制面和游戏转发都是 TCP；连接数规模小，无需自建事件循环 |
| 控制协议 | `proto/switchboard.proto` + Protobuf wire 编解码器 + 有上限的 varint 长度帧 | 对齐客户端 Protobuf 协议；控制消息限制 64 KiB |
| 配置 | YAML，配置读取层独立封装；当前 MVP 锁定 `gopkg.in/yaml.v3` | 与设计文档的 `config.yaml` 一致；v4 是后续首选升级方向，但初始环境无网络访问，MVP 使用已缓存且兼容稳定的 v3 |
| 日志 | 标准库 `log/slog` | 结构化日志足够覆盖连接、房间和会话事件，不引入日志框架 |
| ID / 密钥 | 标准库 `crypto/rand` | 生成不可预测的一次性 sid 与房间 token，不引入 UUID 依赖 |
| 并发 | goroutine、channel、`sync.Mutex`、`context` | 与 Go 网络运行时契合；共享状态由明确的拥有者和锁保护 |
| 测试 | 标准库 `testing` | 当前有房间本机直连路径测试；协议、配置、会话和运维代理仍缺少专门测试 |

第一版不引入数据库、Web 框架、消息队列、依赖注入框架或自定义事件循环。房间状态只保存在内存中，进程重启后清空，与设计目标一致。

## 模块边界

依赖方向以 `cmd/relay → internal/server → 各业务包` 为主，底层包不反向依赖入口或服务器装配包。

| 目录 | 职责 | 不负责 |
| --- | --- | --- |
| `cmd/relay` | 读取启动参数、创建并运行服务器 | 协议业务和共享状态 |
| `internal/config` | 读取 YAML、应用默认值、校验端口范围和限制项 | 监听端口、修改运行中状态 |
| `internal/protocol` | Protobuf 消息结构、编码/解码、64 KiB 上限、首帧解析 | 房间权限和连接管理 |
| `internal/auth` | 密码校验、按来源 IP 限速 | 控制连接读写、房间生命周期 |
| `internal/room` | 房间模型、房间表、端口分配、房间状态与重连保留期 | 游戏字节转发 |
| `internal/session` | sid 生成和一次性配对、等待超时、双向字节拷贝、会话清理 | 控制协议分派 |
| `internal/control` | 控制连接认证后消息分派，处理 list/create/room_mods/close/resume/ping；向房主推送 `new_session` | 监听器生命周期与全局装配 |
| `internal/server` | 组装依赖、启动控制监听、协调关闭和 goroutine 生命周期 | 定义协议消息格式 |
| `configs` | 可提交的示例配置 | 真实密码或部署机配置 |
| `deploy/systemd` | 后续部署单元文件及运维说明 | Go 业务代码 |
| `ops/server` | TLS 运维管理代理，可管理 relay 进程、读取/校验保存配置并采集指标 | relay 玩家控制协议的 TLS |

## 连接与状态约定

- `server` 拥有进程级生命周期；收到退出信号后停止接收新连接并关闭现存监听器。
- `control` 为每条控制连接单独运行处理循环。房间只保存房主连接的受控发送接口，所有异步通知必须串行写入，避免多个 goroutine 同时写 Protobuf 帧。
- `room` 是房间状态和端口的唯一所有者；创建时先成功绑定端口再公布房间，关闭时先停止接入、清理会话，再释放端口。
- `session` 管理 sid 的短期配对表。sid 取出后立即作废；玩家连接等待房主数据连接最多 10 秒。任意一端关闭都结束整组连接并回收人数。
- 服务端以 `max_connections` 限制全部控制/数据连接总数；配对游戏流在 `session_idle_timeout_sec` 秒无入站数据后关闭，默认 300 秒，避免失联客户端长期占用连接。
- `protocol` 解析 `data` 首帧时保留其 `bufio.Reader`。如缓冲区已经读入游戏负载，转发时必须先从同一个 reader 消费，不能丢弃已预读字节。
- 跨连接共享的 map 与计数必须有明确锁保护；不要在持锁期间执行网络 I/O。
- 房间控制连接断开时进入可配置的 30 秒保留期。房主凭 token 可接管仍处于半开状态的旧控制连接；保留期回调按 owner key 检查并原子删除，旧定时器不能误关已恢复的房间。

## 安全和运维边界

relay 实现密码认证、IP 登录限速、Protobuf 帧大小限制、房间/玩家/等待配对上限、随机 sid 与房间 token。relay 玩家/房主控制协议仍以普通 TCP 传输，密码未受 TLS 保护；公网使用需外层可信隧道或其他传输保护。独立的 `ops/server` 管理协议已使用 TLS 1.2+，且通过服主密码认证。部署配置应限制为仅服务账户可读写；日志不记录密码、token 或游戏数据内容。

控制端口与房间端口均只绑定 TCP。部署文档需说明云安全组和主机防火墙都要放行对应范围。优雅关闭要关闭 listeners、拒绝新会话、通知/断开现存连接并等待处理 goroutine 退出。

## 演进路线与里程碑

| 阶段 | 范围 | 验收条件 |
| --- | --- | --- |
| S0 骨架 | 工程布局、配置格式约定、协议和模块边界 | 新贡献者能按目录定位入口和职责；配置样例与设计文档一致 |
| S1 单玩家转发 | 一个房间端口、命令行假房主、sid 配对、TCP 双向转发 | Minecraft 客户端通过 VPS 进入房主局域网世界 |
| S2 多玩家与控制协议 | 多 sid 并发、auth/list/create/close/room_mods/ping、Protobuf 帧限制 | 同一房间多个玩家互不串流；错误密码被拒绝 |
| S3 生命周期和限额 | 房间端口池、人数/房间/等待上限、心跳超时、30 秒 resume、随机 token | 主机短暂断线可接管房间；过期房间和连接均被清理 |
| S4 稳定性与发布 | 优雅关闭、结构化日志、Windows/Linux 构建说明、systemd 示例 | 服务重启后自动恢复进程；退出后端口释放，无遗留连接 |
| S5 安全加固 | TLS、玩家 IP 放行（按需要实现） | 抓包无法读取密码；未授权来源不能占用房间连接 |

实现顺序遵循“先可验证的数据通路，再协议功能，再生命周期和运维”。NeoForge 客户端与 Go 服务端通过稳定的 Protobuf 协议并行开发；TLS 或协议升级不得改变游戏数据通路的字节透明原则。

## 当前目录

```text
cmd/relay/             服务入口
configs/               示例配置
deploy/systemd/        systemd 部署资料
docs/                  项目文档
internal/auth/         认证与限速
internal/config/       配置解析和校验
internal/control/      控制连接与请求处理
internal/protocol/     Protobuf 消息与帧读取
internal/room/         房间状态和端口管理
internal/server/       服务装配和生命周期
internal/session/      sid 配对和字节转发
```

## 当前实现状态

截至 2026-10-18，relay 已实现 Protobuf v2 控制认证与请求分派、房间监听/列表/模组清单、房主 token 恢复与接管、sid 配对、TCP 双向转发、房间端口/人数/连接/超时限制、本机房主直连路径、配置校验、IP 登录限速及优雅关闭。relay 控制协议 TLS、IP allowlist、连接状态指标和 systemd unit 尚未实现。

仓库另有独立 TLS 运维代理：可查询 relay 状态、启停/重启子进程、读写 relay YAML 配置、采集 Linux/Windows CPU 与内存指标；配置更新经校验后原子替换，需重启 relay 才会生效。Qt 6 桌面客户端连接该管理通道。

当前 Go 自动化测试限于 `internal/room/manager_test.go` 中的本机直连路径集成测试；协议、配置、session、server 和 ops agent 没有专项测试。并发压力、Minecraft 多客户端体验、Linux 目标机运行和 systemd 部署尚未验收。当前已生成 `bin/relay-linux-amd64` 与 `ops/server/ops-agent-linux-amd64`，文件已确认是 Linux x86_64 ELF；这不等于目标机运行验收。
