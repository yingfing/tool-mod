package com.phantomstaff.aeronautics;

import com.phantomstaff.PhantomStaff;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 运行时反射桥：调用 Create Aeronautics（modid {@code simulated}）与 Sable 物理库的内部 API，
 * 复刻物理法杖的「锁定 / 拖拽」发包逻辑。
 *
 * <p>为什么用反射？纯客户端模组无法在编译期依赖 Aeronautics 的内部类
 * （{@code Sable.HELPER}、{@code PhysicsStaffActionPacket} 等），但它们运行时必然随
 * Aeronautics 加载（本模组已在 neoforge.mods.toml 声明依赖 {@code simulated}）。
 * 反射让本模组零构建改动即可复用其发包能力；Aeronautics 类缺失或版本不符时仅告警、不崩溃。</p>
 *
 * <p><b>重要约束：</b>服务端 {@code PhysicsStaffServerHandler} 会校验玩家主手/副手手持
 * {@code physics_staff}，因此所有动作仅在玩家真的手持物理法杖时才会被服务端接受——
 * 这正是用户选择「只克隆真发包（必须手持法杖）」所对应的硬限制。</p>
 */
public final class AeronauticsBridge {

    private static final AeronauticsBridge INSTANCE = new AeronauticsBridge();

    public static AeronauticsBridge get() {
        return INSTANCE;
    }

    private static final double RANGE = 128.0;

    private boolean available = false;
    private String unavailableReason = "";

    // ===== 反射缓存 =====
    private Object sableHelper;                       // Sable.HELPER 实例
    private Method getContainingClient;               // HELPER.getContainingClient(Vec3): SubLevel
    private Method subGetUniqueId;                    // SubLevel.getUniqueId(): UUID
    private Method subLogicalPose;                    // SubLevel.logicalPose(): LogicalPose
    private Method poseOrientation;                   // LogicalPose.orientation(): Quaterniondc
    private Method poseTransform;                     // LogicalPose.transformPosition(Vec3): Vec3
    private Method jomlToJOML;                        // JOMLConversion.toJOML(Vec3): Vector3d
    private Method jomlToMojang;                      // JOMLConversion.toMojang(Vector3dc): Vec3
    private Method jomlAtCenterOf;                    // JOMLConversion.atCenterOf(BlockPos): Vector3d
    private Method staffIsHolding;                    // PhysicsStaffItem.isHolding(Player): boolean
    private Object actionLock;
    private Object actionStartDrag;
    private Object actionStopDrag;
    private java.lang.reflect.Constructor<?> actionPacketCtor;  // (PhysicsStaffAction, UUID, Vector3d)
    private java.lang.reflect.Constructor<?> dragPacketCtor;   // (UUID, Vector3dc, Vector3dc, Quaterniondc)

    private AeronauticsBridge() {
        resolve();
    }

    private void resolve() {
        try {
            Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
            java.lang.reflect.Field helperField = sable.getDeclaredField("HELPER");
            helperField.setAccessible(true);
            sableHelper = helperField.get(null);
            getContainingClient = sableHelper.getClass().getMethod("getContainingClient", Vec3.class);

            Class<?> subLevel = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
            subGetUniqueId = subLevel.getMethod("getUniqueId");
            subLogicalPose = subLevel.getMethod("logicalPose");

            Class<?> joml = Class.forName("dev.ryanhcode.sable.companion.math.JOMLConversion");
            jomlToJOML = joml.getMethod("toJOML", Vec3.class);
            jomlToMojang = joml.getMethod("toMojang", Class.forName("org.joml.Vector3dc"));
            jomlAtCenterOf = joml.getMethod("atCenterOf", BlockPos.class);

            Class<?> staffItem = Class.forName("dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem");
            staffIsHolding = staffItem.getMethod("isHolding", Player.class);

            Class<?> action = Class.forName("dev.simulated_team.simulated.content.physics_staff.PhysicsStaffAction");
            actionLock = action.getField("LOCK").get(null);
            actionStartDrag = action.getField("START_DRAG").get(null);
            actionStopDrag = action.getField("STOP_DRAG").get(null);

            Class<?> v3d = Class.forName("org.joml.Vector3d");
            Class<?> v3dc = Class.forName("org.joml.Vector3dc");
            Class<?> qdc = Class.forName("org.joml.Quaterniondc");
            Class<?> actionPacket = Class.forName(
                    "dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffActionPacket");
            actionPacketCtor = actionPacket.getConstructor(action, UUID.class, v3d);
            Class<?> dragPacket = Class.forName(
                    "dev.simulated_team.simulated.network.packets.physics_staff.PhysicsStaffDragPacket");
            dragPacketCtor = dragPacket.getConstructor(UUID.class, v3dc, v3dc, qdc);

            available = true;
            PhantomStaff.LOG.info("[PhantomStaff] Aeronautics 反射桥初始化成功，调整模式可用。");
        } catch (Throwable t) {
            available = false;
            unavailableReason = t.getClass().getSimpleName() + ": " + t.getMessage();
            PhantomStaff.LOG.warn("[PhantomStaff] 未能初始化 Aeronautics 反射桥（调整模式不可用）：{}", unavailableReason);
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }

    /** 玩家是否手持物理法杖（main/off hand 之一）。 */
    public boolean isHoldingStaff(Player player) {
        if (!available || player == null) return false;
        try {
            return (boolean) staffIsHolding.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 射线检测当前瞄准的物理结构。
     * @return 含 subLevel UUID、命中位置、本地锚点、朝向与初始拖拽距离的命中信息；未命中返回 null
     */
    public SubLevelHit raycast(Player player) {
        if (!available || player == null) return null;
        try {
            HitResult hit = player.pick(RANGE, 1.0f, false);
            if (!(hit instanceof BlockHitResult bhr) || hit.getType() == HitResult.Type.MISS) return null;
            Vec3 hitLocation = hit.getLocation();
            Object sub = getContainingClient.invoke(sableHelper, hitLocation);
            if (sub == null) return null;

            UUID uuid = (UUID) subGetUniqueId.invoke(sub);
            Object pose = subLogicalPose.invoke(sub);
            if (poseOrientation == null) {
                poseOrientation = pose.getClass().getMethod("orientation");
                poseTransform = pose.getClass().getMethod("transformPosition", Vec3.class);
            }
            Object quatObj = poseOrientation.invoke(pose);
            Quaterniond orientation = new Quaterniond((org.joml.Quaterniondc) quatObj);
            Vector3d localAnchor = (Vector3d) jomlAtCenterOf.invoke(null, bhr.getBlockPos());
            Vec3 anchorWorld = (Vec3) poseTransform.invoke(pose, jomlToMojang.invoke(null, localAnchor));
            double distance = Math.clamp(player.getEyePosition().distanceTo(anchorWorld), 2.0, RANGE);

            return new SubLevelHit(uuid, hitLocation, localAnchor, orientation, distance);
        } catch (Throwable t) {
            PhantomStaff.LOG.warn("[PhantomStaff] 射线检测物理结构失败：{}", t.getMessage());
            return null;
        }
    }

    /** 锁定/解锁指定 subLevel（坐标为命中世界坐标）。 */
    public void sendLock(UUID subLevel, Vec3 hitLocation) {
        if (!available) return;
        try {
            Object payload = actionPacketCtor.newInstance(actionLock, subLevel, jomlToJOML.invoke(null, hitLocation));
            send(payload);
        } catch (Throwable t) {
            PhantomStaff.LOG.warn("[PhantomStaff] 发送 LOCK 包失败：{}", t.getMessage());
        }
    }

    /** 停止拖拽指定 subLevel（坐标为本地锚点）。 */
    public void sendStopDrag(UUID subLevel, Vector3d localAnchor) {
        if (!available) return;
        try {
            Object payload = actionPacketCtor.newInstance(actionStopDrag, subLevel, localAnchor);
            send(payload);
        } catch (Throwable t) {
            PhantomStaff.LOG.warn("[PhantomStaff] 发送 STOP_DRAG 包失败：{}", t.getMessage());
        }
    }

    /** 拖拽：把 subLevel 拉向「视线方向 × distance」的目标点。 */
    public void sendDrag(UUID subLevel, Vec3 goalMojang, Vector3d localAnchor, Quaterniond orientation) {
        if (!available) return;
        try {
            Object payload = dragPacketCtor.newInstance(
                    subLevel, jomlToJOML.invoke(null, goalMojang), localAnchor, orientation);
            send(payload);
        } catch (Throwable t) {
            PhantomStaff.LOG.warn("[PhantomStaff] 发送 DRAG 包失败：{}", t.getMessage());
        }
    }

    private void send(Object payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundCustomPayloadPacket((CustomPacketPayload) payload));
    }

    /** 一次射线命中的物理结构信息。 */
    public record SubLevelHit(UUID uuid, Vec3 hitLocation, Vector3d localAnchor,
                              Quaterniond orientation, double distance) {
    }
}
