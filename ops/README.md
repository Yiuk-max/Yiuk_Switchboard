# 运维工具

该目录包含独立的服务器运维代理和 Qt 6 桌面客户端。运维代理单独运行，并以子进程方式启动、停止或重启 relay。

## 安全模型

- 运维登录直接使用 relay 配置中的 `host_password`；玩家密码不能登录运维端。
- 服主密码仅在 TLS 加密连接内发送，服务端代理从当前 relay 配置读取并做常量时间校验，不写入日志。认证权限绑定到当前 TLS 连接。
- 管理连接使用独立的 TLS 加密 TCP 消息协议（4 字节长度前缀 + JSON 消息），不是 HTTP/HTTPS；默认端口为 `29564`，可在配置中改成任意可用端口。防火墙只向运维终端开放该端口。
- relay 配置更新先解析并校验 YAML，再原子替换配置文件，不会把配置内容写入日志。
- Qt 客户端第一页是服务器运维面板，登录单独放在第二页。配置以表单逐项编辑端口、双密码、房间/连接上限、超时和日志路径，不需要手写 YAML；CPU 和内存会绘制最近 5 分钟的负载曲线。

消息帧由 4 字节大端长度和 UTF-8 JSON 组成，最大 2 MiB。请求包含 `version: 1`、请求 `id` 和 `type`；响应回传相同的 `id`，并用 `ok/error/data` 表示处理结果。每条 TLS 连接使用服主密码登录，连接断开后需重新登录。服务端按来源 IP 限制登录尝试。

## 服务器代理

构建：`go build -o ops-agent ./ops/server/cmd/ops-agent`

复制 [`server/ops-agent.example.yaml`](server/ops-agent.example.yaml) 为 `ops-agent.yaml`，填写服务器证书以及 relay 程序和配置路径：

```yaml
listen: ":29564"
tls_cert: "/etc/yiuk-ops/server.crt"
tls_key: "/etc/yiuk-ops/server.key"
relay_binary: "/opt/yiuk/relay"
relay_config: "/opt/yiuk/config.yaml"
relay_working_dir: "/opt/yiuk"
```

启动：`./ops-agent -config /etc/yiuk-ops/ops-agent.yaml`。Windows 路径同样可用；TLS 证书应由客户端信任的 CA 签发，并与连接域名匹配。客户端填 `主机名:端口`（例如 `ops.example.org:29564`）。metrics 在 Linux 和 Windows 代理上读取主机 CPU 和内存使用量。启用代理前先停止手工运行的 relay 实例，此后由代理独占管理它配置的 relay 进程。

## Qt 客户端

使用 Qt 6 Widgets 和 Network 模块：

```sh
cmake -S ops/client -B ops/client/build
cmake --build ops/client/build --config Release
```

客户端连接时输入服务器地址和转发后端的服主密码。运维通道通过 TLS 证书验证服务器身份，并校验服主密码。功能包括进程状态、启停/重启、配置读取/保存和 CPU/内存查看。

代理应由 systemd/Windows 服务管理器常驻运行；防火墙只放行可信运维终端访问代理端口，不要让 relay 自己管理或停止运维代理。
