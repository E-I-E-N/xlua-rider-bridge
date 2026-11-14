# XLua CSharp Navigation for Rider

在 Rider 中，从 XLua 的 `CS` 类型或 Lua 类型别名跳到真实 C# 定义，无需切换到 EmmyLua。

Navigate from XLua `CS` expressions and Lua type aliases to C# declarations in Rider, using JetBrains Lua support.

## 安装

1. 使用 **Rider 2026.3**，安装并启用 JetBrains 发布的 [Lua 插件](https://plugins.jetbrains.com/plugin/34924-lua)（`com.intellij.lua`）。
2. Rider → Settings → Plugins → 齿轮 → **Manage Plugin Repositories**，添加下方固定更新源地址。
3. 返回 Plugins，搜索 **XLua CSharp Navigation**，选择该自定义仓库的结果，点击 **Install** 并按提示重启。
4. 打开 Unity/C# 解决方案，等待 Rider 索引完成；将项目的 Lua 根目录加入 Lua 的 Library Roots。
5. 在 Lua 文件的类型名或方法名上执行 **Go to Declaration**。Visual Studio 键位默认是 **F12**，其他键位以 Rider 菜单显示为准。

```text
https://github.com/E-I-E-N/xlua-rider-bridge/releases/latest/download/updatePlugins.xml
```

每台电脑添加一次地址即可，无需 Python、JDK 或源码。Rider 会根据此来源检查更新；是否自动安装更新由 Rider 的插件更新设置决定。
此前从 ZIP 安装的用户，可在插件 **Additional Info → Updates from** 中选择该自定义仓库，再执行更新。
卸载入口为 Settings → Plugins → XLua CSharp Navigation → Uninstall。

也可以从 [Releases](https://github.com/E-I-E-N/xlua-rider-bridge/releases/latest) 下载 `xlua-rider-bridge-*.zip`，用 **Install Plugin from Disk** 安装；页面自动生成的 Source code ZIP 是源码。

## 支持范围

```lua
local Vector2 = CS.UnityEngine.Vector2
GM = CS.GM
GM.GetTranslation("example")
```

- `CS.Namespace.Type` → 项目源码中的类型，或引用程序集中的反编译定义。
- `CS.Namespace.Type.Method` → C# 方法；存在重载时由 Rider 提供候选项。
- `local Alias = CS.Namespace.Type`、全局类型别名及其方法调用；跨文件别名依赖 Lua 插件能够解析该别名。
- 保留 JetBrains Lua 原有的 Lua 定义跳转与查找引用。

原理：读取 Lua PSI 和别名赋值，得到 C# 限定名，再调用 Rider 的 C# 符号索引及导航能力。无需生成 Lua 桩文件，也无需逐个登记 UnityEngine 类型。

## 兼容与限制

| 项目 | 范围 |
| --- | --- |
| Rider | 2026.3，声明的构建范围为 `263.7307`–`263.*` |
| 已实测 Rider | `RD-263.7307.258` |
| Lua 插件 | JetBrains Lua `263.7307.258`；其他版本未验证 |
| 已实测系统 | Windows；包内没有原生组件，macOS/Linux 的 IDE 导航尚未实测 |

- 必须打开拥有相关 C# 类型及程序集引用的解决方案。
- 暂不提供 C# → Lua 的跨语言查找引用、跨语言重命名或补全。
- 暂不推断方法返回值、实例变量及复杂动态表达式的 C# 类型。
- 同名方法重载按候选选择，不根据 Lua 实参推导最合适的重载。
- 使用 Rider/Lua 插件内部 API；升级 IDE 或 Lua 插件后可能需要适配。
- 安装及更新需要能够访问 GitHub 的 Release 下载服务。若提示更新来源未知，在 Additional Info 中重新选择上面的自定义仓库。

## 从源码构建

需要 Python 3.10+、Rider `RD-263.7307.258`（包含 JBR/JDK 25）以及解压后的 JetBrains Lua `263.7307.258`。
SDK 版本和官方下载地址固定在 `sdk.json`；不在仓库中分发 JetBrains SDK。

```powershell
python -B build.py --rider-home "C:/Program Files/JetBrains/JetBrains Rider 2026.3" --lua-plugin "$env:APPDATA/JetBrains/Rider2026.3/plugins/lua"
```

```bash
python3 -B build.py --rider-home /path/to/Rider --lua-plugin /path/to/lua
```

也可设置 `RIDER_HOME` 和 `LUA_PLUGIN_PATH` 后执行 `python -B build.py`。
macOS 的 `--rider-home` 可传 Rider `.app`；`--lua-plugin` 也可直接传 `lua.jar`。
路径以本机实际安装位置为准，构建程序读取 `product-info.json` 核对实际版本。

产物为 `dist/xlua-rider-bridge-<version>.zip`、`dist/SHA256SUMS` 和 `dist/updatePlugins.xml`。索引从同一份插件元数据生成，指向对应版本的 Release 安装包。
构建只包含源码编译结果、插件元数据和许可证，不包含本机路径、游戏源码或 IDE 日志。

## 构建与发布

GitHub Actions 会下载并校验固定版本 SDK，在 Linux 上编译和打包。普通推送和 Pull Request 提供构建产物；推送与 `plugin.xml` 版本一致的 `v<version>` 标签后，自动上传安装包、SHA256 校验文件及插件仓库索引，全部上传成功后才公开 Release 并设为 latest。

固定更新源通过 latest 指向最新 Release 自带的索引，发版时无需手改索引版本或下载地址。日常源码提交不会改变用户的更新源。发布维护版本或回退版本前，需确认将它设为 latest 是否符合预期。

发布前需在目标 Rider 上验证：直接 `CS` 类型、UnityEngine 类型、同文件和跨文件别名方法、原有 Lua 跳转和引用。CI 编译成功不代表 IDE 交互验收完成。

## 反馈与许可证

在 [Issues](https://github.com/E-I-E-N/xlua-rider-bridge/issues) 提供 Rider 构建号、Lua 插件版本、最小 Lua 示例和预期 C# 定义。
提交示例时请移除项目私有内容。

[MIT](LICENSE)。本项目为独立社区插件，与 JetBrains 或 Tencent 无隶属关系。
