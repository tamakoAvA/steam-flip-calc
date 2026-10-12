package com.steamarbitrage.core;

/**
 * 买家支付价与卖家到手价之间的双向换算。
 *
 * <p>这是整个计算器的数学核心。{@link GameProfile} 给出的正向公式是
 * {@code 买家支付 = f(卖家收入)}，其中 {@code f} 关于卖家收入严格递增
 * （因为卖家收入每增加 1 分，买家支付至少增加 1 分）。严格递增意味着反函数唯一，
 * 于是给定买家支付价就能唯一确定卖家到手价。
 *
 * <p>{@link FeeMode#EXACT} 用一次估算加两侧微调来解这个反函数，
 * 通常只需 2～3 次迭代；{@link FeeMode#SIMPLE} 则用线性近似。
 */
public final class FeeCalculator {

    private final GameProfile profile;
    private final FeeMode mode;

    public FeeCalculator(GameProfile profile, FeeMode mode) {
        if (profile == null) {
            throw new IllegalArgumentException("游戏档案不能为空");
        }
        if (mode == null) {
            throw new IllegalArgumentException("手续费模式不能为空");
        }
        this.profile = profile;
        this.mode = mode;
    }

    /**
     * 由买家支付价（分）反推卖家到手价（分）。
     *
     * <p>返回满足 {@code f(卖家到手) <= 买家支付} 的最大卖家到手价；
     * 若买家支付低于 Steam 市场最低可成交价，则返回 {@code 0}。
     */
    public long sellerReceivesCents(long buyerPaysCents) {
        if (buyerPaysCents <= 0) {
            return 0L;
        }
        if (mode == FeeMode.SIMPLE) {
            return buyerPaysCents * BP_SCALE / (BP_SCALE + profile.totalFeeBp());
        }
        return exactSellerReceivesCents(buyerPaysCents);
    }

    /**
     * 无视 {@link FeeMode}，始终按 Steam 真实公式反解。
     *
     * <p>档位校验必须走这条路：近似模式本身就有几分钱偏差，
     * 用近似值去反查 {@code f(卖家收入) == 买家支付} 会把真实存在的档位误判成空档位。
     */
    private long exactSellerReceivesCents(long buyerPaysCents) {
        if (buyerPaysCents <= 0) {
            return 0L;
        }
        // 先用线性估算定位，再向下压到合法值、向上推到最大合法值。
        // estimate 可能略高也可能略低（低金额时最低手续费保底会把买家支付抬上去），所以两侧都要调。
        long sellerCents = buyerPaysCents * BP_SCALE / (BP_SCALE + profile.totalFeeBp());
        while (sellerCents > 0 && profile.buyerPaysCents(sellerCents) > buyerPaysCents) {
            sellerCents--;
        }
        while (profile.buyerPaysCents(sellerCents + 1) <= buyerPaysCents) {
            sellerCents++;
        }
        return sellerCents;
    }

    /** 由卖家到手价（分）正推买家支付价（分）。 */
    public long buyerPaysCents(long sellerReceivesCents) {
        return profile.buyerPaysCents(sellerReceivesCents);
    }

    /**
     * 判断某个买家支付金额是否为 Steam 市场上真实存在的挂单档位。
     *
     * <p>由于每项手续费都要向下取整，并不是每个分值都对应一个卖家收入：
     * 例如 112 分对应卖家收入 99 分，而 113、114 分都取不到，
     * 下一个可达档位直接跳到 115 分。用户如果从页面抄错价格就会落到这种空档上。
     */
    public boolean isReachableBuyerPrice(long buyerPaysCents) {
        if (buyerPaysCents < 3) {
            return false;
        }
        return profile.buyerPaysCents(exactSellerReceivesCents(buyerPaysCents)) == buyerPaysCents;
    }

    /**
     * 返回不高于给定买家支付价的最近可达档位（分）；没有可达档位时返回 {@code 0}。
     */
    public long nearestReachableBuyerPriceBelow(long buyerPaysCents) {
        return profile.buyerPaysCents(exactSellerReceivesCents(buyerPaysCents));
    }

    /** 返回高于给定买家支付价的最近可达档位（分）。 */
    public long nearestReachableBuyerPriceAbove(long buyerPaysCents) {
        return profile.buyerPaysCents(exactSellerReceivesCents(buyerPaysCents) + 1);
    }

    public GameProfile profile() {
        return profile;
    }

    public FeeMode mode() {
        return mode;
    }

    /** 万分比基准，供 {@link #sellerReceivesCents(long)} 使用。 */
    private static final long BP_SCALE = GameProfile.BP_SCALE;
}
