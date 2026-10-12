package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 以“分”为最小单位的不可变金额值类型。
 *
 * <p>所有金额运算都在 {@code long} 分值上完成，全程不出现 {@code double}。
 * 这一点对本工具至关重要：核心公式
 * {@code 需购买件数 = ceil(目标余额 / 单件到手)} 是一次取整运算，
 * 浮点误差（例如 86.97 被存成 86.96999999999999）足以让结果整整多算或少算一件饰品。
 */
public final class Money implements Comparable<Money> {

    /** 金额 0。 */
    public static final Money ZERO = new Money(0L);

    private final long cents;

    private Money(long cents) {
        this.cents = cents;
    }

    /** 以“分”为单位构造。 */
    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    /** 以“元”为单位构造，四舍五入到分。 */
    public static Money ofYuan(BigDecimal yuan) {
        Objects.requireNonNull(yuan, "yuan");
        return new Money(yuan.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    /** 以“元”为单位构造，四舍五入到分。 */
    public static Money ofYuan(String yuan) {
        return ofYuan(new BigDecimal(yuan.trim()));
    }

    /**
     * 解析用户输入的元金额。
     *
     * <p>容忍首尾空白、千分位逗号以及常见的货币符号（{@code ¥ ￥ $}），
     * 例如 {@code "¥1,234.56"}、{@code " 70 "}、{@code "86.9"} 都能解析。
     *
     * @throws IllegalArgumentException 输入为空或不是合法金额
     */
    public static Money parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        String cleaned = text.trim()
                .replace("¥", "")
                .replace("￥", "")
                .replace("$", "")
                .replace("，", "")
                .replace(",", "")
                .replace(" ", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法识别的金额：" + text.trim());
        }
        return ofYuan(value);
    }

    /** 分值，可能为负。 */
    public long cents() {
        return cents;
    }

    /** 元值，恒为两位小数。 */
    public BigDecimal toYuan() {
        return BigDecimal.valueOf(cents, 2);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    /** 乘以整数因子（件数），溢出即抛异常而不是静默回绕。 */
    public Money times(long factor) {
        return new Money(Math.multiplyExact(cents, factor));
    }

    public boolean isNegative() {
        return cents < 0;
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isPositive() {
        return cents > 0;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Money other && other.cents == cents;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(cents);
    }

    /** 形如 {@code ¥86.97}。 */
    @Override
    public String toString() {
        return "¥" + toYuan().toPlainString();
    }

    /** 形如 {@code ¥1,234.56}，带千分位。 */
    public String toGroupedString() {
        boolean negative = cents < 0;
        String plain = toYuan().abs().toPlainString();
        int dot = plain.indexOf('.');
        String integerPart = plain.substring(0, dot);
        String decimals = plain.substring(dot);

        StringBuilder grouped = new StringBuilder();
        int digitCount = 0;
        for (int i = integerPart.length() - 1; i >= 0; i--) {
            grouped.append(integerPart.charAt(i));
            if (++digitCount % 3 == 0 && i > 0) {
                grouped.append(',');
            }
        }
        return (negative ? "-¥" : "¥") + grouped.reverse() + decimals;
    }
}
