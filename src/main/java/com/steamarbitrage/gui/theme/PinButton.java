package com.steamarbitrage.gui.theme;

import javax.swing.JButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * 卡片角落的「置顶」按钮：把卡片固定在窗口顶部，不随内容滚动。
 *
 * <p>图标是自绘的（顶部横线 + 向上箭头），不依赖任何字体的符号字形，
 * 因此在没有装特殊字体的机器上也不会显示成方框。
 */
public final class PinButton extends JButton {

    private boolean pinned;
    private boolean hovered;
    private Consumer<Boolean> onChange;

    public PinButton() {
        setPreferredSize(new Dimension(26, 26));
        setFocusable(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText("固定在窗口顶部，不随内容滚动");

        addActionListener(e -> toggle());
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = false;
                repaint();
            }
        });
    }

    /** 注册状态变化回调。 */
    public void onChange(Consumer<Boolean> listener) {
        this.onChange = listener;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean value) {
        if (pinned == value) {
            return;
        }
        pinned = value;
        setToolTipText(pinned ? "取消置顶，让卡片跟随内容滚动" : "固定在窗口顶部，不随内容滚动");
        repaint();
    }

    private void toggle() {
        setPinned(!pinned);
        if (onChange != null) {
            onChange.accept(pinned);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int cx = width / 2;

            if (pinned || hovered) {
                g2.setColor(pinned ? Theme.INFO_BG : Theme.PAGE_BG);
                g2.fillRoundRect(1, 1, width - 2, height - 2, 7, 7);
            }

            g2.setColor(pinned ? Theme.ACCENT_DEEP
                    : (hovered ? Theme.BRAND_MID : Theme.TEXT_MUTED));
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            // 顶部横线代表窗口顶边
            g2.drawLine(cx - 6, 7, cx + 6, 7);

            int[] xs = {cx, cx - 5, cx + 5};
            int[] ys = {9, 16, 16};
            if (pinned) {
                g2.fillPolygon(xs, ys, 3);
            } else {
                g2.drawPolygon(xs, ys, 3);
            }
            g2.drawLine(cx, 16, cx, 19);
        } finally {
            g2.dispose();
        }
    }
}
