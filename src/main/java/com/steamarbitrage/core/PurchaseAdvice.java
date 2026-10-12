package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 两种购买方式的对比结论与推荐。
 *
 * <p>{@code spendDeltaCents} 与 {@code balanceDeltaCents} 都是
 * 「方式 B 减方式 A」的差值，它们是推荐逻辑的全部依据：
 * <ul>
 *   <li>{@code spendDelta ≤ 0}：方式 B 花更少（或同样多）的钱，却拿到更多余额，无条件更优</li>
 *   <li>{@code spendDelta > 0}：方式 B 多花的钱换来了更多余额，此时看
 *       {@link #marginalRatio()}（边际折合）是否优于按面值充值</li>
 * </ul>
 *
 * @param topUpPlan         方式 A：少倒 + 补差价
 * @param fullFlipPlan      方式 B：多倒 + 全额余额
 * @param recommended       推荐的方式
 * @param spendDeltaCents   方式 B 相对方式 A 的现金增量（可为负）
 * @param balanceDeltaCents 方式 B 相对方式 A 的余额增量
 * @param identical         两种方式是否完全相同（目标余额恰为单件到手的整数倍）
 */
public record PurchaseAdvice(
        PurchasePlan topUpPlan,
        PurchasePlan fullFlipPlan,
        PlanKind recommended,
        long spendDeltaCents,
        long balanceDeltaCents,
        boolean identical) {

    public PurchaseAdvice {
        if (topUpPlan == null || fullFlipPlan == null) {
            throw new IllegalArgumentException("购买方案不能为空");
        }
        if (recommended == null) {
            throw new IllegalArgumentException("推荐方式不能为空");
        }
    }

    public PurchasePlan recommendedPlan() {
        return recommended == PlanKind.TOP_UP ? topUpPlan : fullFlipPlan;
    }

    public PurchasePlan otherPlan() {
        return recommended == PlanKind.TOP_UP ? fullFlipPlan : topUpPlan;
    }

    /**
     * 方式 B 的边际折合 = 多花的现金 ÷ 多拿的余额。
     *
     * <p>例如 61.11% 意味着「多倒一件」换来的那部分余额，只花了面值的六成，
     * 明显优于按面值 1:1 充值（100%）。
     */
    public BigDecimal marginalRatio() {
        if (balanceDeltaCents <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(spendDeltaCents)
                .divide(BigDecimal.valueOf(balanceDeltaCents), 10, RoundingMode.HALF_UP);
    }

    /** 边际折合的百分数。 */
    public BigDecimal marginalPercent() {
        return marginalRatio().movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 方式 B 是否无条件优于方式 A：不多花钱，却多拿余额。
     */
    public boolean fullFlipStrictlyBetter() {
        return !identical && spendDeltaCents <= 0 && balanceDeltaCents > 0;
    }

    /**
     * 充值补差是否小到现实中可能无法操作。
     *
     * <p>Steam 充值渠道通常有最小金额限制，几毛钱的差额往往补不进去，
     * 这种情况应当直接多倒一件。
     */
    public boolean topUpTooSmall() {
        return !identical
                && topUpPlan.topUpCents() > 0
                && topUpPlan.topUpCents() < AdviceCalculator.SMALL_TOP_UP_THRESHOLD_CENTS;
    }
}
