package com.phantomstaff;

import com.phantomstaff.render.TargetLineRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.IConfigScreenFactory;
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
        // 在 Forge 的「Mods」界面为本体注册配置入口，保证始终能打开配置（不依赖热键是否绑定）
        ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class,
                () -> (mc, parent) -> new PhantomStaffGuiConfig(parent));
        // 启动期兼容性自检与日志
        logCompatibility();
    }

    /**
     * 启动日志：打印加载信息，并检测 Create Aeronautics 物理法杖物品是否存在。
     * 该物品不存在（未装 Aeronautics 或版本不匹配）时给出明确告警，
     * 避免用户误以为模组失效却无从排查。
     */
    private void logCompatibility() {
        LOG.info("[PhantomStaff] Phantom Staff 已加载（纯客户端 NeoForge 模组）");
        if (PhantomStaff.PHANTOM_STAFF.isEmpty()) {
            LOG.warn("[PhantomStaff] 未检测到 Create Aeronautics 的物理法杖物品 simulated:creative_physics_staff，"
                    + "针对物理结构的高亮/红线/边缘箭头不会生效。请确认已安装 Create Aeronautics。");
        } else {
            LOG.info("[PhantomStaff] 已识别物理法杖物品 simulated:creative_physics_staff，物理结构辅助功能可用。");
        }
    }
}
