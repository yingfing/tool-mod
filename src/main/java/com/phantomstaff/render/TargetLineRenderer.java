package com.phantomstaff.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * 按下绑定按键后，从玩家视线射出一道红线，追踪指向的 Create Aeronautics 物理结构。
 * 优先锁定类名含 physics/contraption 的实体（Aeronautics 物理载具），
 * 没对准实体时退化为对准方块。
 */
public class TargetLineRenderer {

    /** 由配置里的开关热键切换 */
    public static boolean enabled = false;

    private static final double MAX_DIST = 64.0;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.screen != null) return;

        float partialTick = event.getPartialTick();
        Vec3 eye = player.getEyePosition(partialTick);

        Vec3 target = findTarget(mc, player, eye, partialTick);
        if (target == null) return;

        Vec3 camPos = event.getCamera().getPosition();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        Vec3 dir = target.subtract(eye).normalize();

        RenderSystem.lineWidth(2.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        vc.addVertex(pose.last().pose(),
                (float) eye.x, (float) eye.y, (float) eye.z)
            .setColor(255, 20, 20, 255)
            .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);

        vc.addVertex(pose.last().pose(),
                (float) target.x, (float) target.y, (float) target.z)
            .setColor(255, 20, 20, 255)
            .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** 优先找视线方向上的物理实体，其次找方块 */
    private static Vec3 findTarget(Minecraft mc, Player player, Vec3 eye, float partialTick) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(MAX_DIST));

        // 实体搜索盒：玩家视线方向延伸
        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(MAX_DIST))
                .inflate(2.0);

        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                e -> !e.isSpectator() && e.isPickable() && isPhysicsEntity(e),
                MAX_DIST * MAX_DIST);

        if (entityHit != null) {
            Entity e = entityHit.getEntity();
            // 用实体平滑位置 + 包围盒中心，线始终贴在结构中心
            Vec3 pos = e.getLerpedPos(partialTick);
            return new Vec3(pos.x, pos.y + e.getBbHeight() / 2.0, pos.z);
        }

        // 兜底：方块视线命中
        BlockHitResult blockHit = player.level.clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            return blockHit.getLocation();
        }
        return null;
    }

    /** 字符串匹配 Aeronautics 物理实体类名，避免编译期硬依赖 */
    private static boolean isPhysicsEntity(Entity e) {
        String name = e.getClass().getName().toLowerCase();
        return name.contains("physics") || name.contains("contraption");
    }
}
