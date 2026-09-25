package com.phantomstaff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import com.phantomstaff.render.TargetLineRenderer;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * phantomstaff 的全部可配置项（通过 MaFgLib 的配置菜单调整）。
 * 配置文件位于 游戏目录/config/phantomstaff.json。
 */
public final class PhantomStaffConfig implements IConfigHandler, IKeybindProvider {

    /** 总开关：关闭后虚拟槽位完全不出现 */
    public static final ConfigBoolean ENABLE_PHANTOM_SLOT =
            new ConfigBoolean("enable_phantom_slot", true,
                    "启用虚拟第10格（物理法杖槽位）");

    /** 允许滚轮滚到第10格 */
    public static final ConfigBoolean ALLOW_SCROLL_TO_SLOT_10 =
            new ConfigBoolean("allow_scroll_to_slot_10", true,
                    "允许鼠标滚轮循环选中第10格");

    /** 在热栏上渲染第10格图标 */
    public static final ConfigBoolean RENDER_VIRTUAL_SLOT =
            new ConfigBoolean("render_virtual_slot", true,
                    "在热栏上渲染第10格图标");

    /** 打开配置菜单的快捷键（默认未绑定，可在菜单里自己设） */
    public static final ConfigHotkey OPEN_CONFIG_GUI =
            new ConfigHotkey("open_config_gui", "",
                    "打开 phantomstaff 配置菜单");

    /** 开关追踪红线的快捷键（默认未绑定） */
    public static final ConfigHotkey TOGGLE_TARGET_LINE =
            new ConfigHotkey("toggle_target_line", "",
                    "开关追踪物理结构的红线");

    /** 红线最大追踪距离（方块）。调大可在更远处分辨并锁定你的物理载具 */
    public static final ConfigDouble TARGET_LINE_MAX_DIST =
            new ConfigDouble("target_line_max_distance", 256.0,
                    "红线最大追踪距离（方块）。调大可在更远处分辨并锁定你的物理载具");

    private static final List<ConfigBoolean> GENERIC_OPTIONS = new ArrayList<>();
    private static final List<ConfigHotkey> HOTKEYS = new ArrayList<>();

    static {
        GENERIC_OPTIONS.add(ENABLE_PHANTOM_SLOT);
        GENERIC_OPTIONS.add(ALLOW_SCROLL_TO_SLOT_10);
        GENERIC_OPTIONS.add(RENDER_VIRTUAL_SLOT);
        GENERIC_OPTIONS.add(TARGET_LINE_MAX_DIST);
        HOTKEYS.add(OPEN_CONFIG_GUI);
        HOTKEYS.add(TOGGLE_TARGET_LINE);
    }

    private static PhantomStaffConfig INSTANCE;

    public static PhantomStaffConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new PhantomStaffConfig();
        }
        return INSTANCE;
    }

    private PhantomStaffConfig() {
        // 打开配置菜单的热键回调（MaFgLib 0.4.x：回调挂在 IKeybind 上）
        OPEN_CONFIG_GUI.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new PhantomStaffGuiConfig(mc.screen)));
            return true;
        });

        // 开关追踪红线
        TOGGLE_TARGET_LINE.getKeybind().setCallback((KeyAction action, IKeybind key) -> {
            TargetLineRenderer.enabled = !TargetLineRenderer.enabled;
            return true;
        });
    }

    /** 在模组初始化时调用：注册配置处理器 + 热键提供者 */
    public void init() {
        ConfigManager.getInstance().registerConfigHandler(PhantomStaffMod.MOD_ID, this);
        InputEventHandler.getKeybindManager().registerKeybindProvider(this);
        this.load();
    }

    private Path getConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("phantomstaff.json");
    }

    @Override
    public void load() {
        Path file = getConfigFile();
        if (!Files.exists(file)) {
            this.save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            ConfigUtils.readConfigBase(root, "generic", GENERIC_OPTIONS);
            ConfigUtils.readHotkeys(root, "hotkeys", HOTKEYS);
        } catch (Exception e) {
            PhantomStaff.LOG.warn("[PhantomStaff] 读取配置失败，使用默认值", e);
        }
    }

    @Override
    public void save() {
        Path file = getConfigFile();
        try {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "generic", GENERIC_OPTIONS);
            ConfigUtils.writeHotkeys(root, "hotkeys", HOTKEYS);
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
            }
        } catch (Exception e) {
            PhantomStaff.LOG.warn("[PhantomStaff] 保存配置失败", e);
        }
    }

    // ===== IKeybindProvider：把热键交给 MaFgLib 的输入系统分发 =====

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (ConfigHotkey hotkey : HOTKEYS) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory("Phantom Staff", "phantomstaff", HOTKEYS);
    }
}
