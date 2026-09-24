package com.phantomstaff.mixin;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 选中虚拟第10格时，让客户端 getMainHandItem() 返回物理法杖。
 * Create Aeronautics 的客户端 PhysicsStaffClientHandler 通过
 * player.getMainHandItem() instanceof PhysicsStaffItem 判断是否手持法杖，
 * 伪造后即会触发其交互包（旧版服务器不校验手持物品）。
 */
@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "getMainHandItem", at = @At("HEAD"), cancellable = true)
    private void phantomstaff$fakeStaffInHand(CallbackInfoReturnable<ItemStack> cir) {
        Player self = (Player) (Object) this;

        if (!self.level().isClientSide()) return;        // 仅客户端生效
        if (!PhantomStaffConfig.ENABLE_PHANTOM_SLOT.getBooleanValue()) return;
        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) return; // 物品不存在则不替换
        if (self.getInventory().selected != 9) return;    // 仅选中虚拟第10格
        cir.setReturnValue(PhantomStaff.PHANTOM_STAFF);
    }
}
