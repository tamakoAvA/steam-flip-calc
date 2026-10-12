package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一种购买方式的资金明细。
 *
 * @param kind           方式类型
 * @param itemCount      需要购买的饰品件数（方式 A 可能为 0，表示全部走充值）
 * @param itemCostCents  饰品总支出（分）
 * @param topUpCents     需要用真钱按面值充值补上的金额（分），方式 B 恒为 0
 * @param balanceCents   最终到手的 Steam 余额（分）
 * @param targetCents    目标余额（分），用于计算偏差
 */
public record PurchasePlan(
        PlanKind kind,
        long itemCount,
        long itemCostCents,
        long topUpCents,
        long balanceCents,
        long targetCents) {

    public PurchasePlan {
        if (kind == null) {
            throw new IllegalArgumentException("购买方式不能为空");
        }
        if (itemCount < 0 || itemCostCents < 0 || topUpCents < 0 || balanceCents < 0) {
            throw new IllegalArgumentException("金额与件数不能为负");
        }
    }

    /** 现金总支出 = 饰品支出 + 充值补差。 */
    public long totalSpendCents() {
        return itemCostCents + topUpCents;
    }

    public Money itemCost() {
        return Money.ofCents(itemCostCents);
    }

    public Money topUp() {
        return Money.ofCents(topUpCents);
    }

    public Money totalSpend() {
        return Money.ofCents(totalSpendCents());
    }

    public Money balance() {
        return Money.ofCents(balanceCents);
    }

    public Money target() {
        return Money.ofCents(targetCents);
    }

    /** 最终余额相对目标的偏差；正数表示超出，0 表示恰好达标。 */
    public Money deviation() {
        return Money.ofCents(balanceCents - targetCents);
    }

    public boolean hasTopUp() {
        return topUpCents > 0;
    }

    public boolean isPureTopUp() {
        return itemCount == 0;
    }

    /**
     * 单位余额成本 = 现金总支出 ÷ 最终余额。
     *
     * <p>这是跨方案比较的核心口径：不管两个方案花了多少钱、拿了多少余额，
     * 都用「换到 1 元余额要掏多少钱」来对齐。
     */
    public BigDecimal costRatio() {
        if (balanceCents <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(totalSpendCents())
                .divide(BigDecimal.valueOf(balanceCents), 10, RoundingMode.HALF_UP);
    }

    /** 单位余额成本的百分数，例如 {@code 81.33}。 */
    public BigDecimal costPercent() {
        return costRatio().movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }

    /** 中文习惯的「几折」，例如 {@code 8.13}。 */
    public BigDecimal costZhe() {
        return costRatio().movePointRight(1).setScale(2, RoundingMode.HALF_UP);
    }
}
