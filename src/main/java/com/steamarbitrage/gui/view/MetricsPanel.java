package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.gui.theme.MetricCard;
import com.steamarbitrage.gui.theme.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridLayout;
import java.math.BigDecimal;

/**
 * 核心指标区：折合折扣、需购买件数、总成本、实际到手余额。
 *
 * <p>这四张卡是整个界面的视觉重点，用大号数字承载结论。
 * 列数可动态调整（4 列 / 2 列），以适配不同分辨率的窗口宽度。
 */
public final class MetricsPanel extends JPanel {

    private final MetricCard discountCard = new MetricCard("折合折扣");
    private final MetricCard countCard = new MetricCard("需购买");
    private final MetricCard costCard = new MetricCard("总成本");
    private final MetricCard balanceCard = new MetricCard("实际到手余额");

    public MetricsPanel() {
        super(new GridLayout(1, 4, Theme.GAP_MD, Theme.GAP_MD));
        setOpaque(false);
        add(discountCard);
        add(countCard);
        add(costCard);
        add(balanceCard);
    }

    /**
     * 按窗口宽度切换单行 4 列或两行 2 列。
     *
     * @param columns 每行卡片数
     */
    public void setColumns(int columns) {
        GridLayout layout = (GridLayout) getLayout();
        if (layout.getColumns() == columns) {
            return;
        }
        layout.setColumns(columns);
        layout.setRows(0);
        revalidate();
        repaint();
    }

    public void update(CalcResult result) {
        BigDecimal ratio = result.discountRatio();
        boolean worthwhile = ratio.compareTo(BigDecimal.ONE) < 0;
        Color discountColor = worthwhile ? Theme.POSITIVE : Theme.DANGER;

        discountCard.update(result.discountZhe().toPlainString() + " 折",
                result.discountPercent().toPlainString() + "%"
                        + (worthwhile ? "　低于 10 折" : "　不划算"),
                discountColor);
        discountCard.setNoteColor(worthwhile ? Theme.POSITIVE : Theme.DANGER);

        countCard.update(result.itemCount() + " 件",
                describeDeviation(result.surplus()),
                Theme.BRAND_DARK);

        costCard.update(result.totalCost().toGroupedString(),
                "单件 " + result.purchasePrice().toGroupedString(),
                Theme.BRAND_DARK);

        balanceCard.update(result.actualBalance().toGroupedString(),
                "目标 " + result.targetBalance().toGroupedString(),
                Theme.BRAND_DARK);
    }

    /** 清空为占位状态，用于输入不完整时。 */
    public void clear() {
        discountCard.update("—", " ", Theme.TEXT_MUTED);
        countCard.update("—", " ", Theme.TEXT_MUTED);
        costCard.update("—", " ", Theme.TEXT_MUTED);
        balanceCard.update("—", " ", Theme.TEXT_MUTED);
    }

    private static String describeDeviation(Money surplus) {
        if (surplus.isZero()) {
            return "恰好达标";
        }
        if (surplus.isNegative()) {
            return "尚差 " + surplus.times(-1).toGroupedString();
        }
        return "超出 " + surplus.toGroupedString();
    }
}
