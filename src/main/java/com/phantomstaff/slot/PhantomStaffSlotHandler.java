package com.phantomstaff.slot;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 幽灵法杖槽位：按一下热键，把物理法杖「盖」进当前选中的快捷栏槽位（纯客户端），
 * 之后一直保留，直到物品栏被服务端刷新（同步包覆盖该槽位）才自然消失。
 *
 * <p>这是模组的核心玩法，利用了老版 Aeronautics 服务端「信任客户端」的老 bug：
 * 客户端认为自己手持法杖后，Aeronautics 的客户端输入逻辑就按法杖处理，
 * 发出的操作包在老服务端上被照单全收。纯客户端改动，不会发出任何非法包，
 * 也不会把 {@code selected} 设成 9，因此不会被踢（invalid hotbar）。</p>
 *
 * <p>实现要点：不修改 {@code selected}（始终 0–8），只覆盖「当前选中槽位里是什么物品」的
 * 客户端认知。服务端下发的该槽位同步包会把真实物品写回，幽灵法杖随之消失——
 * 即用户所说的「刷新前保留」。再次按下热键时，通过比对槽位内容判断：
 * 法杖还在（未刷新）→ 取消并还原；法杖已被刷新掉 → 直接重新盖入。</p>
 */
public final class PhantomStaffSlotHandler {

    private static final PhantomStaffSlotHandler INSTANCE = new PhantomStaffSlotHandler();
    public static PhantomStaffSlotHandler get() {
        return INSTANCE;
    }

    /** 幽灵法杖当前是否处于盖入状态 */
    private boolean active = false;
    /** 被盖入法杖的槽位索引（0–8） */
    private int latchedSlot = -1;
    /** 盖入前该槽位的真实物品，用于取消时还原 */
    private ItemStack realItem = ItemStack.EMPTY;

    private PhantomStaffSlotHandler() {}

    /** 热键回调：盖入法杖 / 取消还原；若法杖已被物品栏刷新掉，则重新盖入。 */
    public boolean toggle() {
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return true;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return true;

        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) {
            PhantomStaff.LOG.warn("[PhantomStaff] 未检测到物理法杖物品，无法启用幽灵槽位。请确认已安装 Create Aeronautics。");
            return true;
        }

        Inventory inv = player.getInventory();

        // 已盖入且法杖仍在槽位（未被刷新）→ 取消并还原真实物品
        if (active && latchedSlot >= 0 && latchedSlot < inv.items.size()
                && ItemStack.matches(inv.getItem(latchedSlot), PhantomStaff.PHANTOM_STAFF)) {
            inv.setItem(latchedSlot, realItem);
            active = false;
            latchedSlot = -1;
            realItem = ItemStack.EMPTY;
            PhantomStaff.LOG.info("[PhantomStaff] 幽灵法杖已取消");
            return true;
        }

        // 其余情况（未盖入，或已被服务端刷新掉）→ 重新盖入当前选中槽位
        int sel = inv.selected;
        realItem = inv.getItem(sel).copy();
        latchedSlot = sel;
        inv.setItem(sel, PhantomStaff.PHANTOM_STAFF.copy());
        active = true;
        PhantomStaff.LOG.info("[PhantomStaff] 幽灵法杖已盖入槽位 {}", sel);
        return true;
    }

    public boolean isActive() {
        return active;
    }
}
