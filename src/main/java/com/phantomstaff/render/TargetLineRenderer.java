package com.phantomstaff.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
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
import net.minecraft.client.DeltaTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * 按下开关热键后，从玩家视线射出一道红线，追踪指向的 Create Aeronautics 物理结构。
 * 优先锁定类名含 physics/contraption 的实体（Aeronautics 物理载具），没对准实体时退化为对准方块。
 *
 * 增强：
 * - 关闭深度测试，红线穿透地形/方块始终可见（目标在地下也能看见）
 * - 三层绘制：黑色外描边 + 红色叠加发光 + 亮红核心，远距离也醒目
 * - 锁定到物理结构时，在准星上方显示距离文字提示
 */
public class TargetLineRenderer {

    /** 由配置里的开关热键切换 */
    public static boolean enabled = false;

    /** 本帧是否锁定到了物理结构（供 HUD 文字提示使用） */
    public static boolean foundPhysics = false;

    /** 本帧目标距离（米/方块），供 HUD 文字提示使用 */
    public static double lastDistance = 0.0;

    // ===== 自定义线渲染类型：关闭深度测试 -> 穿透地形可见 =====

    private static RenderType makeLineType(String name, double width,
                                           RenderStateShard.TransparencyStateShard transparency) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                .setCullState(RenderStateShard.NO_CULL)
                .setTransparencyState(transparency)
                .createCompositeState(false);
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, state);
    }

    /** 黑色外描边（最宽，置于最底层） */
    private static final RenderType LINE_OUTLINE =
            makeLineType("ps_target_outline", 6.0, RenderStateShard.NO_TRANSPARENCY);
    /** 红色叠加发光层（中等宽度，加色混合产生发光感） */
    private static final RenderType LINE_GLOW =
            makeLineType("ps_target_glow", 4.0, RenderStateShard.ADDITIVE_TRANSPARENCY);
    /** 亮红核心线（最细、最亮） */
    private static final RenderType LINE_CORE =
            makeLineType("ps_target_core", 2.0, RenderStateShard.NO_TRANSPARENCY);

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled) return;
        // 默认本帧未锁定物理结构；命中后下方会置 true
        foundPhysics = false;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null || mc.screen != null) return;

        // getPartialTick() 返回 vanilla DeltaTracker，渲染用的部分刻度需取 float
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partialTick);

        double maxDist = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_MAX_DIST.getDoubleValue();
        TargetInfo info = findTarget(mc, player, eye, partialTick, maxDist);
        if (info == null) return;

        lastDistance = eye.distanceTo(info.pos);
        if (info.isPhysics) foundPhysics = true;

        Vec3 camPos = event.getCamera().getPosition();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        Vec3 dir = info.pos.subtract(eye).normalize();

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        drawLine(buffers, pose, eye, info.pos, LINE_OUTLINE, 0, 0, 0, 220, dir);
        drawLine(buffers, pose, eye, info.pos, LINE_GLOW, 255, 30, 30, 90, dir);
        drawLine(buffers, pose, eye, info.pos, LINE_CORE, 255, 20, 20, 255, dir);
        buffers.endBatch();

        pose.popPose();
    }

    /** HUD 文字提示：锁定到物理结构时，在准星上方显示距离 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled || !foundPhysics) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        String text = "已锁定物理结构   距离 " + String.format(Locale.US, "%.1f", lastDistance) + " m";

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        int x = (sw - tw) / 2;
        int y = sh / 2 - 36;
        g.drawString(mc.font, text, x, y, 0xFF66FF66, true);
    }

    private static void drawLine(MultiBufferSource.BufferSource buffers, PoseStack pose,
                                 Vec3 from, Vec3 to, RenderType type,
                                 int r, int g, int b, int a, Vec3 dir) {
        VertexConsumer vc = buffers.getBuffer(type);
        vc.addVertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, a)
                .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);
        vc.addVertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, a)
                .setNormal(pose.last(), (float) dir.x, (float) dir.y, (float) dir.z);
    }

    /** 优先找视线方向上的物理实体，其次找方块 */
    private static TargetInfo findTarget(Minecraft mc, Player player, Vec3 eye, float partialTick, double maxDist) {
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(maxDist));

        // 实体搜索盒：玩家视线方向延伸
        AABB searchBox = player.getBoundingBox()
                .expandTowards(look.scale(maxDist))
                .inflate(2.0);

        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player, eye, end, searchBox,
                e -> !e.isSpectator() && e.isPickable() && isPhysicsEntity(e),
                maxDist * maxDist);

        if (entityHit != null) {
            Entity e = entityHit.getEntity();
            // 用实体平滑位置 + 包围盒中心，线始终贴在结构中心
            Vec3 pos = e.getPosition(partialTick);
            Vec3 center = new Vec3(pos.x, pos.y + e.getBbHeight() / 2.0, pos.z);
            return new TargetInfo(center, true);
        }

        // 兜底：方块视线命中
        BlockHitResult blockHit = player.level().clip(
                new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            return new TargetInfo(blockHit.getLocation(), false);
        }
        return null;
    }

    /** 字符串匹配 Aeronautics 物理实体类名，避免编译期硬依赖 */
    private static boolean isPhysicsEntity(Entity e) {
        String name = e.getClass().getName().toLowerCase();
        return name.contains("physics") || name.contains("contraption");
    }

    /** 一次命中结果：目标点与是否为物理实体 */
    private static final class TargetInfo {
        final Vec3 pos;
        final boolean isPhysics;

        TargetInfo(Vec3 pos, boolean isPhysics) {
            this.pos = pos;
            this.isPhysics = isPhysics;
        }
    }
}
