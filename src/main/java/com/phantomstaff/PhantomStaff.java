package com.phantomstaff;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 物理法杖物品引用（Create Aeronautics 的 simulated:creative_physics_staff）。
 * 仅用于在启动时检测 Aeronautics 是否已安装；本模组不再伪造/替换该物品。
 * 物品ID 已核实：Create Aeronautics 的 modid 为 simulated，完整 ID 为 simulated:creative_physics_staff。
 */
public final class PhantomStaff {
    public static final Logger LOG = LoggerFactory.getLogger("phantomstaff");

    public static final ResourceLocation STAFF_ID =
            ResourceLocation.fromNamespaceAndPath("simulated", "creative_physics_staff");

    public static final ItemStack PHANTOM_STAFF;

    static {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(STAFF_ID));
        if (stack.isEmpty()) {
            // 物品不存在（未装 Aeronautics 或版本不匹配）时记日志，后续 Mixin 会直接跳过
            LOG.warn("[PhantomStaff] 未找到物品 {}，请确认已安装 Create Aeronautics", STAFF_ID);
        }
        PHANTOM_STAFF = stack;
    }

    private PhantomStaff() {}
}
