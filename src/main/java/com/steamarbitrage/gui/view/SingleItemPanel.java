package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.Formats;
import com.steamarbitrage.gui.theme.KeyValueRow;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.JPanel;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 单件指标卡：一件饰品从第三方平台购入到 Steam 卖出余额的全部明细。
 */
public final class SingleItemPanel extends SectionCard {

    private final KeyValueRow purchasePrice = new KeyValueRow("饰品购入价");
    private final KeyValueRow marketPrice = new KeyValueRow("Steam 挂单价");
    private final KeyValueRow buyerPays = new KeyValueRow("买家实付");
    private final KeyValueRow steamFee = new KeyValueRow("Steam 手续费");
    private final KeyValueRow gameFee = new KeyValueRow("游戏手续费");
    private final KeyValueRow sellerReceives = new KeyValueRow("卖家到手余额");
    private final KeyValueRow unitDiscount = new KeyValueRow("单件折合折扣");
    private final KeyValueRow valueRatio = new KeyValueRow("性价比");

    private final List<KeyValueRow> rows = List.of(
            purchasePrice, marketPrice, buyerPays, steamFee,
            gameFee, sellerReceives, unitDiscount, valueRatio);

    public SingleItemPanel() {
        super("单件指标");

        JPanel body = UiFactory.transparentPanel(new GridLayout(0, 1, 0, 6));
        for (KeyValueRow row : rows) {
            body.add(row);
        }
        setBody(body);
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
        unitDiscount.setValue(Formats.zhe(unitRatio),
                unitRatio.compareTo(BigDecimal.ONE) < 0 ? Theme.POSITIVE : Theme.DANGER);
        valueRatio.setValue(result.valueRatio().setScale(4, RoundingMode.HALF_UP).toPlainString());
    }

    /** 输入不完整时回到占位状态。 */
    public void clear() {
        for (KeyValueRow row : rows) {
            row.setValue("—", Theme.TEXT_MUTED);
        }
        marketPrice.setKey("Steam 挂单价");
    }
}
