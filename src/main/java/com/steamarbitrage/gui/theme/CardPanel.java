package com.steamarbitrage.gui.theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * 圆角卡片容器。
 *
 * <p>纯 Java2D 自绘，不依赖任何第三方外观库；{@code opaque} 设为 {@code false}
 * 后由本类负责背景与描边，因此圆角不会被 Swing 的矩形背景覆盖。
 */
public class CardPanel extends JPanel {

    private Color borderColor = Theme.BORDER;
    private int radius = Theme.RADIUS_CARD;
    private boolean shadow;

    public CardPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        // 卡片只做展示，不参与焦点遍历。
        // 全局开启了 FlatLaf 的 Component.focusWidth 后，JPanel 默认会变成可聚焦，
        // 一旦拿到焦点就会被画上焦点底色与蓝色焦点环，破坏卡片的统一外观。
        setFocusable(false);
        setBackground(Theme.CARD_BG);
    }

    /** 设置描边色；传 {@code null} 表示不画描边。 */
    public CardPanel withBorderColor(Color color) {
        this.borderColor = color;
        repaint();
        return this;
    }

    public CardPanel withRadius(int newRadius) {
        this.radius = newRadius;
        repaint();
        return this;
    }

    /** 轻微投影，用于强调卡片。 */
    public CardPanel withShadow(boolean enabled) {
        this.shadow = enabled;
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (shadow) {
                g2.setColor(new Color(0x1B, 0x28, 0x38, 18));
                g2.fillRoundRect(1, 2, w - 2, h - 2, radius, radius);
            }
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
