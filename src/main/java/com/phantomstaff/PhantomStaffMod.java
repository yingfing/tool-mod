package com.phantomstaff;

import com.phantomstaff.input.SlotKeyHandler;
import com.phantomstaff.render.TargetLineRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 纯客户端模组：整个模组仅在客户端加载，不注册任何服务端逻辑。
 */
@Mod(value = "phantomstaff", dist = Dist.CLIENT)
public class PhantomStaffMod {

    public static final String MOD_ID = "phantomstaff";
    private static final Logger LOG = LoggerFactory.getLogger("phantomstaff");

    public PhantomStaffMod(IEventBus modEventBus) {
        // 初始化 MaFgLib 配置系统：注册配置处理器 + 热键 + 读取配置文件
        PhantomStaffConfig.getInstance().init();
        // 注册世界渲染事件：追踪红线
        NeoForge.EVENT_BUS.register(TargetLineRenderer.class);
        // 数字键 0 直接选中虚拟第10格
        NeoForge.EVENT_BUS.register(SlotKeyHandler.class);
        // 加入服务器时的 Aeronautics 兼容性检测与提示
        NeoForge.EVENT_BUS.register(ServerCompatCheck.class);
        // 启动期兼容性自检与日志
        logCompatibility();
    }

    /**
     * 启动日志：打印加载信息，并检测 Create Aeronautics 物理法杖物品是否存在。
     * 该物品不存在（未装 Aeronautics 或版本不匹配）时给出明确告警，
     * 避免用户误以为模组失效却无从排查。
     */
    private void logCompatibility() {
        LOG.info("[PhantomStaff] Phantom Staff Slot 已加载（纯客户端 NeoForge 模组）");
        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) {
            LOG.warn("[PhantomStaff] 未检测到 Create Aeronautics 的物理法杖物品，虚拟第10格不会生效。"
                    + "请确认已安装旧版(2026-05-13 之前、服务端无 validateWorthyness 校验)的 Create Aeronautics。");
        } else {
            LOG.info("[PhantomStaff] 已识别物理法杖物品 simulated:creative_physics_staff，虚拟槽位可用。");
        }
    }
}
