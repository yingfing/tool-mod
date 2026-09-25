# Phantom Staff

纯客户端 Minecraft（NeoForge 1.21.1）辅助模组，专门针对 **Create Aeronautics**（modid `simulated`）的物理结构提供可视化与操作辅助：

- **高亮**：给每个已加载的物理结构（飞行载具/物理方块）画发光轮廓框，远距离/小目标也能定位。
- **追踪红线**：从你的眼睛向所有已加载的物理结构画红线——不要求瞄准、不要求进视锥、不要求被渲染；可穿透地形。
- **屏幕边缘箭头**：屏幕外或背后的结构在屏幕边缘画指向箭头，绝不漏标。
- **配置入口**：在游戏内「Mods」界面点本模组 → Config，或按 `G` 键直接打开配置菜单。

> 本模组只做客户端渲染与输入辅助，**不向服务器上报任何伪造数据**，因此不会触发反作弊。

---

## 环境 / 依赖矩阵

| 组件 | 版本要求 | 说明 |
|---|---|---|
| Minecraft | `1.21.1` | 固定版本 |
| NeoForge | `21.1.219+` | `loaderVersion="[4.0,)"` |
| Create | `6.0.10+` | 必需，客户端 |
| Create Aeronautics | `1.0+` | modid `simulated`，必需，客户端 |
| MaFgLib | `0.4.3+` | **客户端必需**，提供配置菜单（仅编译期依赖，游戏内需另行安装）|

> 上述四项在 `META-INF/neoforge.mods.toml` 中声明为 `required`（side=CLIENT）。缺少 MaFgLib 时配置菜单无法打开；缺少 Create / Aeronautics 时高亮与红线不会生效（模组仍可加载）。

---

## 安装

1. 安装匹配版本的 NeoForge、Create、Create Aeronautics、MaFgLib（客户端）。
2. 把本模组的 jar 放入 `.minecraft/mods/` 并启动游戏。

---

## 使用

1. **打开配置**：游戏内按 `Esc` → `Mods` → 找到 **Phantom Staff** → `Config`；或直接按 `G` 键。
   - 配置保存在 `config/phantomstaff.json`，所有开关均可在菜单内调整。
2. **追踪红线 / 高亮 / 边缘箭头**：在配置菜单中开关 `toggle_target_line`（或绑定快捷键），开启后对所有已加载物理结构生效。相关子开关：
   - `target_line_color` / `target_line_width`：红线颜色与线宽
   - `target_line_max_distance`：最大追踪距离（方块）
   - `target_line_through_walls`：是否穿透地形
   - `target_line_edge_arrows`：屏幕边缘指向箭头
   - `target_line_highlight`：物理结构包围盒高亮框

---

## 构建

GitHub Actions 自动构建：推送到 `1.21.1` 分支即触发，产物 jar 在 Actions 页面的 Artifacts 中下载。
打 `v*` 开头的 Tag（如 `v1.14.0`）会额外自动创建 GitHub Release 并附上 jar。

> **版本号软编码**：版本号由构建脚本从 git tag 自动推导——当前提交指向某个 `v*` tag 时直接用该 tag 作版本；分支/提交（未打 tag）构建则形如 `1.13.0-dev.a1b2c3d4`。**发版只需 `git tag vX.Y.Z && git push origin vX.Y.Z`，无需改任何文件。**

本地构建：

```bash
./gradlew build          # 产物位于 build/libs/*.jar
./gradlew runClient      # 本地带模组的客户端调试（需先装好依赖模组）
```

---

## 已知限制 / Roadmap

- 仅支持 Minecraft 1.21.1 / NeoForge 21.1.x。
- 红线/高亮/边缘箭头依赖 Aeronautics 已把物理结构下发到客户端（即你已加载对应维度）。
- 未提供 `en_us` 等多语言文件。
- 物理结构**调整控制**（把左/右键与 Tab 重映射为结构移动/旋转）正在开发中：该能力需要你**主手真的持有** `simulated:creative_physics_staff`（Aeronautics 的服务端硬性要求手持该法杖才处理结构操作），详见发布说明。

---

## 许可证

[CC0 1.0](LICENSE) — 公共领域贡献，可随意使用、修改、再分发，无需署名。
