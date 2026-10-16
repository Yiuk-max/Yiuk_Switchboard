# 开发环境配置

本文说明 Yiuk Switchboard 客户端模组与 Go 中转服务端的本地开发环境。客户端工程位于 `mod/`，服务端 Go module 位于仓库根目录。

## 客户端模组

### 必需环境

- 64 位 JDK 21。Gradle 工具链和 NeoForge 1.21.1 构建均要求 Java 21；仅安装较新版本（例如 Java 25）不能替代。
- 项目自带 Gradle Wrapper，版本为 9.2.1；无需单独安装 Gradle。
- 首次构建需要联网获取 Gradle、NeoForge/Minecraft 1.21.1 及其依赖。

仓库可能预先提供 `mod/.toolchain/jdk-21.0.12.1+1/`。如果此目录存在，可在 PowerShell 中使用：

```powershell
cd mod
$env:JAVA_HOME = (Resolve-Path '.toolchain/jdk-21.0.12.1+1').Path
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

应看到 Java 21。`JAVA_HOME` 只对当前 PowerShell 窗口生效。若仓库没有预置工具链，请安装任意 64 位 JDK 21，并把 `JAVA_HOME` 指向其安装目录，再将 `%JAVA_HOME%\bin` 加入 `PATH`。

### 构建与开发运行

在仓库根目录的 PowerShell 中先进入客户端工程，然后使用项目 Wrapper：

```powershell
cd mod
.\gradlew.bat build
.\gradlew.bat runClient
.\gradlew.bat jar
```

`build` 编译并执行 Gradle 校验任务；`runClient` 启动开发客户端；`jar` 重新生成发布用模组包。JAR 输出在 `mod/build/libs/yiukswitchboard-<版本>.jar`。提交或分发前检查文件时间，并确认 JAR 内包含 `assets/yiukswitchboard/icon.png` 和 `META-INF/neoforge.mods.toml`。

### 常见问题

- **找不到匹配 Java 21 的安装**：确认 `JAVA_HOME` 指向 JDK 根目录（不是 `bin`），并确认 `java -version` 输出 21。仅安装 Java 25 不够。
- **首次构建下载失败**：确认网络可访问 Gradle 分发站、Mojang 下载站及 NeoForge/Maven 仓库；重新执行 Gradle 任务通常会复用已成功下载的缓存。
- **要强制重建 JAR**：在 `mod/` 目录执行 `.\gradlew.bat clean jar`。`clean` 会删除 `mod/build/` 下的生成产物，不会删除源码。
- **游戏配置**：开发客户端的模组配置位于运行实例的 `config/yiukswitchboard.json`。

## Go 中转服务端

- 安装 Go 1.26 或更新版本，与仓库根目录 `go.mod` 的 `go 1.26.0` 声明一致。
- 在仓库根目录执行 `go build ./...` 编译所有包；执行 `go build -o bin/relay.exe ./cmd/relay` 生成 Windows 服务端程序。
- 复制 `configs/config.example.yaml` 作为运行配置，设置连接密码和端口范围，再按根目录 README 的步骤启动。

## 版本与路径速查

| 项目 | 配置 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.244（见 `mod/gradle.properties`） |
| Java | 64 位 JDK 21 |
| Gradle | 9.2.1（项目 Wrapper） |
| 客户端工程 | `mod/` |
| 客户端 JAR 输出 | `mod/build/libs/` |
| 服务端 | 仓库根目录 Go module |
