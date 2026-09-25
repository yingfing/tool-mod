package com.phantomstaff;

import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * phantomstaff 的游戏内配置菜单（MaFgLib 风格 masa 配置界面）。
 * 通过 OPEN_CONFIG_GUI 热键打开，也会自动注册进 MaFgLib 的模组切换下拉框。
 */
public class PhantomStaffGuiConfig extends GuiConfigsBase {

    public PhantomStaffGuiConfig(@Nullable Screen parent) {
        super(10, 20, PhantomStaffMod.MOD_ID, parent, "phantomstaff.config.title");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<ConfigOptionWrapper> list = new ArrayList<>();

        list.add(new ConfigOptionWrapper("通用设置"));
        for (ConfigBoolean option : List.of(
                PhantomStaffConfig.ENABLE_PHANTOM_SLOT,
                PhantomStaffConfig.ALLOW_SCROLL_TO_SLOT_10,
                PhantomStaffConfig.RENDER_VIRTUAL_SLOT)) {
            list.add(new ConfigOptionWrapper(option));
        }

        list.add(new ConfigOptionWrapper("快捷键"));
        for (ConfigHotkey hotkey : List.of(
                PhantomStaffConfig.OPEN_CONFIG_GUI,
                PhantomStaffConfig.TOGGLE_TARGET_LINE)) {
            list.add(new ConfigOptionWrapper(hotkey));
        }

        return list;
    }
}
