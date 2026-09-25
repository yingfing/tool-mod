# Phantom Staff

纯客户端 Minecraft（NeoForge 1.21.1）辅助模组，专门针对 **Create Aeronautics**（modid `simulated`）的物理结构提供可视化与操作辅助：

- **高亮**：给每个已加载的物理结构（飞行载具/物理方块）画发光轮廓框，远距离/小目标也能定位。
- **追踪红线**：从你的眼睛向所有已加载的物理结构画红线——不要求瞄准、不要求进视锥、不要求被渲染；可穿透地形。
- **屏幕边缘箭头**：屏幕外或背后的结构在屏幕边缘画指向箭头，绝不漏标。
- **幽灵法杖槽位（核心）**：按一下热键，把 `simulated:creative_physics_staff` 物理法杖「盖」进**当前选中的快捷栏槽位**，并一直保留，直到服务端刷新该槽位才自然消失。客户端此后认为自己手持法杖，可直接用 Aeronautics 原生的法杖操作（左右键 / Tab 等）调整物理结构。
- **配置入口**：在游戏内「Mods」界面点本模组 → Config，或按 `G` 键直接打开配置菜单。

> 本模组只做客户端层面改动：**不修改快捷栏选中格**（始终 0–8），**不向服务器上报任何伪造数据包**，因此不会被反作弊以「invalid hotbar」踢出。幽灵法杖是否真的能驱动服务端结构，取决于服务端是否仍然信任客户端（见下方「依赖的老 bug」）。

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

3. **幽灵法杖槽位**：在配置菜单中确保 `enable_phantom_slot` 开启（默认开），并在「幽灵法杖槽位」分类下查看/修改热键。
   - 按 `B`（默认）把法杖盖入**当前选中**的快捷栏槽位。
   - 盖入后法杖**一直保留在该槽位**，直到服务端下发同步包刷新该槽位（如拾取物品、切换物品、服务端更新背包）才消失——即「刷新前保留」，而不是「按住才有、松开就没」。
   - 再按一次 `B` 可主动取消（还原成盖入前的真实物品）。
   - 盖入后直接用 **Aeronautics 原生的法杖操作**（左右键 / Tab 等）即可调整物理结构，无需再盯着第 10 格。

### 依赖的老 bug

幽灵法杖槽位利用了**老版 Create Aeronautics 服务端「信任客户端」的老 bug**：客户端认为自己手持法杖后，Aeronautics 的客户端输入逻辑就按法杖处理并发出操作包，老服务端照单全收。

- **老版服务端**：幽灵法杖可正常驱动物理结构调整（这正是本项目的使用场景）。
- **新版服务端（已加服务端 `isHolding` 校验）**：服务端会重新校验玩家实际手持物品，幽灵法杖只在你自己的画面里生效，服务端不会执行调整动作。此时仍需真正手持 `simulated:creative_physics_staff`。

本模组不主动绕过任何校验；上述差异完全由服务端版本决定。

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
- 幽灵法杖槽位仅在**老版（信任客户端的）Aeronautics 服务端**上能真正驱动结构调整；新版服务端会重新校验手持物品，届时只在你自己画面里生效（见上方「依赖的老 bug」）。
- 幽灵法杖盖入后，若服务端刷新了该槽位（拾取物品、切换物品、服务端同步背包），法杖会消失，需重新按热键盖入。
- 未安装 Create Aeronautics 时启动日志会提示「未检测到物理法杖物品」，幽灵槽位不可用，其余功能不受影响。

---

## 许可证

[CC0 1.0](LICENSE) — 公共领域贡献，可随意使用、修改、再分发，无需署名。
