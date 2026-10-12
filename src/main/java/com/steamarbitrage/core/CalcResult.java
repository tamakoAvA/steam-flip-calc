package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 一次倒余额计算的完整结果。
 *
 * <p>金额字段一律以“分”保存，派生指标（折扣、性价比）在读取时用
 * {@link BigDecimal} 现算，避免任何中间精度损失。
 *
 * @param request            原始输入
 * @param sellerReceivesCents 单件饰品在 Steam 卖出后卖家实际到手的余额（分）
 * @param buyerPaysCents      单件饰品在 Steam 市场上买家需要支付的价格（分）
 * @param itemCount           为达到目标余额需购买的饰品件数
 * @param totalCostCents      总购入成本（分）
 * @param actualBalanceCents  买够件数后实际到手的余额（分）
 */
public record CalcResult(
        CalcRequest request,
        long sellerReceivesCents,
        long buyerPaysCents,
        long itemCount,
        long totalCostCents,
        long actualBalanceCents) {

    public CalcResult {
        if (request == null) {
            throw new IllegalArgumentException("request 不能为空");
        }
        if (sellerReceivesCents <= 0) {
            throw new IllegalArgumentException("单件到手余额必须大于 0");
        }
        if (itemCount <= 0) {
            throw new IllegalArgumentException("件数必须大于 0");
        }
    }

    // ── 金额访问器 ──────────────────────────────────────────────────────────

    public Money purchasePrice() {
        return request.purchasePrice();
    }

    public Money marketPrice() {
        return request.marketPrice();
    }

    public Money targetBalance() {
        return request.targetBalance();
    }

    /** 单件到手余额。 */
    public Money sellerReceivesPerItem() {
        return Money.ofCents(sellerReceivesCents);
    }

    /** 单件买家实付。 */
    public Money buyerPaysPerItem() {
        return Money.ofCents(buyerPaysCents);
    }

    /** 单件 Steam 手续费。 */
    public Money steamFeePerItem() {
        return Money.ofCents(request.gameProfile().steamFeeCents(sellerReceivesCents));
    }

    /** 单件游戏手续费。 */
    public Money gameFeePerItem() {
        return Money.ofCents(request.gameProfile().gameFeeCents(sellerReceivesCents));
    }

    /** 单件手续费合计。 */
    public Money totalFeePerItem() {
        return Money.ofCents(buyerPaysCents - sellerReceivesCents);
    }

    /** 总购入成本。 */
    public Money totalCost() {
        return Money.ofCents(totalCostCents);
    }

    /** 实际到手余额。 */
    public Money actualBalance() {
        return Money.ofCents(actualBalanceCents);
    }

    /** 实际到手余额超出目标的部分；为负表示未达标。 */
    public Money surplus() {
        return Money.ofCents(actualBalanceCents - request.targetBalance().cents());
    }

    // ── 派生指标 ────────────────────────────────────────────────────────────

    /** 单件折合折扣 = 单件购入价 ÷ 单件到手余额。 */
    public BigDecimal unitDiscountRatio() {
        return ratio(request.purchasePrice().cents(), sellerReceivesCents);
    }

    /**
     * 整体折合折扣 = 总成本 ÷ 实际到手余额。
     *
     * <p>由于件数是整数，这个值通常与 {@link #unitDiscountRatio()} 完全一致；
     * 分开提供是为了在将来支持混合饰品时仍能表达“整体”的口径。
     */
    public BigDecimal discountRatio() {
        return ratio(totalCostCents, actualBalanceCents);
    }

    /** 折扣百分数，例如 {@code 80.49}（即 80.49%）。 */
    public BigDecimal discountPercent() {
        return discountRatio().movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }

    /** 中文习惯的“几折”，例如 {@code 8.05} 表示 8.05 折。 */
    public BigDecimal discountZhe() {
        return discountRatio().movePointRight(1).setScale(2, RoundingMode.HALF_UP);
    }

    /** 性价比 = 实际到手余额 ÷ 总成本；大于 1 表示这笔倒余额划算。 */
    public BigDecimal valueRatio() {
        return ratio(actualBalanceCents, totalCostCents);
    }

    /**
     * 每花 1 元人民币能换到多少 Steam 余额。
     *
     * <p>与 {@link #valueRatio()} 数值相同，但语义更直白，用于报告展示。
     */
    public BigDecimal balancePerYuan() {
        return valueRatio();
    }

    /** 买入 N 件的平均单件成本（等于购入价）。 */
    public Money averageCostPerItem() {
        return Money.ofCents(totalCostCents / itemCount);
    }

    // ── 市场档位校验 ────────────────────────────────────────────────────────

    /** Steam 售价是否为市场上真实存在的档位（仅当价格语义为买家支付价时才有意义）。 */
    public boolean isMarketPriceReachable() {
        if (request.priceType() != PriceType.BUYER_PAID) {
            return true;
        }
        return request.feeCalculator().isReachableBuyerPrice(request.marketPrice().cents());
    }

    /** 不高于输入价的最近可达档位。 */
    public Money nearestReachableBelow() {
        return Money.ofCents(request.feeCalculator()
                .nearestReachableBuyerPriceBelow(request.marketPrice().cents()));
    }

    /** 高于输入价的最近可达档位。 */
    public Money nearestReachableAbove() {
        return Money.ofCents(request.feeCalculator()
                .nearestReachableBuyerPriceAbove(request.marketPrice().cents()));
    }

    private static BigDecimal ratio(long numeratorCents, long denominatorCents) {
        if (denominatorCents <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numeratorCents)
                .divide(BigDecimal.valueOf(denominatorCents), 10, RoundingMode.HALF_UP);
    }
}
