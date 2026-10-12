package com.steamarbitrage.core;

/**
 * 两种凑够目标余额的购买方式。
 */
public enum PlanKind {

    /**
     * 方式 A · 少倒 + 补差价。
     *
     * <p>只买 {@code ⌊目标 ÷ 单件到手⌋} 件，缺的那部分余额直接用真钱按面值充值补上。
     * 现金支出最少，但补差价那段没有任何折扣。
     */
    TOP_UP("top-up", "方式 A · 少倒 + 补差价"),

    /**
     * 方式 B · 多倒 + 全额余额。
     *
     * <p>多买一件凑够目标，最终余额会超出目标，但每一分余额都享受折扣。
     */
    FULL_FLIP("full-flip", "方式 B · 多倒 + 全额余额");

    private final String id;
    private final String label;

    PlanKind(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }
}
