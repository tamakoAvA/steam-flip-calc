package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.Formats;
import com.steamarbitrage.gui.theme.CardPanel;
import com.steamarbitrage.gui.theme.KeyValueRow;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 明细区：单件指标 + 按目标余额倒推。
 *
 * <p>每行都是 {@link KeyValueRow}（左右两端对齐），因此中英混排天然整齐，
 * 不再依赖等宽字体和空格 padding。
 */
public final class DetailPanel extends JPanel {

    private final KeyValueRow purchasePrice = new KeyValueRow("饰品购入价");
    private final KeyValueRow marketPrice = new KeyValueRow("Steam 挂单价");
    private final KeyValueRow buyerPays = new KeyValueRow("买家实付");
    private final KeyValueRow steamFee = new KeyValueRow("Steam 手续费");
    private final KeyValueRow gameFee = new KeyValueRow("游戏手续费");
    private final KeyValueRow sellerReceives = new KeyValueRow("卖家到手余额");
    private final KeyValueRow unitDiscount = new KeyValueRow("单件折合折扣");
    private final KeyValueRow valueRatio = new KeyValueRow("性价比");

    private final KeyValueRow target = new KeyValueRow("目标余额");
    private final KeyValueRow itemCount = new KeyValueRow("需购买数量");
    private final KeyValueRow totalCost = new KeyValueRow("总成本");
    private final KeyValueRow actualBalance = new KeyValueRow("实际到手余额");
    private final KeyValueRow surplus = new KeyValueRow("超出目标");

    private final List<KeyValueRow> allRows = List.of(
            purchasePrice, marketPrice, buyerPays, steamFee, gameFee,
            sellerReceives, unitDiscount, valueRatio,
            target, itemCount, totalCost, actualBalance, surplus);

    public DetailPanel() {
        super(new GridBagLayout());
        setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        c.gridy = 0;
        c.insets = new Insets(0, 0, Theme.GAP_MD, 0);
        add(card("单件指标", purchasePrice, marketPrice, buyerPays, steamFee, gameFee,
                sellerReceives, unitDiscount, valueRatio), c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 0);
        add(card("按目标余额倒推", target, itemCount, totalCost, actualBalance, surplus), c);

        // 占位：把两张卡片顶到上方，避免在 GridLayout 拉伸时被垂直居中
        c.gridy = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        add(filler, c);
    }

    private static CardPanel card(String heading, JComponent... rows) {
        CardPanel card = UiFactory.card(heading);
        JPanel body = new JPanel(new GridLayout(0, 1, 0, 6));
        body.setOpaque(false);
        for (JComponent row : rows) {
            body.add(row);
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    public void update(CalcResult result) {
        purchasePrice.setValue(result.purchasePrice().toGroupedString());

        marketPrice.setKey(result.request().marketPriceLabel());
        marketPrice.setValue(result.marketPrice().toGroupedString());

        buyerPays.setValue(result.buyerPaysPerItem().toGroupedString());
        steamFee.setValue(result.steamFeePerItem().toGroupedString());
        gameFee.setValue(result.gameFeePerItem().toGroupedString());
        sellerReceives.setValue(result.sellerReceivesPerItem().toGroupedString(), Theme.BRAND_MID);

        BigDecimal unitRatio = result.unitDiscountRatio();
        unitDiscount.setValue(Formats.zhe(unitRatio), worthwhileColor(unitRatio));
        valueRatio.setValue(result.valueRatio().setScale(4, RoundingMode.HALF_UP).toPlainString());

        target.setValue(result.targetBalance().toGroupedString());
        itemCount.setValue(result.itemCount() + " 件");
        totalCost.setValue(result.totalCost().toGroupedString());
        actualBalance.setValue(result.actualBalance().toGroupedString(), Theme.BRAND_MID);
        surplus.setValue(describeSurplus(result), surplusColor(result));
    }

    /** 输入不完整时回到占位状态。 */
    public void clear() {
        for (KeyValueRow row : allRows) {
            row.setValue("—", Theme.TEXT_MUTED);
        }
        marketPrice.setKey("Steam 挂单价");
    }

    private static Color worthwhileColor(BigDecimal ratio) {
        return ratio.compareTo(BigDecimal.ONE) < 0 ? Theme.POSITIVE : Theme.DANGER;
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
