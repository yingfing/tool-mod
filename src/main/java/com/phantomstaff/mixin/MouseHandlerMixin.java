package com.phantomstaff.mixin;

import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 允许滚轮在 0-9 之间循环选中（原版 0-8）。
 * 直接写 inv.selected 字段，绕过 LocalPlayer.setSelectedSlot()，
 * 因此不会向服务器发送 ServerboundSetCarriedItemPacket，避免服务端 selected=9 越界。
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void phantomstaff$allowVirtualSlot(long window, double xOffset, double yOffset, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return;
        if (!PhantomStaffConfig.ALLOW_SCROLL_TO_SLOT_10.getBooleanValue()) return;

        Inventory inv = mc.player.getInventory();

        if (yOffset > 0) {
            inv.selected = (inv.selected + 1) % 10;
            ci.cancel();
        } else if (yOffset < 0) {
            inv.selected = (inv.selected - 1 + 10) % 10;
            ci.cancel();
        }
    }
}
