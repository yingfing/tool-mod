package com.phantomstaff;

import com.phantomstaff.render.TargetLineRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 纯客户端模组：整个模组仅在客户端加载，不注册任何服务端逻辑。
 */
@Mod(value = "phantomstaff", dist = Dist.CLIENT)
public class PhantomStaffMod {

    public static final String MOD_ID = "phantomstaff";

    public PhantomStaffMod(IEventBus modEventBus) {
        // 初始化 MaFgLib 配置系统：注册配置处理器 + 热键 + 读取配置文件
        PhantomStaffConfig.getInstance().init();
        // 注册世界渲染事件：追踪红线
        NeoForge.EVENT_BUS.register(TargetLineRenderer.class);
    }
}
