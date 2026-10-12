package com.steamarbitrage.gui.view;

import com.steamarbitrage.gui.theme.CardHeader;
import com.steamarbitrage.gui.theme.CardPanel;
import com.steamarbitrage.gui.theme.Theme;

import javax.swing.JComponent;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.util.function.Consumer;

/**
 * 可显示 / 隐藏、可置顶的内容卡片基类。
 *
 * <p>三块内容（购买建议、单件指标、按目标余额倒推）都继承它，
 * 因此侧边抽屉的显示开关与卡片角落的置顶按钮对它们一视同仁。
 */
public abstract class SectionCard extends CardPanel {

    private final CardHeader header;

    protected SectionCard(String title) {
        super(new BorderLayout(0, Theme.GAP_MD));
        setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG));
        header = new CardHeader(title, true);
        add(header, BorderLayout.NORTH);
    }

    /** 设置卡片正文。 */
    protected void setBody(JComponent body) {
        add(body, BorderLayout.CENTER);
    }

    /** 注册置顶状态变化回调。 */
    public void onPin(Consumer<Boolean> listener) {
        header.onPin(listener);
    }

    /** 由外部同步置顶状态（例如被显示开关隐藏时强制取消置顶）。 */
    public void setPinned(boolean pinned) {
        header.setPinned(pinned);
    }

    public boolean isPinned() {
        return header.isPinned();
    }

    public CardHeader header() {
        return header;
    }
}
