package com.phantomstaff.input;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * 数字键 0 直接选中虚拟第10格。
 * 原版热栏数字键只绑定 1-9（index 0-8），0 键空闲。
 * 这里通过 NeoForge 的 InputEvent.Key 捕获 0 键按下，直接写客户端 inventory.selected=9，
 * 绕过 LocalPlayer.setSelectedSlot()，因此不会向服务器发送 ServerboundSetCarriedItemPacket。
 */
public final class SlotKeyHandler {

    private SlotKeyHandler() {}

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        if (event.getKey() != GLFW.GLFW_KEY_0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return;
        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) return;

        mc.player.getInventory().selected = 9;
    }
}
