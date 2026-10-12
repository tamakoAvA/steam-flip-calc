package com.steamarbitrage.gui.theme;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;

/**
 * 卡片头部：左侧「强调竖条 + 标题」，右侧可选的置顶按钮。
 *
 * <p>可置顶的卡片把 {@link PinButton} 放在这里，用户就能在卡片角落直接切换固定状态。
 */
public final class CardHeader extends JPanel {

    private final PinButton pinButton;

    /**
     * @param title    卡片标题
     * @param pinnable 是否显示置顶按钮
     */
    public CardHeader(String title, boolean pinnable) {
        super(new BorderLayout(Theme.GAP_SM, 0));
        setOpaque(false);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        left.setOpaque(false);
        left.add(UiFactory.accentBar());
        left.add(UiFactory.title(title));
        add(left, BorderLayout.WEST);

        if (pinnable) {
            pinButton = new PinButton();
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            right.setOpaque(false);
            right.add(pinButton);
            add(right, BorderLayout.EAST);
        } else {
            pinButton = null;
        }
    }

    /** 注册置顶状态变化回调；不可置顶的卡片会被忽略。 */
    public void onPin(Consumer<Boolean> listener) {
        if (pinButton != null) {
            pinButton.onChange(listener);
        }
    }

    /** 由外部同步置顶状态（例如卡片被侧边开关隐藏时强制取消置顶）。 */
    public void setPinned(boolean pinned) {
        if (pinButton != null) {
            pinButton.setPinned(pinned);
        }
    }

    public boolean isPinnable() {
        return pinButton != null;
    }

    /** 供自测断言使用。 */
    public boolean isPinned() {
        return pinButton != null && pinButton.isPinned();
    }

    /** 供自测驱动使用：模拟点击置顶按钮。 */
    public void togglePinForTest() {
        if (pinButton != null) {
            pinButton.doClick();
        }
    }
}
