package com.steamarbitrage.cli;

import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeCalculator;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 把 {@link CalcResult} 渲染成终端报告。
 *
 * <p>报告按“显示宽度”而不是字符个数对齐：中文、全角括号占 2 列，
 * ASCII 占 1 列，因此直接按 {@code String.length()} 补空格会在中英混排时错位。
 */
public final class ConsoleReport {

    private static final int LABEL_WIDTH = 13;
    private static final int RULE_WIDTH = 66;
    private static final String RULE = "=".repeat(RULE_WIDTH);
    private static final String THIN = "-".repeat(RULE_WIDTH);

    private ConsoleReport() {
    }

    /** 渲染完整报告。 */
    public static String render(CalcResult result) {
        CalcRequest req = result.request();
        GameProfile profile = req.gameProfile();

        StringBuilder sb = new StringBuilder();
        sb.append(RULE).append('\n');
        sb.append(" Steam 倒余额计算结果\n");
        sb.append(RULE).append('\n');
        row(sb, "游戏档案", profile.displayName());
        row(sb, "手续费", profile.feeDescription());
        row(sb, "价格语义", req.priceType().label());
        row(sb, "手续费模式", req.feeMode().label());
        sb.append('\n');

        sb.append(THIN).append('\n');
        sb.append(" [单件指标]\n");
        sb.append(THIN).append('\n');
        row(sb, "饰品购入价", result.purchasePrice().toGroupedString());
        row(sb, req.marketPriceLabel(), result.marketPrice().toGroupedString());
        row(sb, "买家实付", result.buyerPaysPerItem().toGroupedString());
        row(sb, "Steam 手续费", result.steamFeePerItem().toGroupedString());
        row(sb, "游戏手续费", result.gameFeePerItem().toGroupedString());
        row(sb, "卖家到手余额", result.sellerReceivesPerItem().toGroupedString());
        row(sb, "单件折合折扣", formatDiscount(result.unitDiscountRatio()));
        row(sb, "性价比", formatValueRatio(result.valueRatio()));
        sb.append('\n');

        sb.append(THIN).append('\n');
        sb.append(" [按目标余额倒推]\n");
        sb.append(THIN).append('\n');
        row(sb, "目标余额", result.targetBalance().toGroupedString());
        row(sb, "需购买数量", result.itemCount() + " 件");
        row(sb, "总成本", result.totalCost().toGroupedString());
        row(sb, "实际到手余额", result.actualBalance().toGroupedString());
        row(sb, "实际折合折扣", formatDiscount(result.discountRatio()));
        row(sb, "超出目标", formatSurplus(result));
        sb.append(RULE).append('\n');

        return sb.toString();
    }

    /** 单行摘要，便于粘贴到聊天窗口。 */
    public static String renderSummaryLine(CalcResult result) {
        return String.format("折扣 %s%%（%s 折） | 需购买 %d 件 | 总成本 %s | 到手 %s",
                result.discountPercent().toPlainString(),
                result.discountZhe().toPlainString(),
                result.itemCount(),
                result.totalCost().toGroupedString(),
                result.actualBalance().toGroupedString());
    }

    /**
     * 需要提醒用户但不算错误的信息，例如价格不是 Steam 真实档位。
     *
     * @return 无提醒时返回空字符串
     */
    public static String warnings(CalcResult result) {
        StringBuilder sb = new StringBuilder();
        CalcRequest req = result.request();

        if (!result.isMarketPriceReachable()) {
            sb.append("注意：").append(result.marketPrice().toGroupedString())
              .append(" 不是 Steam 市场的真实挂单档位（相邻档位为 ")
              .append(result.nearestReachableBelow().toGroupedString())
              .append(" 与 ")
              .append(result.nearestReachableAbove().toGroupedString())
              .append("），已按不超过该价的最高档位计算。\n");
        }

        if (req.feeMode() == FeeMode.SIMPLE && req.priceType() == PriceType.BUYER_PAID) {
            long exactSeller = new FeeCalculator(req.gameProfile(), FeeMode.EXACT)
                    .sellerReceivesCents(req.marketPrice().cents());
            long delta = Math.abs(exactSeller - result.sellerReceivesCents());
            sb.append("提示：simple 近似模式按 售价 ÷ 1.15 直接取整，上表手续费与买家实付不会严格相加。")
              .append("同一价格下 Steam 实际到手为 ")
              .append(Money.ofCents(exactSeller).toGroupedString());
            if (delta > 0) {
                sb.append("，相差 ").append(Money.ofCents(delta).toGroupedString());
            }
            sb.append("；加 --fee-mode exact 可得到精确结果。\n");
        }

        if (result.discountRatio().compareTo(BigDecimal.ONE) >= 0) {
            sb.append("提示：折合折扣不低于 10 折，这笔倒余额不划算（成本不低于到手的余额）。\n");
        }

        return sb.toString();
    }

    // ── 内部渲染工具 ────────────────────────────────────────────────────────

    private static void row(StringBuilder sb, String label, String value) {
        sb.append(' ').append(padRight(label, LABEL_WIDTH))
          .append(" : ").append(value).append('\n');
    }

    /** 把折扣比率格式化成 {@code 80.49% （8.05 折，划算）}。 */
    public static String formatDiscount(BigDecimal ratio) {
        BigDecimal percent = ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
        BigDecimal zhe = ratio.movePointRight(1).setScale(2, RoundingMode.HALF_UP);
        String verdict = ratio.compareTo(BigDecimal.ONE) < 0 ? "划算" : "不划算";
        return percent.toPlainString() + "% （" + zhe.toPlainString() + " 折，" + verdict + "）";
    }

    /** 把性价比格式化成 {@code 1.2424（每花 ¥1 到手 ¥1.2424 余额）}。 */
    public static String formatValueRatio(BigDecimal ratio) {
        BigDecimal shown = ratio.setScale(4, RoundingMode.HALF_UP);
        return shown.toPlainString() + "（每花 ¥1 到手 ¥" + shown.toPlainString() + " 余额）";
    }

    private static String formatSurplus(CalcResult result) {
        if (result.surplus().isZero()) {
            return "¥0.00（刚好达标）";
        }
        if (result.surplus().isNegative()) {
            return "尚差 " + result.surplus().times(-1).toGroupedString();
        }
        return result.surplus().toGroupedString();
    }

    private static String padRight(String text, int width) {
        int padding = width - displayWidth(text);
        return padding <= 0 ? text : text + " ".repeat(padding);
    }

    /** 终端显示宽度：CJK 与全角字符按 2 列计算，供中文表格对齐复用。 */
    public static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) {
            width += isWide(text.charAt(i)) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F)
                || (c >= 0x2E80 && c <= 0x303E)
                || (c >= 0x3041 && c <= 0x33FF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0xA000 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }
}
