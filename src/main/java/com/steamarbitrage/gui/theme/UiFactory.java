package com.steamarbitrage.gui.theme;

import javax.swing.ComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * 组件工厂：把 {@link Theme} 的设计令牌落到具体控件上。
 *
 * <p>所有 {@code putClientProperty} 用的都是 FlatLaf 的字符串键。
 * FlatLaf 缺失时这些属性被忽略，控件退回原生外观，不会报错。
 */
public final class UiFactory {

    private UiFactory() {
    }

    // ── 文本 ────────────────────────────────────────────────────────────────

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static JLabel title(String text) {
        return label(text, Theme.bold(Theme.SIZE_TITLE), Theme.TEXT_PRIMARY);
    }

    public static JLabel caption(String text) {
        return label(text, Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_MUTED);
    }

    // ── 卡片 ────────────────────────────────────────────────────────────────

    /** 卡片标题：左侧一道强调竖条 + 标题文字。 */
    public static JComponent heading(String text) {
        return new CardHeader(text, false);
    }

    /** 标题左侧的强调竖条，供 {@link CardHeader} 与自定义头部复用。 */
    public static JComponent accentBar() {
        return accentBar(Theme.ACCENT_DEEP);
    }

    private static JComponent accentBar(Color color) {
        JComponent bar = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 3, 3);
                } finally {
                    g2.dispose();
                }
            }
        };
        bar.setPreferredSize(new Dimension(3, 15));
        return bar;
    }

    /**
     * 带标题的卡片。正文请加到 {@link java.awt.BorderLayout#CENTER}。
     *
     * @param text 标题，{@code null} 表示无标题
     */
    public static CardPanel card(String text) {
        CardPanel card = new CardPanel(new BorderLayout(0, Theme.GAP_MD));
        card.setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG));
        if (text != null && !text.isBlank()) {
            card.add(heading(text), BorderLayout.NORTH);
        }
        return card;
    }

    /** 一行「标签 : 数值」，实现见 {@link KeyValueRow}。 */
    public static KeyValueRow row(String key) {
        return new KeyValueRow(key);
    }

    /** 细分隔线。 */
    public static JComponent divider() {
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, getHeight() / 2, getWidth(), 1);
            }
        };
        line.setPreferredSize(new Dimension(1, 9));
        return line;
    }

    // ── 提示条 ──────────────────────────────────────────────────────────────

    /**
     * 带浅色底的提示条。
     *
     * <p>正文用 {@link JTextArea} 而不是 HTML 标签：它会跟着容器宽度自动换行，
     * 因此窄窗口下也不会被截断。
     */
    public static JComponent banner(String text, Color foreground, Color background) {
        CardPanel panel = new CardPanel(new BorderLayout());
        panel.setBackground(background);
        panel.withBorderColor(null);
        panel.withRadius(Theme.GAP_SM);
        panel.setBorder(new EmptyBorder(Theme.GAP_SM, Theme.GAP_MD, Theme.GAP_SM, Theme.GAP_MD));

        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.regular(Theme.SIZE_SMALL));
        area.setForeground(foreground);
        area.setBorder(null);
        panel.add(area, BorderLayout.CENTER);
        return panel;
    }

    /** 深色强调条，用于推荐结论。 */
    public static CardPanel darkBanner() {
        CardPanel panel = new CardPanel(new BorderLayout());
        panel.setBackground(Theme.BRAND_DARK);
        panel.withBorderColor(Theme.BRAND_MID);
        panel.withRadius(Theme.GAP_SM);
        panel.setBorder(new EmptyBorder(Theme.GAP_SM, Theme.GAP_MD, Theme.GAP_SM, Theme.GAP_MD));
        return panel;
    }

    // ── 控件 ────────────────────────────────────────────────────────────────

    /** 金额输入框：圆角、占位符、一键清空。 */
    public static JTextField moneyField(int columns) {
        JTextField field = new JTextField(columns);
        field.setFont(Theme.regular(15));
        field.putClientProperty("JTextField.placeholderText", "0.00");
        field.putClientProperty("JTextField.showClearButton", true);
        field.putClientProperty("JComponent.roundRect", true);
        return field;
    }

    public static <T> JComboBox<T> comboBox(ComboBoxModel<T> model) {
        JComboBox<T> box = new JComboBox<>(model);
        box.setFont(Theme.regular(Theme.SIZE_BODY));
        box.putClientProperty("JComponent.roundRect", true);
        return box;
    }

    /** 主按钮：深蓝实心。 */
    public static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.bold(Theme.SIZE_BODY));
        button.setForeground(Theme.TEXT_ON_DARK);
        button.setBackground(Theme.BRAND_DARK);
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    /** 次要按钮：描边样式。 */
    public static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(Theme.regular(Theme.SIZE_BODY));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    // ── 布局辅助 ────────────────────────────────────────────────────────────

    public static JPanel transparentPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /** 固定高度的垂直留白。 */
    public static JComponent vgap(int height) {
        JPanel gap = new JPanel();
        gap.setOpaque(false);
        gap.setPreferredSize(new Dimension(1, height));
        return gap;
    }
}
