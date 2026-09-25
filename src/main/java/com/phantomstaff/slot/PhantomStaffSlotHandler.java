package com.phantomstaff.slot;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * 幽灵法杖槽位：按一下热键，把物理法杖「换」到手上；再按一下换回。
 *
 * <p><b>为什么发真实数据包，而不是直接改客户端物品栏？</b>
 * Create Aeronautics 服务端在处理法杖操作包时会校验 {@code validateWorthyness}——
 * 检查<b>服务端眼里</b>玩家主手/副手是否真的是物理法杖，不是就
 * {@code disconnect("Invalid packet")} 直接踢下线。
 * 纯客户端改物品栏骗不过服务端的权威状态，所以这里改用原版网络包真实切换，
 * 让服务端也认可你确实拿着法杖，左键固定 / 右键拖拽 / 滚轮 / Tab 才能全部生效。</p>
 *
 * <p>切换优先级（照 Create: Cyber Goggles 的 ItemSwapUtil 思路，但改为「按一下切换」而非「按住」）：
 * <ol>
 *   <li>快捷栏其他格有法杖 → 发 {@code ServerboundSetCarriedItemPacket} 切选中格（服务端认可）</li>
 *   <li>主背包有法杖 → 发 {@code ServerboundContainerClickPacket(SWAP)} 交换（服务端认可）</li>
 *   <li>都没有 → 本地生成（仅客户端，服务端不认可，会在游戏内警告）</li>
 * </ol>
 * 全部使用原版 Minecraft 网络包与 API，不反射任何 Aeronautics 内部类，零额外依赖。</p>
 */
public final class PhantomStaffSlotHandler {

    private static final PhantomStaffSlotHandler INSTANCE = new PhantomStaffSlotHandler();
    public static PhantomStaffSlotHandler get() {
        return INSTANCE;
    }

    /** 未处于切换状态 */
    private static final int ORIGIN_NONE = -1;
    /** 通过快捷栏选中切换（还原时切回原选中格） */
    private static final int ORIGIN_HOTBAR_SELECT = -2;
    /** 本地生成（还原时仅客户端写回） */
    private static final int ORIGIN_LOCAL_SPAWN = -3;

    private boolean swapped = false;
    /** 法杖的来源：{@link #ORIGIN_HOTBAR_SELECT} / {@link #ORIGIN_LOCAL_SPAWN} / &gt;=0 表示背包交换原点槽位 */
    private int originSlot = ORIGIN_NONE;
    /** 切换前选中的快捷栏格 */
    private int handSlot = -1;
    /** 切换前主手的物品，用于还原 */
    private ItemStack preSwapMainHand = ItemStack.EMPTY;

    private PhantomStaffSlotHandler() {}

    /** 热键回调：把法杖换到手上 / 换回。功能被配置关闭时返回 false，让按键正常透传。 */
    public boolean toggle() {
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) return false;

        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) {
            PhantomStaff.LOG.warn("[PhantomStaff] 未检测到物理法杖物品，无法切换。请确认已安装 Create Aeronautics。");
            player.displayClientMessage(Component.literal(
                    "[PhantomStaff] 未检测到 Create Aeronautics 的物理法杖物品，幽灵法杖槽位不可用。"), true);
            return true;
        }

        Inventory inv = player.getInventory();

        // 已处于切换状态：若手上的法杖还在就还原，否则（被刷新掉了）直接重新切换
        if (swapped) {
            if (isStaff(inv.getItem(inv.selected))) {
                release(player, inv);
                return true;
            }
            reset();
        }

        acquire(player, inv);
        return true;
    }

    /**
     * 判断一个物品是不是物理法杖。
     *
     * <p>只比物品本身（{@link ItemStack#isSameItem}），<b>不比组件</b>：服务端或 Aeronautics
     * 很可能往法杖上挂了 NBT / 数据组件（模式、耐久、自定义名等），用
     * {@code isSameItemSameComponents} 会因为一点无关差异就认不出来，
     * 结果明明背包里有法杖却走到「本地生成」分支 —— 服务端不认可，左键固定直接被踢。</p>
     */
    private static boolean isStaff(ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItem(stack, PhantomStaff.PHANTOM_STAFF);
    }

    /**
     * 当前打开的是不是玩家自己的物品栏（containerId 0）。
     *
     * <p>只有在这种情况下，槽位编号 9..35 才对应主背包、36..44 才对应快捷栏，
     * {@code ServerboundContainerClickPacket} 的 SWAP 才会按预期工作。
     * 打开着箱子/熔炉时编号指的是那个容器的格子，照背包编号发包会把容器里的物品
     * 换进快捷栏，造成物品错乱甚至丢失。</p>
     */
    private static boolean isPlayerInventoryOpen(final LocalPlayer player) {
        return player.containerMenu != null && player.containerMenu.containerId == 0;
    }

    /** 把法杖换到手上。 */
    private void acquire(final LocalPlayer player, final Inventory inv) {
        final ItemStack target = PhantomStaff.PHANTOM_STAFF;
        final int sel = inv.selected;

        // 主手或副手已经是法杖，无需切换。
        // 这里必须给提示：原先是静默 return，用户按了键什么都没发生，会以为模组坏了。
        if (isStaff(inv.getItem(sel)) || isStaff(player.getOffhandItem())) {
            player.displayClientMessage(Component.literal(
                    "[PhantomStaff] 手上已经是物理法杖，无需切换。"), true);
            return;
        }

        preSwapMainHand = inv.getItem(sel).copy();
        handSlot = sel;

        // 1) 快捷栏其他格里有法杖 —— 发真实包切换选中格
        for (int i = 0; i < 9; i++) {
            if (i == sel) continue;
            if (!isStaff(inv.getItem(i))) continue;
            player.connection.send(new ServerboundSetCarriedItemPacket(i));
            inv.selected = i;
            originSlot = ORIGIN_HOTBAR_SELECT;
            swapped = true;
            PhantomStaff.LOG.info("[PhantomStaff] 已切换到快捷栏槽位 {} 的物理法杖", i);
            return;
        }

        // 2) 主背包里有法杖 —— 发真实包交换到主手
        for (int i = 9; i < inv.items.size(); i++) {
            if (!isStaff(inv.getItem(i))) continue;
            // 打开着外部容器时不能交换（槽位编号含义完全不同）。
            // 这里拒绝并提示，而不是退化成本地生成 —— 本地生成服务端不认可，左键会被踢。
            if (!isPlayerInventoryOpen(player)) {
                player.displayClientMessage(Component.literal(
                        "[PhantomStaff] 法杖在主背包里，请先关闭当前容器界面再按切换键。"), true);
                return;
            }
            final ItemStack staffStack = inv.getItem(i).copy();
            // 交换后：背包槽 i = 原来手上的物品；手上 = 法杖
            sendSwap(player, i, sel, i, preSwapMainHand, staffStack);
            inv.setItem(sel, staffStack);
            inv.setItem(i, preSwapMainHand);
            originSlot = i;
            swapped = true;
            PhantomStaff.LOG.info("[PhantomStaff] 已从背包槽位 {} 交换出物理法杖", i);
            return;
        }

        // 3) 都没有 —— 本地生成（仅客户端，服务端不认可）
        inv.setItem(sel, target.copy());
        originSlot = ORIGIN_LOCAL_SPAWN;
        swapped = true;
        PhantomStaff.LOG.warn("[PhantomStaff] 背包中未找到物理法杖，已本地生成（仅客户端）。服务端不会认可，左键固定结构可能被踢下线。");
        player.displayClientMessage(Component.literal(
                "[PhantomStaff] 背包里没有物理法杖，当前为本地生成（服务端不认可），左键固定可能导致被踢。请在背包中放一把 creative_physics_staff。"),
                true);
    }

    /** 把法杖换回去。 */
    private void release(final LocalPlayer player, final Inventory inv) {
        if (originSlot == ORIGIN_HOTBAR_SELECT) {
            // 快捷栏选择 —— 切回原选中格
            inv.selected = handSlot;
            player.connection.send(new ServerboundSetCarriedItemPacket(handSlot));
        } else if (originSlot >= 0) {
            // 背包交换 —— 换回原位。
            // 必须从 handSlot（当初放法杖的那格）取，不能用 inv.selected：
            // 玩家中途自己滚轮换了格的话，当前选中格拿到的就不是法杖，交换语义会整个错乱。
            if (!isPlayerInventoryOpen(player)) {
                // 打开着外部容器时同样不能发交换包。只做客户端本地写回，
                // 并提示玩家关掉界面后再按一次，好让服务端也同步过去。
                inv.setItem(handSlot, preSwapMainHand);
                inv.setItem(originSlot, PhantomStaff.PHANTOM_STAFF.copy());
                reset();
                player.displayClientMessage(Component.literal(
                        "[PhantomStaff] 已本地换回；请关闭当前容器界面后再按一次切换键以同步服务端。"), true);
                return;
            }
            final ItemStack currentMainHand = inv.getItem(handSlot);
            sendSwap(player, originSlot, handSlot, originSlot, currentMainHand, preSwapMainHand);
            inv.setItem(handSlot, preSwapMainHand);
            inv.setItem(originSlot, currentMainHand);
        } else if (originSlot == ORIGIN_LOCAL_SPAWN) {
            // 本地生成 —— 仅客户端写回
            inv.setItem(handSlot, preSwapMainHand);
        }
        reset();
        PhantomStaff.LOG.info("[PhantomStaff] 已换回原物品");
    }

    /**
     * 发送容器交换包（与原版创造/生存背包交换一致），让服务端同步这次交换。
     * slot 参数含义照 Create: Cyber Goggles 的 ItemSwapUtil 实现。
     */
    private void sendSwap(final LocalPlayer player, final int slotNum, final int buttonNum,
                          final int originSlotId, final ItemStack toOrigin, final ItemStack toHand) {
        final Int2ObjectOpenHashMap<ItemStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(originSlotId, toOrigin);
        changedSlots.put(36 + handSlot, toHand);
        player.connection.send(new ServerboundContainerClickPacket(
                player.containerMenu.containerId,
                player.containerMenu.getStateId(),
                slotNum,
                buttonNum,
                ClickType.SWAP,
                ItemStack.EMPTY,
                changedSlots));
    }

    private void reset() {
        swapped = false;
        originSlot = ORIGIN_NONE;
        handSlot = -1;
        preSwapMainHand = ItemStack.EMPTY;
    }

    public boolean isSwapped() {
        return swapped;
    }
}
