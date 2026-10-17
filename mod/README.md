# Yiuk Switchboard 客户端模组

独立的 Minecraft 1.21.1 NeoForge 客户端模组工程。Go 服务端留在仓库根目录，Java/Gradle 工程只放在本目录。

工程已配置为 Java 21、NeoForge 1.21.1、官方 Mojang mappings，包含客户端 UI、控制连接、房间浏览、主机转发和模组列表比对的首版实现。Gradle Wrapper 已包含在项目中；首次构建需要联网下载 NeoForge 依赖。

## 开发

安装 64 位 Java 21 JDK 后，在 `mod` 目录执行 `gradlew.bat build` 编译，或执行 `gradlew.bat runClient` 启动开发客户端。首次运行需要联网下载 Gradle、Minecraft/NeoForge 和插件依赖。配置文件位于游戏目录 `config/yiukswitchboard.json`。

环境准备、Java 21 配置、构建命令和常见问题见[开发环境配置](docs/development-environment.md)。

房间列表页和房主建房页使用独立 Screen；入口按钮通过 NeoForge 屏幕初始化事件注入多人游戏列表和单人世界暂停菜单。客户端控制连接使用与仓库根目录 `proto/switchboard.proto` 对应的长度前缀 Protobuf（协议版本 2）；房主建立远端数据通道并双向转发到单人世界开放的 LAN 端口。服务端与模组必须一起升级，不能与旧 JSONL 服务端互通。

界面原则：不替换原版多人游戏页或暂停菜单，只在对应原版页面里添加一个入口按钮。按钮沿用原版控件、翻译、导航和返回习惯；进入后使用独立的中转联机页面。玩家入口位于多人游戏页，房主入口位于单人世界的暂停菜单。

开发分解、界面流程和验收标准见 [客户端开发规划](docs/development-plan.md)。
