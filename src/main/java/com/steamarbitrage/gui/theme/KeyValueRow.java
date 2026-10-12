package com.steamarbitrage.gui.theme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * 一行「标签 : 数值」。
 *
 * <p>用 {@link BorderLayout} 两端对齐，因此中英文混排天然整齐，
 * 不再依赖等宽字体与空格 padding（那是命令行报告的做法）。
 */
public class KeyValueRow extends JPanel {

    private final JLabel keyLabel;
    private final JLabel valueLabel;

    public KeyValueRow(String key) {
        super(new BorderLayout(Theme.GAP_MD, 0));
        setOpaque(false);

        keyLabel = UiFactory.label(key, Theme.regular(Theme.SIZE_BODY), Theme.TEXT_SECONDARY);
        add(keyLabel, BorderLayout.WEST);

        valueLabel = UiFactory.label("—", Theme.bold(Theme.SIZE_BODY), Theme.TEXT_PRIMARY);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(valueLabel, BorderLayout.EAST);
    }

    /** 更新左侧标签；用于「Steam 挂单价 / Steam 到手价」这类随输入变化的行。 */
    public void setKey(String key) {
        keyLabel.setText(key);
    }

    public void setValue(String value) {
        setValue(value, Theme.TEXT_PRIMARY);
    }

    public void setValue(String value, Color color) {
        valueLabel.setText(value);
        valueLabel.setForeground(color);
    }
}
