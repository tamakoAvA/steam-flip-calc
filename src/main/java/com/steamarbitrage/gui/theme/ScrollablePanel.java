package com.steamarbitrage.gui.theme;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * 放进 {@link javax.swing.JScrollPane} 的内容面板。
 *
 * <p>{@link #getScrollableTracksViewportWidth()} 返回 {@code true}，
 * 让内容宽度始终跟随视口宽度：窗口窄的时候内容会被压缩（配合响应式断点），
 * 而不是撑出横向滚动条。高度仍按内容计算，因此可以纵向滚动。
 */
public class ScrollablePanel extends JPanel implements Scrollable {

    public ScrollablePanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 80;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
