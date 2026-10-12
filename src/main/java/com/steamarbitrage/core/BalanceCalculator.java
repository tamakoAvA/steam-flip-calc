package com.steamarbitrage.core;

/**
 * 倒余额核心算法。
 *
 * <p>纯函数、无 IO、无状态，所有输入都来自 {@link CalcRequest}，
 * 因此可以被命令行、图形界面和自测套件共用，也便于将来替换 UI。
 *
 * <p>计算流程：
 * <ol>
 *   <li>把用户输入的 Steam 售价统一折算成“单件到手余额”</li>
 *   <li>件数 = ⌈目标余额 ÷ 单件到手余额⌉</li>
 *   <li>总成本与实际上手余额分别由件数乘出</li>
 * </ol>
 */
public final class BalanceCalculator {

    private BalanceCalculator() {
    }

    /**
     * 执行一次计算。
     *
     * @throws IllegalArgumentException 输入非法（由 {@link CalcRequest} 抛出）
     * @throws IllegalStateException    售价低于 Steam 市场最低可成交价，卖家实得为 0
     */
    public static CalcResult calculate(CalcRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("计算请求不能为空");
        }

        FeeCalculator fee = request.feeCalculator();
        long marketCents = request.marketPrice().cents();

        long sellerReceivesPerItem;
        long buyerPaysPerItem;
        if (request.priceType() == PriceType.SELLER_RECEIVE) {
            // 用户直接给的是到手价，不再扣手续费；买家实付只是参考信息。
            sellerReceivesPerItem = marketCents;
            buyerPaysPerItem = fee.buyerPaysCents(sellerReceivesPerItem);
        } else {
            // 用户给的是市场挂单价（买家支付），需要反推卖家到手。
            sellerReceivesPerItem = fee.sellerReceivesCents(marketCents);
            buyerPaysPerItem = marketCents;
        }

        if (sellerReceivesPerItem <= 0) {
            throw new IllegalStateException(String.format(
                    "Steam 售价 %s 低于市场最低可成交价 ¥0.03，卖家实得余额为 0，无法用于倒余额。",
                    request.marketPrice().toGroupedString()));
        }

        long itemCount = ceilDiv(request.targetBalance().cents(), sellerReceivesPerItem);
        long totalCostCents = Math.multiplyExact(request.purchasePrice().cents(), itemCount);
        long actualBalanceCents = Math.multiplyExact(sellerReceivesPerItem, itemCount);

        return new CalcResult(request, sellerReceivesPerItem, buyerPaysPerItem,
                itemCount, totalCostCents, actualBalanceCents);
    }

    /**
     * 向上取整的整数除法，即 {@code ⌈dividend / divisor⌉}。
     *
     * <p>“需要买几件”必须向上取整：差一分钱也是不够。
     */
    public static long ceilDiv(long dividend, long divisor) {
        if (divisor <= 0) {
            throw new ArithmeticException("除数必须为正数，实际为 " + divisor);
        }
        if (dividend <= 0) {
            return 0L;
        }
        return (dividend + divisor - 1) / divisor;
    }

    /**
     * 仅计算单件指标（不关心目标余额），件数固定为 1。
     *
     * <p>供图形界面在用户尚未填写目标余额时实时预览折扣使用。
     */
    public static CalcResult calculateUnit(CalcRequest request) {
        FeeCalculator fee = request.feeCalculator();
        long sellerReceivesPerItem = request.priceType() == PriceType.SELLER_RECEIVE
                ? request.marketPrice().cents()
                : fee.sellerReceivesCents(request.marketPrice().cents());

        if (sellerReceivesPerItem <= 0) {
            throw new IllegalStateException(String.format(
                    "Steam 售价 %s 低于市场最低可成交价 ¥0.03，卖家实得余额为 0，无法用于倒余额。",
                    request.marketPrice().toGroupedString()));
        }

        CalcRequest unit = new CalcRequest(
                request.purchasePrice(),
                request.marketPrice(),
                request.priceType(),
                request.gameProfile(),
                request.feeMode(),
                Money.ofCents(sellerReceivesPerItem));
        return calculate(unit);
    }
}
