package com.phantomstaff;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.util.StringUtils;
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

        list.add(new ConfigOptionWrapper(StringUtils.translate("phantomstaff.config.category.target_line")));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_COLOR));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_WIDTH));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_MAX_DIST));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_THROUGH_WALLS));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_EDGE_ARROWS));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TARGET_LINE_HIGHLIGHT));

        list.add(new ConfigOptionWrapper(StringUtils.translate("phantomstaff.config.category.hotkeys")));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.OPEN_CONFIG_GUI));
        list.add(new ConfigOptionWrapper(PhantomStaffConfig.TOGGLE_TARGET_LINE));

        return list;
    }
}
