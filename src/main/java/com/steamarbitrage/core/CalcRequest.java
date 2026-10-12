package com.steamarbitrage.core;

import java.util.Objects;

/**
 * 一次倒余额计算的输入。
 *
 * @param purchasePrice 饰品购入价（第三方平台的单价，例如 BUFF、悠悠有品）
 * @param marketPrice   Steam 售价，含义由 {@code priceType} 决定
 * @param priceType     Steam 售价是买家支付价还是卖家到手价
 * @param gameProfile   游戏手续费档案（CS2 / TF2）
 * @param feeMode       由买家支付价反推到手价时使用的算法
 * @param targetBalance 想要获得的 Steam 余额
 */
public record CalcRequest(
        Money purchasePrice,
        Money marketPrice,
        PriceType priceType,
        GameProfile gameProfile,
        FeeMode feeMode,
        Money targetBalance) {

    public CalcRequest {
        requirePositive(purchasePrice, "饰品购入价");
        requirePositive(marketPrice, "Steam 售价");
        requirePositive(targetBalance, "目标余额");
        Objects.requireNonNull(priceType, "价格语义不能为空");
        Objects.requireNonNull(gameProfile, "游戏档案不能为空");
        Objects.requireNonNull(feeMode, "手续费模式不能为空");
    }

    /** 按当前档案与模式新建一个换算器。 */
    public FeeCalculator feeCalculator() {
        return new FeeCalculator(gameProfile, feeMode);
    }

    /** Steam 售价在报告中的标签，随价格语义变化。 */
    public String marketPriceLabel() {
        return priceType == PriceType.BUYER_PAID ? "Steam 挂单价" : "Steam 到手价";
    }

    private static void requirePositive(Money value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        if (!value.isPositive()) {
            throw new IllegalArgumentException(name + "必须大于 0（当前为 " + value + "）");
        }
    }
}
