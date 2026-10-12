package com.steamarbitrage.gui.theme;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * 垂直堆叠布局：每个子组件占满父容器宽度，高度取各自的首选高度，之间留固定间距。
 *
 * <p>用它而不是 {@code BoxLayout} / {@code GridBagLayout}，是因为置顶功能需要频繁地
 * 在「滚动区」与「固定区」之间搬动卡片 —— 这个布局对增删组件天然友好，
 * 不需要在每次变更时重建约束。
 *
 * <p>不可见的组件会被跳过，因此侧边抽屉的显示开关可以直接用
 * {@code setVisible(false)} 实现。
 */
public final class VerticalLayout implements LayoutManager {

    private final int gap;

    public VerticalLayout(int gap) {
        this.gap = gap;
    }

    @Override
    public void addLayoutComponent(String name, Component comp) {
        // 无需记录约束
    }

    @Override
    public void removeLayoutComponent(Component comp) {
        // 无需清理
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets insets = parent.getInsets();
        int width = 0;
        int height = 0;
        int count = 0;

        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            Dimension size = child.getPreferredSize();
            width = Math.max(width, size.width);
            height += size.height;
            count++;
        }
        if (count > 1) {
            height += gap * (count - 1);
        }

        return new Dimension(width + insets.left + insets.right,
                height + insets.top + insets.bottom);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void layoutContainer(Container parent) {
        Insets insets = parent.getInsets();
        int x = insets.left;
        int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
        int y = insets.top;

        for (Component child : parent.getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            int height = child.getPreferredSize().height;
            child.setBounds(x, y, width, height);
            y += height + gap;
        }
    }
}
