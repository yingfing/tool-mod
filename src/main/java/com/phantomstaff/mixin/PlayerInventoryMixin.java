package com.phantomstaff.mixin;

import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让客户端认为热栏有 10 格（原版为 9）。
 * getHotbarSize() 是静态方法，无法拿到玩家对象，用 FMLEnvironment.dist 确保仅客户端修改，
 * 避免服务端物品栏错位。
 */
@Mixin(Inventory.class)
public class PlayerInventoryMixin {

    @Inject(method = "getHotbarSize", at = @At("HEAD"), cancellable = true)
    private static void phantomstaff$expandHotbar(CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist == Dist.CLIENT && PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) {
            cir.setReturnValue(10);
        }
    }
}
