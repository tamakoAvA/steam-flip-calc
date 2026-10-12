package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.AdviceCalculator;
import com.steamarbitrage.core.AdviceNarrator;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.Formats;
import com.steamarbitrage.core.PlanKind;
import com.steamarbitrage.core.PurchaseAdvice;
import com.steamarbitrage.core.PurchasePlan;
import com.steamarbitrage.gui.theme.CardPanel;
import com.steamarbitrage.gui.theme.KeyValueRow;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;

/**
 * 购买建议卡：两种买法并排对比 + 推荐结论 + 理由 + 注意事项。
 *
 * <p>推荐的那张换浅绿底并加绿色描边，配「★ 推荐」徽标，扫一眼就知道选哪个。
 * 理由文案由 {@link AdviceNarrator} 提供，与命令行逐字同源。
 */
public final class AdvicePanel extends SectionCard {

    private final PlanCard topUpCard = new PlanCard();
    private final PlanCard fullFlipCard = new PlanCard();
    private final JLabel recommendLabel = UiFactory.label("", Theme.bold(15), Theme.TEXT_ON_DARK);
    private final JTextArea reasonArea = new JTextArea();
    private final JPanel bannerHolder = UiFactory.transparentPanel(new GridLayout(0, 1, 0, Theme.GAP_SM));

    public AdvicePanel() {
        super("购买建议");

        JPanel body = UiFactory.transparentPanel(new GridBagLayout());

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        CardPanel banner = UiFactory.darkBanner();
        banner.add(recommendLabel, BorderLayout.WEST);
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, Theme.GAP_MD, 0);
        body.add(banner, c);

        c.gridy = 1;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.BOTH;
        body.add(topUpCard, c);

        c.gridx = 1;
        c.insets = new Insets(0, Theme.GAP_MD, Theme.GAP_MD, 0);
        body.add(fullFlipCard, c);

        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, Theme.GAP_MD, 0);
        body.add(buildReasonArea(), c);

        c.gridy = 3;
        c.insets = new Insets(0, 0, 0, 0);
        c.weighty = 1;
        c.anchor = GridBagConstraints.NORTH;
        body.add(bannerHolder, c);

        setBody(body);
    }

    private JComponent buildReasonArea() {
        reasonArea.setEditable(false);
        reasonArea.setOpaque(false);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);
        reasonArea.setFont(Theme.regular(Theme.SIZE_BODY));
        reasonArea.setForeground(Theme.TEXT_SECONDARY);
        reasonArea.setBorder(null);
        return reasonArea;
    }

    public void update(CalcResult result) {
        PurchaseAdvice advice = AdviceCalculator.advise(result);
        boolean recommendTopUp = advice.recommended() == PlanKind.TOP_UP;
        boolean identical = advice.identical();

        // 两种方式完全一样时，并排对比没有意义，只留结论文字
        topUpCard.setVisible(!identical);
        fullFlipCard.setVisible(!identical);
        if (!identical) {
            topUpCard.update(advice.topUpPlan(), recommendTopUp);
            fullFlipCard.update(advice.fullFlipPlan(), !recommendTopUp);
        }

        recommendLabel.setText(identical
                ? "两种购买方式完全相同"
                : "★ 推荐：" + advice.recommendedPlan().kind().label());

        reasonArea.setText(String.join("\n", AdviceNarrator.reasons(result, advice)));
        reasonArea.setCaretPosition(0);

        bannerHolder.removeAll();
        for (String caution : AdviceNarrator.cautions(result)) {
            boolean severe = caution.startsWith("注意：");
            bannerHolder.add(UiFactory.banner(caution,
                    severe ? Theme.DANGER : Theme.WARNING,
                    severe ? Theme.DANGER_BG : Theme.WARNING_BG));
        }
        bannerHolder.revalidate();
        bannerHolder.repaint();
    }

    public void clear() {
        topUpCard.setVisible(true);
        fullFlipCard.setVisible(true);
        topUpCard.clear();
        fullFlipCard.clear();
        recommendLabel.setText("等待有效输入");
        reasonArea.setText("");
        bannerHolder.removeAll();
        bannerHolder.revalidate();
        bannerHolder.repaint();
    }

    // ── 单个买法卡片 ────────────────────────────────────────────────────────

    private static final class PlanCard extends CardPanel {

        private final JLabel titleLabel =
                UiFactory.label("", Theme.bold(Theme.SIZE_BODY), Theme.TEXT_PRIMARY);
        private final CardPanel badge = buildBadge();

        private final KeyValueRow items = new KeyValueRow("饰品件数");
        private final KeyValueRow itemCost = new KeyValueRow("饰品支出");
        private final KeyValueRow topUp = new KeyValueRow("充值补差");
        private final KeyValueRow spend = new KeyValueRow("现金支出");
        private final KeyValueRow balance = new KeyValueRow("最终余额");
        private final KeyValueRow cost = new KeyValueRow("单位余额成本");

        PlanCard() {
            super(new BorderLayout(0, Theme.GAP_SM));
            setBorder(new EmptyBorder(Theme.GAP_MD, Theme.GAP_MD, Theme.GAP_MD, Theme.GAP_MD));

            JPanel head = UiFactory.transparentPanel(new BorderLayout(Theme.GAP_SM, 0));
            head.add(titleLabel, BorderLayout.CENTER);
            head.add(badge, BorderLayout.EAST);
            add(head, BorderLayout.NORTH);

            JPanel body = UiFactory.transparentPanel(new GridLayout(0, 1, 0, 5));
            for (JComponent row : new JComponent[]{items, itemCost, topUp, spend, balance, cost}) {
                body.add(row);
            }
            add(body, BorderLayout.CENTER);
        }

        private static CardPanel buildBadge() {
            CardPanel panel = new CardPanel(new BorderLayout());
            panel.setBackground(Theme.POSITIVE);
            panel.withBorderColor(null);
            panel.withRadius(6);
            panel.setBorder(new EmptyBorder(2, 8, 2, 8));
            panel.add(UiFactory.label("★ 推荐", Theme.bold(Theme.SIZE_SMALL), Color.WHITE),
                    BorderLayout.CENTER);
            return panel;
        }

        void update(PurchasePlan plan, boolean recommended) {
            titleLabel.setText(plan.kind().label());
            badge.setVisible(recommended);

            String itemText = plan.itemCount() + " 件";
            if (plan.isPureTopUp()) {
                itemText += "（纯充值）";
            }
            items.setValue(itemText);
            itemCost.setValue(plan.itemCost().toGroupedString());
            topUp.setValue(plan.topUp().toGroupedString(),
                    plan.hasTopUp() ? Theme.WARNING : Theme.TEXT_MUTED);
            spend.setValue(plan.totalSpend().toGroupedString());
            balance.setValue(plan.balance().toGroupedString() + deviationText(plan));

            BigDecimal ratio = plan.costRatio();
            cost.setValue(Formats.percent(ratio),
                    ratio.compareTo(BigDecimal.ONE) < 0 ? Theme.POSITIVE : Theme.DANGER);

            setBackground(recommended ? Theme.POSITIVE_BG : Theme.CARD_BG);
            withBorderColor(recommended ? Theme.POSITIVE : Theme.BORDER);
            repaint();
        }

        void clear() {
            titleLabel.setText("");
            badge.setVisible(false);
            for (KeyValueRow row : new KeyValueRow[]{items, itemCost, topUp, spend, balance, cost}) {
                row.setValue("—", Theme.TEXT_MUTED);
            }
            setBackground(Theme.CARD_BG);
            withBorderColor(Theme.BORDER);
            repaint();
        }

        private static String deviationText(PurchasePlan plan) {
            if (plan.deviation().isZero()) {
                return "（恰好达标）";
            }
            if (plan.deviation().isNegative()) {
                return "（尚差 " + plan.deviation().times(-1).toGroupedString() + "）";
            }
            return "（超出 " + plan.deviation().toGroupedString() + "）";
        }
    }
}
