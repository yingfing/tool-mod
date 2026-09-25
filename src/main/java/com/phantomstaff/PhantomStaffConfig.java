package com.phantomstaff;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.config.options.ConfigDouble;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import com.phantomstaff.adjust.AdjustmentModeHandler;
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

    /** 红线核心颜色（ARGB） */
    public static final ConfigColor TARGET_LINE_COLOR =
            new ConfigColor("target_line_color", "#FFFF1414").apply("phantomstaff.config");

    /** 红线核心线宽 */
    public static final ConfigDouble TARGET_LINE_WIDTH =
            new ConfigDouble("target_line_width", 2.0, 0.5, 16.0).apply("phantomstaff.config");

    /** 红线最大追踪距离（方块）。调大可在更远处分辨并锁定你的物理载具 */
    public static final ConfigDouble TARGET_LINE_MAX_DIST =
            new ConfigDouble("target_line_max_distance", 256.0, 16.0, 4096.0).apply("phantomstaff.config");

    /** 红线是否常显（穿透地形）。关闭后被方块遮挡 */
    public static final ConfigBoolean TARGET_LINE_THROUGH_WALLS =
            new ConfigBoolean("target_line_through_walls", true).apply("phantomstaff.config");

    /** 屏幕边缘指向箭头：屏幕外/背后的物理结构在屏幕边缘画指向箭头。关闭后只保留 3D 红线 */
    public static final ConfigBoolean TARGET_LINE_EDGE_ARROWS =
            new ConfigBoolean("target_line_edge_arrows", true).apply("phantomstaff.config");

    /** 高亮物理结构：在其包围盒上画发光轮廓框，便于远距离/小目标定位 */
    public static final ConfigBoolean TARGET_LINE_HIGHLIGHT =
            new ConfigBoolean("target_line_highlight", true).apply("phantomstaff.config");

    /** 打开配置菜单的快捷键（默认 G 键；也可在菜单里改成别的） */
    public static final ConfigHotkey OPEN_CONFIG_GUI =
            new ConfigHotkey("open_config_gui", "G").apply("phantomstaff.config");

    /** 开关追踪红线的快捷键（默认未绑定） */
    public static final ConfigHotkey TOGGLE_TARGET_LINE =
            new ConfigHotkey("toggle_target_line", "").apply("phantomstaff.config");

    // ===== 调整模式（克隆 Aeronautics 物理法杖的锁定 / 拖拽 / 旋转发包，需手持法杖） =====

    /** 总开关：是否启用「调整模式」功能 */
    public static final ConfigBoolean ENABLE_ADJUST_MODE =
            new ConfigBoolean("enable_adjust_mode", true).apply("phantomstaff.config");

    /** 进入 / 退出调整模式的快捷键（默认 B） */
    public static final ConfigHotkey ADJUST_MODE =
            new ConfigHotkey("adjust_mode", "B").apply("phantomstaff.config");

    /** 锁定 / 解锁当前瞄准的物理结构（默认 L） */
    public static final ConfigHotkey ADJUST_LOCK =
            new ConfigHotkey("adjust_lock", "L").apply("phantomstaff.config");

    /** 开始 / 停止拖拽当前瞄准的物理结构（默认 K） */
    public static final ConfigHotkey ADJUST_DRAG =
            new ConfigHotkey("adjust_drag", "K").apply("phantomstaff.config");

    /** 拖拽中绕竖直轴旋转 15°（默认 TAB） */
    public static final ConfigHotkey ADJUST_ROTATE =
            new ConfigHotkey("adjust_rotate", "TAB").apply("phantomstaff.config");

    private static final List<IConfigBase> GENERIC_OPTIONS = new ArrayList<>();
    private static final List<ConfigHotkey> HOTKEYS = new ArrayList<>();

    static {
        GENERIC_OPTIONS.add(TARGET_LINE_COLOR);
        GENERIC_OPTIONS.add(TARGET_LINE_WIDTH);
        GENERIC_OPTIONS.add(TARGET_LINE_MAX_DIST);
        GENERIC_OPTIONS.add(TARGET_LINE_THROUGH_WALLS);
        GENERIC_OPTIONS.add(TARGET_LINE_EDGE_ARROWS);
        GENERIC_OPTIONS.add(TARGET_LINE_HIGHLIGHT);
        GENERIC_OPTIONS.add(ENABLE_ADJUST_MODE);

        HOTKEYS.add(OPEN_CONFIG_GUI);
        HOTKEYS.add(TOGGLE_TARGET_LINE);
        HOTKEYS.add(ADJUST_MODE);
        HOTKEYS.add(ADJUST_LOCK);
        HOTKEYS.add(ADJUST_DRAG);
        HOTKEYS.add(ADJUST_ROTATE);
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
            com.phantomstaff.render.TargetLineRenderer.enabled = !com.phantomstaff.render.TargetLineRenderer.enabled;
            return true;
        });

        // 调整模式相关热键回调统一注册（锁定/拖拽/旋转由 AdjustmentModeHandler 处理）
        AdjustmentModeHandler.get().registerHotkeys();
    }

    /** 在模组初始化时调用：注册配置处理器 + 热键提供者 */
    public void init() {
        ConfigManager.getInstance().registerConfigHandler(PhantomStaffMod.MOD_ID, this);
        InputEventHandler.getKeybindManager().registerKeybindProvider(this);
        this.load();
        // 旧配置文件里 open_config_gui 可能为空（早期版本默认未绑定），
        // 强制补一个默认键 G，保证即使旧配置也不会出现「打不开配置界面」的情况。
        if (OPEN_CONFIG_GUI.getStringValue().isEmpty()) {
            OPEN_CONFIG_GUI.setValueFromString("G");
            this.save();
        }
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
