package com.phantomstaff;

import fi.dy.masa.malilib.util.StringUtils;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkChannel;
import net.neoforged.neoforge.network.registration.NetworkPayloadSetup;

import java.util.Locale;
import java.util.Map;

/**
 * 加入服务器时的 Create Aeronautics 兼容性提示。
 *
 * NeoForge 21.1 不再向客户端下发完整的服务端模组列表，客户端能拿到的只有
 * 双方协商后的网络 payload 通道（含各自协商版本）。这里退而求其次：
 * 在登录完成时扫描 PLAY 阶段协商出的通道，若存在 modid 为 simulated（Aeronautics）
 * 的通道，则认为服务器装有 Aeronautics，弹出一次兼容性提醒。
 *
 * 该检测是启发式的：仅当 Aeronautics 注册了 NeoForge 网络通道时才会命中。
 */
public final class ServerCompatCheck {

    private static final String AERONAUTICS_NAMESPACE = "simulated";

    private static boolean warned = false;

    private ServerCompatCheck() {}

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (warned) return;
        if (!PhantomStaffConfig.WARN_ON_AERONAUTICS_SERVER.getBooleanValue()) return;

        Connection connection = event.getConnection();
        if (connection == null) return;

        try {
            NetworkPayloadSetup setup = ChannelAttributes.getPayloadSetup(connection);
            if (setup == null) return;

            Map<ResourceLocation, NetworkChannel> channels = setup.getChannels(ConnectionProtocol.PLAY);
            if (channels == null || channels.isEmpty()) return;

            String version = null;
            for (Map.Entry<ResourceLocation, NetworkChannel> entry : channels.entrySet()) {
                if (AERONAUTICS_NAMESPACE.equals(entry.getKey().getNamespace())) {
                    version = entry.getValue().chosenVersion();
                    break;
                }
            }
            if (version == null) return;

            warned = true;
            PhantomStaff.LOG.warn("[PhantomStaff] 服务器端检测到 Create Aeronautics(simulated)，协商网络版本 {}。"
                    + "本模组的虚拟第10格仅在旧版(服务端无 validateWorthyness 校验)可用，新版会把客户端踢下线。", version);

            LocalPlayer player = event.getPlayer();
            if (player != null) {
                String message = String.format(Locale.US,
                        StringUtils.translate("phantomstaff.msg.aeronautics_warning"), version);
                player.displayClientMessage(Component.literal(message), false);
            }
        } catch (Exception e) {
            PhantomStaff.LOG.debug("[PhantomStaff] 服务器 Aeronautics 兼容性检测失败", e);
        }
    }
}
