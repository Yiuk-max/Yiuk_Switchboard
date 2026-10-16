# 客户端模组开发规划

## 目标与边界

- 平台固定为 Minecraft 1.21.1、NeoForge、Java 21，只在客户端运行。
- 玩家浏览房间、登录和加入的界面是一组独立的中转联机页面，不把房间列表塞进原版服务器列表，也不改写原版多人游戏流程。
- 房主先进入自己的单人世界，再从原版暂停菜单进入单独的开房流程。
- 原版多人游戏页、暂停菜单及服务器连接页继续保留。只追加入口按钮，使用原版 `Screen`、`Button`、`EditBox`、列表控件、字体/间距和翻译机制；按原版方式返回上一级。
- 客户端到 Go 服务端使用普通 TCP JSONL 控制连接；房主为每位玩家创建数据 TCP 连接并转发到本机 LAN 端口。不注册 Minecraft 自定义网络 payload，不改变游戏握手或游戏数据。

## 玩家与房主入口

### 玩家入口

在原版多人游戏页追加“中转联机”按钮。点击后打开独立的 `RelayHomeScreen`，其导航关系如下：

```text
原版多人游戏页
  └─ 中转联机按钮 → RelayHomeScreen
                         ├─ 未登录 → RelayLoginScreen → RelayRoomListScreen
                         └─ 已登录 ───────────────→ RelayRoomListScreen
                                                        ├─ 刷新 / 新建房间
                                                        └─ 选择房间 → 模组差异提示 → 原版服务器连接流程
```

中转页面显示连接地址、密码、昵称、房间名、房主、人数、MC 版本、加载器和模组数。列表用原版风格的行选择/按钮；模组不一致弹窗提供“仍然加入”和“取消”。继续加入时调用原版服务器连接页面/流程，服务器地址仍是服务端返回的 `IP:房间端口`。

按返回键或点击返回时回到打开中转入口的原版多人游戏页。刷新、登录失败、空列表和断线都在中转页面内显示状态，不覆盖原版提示。

### 房主入口

只有已进入单人世界时，才在原版暂停菜单追加“开放中转房间”按钮。点击后打开 `RelayHostCreateScreen`，填写房间名和人数上限，再启动 LAN 开放并创建服务端房间。开房成功后进入 `RelayHostStatusScreen`，展示房间名、房间端口、人数和关闭按钮。

多人游戏页中的中转按钮服务于玩家加入；暂停菜单里的中转按钮服务于当前世界房主。这两个入口分开，开房状态与玩家浏览流程也分开。多人游戏中的房主状态由暂停菜单入口或状态面板访问，不挤入原版服务器列表。

房主退出世界、关闭房间或游戏关闭时，发送 `close`（可用时）并关闭控制连接和所有数据转发连接。LAN 端口和游戏状态由原版集成服务器管理；实现时必须先检查 Minecraft 1.21.1 反编译源码，确认“对局域网开放”和端口读取的真实方法签名，不根据其他版本猜接口。

## 原版一致性规则

1. 使用 NeoForge 1.21.1 对应的 Screen 初始化事件追加按钮；不取消原版事件、不替换原版 Screen、不重排或隐藏原版控件。
2. 入口按钮放在原版布局预留的底部操作区，避开原版按钮；窗口缩放后重新布局，检查窄窗口和长翻译文本。
3. 自定义页面继承原版 `Screen`，使用原版按钮、输入框、列表、标题、背景、焦点/键盘行为和叙述支持。
4. 按原版返回键语义回到父页面；成功进入世界后由游戏原生连接流程控制页面跳转。
5. 所有界面文案走语言资源（至少 `zh_cn`、`en_us`），错误显示简短、可操作的本地化信息。
6. 网络读写绝不在渲染/游戏主线程执行；后台结果通过 `Minecraft#execute` 切回主线程更新 screen。
7. 客户端专用类必须放在客户端入口/包中，避免 dedicated server 加载时解析 `net.minecraft.client` 类型。

## 模块目录

```text
mod/
├─ docs/development-plan.md
└─ src/main/
   ├─ java/com/yiuk/switchboard/
   │  ├─ SwitchboardMod.java       mod 初始化入口
   │  ├─ client/                   客户端专用注册与 Screen 注入
   │  │  ├─ ClientEvents.java      多人页和暂停页追加按钮
   │  │  ├─ gui/                   玩家中转页、房主开房页/状态页
   │  │  ├─ host/                  开 LAN、房主通知处理、数据连接转发
   │  │  └─ connect/               调用原版加入服务器流程
   │  ├─ config/                   本地地址、昵称和可选记住的密码
   │  ├─ control/                  控制连接、JSONL 收发、心跳和消息路由
   │  ├─ protocol/                 客户端 JSONL 消息模型
   │  └─ mods/                     本地模组清单扫描和对比
   └─ resources/
      ├─ META-INF/neoforge.mods.toml
      └─ assets/yiukswitchboard/lang/{zh_cn,en_us}.json
```

`control` 不直接依赖 UI；GUI 调用异步控制服务接口。`host` 订阅 `new_session` 并运行每个会话的 Socket 转发。`mods` 只负责收集 ID/版本和对比，不自动下载。协议字段与 Go 服务端保持一致。

## 技术选型

| 范围 | 选择 |
| --- | --- |
| 模组构建 | NeoForge 官方 1.21.1 MDK，ModDevGradle 或 NeoGradle；Gradle Wrapper 随工程提交 |
| Java | JDK 21，64 位 |
| 界面 | 原版 `Screen` 和常用原版控件；NeoForge 客户端 Screen 事件只用于追加入口按钮 |
| 控制网络 | Java 标准 `Socket` + UTF-8 JSONL；控制消息单行最大 64 KiB |
| 房主转发 | Java 21 虚拟线程或有界线程池；每个玩家一条服务器数据 Socket 和一个本机 LAN Socket，双向流复制 |
| 配置 | 游戏配置目录 JSON；密码只在用户勾选“记住”时保存，并明确告知本地明文存储 |
| Minecraft 内部调用 | 对 LAN 开放、获取端口、原版服务器连接均以 1.21.1 MDK 反编译源码为准，先做探针/小功能再封装 |

NeoForge 官方 1.21.1 文档要求 64 位 Java 21，提供 `runClient` 开发运行方式；第一次 Gradle 导入会下载并反编译游戏与 NeoForge 源码。普通 Screen 可独立处理 UI；本项目的房间网络不使用 NeoForge payload，因为服务器只透明转发原始 TCP 字节。[官方开发环境文档](https://docs.neoforged.net/docs/1.21.1/gettingstarted/)、[官方 Screen 文档](https://docs.neoforged.net/docs/1.21.1/gui/screens/)、[官方网络文档](https://docs.neoforged.net/docs/1.21.1/networking/)

## 里程碑

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| C0 MDK 基础 | 初始化 NeoForge 1.21.1 MDK、mod 元数据、客户端/服务端安全边界 | `runClient` 可启动，dedicated server 不因客户端类崩溃 |
| C1 原版入口 | 多人页追加“中转联机”；单人暂停菜单追加“开放中转房间” | 原版按钮仍工作；布局缩放正常；两入口分别打开独立 Screen |
| C2 独立页面与本地配置 | 登录、房间列表、创建页空界面、中文/英文文案、返回导航 | 页面样式和交互遵循原版；无网络逻辑也可操作/退出 |
| C3 玩家控制流程 | 登录、list、room_mods、差异提示、调用原版连接流程 | 可从中转页选房并进入服务端房间端口 |
| C4 房主开房与转发 | LAN 开放、create、new_session、data sid、本机转发、关闭清理 | 一名朋友通过服务端进入房主单人世界 |
| C5 多人和稳定性 | 多玩家并行、心跳、网络错误/重连、世界退出清理、窗口缩放/键盘导航 | 3 名玩家同时连接；断线后无遗留 Socket/线程 |
| C6 模组对比与打包 | 扫描 ModList、缺少/版本差异/多余展示、构建 JAR、PCL NeoForge 实例安装验证 | 差异提示正确；安装后在实际 1.21.1 实例可加入 |

每个 UI 里程碑都先用 `runClient` 看实际布局，涉及游戏内部方法的阶段先查 1.21.1 反编译源码。避免一口气同时做界面、协议和转发。
