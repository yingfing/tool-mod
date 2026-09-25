package com.phantomstaff.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.DeltaTracker;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * 按下开关热键后，绘制指向 Create Aeronautics 物理结构的红线。
 *
 * 行为：只要客户端世界里已加载（即服务器数据包已下发）的物理结构实体，
 * 一律画一条线指向它——不要求瞄准、不要求在视锥内、不要求被渲染。
 * 没有物理结构时，退化为指向视线命中的方块。
 *
 * 增强：
 * - 关闭深度测试时，红线穿透地形/方块始终可见（目标在地下或隔墙也能看见）
 * - 三层绘制：黑色外描边 + 红色叠加发光 + 亮红核心，远距离也醒目
 * - 锁定到物理结构时，在准星上方显示最近一个的距离文字提示
 */
public class TargetLineRenderer {

    /** 由配置里的开关热键切换 */
    public static boolean enabled = false;

    /** 本帧是否锁定到了至少一个物理结构（供 HUD 文字提示使用） */
    public static boolean foundPhysics = false;

    /** 本帧最近物理结构的距离（米/方块），供 HUD 文字提示使用 */
    public static double lastDistance = 0.0;

    // ===== 自定义线渲染类型：由配置决定颜色/线宽/是否穿透地形 =====

    private static RenderType lineOutline;
    private static RenderType lineGlow;
    private static RenderType lineCore;
    private static int cachedColor = Integer.MIN_VALUE;
    private static double cachedWidth = -1.0;
    private static boolean cachedThroughWalls;

    private static RenderType makeLineType(String name, double width,
                                           RenderStateShard.TransparencyStateShard transparency,
                                           RenderStateShard.DepthTestStateShard depthTest) {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.of(width)))
                .setDepthTestState(depthTest)
                .setCullState(RenderStateShard.NO_CULL)
                .setTransparencyState(transparency)
                .createCompositeState(false);
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, state);
    }

    /** 读取配置并在必要时重建渲染层。颜色/线宽/穿透开关任一变化都会触发重建。 */
    private static void refreshLineTypes() {
        int color = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_COLOR.getIntegerValue();
        double width = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_WIDTH.getDoubleValue();
        boolean throughWalls = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_THROUGH_WALLS.getBooleanValue();

        if (lineCore != null && color == cachedColor && width == cachedWidth && throughWalls == cachedThroughWalls) {
            return;
        }
        cachedColor = color;
        cachedWidth = width;
        cachedThroughWalls = throughWalls;

        RenderStateShard.DepthTestStateShard depthTest = throughWalls
                ? RenderStateShard.NO_DEPTH_TEST
                : RenderStateShard.LEQUAL_DEPTH_TEST;

        // 黑色外描边（最宽，置于最底层）
        lineOutline = makeLineType("ps_target_outline", width + 4.0,
                RenderStateShard.NO_TRANSPARENCY, depthTest);
        // 红色叠加发光层（中等宽度，加色混合产生发光感）
        lineGlow = makeLineType("ps_target_glow", width + 2.0,
                RenderStateShard.ADDITIVE_TRANSPARENCY, depthTest);
        // 亮红核心线（最细、最亮）
        lineCore = makeLineType("ps_target_core", width,
                RenderStateShard.NO_TRANSPARENCY, depthTest);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 只在最后一个阶段画一次，避免每个阶段重复绘制导致发光层叠加过亮
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!enabled) return;
        // 本帧默认未锁定物理结构；命中后下方会置 true
        foundPhysics = false;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = (ClientLevel) mc.level;
        if (player == null || level == null || mc.screen != null) return;

        // getPartialTick() 返回 vanilla DeltaTracker，渲染用的部分刻度需取 float
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 eye = player.getEyePosition(partialTick);

        refreshLineTypes();

        fi.dy.masa.malilib.util.Color4f c = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_COLOR.getColor();
        int cr = toByte(c.r);
        int cg = toByte(c.g);
        int cb = toByte(c.b);
        int ca = toByte(c.a);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        Vec3 camPos = event.getCamera().getPosition();
        pose.translate(-camPos.x, -camPos.y, -camPos.z);

        // 遍历本维度内所有已加载实体（即服务器数据包已下发的）：
        // 凡是物理结构（类名含 physics/contraption）一律画一条线指向它，
        // 不要求它在视锥内、被渲染、或被瞄准。
        double maxDist = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_MAX_DIST.getDoubleValue();
        double nearest = Double.MAX_VALUE;
        boolean anyPhysics = false;
        for (Entity e : level.entitiesForRendering()) {
            if (!isPhysicsEntity(e)) continue;
            Vec3 pos = e.getPosition(partialTick);
            Vec3 center = new Vec3(pos.x, pos.y + e.getBbHeight() / 2.0, pos.z);
            double d = eye.distanceTo(center);
            if (d < nearest) nearest = d;
            anyPhysics = true;
            drawLineTo(buffers, pose, eye, center, cr, cg, cb, ca);
        }

        if (anyPhysics) {
            foundPhysics = true;
            lastDistance = nearest;
        } else {
            // 没有物理结构时，退回“瞄准方块”线（保留原行为）
            Vec3 look = player.getViewVector(partialTick);
            Vec3 end = eye.add(look.scale(maxDist));
            BlockHitResult blockHit = player.level().clip(
                    new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                drawLineTo(buffers, pose, eye, blockHit.getLocation(), cr, cg, cb, ca);
            }
        }

        buffers.endBatch();
        pose.popPose();
    }

    private static int toByte(float value) {
        int v = Math.round(value * 255.0f);
        return Math.max(0, Math.min(255, v));
    }

    /** HUD 文字提示：锁定到物理结构时，在准星上方显示最近一个的距离 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!enabled || !foundPhysics) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        String text = fi.dy.masa.malilib.util.StringUtils.translate(
                "phantomstaff.hud.locked_distance", String.format(Locale.US, "%.1f", lastDistance));

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int tw = mc.font.width(text);
        int x = (sw - tw) / 2;
        int y = sh / 2 - 36;
        g.drawString(mc.font, text, x, y, 0xFF66FF66, true);
    }

    private static void drawLineTo(MultiBufferSource.BufferSource buffers, PoseStack pose,
                                   Vec3 from, Vec3 to, int r, int g, int b, int a) {
        Vec3 dir = to.subtract(from).normalize();
        drawLine(buffers, pose, from, to, lineOutline, 0, 0, 0, 220, dir);
        drawLine(buffers, pose, from, to, lineGlow, r, g, b, Math.min(90, a), dir);
        drawLine(buffers, pose, from, to, lineCore, r, g, b, a, dir);
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

    /** 字符串匹配 Aeronautics 物理实体类名，避免编译期硬依赖 */
    private static boolean isPhysicsEntity(Entity e) {
        String name = e.getClass().getName().toLowerCase();
        return name.contains("physics") || name.contains("contraption");
    }
}
