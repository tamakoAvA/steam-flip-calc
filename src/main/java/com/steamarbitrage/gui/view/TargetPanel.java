package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.gui.theme.KeyValueRow;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;

/**
 * 按目标余额倒推卡：为了拿到目标余额需要买几件、总共花多少、最终多出多少。
 */
public final class TargetPanel extends SectionCard {

    private final KeyValueRow target = new KeyValueRow("目标余额");
    private final KeyValueRow itemCount = new KeyValueRow("需购买数量");
    private final KeyValueRow totalCost = new KeyValueRow("总成本");
    private final KeyValueRow actualBalance = new KeyValueRow("实际到手余额");
    private final KeyValueRow surplus = new KeyValueRow("超出目标");

    private final List<KeyValueRow> rows = List.of(target, itemCount, totalCost, actualBalance, surplus);

    public TargetPanel() {
        super("按目标余额倒推");

        JPanel body = UiFactory.transparentPanel(new GridLayout(0, 1, 0, 6));
        for (KeyValueRow row : rows) {
            body.add(row);
        }
        setBody(body);
    }

    public void update(CalcResult result) {
        target.setValue(result.targetBalance().toGroupedString());
        itemCount.setValue(result.itemCount() + " 件");
        totalCost.setValue(result.totalCost().toGroupedString());
        actualBalance.setValue(result.actualBalance().toGroupedString(), Theme.BRAND_MID);
        surplus.setValue(describeSurplus(result), surplusColor(result));
    }

    public void clear() {
        for (KeyValueRow row : rows) {
            row.setValue("—", Theme.TEXT_MUTED);
        }
    }

    private static Color surplusColor(CalcResult result) {
        if (result.surplus().isNegative()) {
            return Theme.DANGER;
        }
        return result.surplus().isZero() ? Theme.POSITIVE : Theme.TEXT_PRIMARY;
    }

    private static String describeSurplus(CalcResult result) {
        if (result.surplus().isZero()) {
            return "恰好达标";
        }
        if (result.surplus().isNegative()) {
            return "尚差 " + result.surplus().times(-1).toGroupedString();
        }
        return result.surplus().toGroupedString();
    }
}
