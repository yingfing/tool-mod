package com.phantomstaff.adjust;

import com.phantomstaff.PhantomStaff;
import com.phantomstaff.PhantomStaffConfig;
import com.phantomstaff.aeronautics.AeronauticsBridge;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.UUID;

/**
 * 调整模式：手持物理法杖时，用一组热键把物理结构当作「可调整对象」来锁定 / 拖拽 / 旋转。
 *
 * <p>复刻 Aeronautics 物理法杖的发包逻辑（见 {@link AeronauticsBridge}），但控制键由本模组接管，
 * <b>不与法杖原生左右键冲突</b>——法杖原生靠手持即生效，本模组则用独立热键触发，
 * 避免重复发包。所有动作仍只在玩家真正手持 {@code physics_staff} 时被服务端接受。</p>
 *
 * <p>控制（均可在配置里改键，默认值见 {@link PhantomStaffConfig}）：
 * <ul>
 *   <li>{@code adjust_mode}（默认 B）：进入 / 退出调整模式</li>
 *   <li>{@code adjust_lock}（默认 L）：锁定 / 解锁当前瞄准的物理结构</li>
 *   <li>{@code adjust_drag}（默认 K）：开始 / 停止拖拽当前瞄准的物理结构</li>
 *   <li>{@code adjust_rotate}（默认 TAB）：把拖拽中的结构绕竖直轴旋转 15°</li>
 * </ul>
 * 拖拽中结构会持续跟随你的视线方向（固定初始距离），按 K 停止。</p>
 */
public class AdjustmentModeHandler {

    private static final AdjustmentModeHandler INSTANCE = new AdjustmentModeHandler();
    public static AdjustmentModeHandler get() {
        return INSTANCE;
    }

    private static final double MIN_DIST = 2.0;
    private static final double MAX_DIST = 128.0;
    private static final double ROTATE_STEP = Math.toRadians(15.0);

    private boolean modeActive = false;
    private DragState drag = null;
    private int frame = 0;

    /** 当前拖拽会话状态。 */
    private static final class DragState {
        final UUID uuid;
        final Vector3d localAnchor;
        final Quaterniond orientation;
        double distance;

        DragState(UUID uuid, Vector3d localAnchor, Quaterniond orientation, double distance) {
            this.uuid = uuid;
            this.localAnchor = localAnchor;
            this.orientation = orientation;
            this.distance = distance;
        }
    }

    private AdjustmentModeHandler() {}

    /** 把调整模式相关的热键回调注册到 MaFgLib 输入系统（在配置 init 之后调用）。 */
    public void registerHotkeys() {
        PhantomStaffConfig.ADJUST_MODE.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            if (!PhantomStaffConfig.ENABLE_ADJUST_MODE.getBooleanValue()) return true;
            modeActive = !modeActive;
            if (!modeActive) stopDrag(true);
            PhantomStaff.LOG.info("[PhantomStaff] 调整模式 {}", modeActive ? "开启" : "关闭");
            return true;
        });

        PhantomStaffConfig.ADJUST_LOCK.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            if (!active()) return true;
            AeronauticsBridge b = AeronauticsBridge.get();
            Player p = Minecraft.getInstance().player;
            AeronauticsBridge.SubLevelHit hit = b.raycast(p);
            if (hit == null) return true;
            // 若正在拖拽该结构，锁定时顺手停拖（与法杖原生行为一致）
            if (drag != null && drag.uuid.equals(hit.uuid())) {
                b.sendLock(hit.uuid(), hit.hitLocation());
                stopDrag(true);
            } else {
                b.sendLock(hit.uuid(), hit.hitLocation());
            }
            return true;
        });

        PhantomStaffConfig.ADJUST_DRAG.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            if (!active()) return true;
            if (drag != null) {
                stopDrag(true);
                return true;
            }
            AeronauticsBridge b = AeronauticsBridge.get();
            Player p = Minecraft.getInstance().player;
            AeronauticsBridge.SubLevelHit hit = b.raycast(p);
            if (hit == null) return true;
            drag = new DragState(hit.uuid(), hit.localAnchor(), hit.orientation(), hit.distance());
            return true;
        });

        PhantomStaffConfig.ADJUST_ROTATE.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            if (!active() || drag == null) return true;
            drag.orientation.rotateLocalY(ROTATE_STEP);
            sendDragNow();
            return true;
        });
    }

    /** 是否满足「功能开启 + 模式开启 + 手持法杖」。 */
    private boolean active() {
        if (!PhantomStaffConfig.ENABLE_ADJUST_MODE.getBooleanValue() || !modeActive) return false;
        Player p = Minecraft.getInstance().player;
        if (p == null) return false;
        if (!AeronauticsBridge.get().isHoldingStaff(p)) {
            return false;
        }
        return true;
    }

    private void stopDrag(boolean sendPacket) {
        if (drag != null && sendPacket) {
            AeronauticsBridge.get().sendStopDrag(drag.uuid, drag.localAnchor);
        }
        drag = null;
    }

    /** 立即按当前视线方向发送一次拖拽包（用于旋转/距离变化后即时生效）。 */
    private void sendDragNow() {
        if (drag == null) return;
        Player p = Minecraft.getInstance().player;
        if (p == null) return;
        Vec3 goal = p.getLookAngle().scale(drag.distance);
        AeronauticsBridge.get().sendDrag(drag.uuid, goal, drag.localAnchor, drag.orientation);
    }

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (!active()) {
            if (drag != null) drag = null;
            return;
        }
        // 约 20Hz 发送拖拽包（每 3 帧一次），与法杖原生频率一致
        if (drag != null && (frame++ % 3) == 0) {
            sendDragNow();
        }
    }

    /** 调整模式状态 HUD（屏幕左上角）。 */
    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!PhantomStaffConfig.ENABLE_ADJUST_MODE.getBooleanValue() || !modeActive) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        GuiGraphics g = event.getGuiGraphics();
        boolean holding = AeronauticsBridge.get().isHoldingStaff(mc.player);
        int y = 8;
        g.drawString(mc.font, "调整模式 [B]  手持法杖: " + (holding ? "是" : "否"), 8, y, 0xFF66FF66, true);
        y += mc.font.lineHeight + 4;
        if (drag != null) {
            g.drawString(mc.font, "拖拽中 [K 停止]  [TAB 旋转]", 8, y, 0xFFFFD866, true);
        } else {
            g.drawString(mc.font, "L 锁定  K 拖拽  TAB 旋转(拖拽中)", 8, y, 0xFFAAAAAA, true);
        }
    }
}
