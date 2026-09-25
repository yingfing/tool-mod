# Phantom Staff Slot（虚拟物理法杖槽位）

纯客户端 Minecraft 模组：在快捷栏旁渲染一个虚拟第 10 格，选中后客户端「以为」你手持创造模式物理法杖，从而触发 Create Aeronautics 的物理装置交互。实际上你手上并没有拿那根法杖。

> 本质上是利用**服务端不校验手持物品**的漏洞实现交互，纯客户端、不向服务器上报任何额外数据。

---

## ⚠️ 重要兼容性警告（必读）

本模组依赖一个**已修复的漏洞**：旧版 Create Aeronautics（**2026-05-13 之前**，服务端没有 `validateWorthyness` 手持校验）不会检查你手里到底拿没拿法杖。

- ✅ **能用**：服务端装的是旧版 Aeronautics（无 `validateWorthyness` 校验）。
- ❌ **会被踢下线**：服务端装的是新版 Aeronautics，连上后会因 `Invalid packet` 把客户端踢掉。

如果你的服务器是新版 Aeronautics，**本模组不可用**，请勿在生存/公共服务器上尝试，避免在别人服务器上被封。

---

## 环境 / 依赖矩阵

| 组件 | 版本要求 | 说明 |
|---|---|---|
| Minecraft | `1.21.1` | 固定版本 |
| NeoForge | `21.1.219+` | `loaderVersion="[21.1.219,)"` |
| Create | `6.0.10+` | 必需，客户端 |
| Create Aeronautics | **旧版（2026-05-13 之前的构建）** | modid `simulated`，服务端校验缺失才可用 |
| MaFgLib | `0.4.3+` | **客户端必需**，提供游戏内配置菜单（仅编译期依赖，游戏内需另行安装）|

> 模组本身在 `META-INF/neoforge.mods.toml` 中已将上述四项声明为 `required`（side=CLIENT）。
> 若缺少 MaFgLib，游戏内配置菜单将无法打开。

---

## 安装

1. 安装匹配版本的 NeoForge 与 Create。
2. 安装**旧版** Create Aeronautics（见上方兼容性警告）。
3. 安装 MaFgLib `0.4.3+`（客户端）。
4. 把本模组的 jar 放入 `.minecraft/mods/` 并启动游戏。

---

## 使用

1. 滚轮切到热栏最右侧的**第 10 格**（法杖图标，白色高亮即选中）。
2. 右键点击物理结构，即等同于手持物理法杖开始拖拽；左键停止/锁定。
3. 切回普通槽位即恢复正常手持物品。
4. **调配置**：在游戏内按你自己绑定的 `open_config_gui` 快捷键打开配置菜单（首次默认未绑定，可在 MaFgLib 的模组配置总菜单里找到 Phantom Staff 设置该快捷键）。可开关：
   - 虚拟槽总开关 `enable_phantom_slot`
   - 滚轮可达第 10 格 `allow_scroll_to_slot_10`
   - HUD 渲染第 10 格 `render_virtual_slot`
   - 配置保存在 `config/phantomstaff.json`。
5. **追踪红线**：绑定 `toggle_target_line` 快捷键后，按一下开启、再按关闭。开启时从你眼睛向**所有已加载**（服务器数据包已下发）的 Aeronautics 物理结构各画一道红线——**不要求瞄准、不要求在视锥内、不要求被渲染**；红线穿透地形始终可见（目标在地下或隔墙也能看见）。每个物理结构还会在包围盒上画**发光轮廓框高亮**，便于远距离/小目标定位。屏幕外、或在相机背后的结构，会在**屏幕边缘画一个指向它的箭头**，保证不漏标；准星上方显示最近结构的距离。没有物理结构时，线退化为指向视线命中的方块。

   红线相关子开关（均可在配置菜单调整，默认开）：
   - `target_line_edge_arrows`：屏幕边缘指向箭头（关掉后只保留 3D 红线）
   - `target_line_highlight`：物理结构包围盒高亮框（关掉后不画轮廓框）

---

## 原理（4 个客户端 Mixin）

| Mixin | 作用 |
|---|---|
| `PlayerMixin` | 选中第 10 格时，客户端 `getMainHandItem()` 返回 `simulated:creative_physics_staff` |
| `PlayerInventoryMixin` | 客户端热栏大小 9→10（仅 `Dist.CLIENT`） |
| `MouseHandlerMixin` | 滚轮允许在 0-9 循环；直接写字段，不发 `ServerboundSetCarriedItemPacket` |
| `InGameHudMixin` | HUD 渲染第 10 格图标与选中高亮 |

物品 ID 已对照 Aeronautics 源码核实：`SiMitems.REGISTRATE.item("creative_physics_staff", ...)`，即 `simulated:creative_physics_staff`。

---

## 构建

GitHub Actions 自动构建：推送到 `1.21.1` 分支即触发，产物 jar 在 Actions 页面的 Artifacts 中下载。
打 `v*` 开头的 Tag（如 `v1.0.0`）会额外自动创建 GitHub Release 并附上 jar。

本地构建：

```bash
./gradlew build          # 产物位于 build/libs/*.jar
./gradlew runClient      # 本地带模组的客户端调试（需先装好依赖模组）
```

---

## 已知限制 / Roadmap

- 仅支持 Minecraft 1.21.1 / NeoForge 21.1.x，未做其他版本适配。
- 强依赖「服务端不校验手持物品」的漏洞，新版 Aeronautics 下不可用（见兼容性警告）。
- 追踪红线的颜色、线宽、最大距离、是否穿透地形、边缘箭头开关、高亮框开关均已暴露到 MaFgLib 配置（`target_line_color` / `target_line_width` / `target_line_max_distance` / `target_line_through_walls` / `target_line_edge_arrows` / `target_line_highlight`）。
- 未提供 `en_us` 等多语言文件。
- 更多计划见 [ROADMAP.md](ROADMAP.md)。

---

## 许可证

[CC0 1.0](LICENSE) — 公共领域贡献，可随意使用、修改、再分发，无需署名。
