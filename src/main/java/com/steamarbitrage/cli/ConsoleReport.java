package com.steamarbitrage.cli;

import com.steamarbitrage.core.AdviceCalculator;
import com.steamarbitrage.core.AdviceNarrator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.Formats;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.PurchaseAdvice;
import com.steamarbitrage.core.PurchasePlan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 把 {@link CalcResult} 渲染成终端报告。
 *
 * <p>报告按“显示宽度”而不是字符个数对齐：中文、全角括号占 2 列，
 * ASCII 占 1 列，因此直接按 {@code String.length()} 补空格会在中英混排时错位。
 *
 * <p>折扣格式与购买建议的措辞都委托给 {@link Formats} 与 {@link AdviceNarrator}，
 * 图形界面用的是同一套，两个入口的说法不会分叉。
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
        sb.append('\n');

        appendAdvice(sb, result);

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
        List<String> cautions = AdviceNarrator.cautions(result);
        if (cautions.isEmpty()) {
            return "";
        }
        return String.join("\n", cautions) + "\n";
    }

    // ── 内部渲染工具 ────────────────────────────────────────────────────────

    private static void row(StringBuilder sb, String label, String value) {
        sb.append(' ').append(padRight(label, LABEL_WIDTH))
          .append(" : ").append(value).append('\n');
    }

    private static void subRow(StringBuilder sb, String label, String value) {
        sb.append("   ").append(padRight(label, LABEL_WIDTH)).append(" : ").append(value).append('\n');
    }

    /** 把折扣比率格式化成 {@code 80.49% （8.05 折，划算）}。实现见 {@link Formats#discount}。 */
    public static String formatDiscount(BigDecimal ratio) {
        return Formats.discount(ratio);
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

    // ── 购买建议 ────────────────────────────────────────────────────────────

    private static void appendAdvice(StringBuilder sb, CalcResult result) {
        PurchaseAdvice advice = AdviceCalculator.advise(result);

        sb.append(THIN).append('\n');
        sb.append(" [购买建议]\n");
        sb.append(THIN).append('\n');

        if (advice.identical()) {
            for (String line : AdviceNarrator.reasons(result, advice)) {
                sb.append(' ').append(line).append('\n');
            }
            sb.append(RULE).append('\n');
            return;
        }

        appendPlan(sb, advice.topUpPlan());
        sb.append('\n');
        appendPlan(sb, advice.fullFlipPlan());
        sb.append('\n');
        appendRecommendation(sb, result, advice);

        sb.append(RULE).append('\n');
    }

    private static void appendPlan(StringBuilder sb, PurchasePlan plan) {
        sb.append(' ').append(plan.kind().label()).append('\n');

        String itemText = plan.itemCount() + " 件";
        if (plan.isPureTopUp()) {
            itemText += "（全部走充值，不买饰品）";
        }

        subRow(sb, "饰品件数", itemText);
        subRow(sb, "饰品支出", plan.itemCost().toGroupedString());
        subRow(sb, "充值补差", plan.topUp().toGroupedString());
        subRow(sb, "现金支出", plan.totalSpend().toGroupedString());
        subRow(sb, "最终余额", plan.balance().toGroupedString() + describeDeviation(plan));
        subRow(sb, "单位余额成本", formatDiscount(plan.costRatio()));
    }

    private static String describeDeviation(PurchasePlan plan) {
        if (plan.deviation().isZero()) {
            return "（恰好达标）";
        }
        if (plan.deviation().isNegative()) {
            return "（尚差 " + plan.deviation().times(-1).toGroupedString() + "）";
        }
        return "（超出 " + plan.deviation().toGroupedString() + "）";
    }

    /** 推荐结论与理由，措辞由 {@link AdviceNarrator} 提供。 */
    private static void appendRecommendation(StringBuilder sb, CalcResult result, PurchaseAdvice advice) {
        sb.append(" ★ 推荐：").append(advice.recommendedPlan().kind().label()).append('\n');
        for (String line : AdviceNarrator.reasons(result, advice)) {
            sb.append("   ").append(line).append('\n');
        }
    }

    // ── 中文宽度 ────────────────────────────────────────────────────────────

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
