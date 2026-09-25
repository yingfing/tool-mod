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

    /** 热键回调：把法杖换到手上 / 换回。 */
    public boolean toggle() {
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof LocalPlayer player)) return true;

        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) {
            PhantomStaff.LOG.warn("[PhantomStaff] 未检测到物理法杖物品，无法切换。请确认已安装 Create Aeronautics。");
            return true;
        }

        Inventory inv = player.getInventory();

        // 已处于切换状态：若手上的法杖还在就还原，否则（被刷新掉了）直接重新切换
        if (swapped) {
            if (ItemStack.isSameItemSameComponents(inv.getItem(inv.selected), PhantomStaff.PHANTOM_STAFF)) {
                release(player, inv);
                return true;
            }
            reset();
        }

        acquire(player, inv);
        return true;
    }

    /** 把法杖换到手上。 */
    private void acquire(final LocalPlayer player, final Inventory inv) {
        final ItemStack target = PhantomStaff.PHANTOM_STAFF;
        final int sel = inv.selected;

        // 主手或副手已经是法杖，无需切换
        if (ItemStack.isSameItemSameComponents(inv.getItem(sel), target)
                || ItemStack.isSameItemSameComponents(player.getOffhandItem(), target)) {
            return;
        }

        preSwapMainHand = inv.getItem(sel).copy();
        handSlot = sel;

        // 1) 快捷栏其他格里有法杖 —— 发真实包切换选中格
        for (int i = 0; i < 9; i++) {
            if (i == sel) continue;
            if (!ItemStack.isSameItemSameComponents(inv.getItem(i), target)) continue;
            player.connection.send(new ServerboundSetCarriedItemPacket(i));
            inv.selected = i;
            originSlot = ORIGIN_HOTBAR_SELECT;
            swapped = true;
            PhantomStaff.LOG.info("[PhantomStaff] 已切换到快捷栏槽位 {} 的物理法杖", i);
            return;
        }

        // 2) 主背包里有法杖 —— 发真实包交换到主手
        for (int i = 9; i < inv.items.size(); i++) {
            if (!ItemStack.isSameItemSameComponents(inv.getItem(i), target)) continue;
            final ItemStack targetItem = inv.getItem(i);
            sendSwap(player, i, sel, i, targetItem, preSwapMainHand);
            inv.setItem(sel, targetItem);
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
            // 背包交换 —— 换回原位
            final ItemStack currentMainHand = inv.getItem(inv.selected);
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
