package com.steamarbitrage.gui.theme;

import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 界面设计令牌：配色、字体、间距、圆角，以及外观（Look and Feel）的安装。
 *
 * <h2>配色</h2>
 * 取自 Steam 客户端的深蓝体系：{@code #1B2838} 是 Steam 的主背景色，
 * {@code #66C0F4} 是它的亮蓝强调色。浅色主题下把深蓝用作标题栏与主文字，
 * 亮蓝用作交互强调，既保留 Steam 观感又保证正文可读性。
 * 正向色没有直接用 Steam 的促销亮绿 {@code #A4D007}（在白底上对比度不足），
 * 而是取同色系加深的 {@code #4C8C2B}。
 *
 * <h2>关于 FlatLaf</h2>
 * FlatLaf 通过 {@link UIManager#setLookAndFeel(String)} 按<b>类名</b>加载，
 * 因此本类在编译期不引用任何第三方类型 —— {@code javac} 永远只需要 JDK。
 * 运行时若 {@code lib\} 下有 FlatLaf 的 JAR 就启用它，没有则回退到系统外观，
 * 界面依旧可用（自绘卡片本来就不依赖 FlatLaf）。
 *
 * <h2>深色主题</h2>
 * 目前只实现浅色，但所有颜色都集中在本类常量中；
 * 将来加深色只需再声明一套值并在 {@link #install(boolean)} 里按模式选择。
 */
public final class Theme {

    // ── Steam 深蓝体系 ──────────────────────────────────────────────────────

    /** Steam 主背景深蓝，用作标题栏与主文字。 */
    public static final Color BRAND_DARK = new Color(0x1B, 0x28, 0x38);

    /** Steam 次深蓝，用作悬停态与次级深色。 */
    public static final Color BRAND_MID = new Color(0x2A, 0x47, 0x5E);

    /** Steam 亮蓝，用作强调与装饰。 */
    public static final Color ACCENT = new Color(0x66, 0xC0, 0xF4);

    /** Steam 链接蓝，用于聚焦环与可交互高亮。 */
    public static final Color ACCENT_DEEP = new Color(0x1A, 0x9F, 0xFF);

    // ── 语义色 ──────────────────────────────────────────────────────────────

    /** 正向：折扣划算、推荐方案。 */
    public static final Color POSITIVE = new Color(0x4C, 0x8C, 0x2B);

    /** 正向浅底：推荐卡片背景。 */
    public static final Color POSITIVE_BG = new Color(0xEF, 0xF7, 0xE8);

    /** 警示：档位空档、补差过小。 */
    public static final Color WARNING = new Color(0xE8, 0x71, 0x0A);

    /** 警示浅底。 */
    public static final Color WARNING_BG = new Color(0xFD, 0xF3, 0xE6);

    /** 错误：输入非法、折扣不划算。 */
    public static final Color DANGER = new Color(0xC6, 0x28, 0x28);

    /** 错误浅底。 */
    public static final Color DANGER_BG = new Color(0xFB, 0xEC, 0xEC);

    /** 信息浅底：普通提示条。 */
    public static final Color INFO_BG = new Color(0xE8, 0xF4, 0xFD);

    // ── 中性色 ──────────────────────────────────────────────────────────────

    /** 页面底色：带一点蓝的浅灰。 */
    public static final Color PAGE_BG = new Color(0xEC, 0xF1, 0xF6);

    /** 卡片底色。 */
    public static final Color CARD_BG = Color.WHITE;

    /** 卡片描边。 */
    public static final Color BORDER = new Color(0xD5, 0xDE, 0xE7);

    /** 主文字。 */
    public static final Color TEXT_PRIMARY = BRAND_DARK;

    /** 次要文字。 */
    public static final Color TEXT_SECONDARY = new Color(0x5C, 0x6B, 0x7A);

    /** 弱化文字（单位、辅助说明）。 */
    public static final Color TEXT_MUTED = new Color(0x8A, 0x99, 0xA8);

    /** 深色区上的文字。 */
    public static final Color TEXT_ON_DARK = new Color(0xF2, 0xF6, 0xFA);

    /** 深色区上的次要文字。 */
    public static final Color TEXT_ON_DARK_MUTED = new Color(0xA8, 0xBD, 0xCE);

    // ── 间距（8pt 网格）与圆角 ──────────────────────────────────────────────

    public static final int GAP_XS = 4;
    public static final int GAP_SM = 8;
    public static final int GAP_MD = 12;
    public static final int GAP_LG = 16;
    public static final int GAP_XL = 24;

    public static final int RADIUS_CARD = 10;
    public static final int RADIUS_METRIC = 12;

    // ── 字号阶梯 ────────────────────────────────────────────────────────────

    public static final int SIZE_METRIC = 30;
    public static final int SIZE_METRIC_SUB = 21;
    public static final int SIZE_TITLE = 14;
    public static final int SIZE_BODY = 13;
    public static final int SIZE_SMALL = 11;

    // ── 字体 ────────────────────────────────────────────────────────────────

    private static final String UI_FAMILY = resolveUiFamily();

    private static boolean flatLafActive;

    private Theme() {
    }

    /** 依次尝试常见中文字体，全部缺失时退回逻辑无衬线字体。 */
    private static String resolveUiFamily() {
        String[] candidates = {
                "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "Source Han Sans SC", "SimHei"
        };
        try {
            Set<String> available = new HashSet<>(List.of(
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames(Locale.ROOT)));
            for (String candidate : candidates) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // 无图形环境时直接回退
        }
        return Font.SANS_SERIF;
    }

    public static String uiFamily() {
        return UI_FAMILY;
    }

    public static Font regular(int size) {
        return new Font(UI_FAMILY, Font.PLAIN, size);
    }

    public static Font bold(int size) {
        return new Font(UI_FAMILY, Font.BOLD, size);
    }

    /** FlatLaf 是否成功启用。 */
    public static boolean isFlatLafActive() {
        return flatLafActive;
    }

    /**
     * 安装外观与设计令牌。必须在创建任何 Swing 组件之前调用。
     *
     * @return 是否成功启用了 FlatLaf
     */
    public static boolean install() {
        install(true);
        return flatLafActive;
    }

    /**
     * @param preferFlatLaf false 时直接使用系统外观（便于对比调试）
     */
    public static void install(boolean preferFlatLaf) {
        flatLafActive = false;

        if (preferFlatLaf) {
            // 按类名加载：编译期不需要 FlatLaf 的 JAR
            try {
                UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
                flatLafActive = true;
            } catch (Throwable ignored) {
                // 没有 JAR 或版本不兼容，回退即可
            }
        }

        if (!flatLafActive) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {
                // 保持 Swing 默认外观
            }
        }

        applyFont();
        applyDefaults();
    }

    /** 统一全局字体；FlatLaf 支持 defaultFont 键，其余外观需要逐个替换。 */
    private static void applyFont() {
        FontUIResource base = new FontUIResource(regular(SIZE_BODY));

        if (flatLafActive) {
            UIManager.put("defaultFont", base);
            return;
        }

        // 非 FlatLaf：先快照键集合再写入，避免遍历时修改 UIDefaults
        List<Object> keys = new ArrayList<>();
        for (Enumeration<Object> e = UIManager.getDefaults().keys(); e.hasMoreElements(); ) {
            keys.add(e.nextElement());
        }
        for (Object key : keys) {
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, base);
            }
        }
    }

    /** 统一控件外观。FlatLaf 专属的键在没有 FlatLaf 时会被忽略，不影响运行。 */
    private static void applyDefaults() {
        UIManager.put("Panel.background", PAGE_BG);
        UIManager.put("OptionPane.background", CARD_BG);
        UIManager.put("TextField.background", CARD_BG);
        UIManager.put("ComboBox.background", CARD_BG);
        UIManager.put("Button.background", CARD_BG);

        // 语义色（FlatLaf 3.x 的 accent 体系）
        UIManager.put("Component.accentColor", ACCENT_DEEP);
        UIManager.put("TextComponent.selectionBackground", ACCENT_DEEP);
        UIManager.put("TextComponent.selectionForeground", Color.WHITE);
        UIManager.put("Component.focusColor", ACCENT_DEEP);
        UIManager.put("Component.borderColor", BORDER);
        UIManager.put("Component.focusedBorderColor", ACCENT_DEEP);
        UIManager.put("Component.disabledBorderColor", BORDER);

        // 圆角与焦点环
        UIManager.put("Component.arc", RADIUS_CARD);
        UIManager.put("Button.arc", RADIUS_CARD);
        UIManager.put("TextComponent.arc", RADIUS_CARD - 2);
        UIManager.put("CheckBox.arc", 4);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 1);
        UIManager.put("Component.focusInsets", new Insets(0, 0, 0, 0));

        // 滚动条：细、圆、无箭头按钮
        UIManager.put("ScrollBar.width", 11);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.track", PAGE_BG);

        // 下拉框与提示
        UIManager.put("ComboBox.showPopupShadow", true);
        UIManager.put("ComboBox.arrowType", "chevron");
        UIManager.put("ToolTip.background", BRAND_DARK);
        UIManager.put("ToolTip.foreground", TEXT_ON_DARK);
        UIManager.put("ToolTip.border", new Insets(6, 10, 6, 10));
    }
}
