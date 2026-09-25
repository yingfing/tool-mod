package com.phantomstaff.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
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
 * - 屏幕边缘箭头：屏幕外或被遮挡在背后的物理结构，在屏幕边缘画一个指向它的箭头，
 *   保证“不漏标”——视线里看不到的，也能从边缘知道方位
 */
public class TargetLineRenderer {

    /**
     * 追踪红线总开关。
     *
     * <p><b>必须每帧从配置实时读取</b>，不能用静态布尔缓存：MaLiLib 的配置界面里改值并保存后
     * 不会回调任何监听器，缓存字段会永远停留在启动时的值，导致「界面里打开了却没反应」。
     * 每帧读一次配置项代价可忽略，换来的是界面/热键/配置文件三种途径改动都立即生效。</p>
     */
    private static boolean isEnabled() {
        return com.phantomstaff.PhantomStaffConfig.TARGET_LINE_ENABLE.getBooleanValue();
    }

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
        if (!isEnabled()) return;
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
        boolean highlight = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_HIGHLIGHT.getBooleanValue();
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
            // 高亮：在实体包围盒上画发光轮廓框（轻微外扩避免与模型 z-fighting）
            if (highlight) {
                drawBoxOutline(buffers, pose, e.getBoundingBox().inflate(0.1), lineCore, cr, cg, cb, ca);
            }
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

    /** HUD：屏幕边缘指向箭头（屏幕外/背后的物理结构）+ 准星上方距离文字 */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!isEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        ClientLevel level = (ClientLevel) mc.level;
        if (player == null || level == null || mc.screen != null) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        GuiGraphics g = event.getGuiGraphics();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // 屏幕边缘箭头：指向所有屏幕外/背后的物理结构，保证“不漏标”（可由配置开关关闭）
        if (com.phantomstaff.PhantomStaffConfig.TARGET_LINE_EDGE_ARROWS.getBooleanValue()) {
            drawEdgeArrows(mc, level, partialTick, sw, sh, g);
        }

        // 距离文字提示：锁定到物理结构时，在准星上方显示最近一个的距离
        if (foundPhysics) {
            String text = fi.dy.masa.malilib.util.StringUtils.translate(
                    "phantomstaff.hud.locked_distance", String.format(Locale.US, "%.1f", lastDistance));
            int tw = mc.font.width(text);
            int x = (sw - tw) / 2;
            int y = sh / 2 - 36;
            g.drawString(mc.font, text, x, y, 0xFF66FF66, true);
        }
    }

    /**
     * 遍历所有已加载物理结构，对“屏幕外”或“在相机背后”的实体在屏幕边缘画一个指向箭头。
     * 屏幕内（视锥内且未被边缘裁剪）的实体已有 3D 红线指向，无需箭头。
     */
    private static void drawEdgeArrows(Minecraft mc, ClientLevel level, float partialTick,
                                       int sw, int sh, GuiGraphics g) {
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();
        // 相机基向量（返回 Vector3f）：left 指向屏幕左、up 指向上、forward 指向前方
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();
        Vector3f forward = camera.getLookVector();

        // 垂直 FOV（来自设置），水平 FOV 由宽高比推出，用于判断实体是否在屏幕内
        float fov = (float) mc.options.fov().get();
        double tanHalfY = Math.tan(Math.toRadians(fov / 2.0));
        double tanHalfX = tanHalfY * ((double) sw / (double) sh);

        fi.dy.masa.malilib.util.Color4f c = com.phantomstaff.PhantomStaffConfig.TARGET_LINE_COLOR.getColor();
        int r = toByte(c.r), gg = toByte(c.g), b = toByte(c.b);
        int a = Math.max(200, toByte(c.a));
        int color = (a << 24) | (r << 16) | (gg << 8) | b;

        for (Entity e : level.entitiesForRendering()) {
            if (!isPhysicsEntity(e)) continue;
            Vec3 pos = e.getPosition(partialTick);
            Vec3 center = new Vec3(pos.x, pos.y + e.getBbHeight() / 2.0, pos.z);
            Vec3 v = center.subtract(camPos);
            // 手动点积（left/up/forward 是 Vector3f，v 是 Vec3）
            double Xv = v.x * left.x + v.y * left.y + v.z * left.z;
            double Yv = v.x * up.x + v.y * up.y + v.z * up.z;
            double Zv = v.x * forward.x + v.y * forward.y + v.z * forward.z;

            // 绘制坐标系：x 右为正、y 下为正（与 GuiGraphics 一致）
            double dirX, dirY;
            boolean onScreen;
            if (Zv <= 0.0) {
                // 在相机背后：一定在屏幕外
                onScreen = false;
                double len = Math.hypot(-Xv, -Yv);
                if (len < 1e-6) continue;
                dirX = -Xv / len;
                dirY = -Yv / len;
            } else {
                double ndcX = (-Xv / Zv) / tanHalfX;
                double ndcY = (Yv / Zv) / tanHalfY;
                onScreen = Math.abs(ndcX) <= 1.0 && Math.abs(ndcY) <= 1.0;
                if (onScreen) continue; // 屏幕内已有 3D 红线
                double len = Math.hypot(ndcX, ndcY);
                dirX = ndcX / len;
                dirY = -ndcY / len; // ndcY 上为正，绘制 y 下为正 → 取反
            }

            // 把方向映射到屏幕边缘（矩形内缩 margin），沿该方向停在边界上
            double margin = 26.0;
            double sx = (dirX != 0) ? (sw / 2.0 - margin) / Math.abs(dirX) : Double.MAX_VALUE;
            double sy = (dirY != 0) ? (sh / 2.0 - margin) / Math.abs(dirY) : Double.MAX_VALUE;
            double scale = Math.min(sx, sy);
            double ex = sw / 2.0 + dirX * scale;
            double ey = sh / 2.0 + dirY * scale;

            drawEdgeArrow(g, mc, ex, ey, Math.atan2(dirY, dirX), color);
        }
    }

    /** 8 向箭头字形（屏幕绘制坐标系：x 右正、y 下正，角度顺时针） */
    private static final String[] EDGE_ARROWS = {"→", "↘", "↓", "↙", "←", "↖", "↑", "↗"};

    /** 在 (cx, cy) 处画一个指向 ang 方向（x 右正、y 下正）的方向箭头字形 */
    private static void drawEdgeArrow(GuiGraphics g, Minecraft mc, double cx, double cy,
                                      double ang, int color) {
        int idx = (int) Math.round(Math.toDegrees(ang) / 45.0);
        idx = ((idx % 8) + 8) % 8;
        String glyph = EDGE_ARROWS[idx];
        int tw = mc.font.width(glyph);
        int th = mc.font.lineHeight;
        int dx = (int) Math.round(cx) - tw / 2;
        int dy = (int) Math.round(cy) - th / 2;
        // 先画深色描边衬托，再画彩色箭头，保证在亮背景下也清晰
        g.drawString(mc.font, glyph, dx + 1, dy + 1, 0xFF000000, false);
        g.drawString(mc.font, glyph, dx, dy, color, false);
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

    /** 画一段线（自动计算方向法线），供轮廓框复用 */
    private static void drawSeg(MultiBufferSource.BufferSource buffers, PoseStack pose,
                                Vec3 from, Vec3 to, RenderType type, int r, int g, int b, int a) {
        Vec3 dir = to.subtract(from).normalize();
        drawLine(buffers, pose, from, to, type, r, g, b, a, dir);
    }

    /** 在实体包围盒上画 12 条棱的发光轮廓框 */
    private static void drawBoxOutline(MultiBufferSource.BufferSource buffers, PoseStack pose,
                                       AABB box, RenderType type, int r, int g, int b, int a) {
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
        double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        Vec3 p000 = new Vec3(x0, y0, z0), p100 = new Vec3(x1, y0, z0);
        Vec3 p010 = new Vec3(x0, y1, z0), p110 = new Vec3(x1, y1, z0);
        Vec3 p001 = new Vec3(x0, y0, z1), p101 = new Vec3(x1, y0, z1);
        Vec3 p011 = new Vec3(x0, y1, z1), p111 = new Vec3(x1, y1, z1);
        // 底面
        drawSeg(buffers, pose, p000, p100, type, r, g, b, a);
        drawSeg(buffers, pose, p100, p110, type, r, g, b, a);
        drawSeg(buffers, pose, p110, p010, type, r, g, b, a);
        drawSeg(buffers, pose, p010, p000, type, r, g, b, a);
        // 顶面
        drawSeg(buffers, pose, p001, p101, type, r, g, b, a);
        drawSeg(buffers, pose, p101, p111, type, r, g, b, a);
        drawSeg(buffers, pose, p111, p011, type, r, g, b, a);
        drawSeg(buffers, pose, p011, p001, type, r, g, b, a);
        // 立柱
        drawSeg(buffers, pose, p000, p001, type, r, g, b, a);
        drawSeg(buffers, pose, p100, p101, type, r, g, b, a);
        drawSeg(buffers, pose, p010, p011, type, r, g, b, a);
        drawSeg(buffers, pose, p110, p111, type, r, g, b, a);
    }

    /**
     * 字符串匹配 Aeronautics 物理实体类名，避免编译期硬依赖。
     *
     * <p>两个修正：
     * <ol>
     *   <li>结果按实体类缓存（{@link ClassValue}）：原先每帧对每个实体都做一次
     *       {@code getName().toLowerCase()}——上百个实体 × 60 帧 = 每秒上万次字符串分配。</li>
     *   <li>显式排除 {@code com.simibubi.create} 包：Create 自己的载具类名就带
     *       {@code Contraption}，不排除的话每个机械装置/矿车都会被误判成物理结构画上红线。</li>
     * </ol>
     */
    private static final ClassValue<Boolean> PHYSICS_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName().toLowerCase(Locale.ROOT);
            if (name.startsWith("com.simibubi.create")) return Boolean.FALSE;
            return name.contains("physics") || name.contains("contraption");
        }
    };

    private static boolean isPhysicsEntity(Entity e) {
        return PHYSICS_CACHE.get(e.getClass());
    }
}
