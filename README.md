# Phantom Staff Slot（虚拟物理法杖槽位）

纯客户端 Minecraft 模组：在快捷栏旁渲染一个虚拟第 10 格，选中后客户端「以为」你手持创造模式物理法杖，从而触发 Create Aeronautics 的物理装置交互。

## 环境
- Minecraft 1.21.1
- NeoForge 21.1.219+
- 依赖：Create 6.0.10+、Create Aeronautics（modid: `simulated`）、**MaFgLib 0.4.3+**（客户端必需，提供配置菜单）
- **服务器前提**：旧版 Aeronautics（2026-05-13 之前，服务端无 `validateWorthyness` 手持校验）。新版会因服务端校验把客户端踢下线（"Invalid packet"）。

## 使用
1. 滚轮切到热栏最右侧的第 10 格（法杖图标，白色高亮即选中）。
2. 右键点击物理结构，即等同于手持物理法杖开始拖拽；左键停止/锁定。
3. 切回普通槽位即恢复正常手持物品。
4. **调配置**：在游戏内按你自己绑定的「open_config_gui」快捷键打开配置菜单（首次默认未绑定，可在 MaFgLib 的模组配置总菜单里找到 Phantom Staff 设置快捷键）。可开关：虚拟槽总开关、滚轮可达第10格、HUD 渲染第10格。配置保存在 `config/phantomstaff.json`。
5. **追踪红线**：绑定「toggle_target_line」快捷键后，按一下开启、再按关闭。开启时从你眼睛射出一道红线，自动锁定视线指到的 Aeronautics 物理结构（载具移动线会跟着走）；没对准实体时线指向你看的方块。

## 原理（4 个客户端 Mixin）
| Mixin | 作用 |
|---|---|
| `PlayerMixin` | 选中第 10 格时，客户端 `getMainHandItem()` 返回 `simulated:creative_physics_staff` |
| `PlayerInventoryMixin` | 客户端热栏大小 9→10（仅 `Dist.CLIENT`） |
| `MouseHandlerMixin` | 滚轮允许在 0-9 循环；直接写字段，不发 `ServerboundSetCarriedItemPacket` |
| `InGameHudMixin` | HUD 渲染第 10 格图标与选中高亮 |

物品 ID 已对照 Aeronautics 源码核实：`SimItems.REGISTRATE.item("creative_physics_staff", ...)`，即 `simulated:creative_physics_staff`。

## 构建
GitHub Action 自动构建（推送到本分支即触发），产物 jar 在 Actions 页面的 Artifacts 里下载。
