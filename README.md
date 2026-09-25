# Phantom Staff

纯客户端 Minecraft（NeoForge 1.21.1）辅助模组，专门针对 **Create Aeronautics**（modid `simulated`）的物理结构提供可视化与操作辅助：

- **高亮**：给每个已加载的物理结构（飞行载具/物理方块）画发光轮廓框，远距离/小目标也能定位。
- **追踪红线**：从你的眼睛向所有已加载的物理结构画红线——不要求瞄准、不要求进视锥、不要求被渲染；可穿透地形。
- **屏幕边缘箭头**：屏幕外或背后的结构在屏幕边缘画指向箭头，绝不漏标。
- **调整模式（克隆物理法杖）**：手持 `simulated:creative_physics_staff` 时，用热键把物理结构当作可调整对象——锁定 / 拖拽跟随视线 / 旋转，复刻 Aeronautics 物理法杖的发包逻辑（详见下方「使用」）。
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

3. **调整模式（需手持物理法杖）**：在配置菜单中确保 `enable_adjust_mode` 开启（默认开），并在「调整模式」分类下查看/修改热键。手持 `simulated:creative_physics_staff` 后：
   - 按 `B` 进入/退出调整模式（屏幕左上角有状态提示）。
   - `L`：锁定 / 解锁当前准星瞄准的物理结构（固定约束）。
   - `K`：开始 / 停止拖拽当前瞄准的结构——拖拽中结构会持续跟随你的视线方向。
   - `TAB`：拖拽中把结构绕竖直轴旋转 15°（可连按多次）。
   - 所有动作都复用 Aeronautics 物理法杖的真实网络包；**服务端只在你真的手持物理法杖时才执行**，未持法杖时按键无效。

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
- 调整模式的「锁定 / 拖拽 / 旋转」复用 Aeronautics 物理法杖的真实网络包，**必须主手真正手持 `simulated:creative_physics_staff`** 才被服务端接受（Aeronautics 服务端硬性校验）；未持法杖时热键无效。
- 调整模式当前用独立热键（`B`/`L`/`K`/`TAB`）而非接管鼠标左右键，以避免与法杖原生左右键冲突；若需要左/右键映射，后续可加。
- 调整模式通过运行时反射调用 Aeronautics / Sable 内部 API 实现，零构建依赖；若 Aeronautics 版本变动导致类名/方法签名变化，启动日志会提示「调整模式不可用」，其余功能不受影响。

---

## 许可证

[CC0 1.0](LICENSE) — 公共领域贡献，可随意使用、修改、再分发，无需署名。
