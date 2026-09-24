package com.phantomstaff.mixin;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在原版热栏右侧渲染虚拟第10格（物理法杖图标 + 选中高亮）。
 * 注意 1.21.1 中 HUD 主类为 net.minecraft.client.gui.Gui（非 InGameHud），
 * renderHotbar(GuiGraphics, float)，GuiGraphics 已提供 guiWidth()/renderItem()/renderOutline()。
 */
@Mixin(Gui.class)
public class InGameHudMixin {

    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void phantomstaff$renderVirtualSlot(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return;
        if (!PhantomStaffConfig.RENDER_VIRTUAL_SLOT.getBooleanValue()) return;
        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) return;

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int baseX = screenWidth / 2 - 91;
        int baseY = screenHeight - 22;

        // 第10格：紧挨原版第9格右侧
        int slotX = baseX + 9 * 20;
        graphics.fill(slotX, baseY, slotX + 20, baseY + 20, 0x80000000);
        graphics.renderItem(PhantomStaff.PHANTOM_STAFF, slotX + 3, baseY + 3);

        // 选中虚拟第10格时绘制白色高亮框
        if (mc.player.getInventory().selected == 9) {
            graphics.renderOutline(slotX - 1, baseY - 1, 22, 22, 0xFFFFFFFF);
        }
    }
}
