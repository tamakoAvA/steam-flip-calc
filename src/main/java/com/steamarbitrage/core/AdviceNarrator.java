package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 把购买建议翻译成人话。
 *
 * <p>命令行报告和图形界面都从这里取句子，因此两个入口的说法永远一致；
 * 调整措辞也只需要改这一处。
 *
 * <p>每句话都控制得比较短，命令行按行列出不会超宽，图形界面里也能自然换行。
 */
public final class AdviceNarrator {

    private AdviceNarrator() {
    }

    /**
     * 推荐理由，按显示顺序排列，每句一条。
     */
    public static List<String> reasons(CalcResult result, PurchaseAdvice advice) {
        List<String> lines = new ArrayList<>();

        if (advice.identical()) {
            lines.add("目标余额恰好是单件到手余额的整数倍，买 "
                    + advice.fullFlipPlan().itemCount() + " 件即可，既不用充值补差，也不会多余额。");
            return lines;
        }

        if (advice.recommended() == PlanKind.TOP_UP) {
            appendTopUpReason(result, advice, lines);
        } else {
            appendFullFlipReason(result, advice, lines);
        }
        return lines;
    }

    private static void appendTopUpReason(CalcResult result, PurchaseAdvice advice, List<String> lines) {
        PurchasePlan plan = advice.topUpPlan();

        if (plan.isPureTopUp()) {
            lines.add("目标余额 " + result.targetBalance().toGroupedString()
                    + " 还买不到一件饰品，方式 A 只能全额按面值充值。");
            lines.add("单件饰品本身的折合折扣为 " + Formats.discount(result.unitDiscountRatio())
                    + "，倒它反而亏。");
            lines.add("建议直接按面值充值，不要通过倒余额获取。");
            return;
        }

        lines.add("单件饰品本身的折合折扣为 " + Formats.discount(result.unitDiscountRatio())
                + "，每倒一件都不划算。");
        lines.add("多倒只会多亏，建议只倒 " + plan.itemCount() + " 件，缺口 "
                + plan.topUp().toGroupedString() + " 直接按面值充值。");
        lines.add("充值不产生折扣，但也不会亏钱。");
    }

    private static void appendFullFlipReason(CalcResult result, PurchaseAdvice advice, List<String> lines) {
        PurchasePlan recommended = advice.fullFlipPlan();
        Money spendDelta = Money.ofCents(Math.abs(advice.spendDeltaCents()));
        Money balanceDelta = Money.ofCents(advice.balanceDeltaCents());

        if (advice.topUpPlan().isPureTopUp()) {
            lines.add("目标余额 " + result.targetBalance().toGroupedString()
                    + " 还买不到一件饰品，方式 A 只能全额按面值充值，毫无折扣。");
            lines.add("方式 B 花 " + recommended.totalSpend().toGroupedString()
                    + " 换到 " + recommended.balance().toGroupedString()
                    + " 余额，折合 " + Formats.percent(recommended.costRatio())
                    + "，比原价充值便宜 "
                    + Money.ofCents(recommended.balanceCents() - recommended.totalSpendCents())
                            .toGroupedString()
                    + "。");
            return;
        }

        if (advice.fullFlipStrictlyBetter()) {
            String spendPhrase = advice.spendDeltaCents() == 0
                    ? "不多花钱"
                    : "反而少花 " + spendDelta.toGroupedString();
            lines.add("方式 B " + spendPhrase + "，还多拿 " + balanceDelta.toGroupedString() + " 余额。");
            lines.add("与其按面值充值补差，不如把这笔钱换成折扣余额，没有任何理由选方式 A。");
            return;
        }

        lines.add("比方式 A 多花 " + spendDelta.toGroupedString()
                + "，多拿 " + balanceDelta.toGroupedString() + " 余额。");
        lines.add("这部分边际折合仅 " + Formats.percent(advice.marginalRatio())
                + "，远优于按面值 1:1 充值（100%）。");
        lines.add("方式 B 的全部余额都享受 " + Formats.percent(recommended.costRatio())
                + " 折扣，不存在原价充值部分。");
        lines.add("若只需恰好 " + result.targetBalance().toGroupedString()
                + " 余额、希望现金支出最少，可选方式 A（少花 "
                + spendDelta.toGroupedString() + "）。");
    }

    /**
     * 需要提醒但不算错误的信息：档位空档、近似模式、折扣不划算、补差过小。
     *
     * @return 无提醒时返回空列表
     */
    public static List<String> cautions(CalcResult result) {
        List<String> lines = new ArrayList<>();
        CalcRequest request = result.request();

        if (!result.isMarketPriceReachable()) {
            lines.add("注意：" + result.marketPrice().toGroupedString()
                    + " 不是 Steam 市场的真实挂单档位（相邻档位为 "
                    + result.nearestReachableBelow().toGroupedString()
                    + " 与 " + result.nearestReachableAbove().toGroupedString()
                    + "），已按不超过该价的最高档位计算。");
        }

        if (request.feeMode() == FeeMode.SIMPLE && request.priceType() == PriceType.BUYER_PAID) {
            long exactSeller = new FeeCalculator(request.gameProfile(), FeeMode.EXACT)
                    .sellerReceivesCents(request.marketPrice().cents());
            StringBuilder sb = new StringBuilder();
            sb.append("提示：simple 近似模式按 售价 ÷ 1.15 直接取整，")
              .append("上表手续费与买家实付不会严格相加。同一价格下 Steam 实际到手为 ")
              .append(Money.ofCents(exactSeller).toGroupedString());
            long delta = Math.abs(exactSeller - result.sellerReceivesCents());
            if (delta > 0) {
                sb.append("，相差 ").append(Money.ofCents(delta).toGroupedString());
            }
            sb.append("；加 --fee-mode exact 可得到精确结果。");
            lines.add(sb.toString());
        }

        if (request.feeMode() == FeeMode.SIMPLE && request.priceType() == PriceType.SELLER_RECEIVE) {
            lines.add("提示：价格语义是卖家到手价，程序不再扣手续费，因此换算模式对结果没有影响。");
        }

        if (result.discountRatio().compareTo(BigDecimal.ONE) >= 0) {
            lines.add("提示：折合折扣不低于 10 折，这笔倒余额不划算（成本不低于到手的余额）。");
        }

        if (AdviceCalculator.advise(result).topUpTooSmall()) {
            PurchasePlan plan = AdviceCalculator.advise(result).topUpPlan();
            lines.add("提示：方式 A 的补差只有 " + plan.topUp().toGroupedString()
                    + "，Steam 充值渠道通常有最小金额限制，这种零头往往补不进去，"
                    + "实操上直接多倒一件更省事。");
        }

        return lines;
    }
}
