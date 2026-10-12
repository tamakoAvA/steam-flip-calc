package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 折扣、百分比等数值的统一文本格式，供命令行与图形界面共用。
 */
public final class Formats {

    private Formats() {
    }

    /** 形如 {@code 80.49% （8.05 折，划算）}。 */
    public static String discount(BigDecimal ratio) {
        BigDecimal percent = ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal zhe = ratio.movePointRight(1).setScale(2, RoundingMode.HALF_UP);
        int compared = ratio.compareTo(BigDecimal.ONE);
        String verdict = compared < 0 ? "划算" : (compared == 0 ? "无折扣" : "不划算");
        return percent.toPlainString() + "% （" + zhe.toPlainString() + " 折，" + verdict + "）";
    }

    /** 形如 {@code 80.49%}。 */
    public static String percent(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** 形如 {@code 8.05 折}。 */
    public static String zhe(BigDecimal ratio) {
        return ratio.movePointRight(1).setScale(2, RoundingMode.HALF_UP).toPlainString() + " 折";
    }
}
