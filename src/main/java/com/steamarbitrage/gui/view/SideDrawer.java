package com.steamarbitrage.gui.view;

import com.steamarbitrage.gui.Section;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 可折叠的侧边抽屉：用三个开关控制内容区域的显示与隐藏。
 *
 * <p>默认收起（宽度 0，不占空间），点标题栏的按钮后带缓动动画滑出。
 * 展开宽度固定，滑出时会挤压内容区，因此窗口窄时也能用。
 */
public final class SideDrawer extends JPanel {

    /** 展开后的宽度。 */
    public static final int EXPANDED_WIDTH = 200;

    private static final int ANIMATION_MS = 170;
    private static final int FRAME_MS = 12;

    private final Map<Section, JCheckBox> boxes = new EnumMap<>(Section.class);

    private boolean expanded;
    private Timer animation;
    private BiConsumer<Section, Boolean> listener = (section, visible) -> {
    };

    public SideDrawer() {
        super(new BorderLayout());
        setBackground(Theme.CARD_BG);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.BORDER));
        setPreferredSize(new Dimension(0, 0));

        JPanel content = UiFactory.transparentPanel(new BorderLayout(0, Theme.GAP_MD));
        content.setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_MD, Theme.GAP_LG, Theme.GAP_MD));

        content.add(UiFactory.label("显示区域", Theme.bold(Theme.SIZE_TITLE), Theme.TEXT_PRIMARY),
                BorderLayout.NORTH);

        JPanel switches = UiFactory.transparentPanel(new GridLayout(0, 1, 0, Theme.GAP_SM));
        for (Section section : Section.values()) {
            JCheckBox box = new JCheckBox(section.label(), true);
            box.setOpaque(false);
            box.setFont(Theme.regular(Theme.SIZE_BODY));
            box.setForeground(Theme.TEXT_PRIMARY);
            box.setFocusable(false);
            box.addActionListener(e -> listener.accept(section, box.isSelected()));
            boxes.put(section, box);
            switches.add(box);
        }

        JPanel middle = UiFactory.transparentPanel(new BorderLayout());
        middle.add(switches, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);

        content.add(UiFactory.label(
                        "<html>卡片右上角的图钉可把卡片固定在窗口顶部，不随内容滚动。</html>",
                        Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_MUTED),
                BorderLayout.SOUTH);

        add(content, BorderLayout.CENTER);
    }

    /** 注册开关变化回调。 */
    public void onSectionChanged(BiConsumer<Section, Boolean> callback) {
        this.listener = callback;
    }

    public boolean isExpanded() {
        return expanded;
    }

    /** 展开或收起抽屉（带动画）。 */
    public void setExpanded(boolean value) {
        this.expanded = value;
        animateTo(value ? EXPANDED_WIDTH : 0);
    }

    public void toggle() {
        setExpanded(!expanded);
    }

    public boolean isSectionSelected(Section section) {
        JCheckBox box = boxes.get(section);
        return box != null && box.isSelected();
    }

    public void setSectionSelected(Section section, boolean selected) {
        JCheckBox box = boxes.get(section);
        if (box != null) {
            box.setSelected(selected);
        }
    }

    /** 供自测驱动：模拟点击某个开关。 */
    public void toggleForTest(Section section) {
        JCheckBox box = boxes.get(section);
        if (box != null) {
            box.doClick();
        }
    }

    private void animateTo(int targetWidth) {
        if (animation != null && animation.isRunning()) {
            animation.stop();
        }
        int startWidth = getPreferredSize().width;
        if (startWidth == targetWidth) {
            return;
        }

        long startTime = System.currentTimeMillis();
        animation = new Timer(FRAME_MS, e -> {
            float progress = Math.min(1f,
                    (System.currentTimeMillis() - startTime) / (float) ANIMATION_MS);
            // ease-out，让滑动收尾更自然
            float eased = 1f - (1f - progress) * (1f - progress);
            int width = Math.round(startWidth + (targetWidth - startWidth) * eased);
            if (progress >= 1f) {
                width = targetWidth;
                ((Timer) e.getSource()).stop();
            }
            applyWidth(width);
        });
        animation.start();
    }

    /** 立即完成动画，供自测在无等待条件下断言最终宽度。 */
    public void finishAnimation() {
        if (animation != null && animation.isRunning()) {
            animation.stop();
        }
        applyWidth(expanded ? EXPANDED_WIDTH : 0);
    }

    private void applyWidth(int width) {
        setPreferredSize(new Dimension(width, 0));
        revalidate();
        Container parent = getParent();
        if (parent != null) {
            parent.revalidate();
        }
    }
}
