package com.steamarbitrage.gui.theme;

import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;

/**
 * 核心指标卡：上方小标签、中间大号数字、下方补充说明。
 *
 * <p>数值颜色用来表达语义（划算为绿、不划算为红），字号用 {@link Theme#SIZE_METRIC}，
 * 让折扣与件数这类结论在界面上一眼可见。
 */
public class MetricCard extends CardPanel {

    private final JLabel valueLabel;
    private final JLabel noteLabel;

    public MetricCard(String caption) {
        super(new BorderLayout(0, 2));
        withRadius(Theme.RADIUS_METRIC);
        setBorder(new EmptyBorder(Theme.GAP_MD, Theme.GAP_LG, Theme.GAP_MD, Theme.GAP_LG));

        add(UiFactory.label(caption, Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_MUTED),
                BorderLayout.NORTH);

        valueLabel = UiFactory.label("—", Theme.bold(Theme.SIZE_METRIC), Theme.TEXT_PRIMARY);
        add(valueLabel, BorderLayout.CENTER);

        noteLabel = UiFactory.label(" ", Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_SECONDARY);
        add(noteLabel, BorderLayout.SOUTH);
    }

    /** 更新数值与补充说明。 */
    public void update(String value, String note, Color valueColor) {
        valueLabel.setText(value);
        valueLabel.setForeground(valueColor);
        noteLabel.setText(note == null || note.isEmpty() ? " " : note);
    }

    public void setNoteColor(Color color) {
        noteLabel.setForeground(color);
    }
}
