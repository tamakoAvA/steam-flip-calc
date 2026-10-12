package com.steamarbitrage.core;

/**
 * 由一次计算结果推导两种购买方式，并给出推荐。
 *
 * <h2>两种方式</h2>
 * <ul>
 *   <li><b>方式 A · 少倒 + 补差价</b>：买 {@code k = ⌊T ÷ s⌋} 件，
 *       到手 {@code k·s}，差额 {@code r = T − k·s} 直接用真钱按面值充值。
 *       这是「现金支出最少」的方案——因为每多买一件都在赚差价，所以买得越多、
 *       需要原价充值的部分越少。</li>
 *   <li><b>方式 B · 多倒 + 全额余额</b>：买 {@code k + 1} 件，全部余额都是折扣价换来的，
 *       但会超出目标。</li>
 * </ul>
 *
 * <h2>为什么这样推荐</h2>
 * 两者相比，方式 B 的<b>边际支出</b>是 {@code c − r}，<b>边际余额</b>是 {@code s − r}
 * （{@code c} 为单件成本、{@code s} 为单件到手）。于是：
 * <ul>
 *   <li>若 {@code r ≥ c}：边际支出 ≤ 0，方式 B <b>又省钱又多拿余额</b>，无条件更优。
 *       直觉上很好理解——与其原价充 {@code r} 元，不如再加一点买一件，
 *       还能额外拿到余额。</li>
 *   <li>若 {@code r < c}：方式 B 要多掏 {@code c − r}，但换回 {@code s − r} 余额。
 *       只要单件本身划算（{@code c < s}），这个边际折合就必然低于 100%，
 *       比按面值充值便宜，因此仍推荐方式 B。</li>
 *   <li>若单件本身就不划算（{@code c ≥ s}）：多倒一件只会多亏，改推荐方式 A。</li>
 * </ul>
 *
 * <p>换句话说：只要倒余额本身是赚的，方式 B 的单位余额成本永远不会比方式 A 差，
 * 唯一的代价是现金占用更多。方式 A 的价值在于「现金支出最少」和「余额恰好等于目标」。
 */
public final class AdviceCalculator {

    /** 低于这个金额的充值补差视为现实中难以操作（1 元）。 */
    public static final long SMALL_TOP_UP_THRESHOLD_CENTS = 100L;

    private AdviceCalculator() {
    }

    /**
     * 根据已有计算结果推导购买建议。
     *
     * @throws IllegalArgumentException result 为空
     */
    public static PurchaseAdvice advise(CalcResult result) {
        if (result == null) {
            throw new IllegalArgumentException("计算结果不能为空");
        }

        long targetCents = result.targetBalance().cents();
        long perItemBalance = result.sellerReceivesCents();
        long perItemCost = result.purchasePrice().cents();

        // 方式 A：向下取整，缺口用真钱补
        long downCount = targetCents / perItemBalance;
        long remainder = targetCents - downCount * perItemBalance;

        // 方式 B：向上取整（CalcResult 里已经算好）
        long flipCount = result.itemCount();

        PurchasePlan topUpPlan = new PurchasePlan(
                PlanKind.TOP_UP,
                downCount,
                Math.multiplyExact(perItemCost, downCount),
                remainder,
                targetCents,
                targetCents);

        PurchasePlan fullFlipPlan = new PurchasePlan(
                PlanKind.FULL_FLIP,
                flipCount,
                Math.multiplyExact(perItemCost, flipCount),
                0L,
                Math.multiplyExact(perItemBalance, flipCount),
                targetCents);

        boolean identical = remainder == 0L;
        long spendDelta = fullFlipPlan.totalSpendCents() - topUpPlan.totalSpendCents();
        long balanceDelta = fullFlipPlan.balanceCents() - topUpPlan.balanceCents();

        return new PurchaseAdvice(topUpPlan, fullFlipPlan, decide(identical, perItemCost, perItemBalance),
                spendDelta, balanceDelta, identical);
    }

    private static PlanKind decide(boolean identical, long perItemCost, long perItemBalance) {
        if (identical) {
            // 目标恰好是单件到手的整数倍，两种方式完全一样
            return PlanKind.FULL_FLIP;
        }
        if (perItemCost >= perItemBalance) {
            // 单件本身就不划算，多倒只会多亏，少倒为妙
            return PlanKind.TOP_UP;
        }
        return PlanKind.FULL_FLIP;
    }
}
