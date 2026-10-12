package com.steamarbitrage.core;

/**
 * 由“买家支付价”反推“卖家到手价”时采用的算法。
 */
public enum FeeMode {

    /**
     * 精确反解：按 Steam 真实手续费公式逐分求解，能正确还原最低手续费保底
     * 与不可达档位，结果与 Steam 页面上的“卖家收入”一致。
     */
    EXACT("exact", "精确反解（按 Steam 真实手续费公式逐分求解）"),

    /**
     * 粗略近似：直接 {@code 买家支付 ÷ (1 + 总费率)} 向下取整。
     * 计算快但与 Steam 实际到手价有几分钱偏差，仅用于对比。
     */
    SIMPLE("simple", "粗略近似（买家支付 ÷ 1.15 向下取整）");

    private final String id;
    private final String label;

    FeeMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public static FeeMode defaultMode() {
        return EXACT;
    }

    /**
     * 按 id 查找，容忍大小写。
     *
     * @throws IllegalArgumentException 无法识别
     */
    public static FeeMode byId(String id) {
        if (id != null) {
            String key = id.trim().toLowerCase(java.util.Locale.ROOT);
            for (FeeMode mode : values()) {
                if (mode.id.equals(key)) {
                    return mode;
                }
            }
            if (key.equals("approx") || key.equals("fast")) {
                return SIMPLE;
            }
            if (key.equals("real") || key.equals("accurate")) {
                return EXACT;
            }
        }
        throw new IllegalArgumentException(
                "无法识别的手续费模式：" + id + "（可用：exact / simple）");
    }
}
