package com.steamarbitrage.core;

/**
 * “Steam 售价”这个输入到底指哪一笔钱。
 *
 * <p>Steam 社区市场页面上同时存在两个数字：买家实际支付的价格，以及卖家实际收到的余额。
 * 两者相差一笔手续费，用这个枚举明确区分，避免算错折扣。
 */
public enum PriceType {

    /** 买家支付价，也就是 Steam 市场挂单显示的价格；程序负责扣掉手续费。 */
    BUYER_PAID("buyer", "买家支付价（Steam 市场挂单价）"),

    /** 卖家到手价，也就是 Steam 卖家收入；程序不再扣手续费。 */
    SELLER_RECEIVE("seller", "卖家到手价（Steam 卖家收入）");

    private final String id;
    private final String label;

    PriceType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static PriceType defaultType() {
        return BUYER_PAID;
    }

    /**
     * 按 id 查找，容忍大小写与常见别名。
     *
     * @throws IllegalArgumentException 无法识别
     */
    public static PriceType byId(String id) {
        if (id != null) {
            String key = id.trim().toLowerCase(java.util.Locale.ROOT);
            for (PriceType type : values()) {
                if (type.id.equals(key)) {
                    return type;
                }
            }
            if (key.equals("buy") || key.equals("buyer-paid") || key.equals("market")) {
                return BUYER_PAID;
            }
            if (key.equals("sell") || key.equals("seller-receive") || key.equals("receive")) {
                return SELLER_RECEIVE;
            }
        }
        throw new IllegalArgumentException(
                "无法识别的价格语义：" + id + "（可用：buyer / seller）");
    }
}
