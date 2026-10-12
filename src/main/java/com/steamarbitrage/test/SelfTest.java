package com.steamarbitrage.test;

import com.steamarbitrage.cli.ArgParser;
import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeCalculator;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 零依赖自测套件。
 *
 * <p>没有引入 JUnit：项目不使用构建工具，保持「克隆下来 javac 就能跑」。
 * 断言失败会逐条打印，全部通过时退出码为 0，失败为 1，可直接接到 CI 上。
 */
public final class SelfTest {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private SelfTest() {
    }

    public static void main(String[] args) {
        testMoney();
        testGameProfile();
        testFeeCalculator();
        testFeeRoundTrip();
        testReachablePrices();
        testCalculation();
        testSellerPriceType();
        testSimpleMode();
        testEdgeCases();
        testCeilDiv();
        testArgParser();
        testReportRendering();

        System.out.println("=".repeat(60));
        System.out.printf("自测完成：通过 %d 项，失败 %d 项%n", passed, failures.size());
        if (!failures.isEmpty()) {
            System.out.println("-".repeat(60));
            for (String failure : failures) {
                System.out.println("  ✗ " + failure);
            }
        }
        System.out.println("=".repeat(60));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ── 各测试组 ────────────────────────────────────────────────────────────

    private static void testMoney() {
        checkLong("Money.parse(\"70\") 分", 7000L, Money.parse("70").cents());
        checkLong("Money.parse(\"¥1,234.56\") 分", 123456L, Money.parse("¥1,234.56").cents());
        checkLong("Money.parse(\" 86.9 \") 分", 8690L, Money.parse(" 86.9 ").cents());
        checkLong("Money.parse(\"0.005\") 四舍五入到分", 1L, Money.parse("0.005").cents());

        check("千分位格式（大额）", "¥1,234.56", Money.ofCents(123456).toGroupedString());
        check("千分位格式（负数）", "-¥1,234.56", Money.ofCents(-123456).toGroupedString());
        check("千分位格式（小额）", "¥1.00", Money.ofCents(100).toGroupedString());
        check("千分位格式（零）", "¥0.00", Money.ofCents(0).toGroupedString());

        checkLong("乘法不丢精度", 84000L, Money.ofCents(7000).times(12).cents());
        checkLong("加减法", 4397L, Money.ofCents(8697).minus(Money.ofCents(4300)).cents());

        checkThrows("非法金额文本", IllegalArgumentException.class, () -> Money.parse("abc"));
        checkThrows("空金额文本", IllegalArgumentException.class, () -> Money.parse("   "));
        checkThrows("空金额（null）", IllegalArgumentException.class, () -> Money.parse(null));
    }

    private static void testGameProfile() {
        check("CS2 Steam 手续费（8697 分）", 434L, GameProfile.CS2.steamFeeCents(8697));
        check("CS2 游戏手续费（8697 分）", 869L, GameProfile.CS2.gameFeeCents(8697));
        check("CS2 买家支付（8697 分）", 10000L, GameProfile.CS2.buyerPaysCents(8697));

        // 最低手续费保底：卖家收入 1 分时两项手续费各保底 1 分
        check("最低手续费保底（1 分）", 3L, GameProfile.CS2.buyerPaysCents(1));
        check("零收入不收费", 0L, GameProfile.CS2.buyerPaysCents(0));

        check("CS2 总费率万分比", 1500, GameProfile.CS2.totalFeeBp());
        check("TF2 总费率万分比", 1500, GameProfile.TF2.totalFeeBp());
        check("内置档案数量", 2, GameProfile.builtIns().size());

        check("id 查表（cs2）", "cs2", GameProfile.byId("cs2").id());
        check("id 查表（大小写不敏感）", "cs2", GameProfile.byId("CS2").id());
        check("别名查表（csgo）", "cs2", GameProfile.byId("csgo").id());
        check("id 查表（tf2）", "tf2", GameProfile.byId("tf2").id());

        checkTrue("费率描述包含合计 15%", GameProfile.CS2.feeDescription().contains("15%"));
        checkTrue("TF2 菜单项包含 id", GameProfile.TF2.menuLabel().contains("[tf2]"));

        checkThrows("未知游戏", IllegalArgumentException.class, () -> GameProfile.byId("dota2"));
    }

    private static void testFeeCalculator() {
        FeeCalculator exact = new FeeCalculator(GameProfile.CS2, FeeMode.EXACT);
        FeeCalculator simple = new FeeCalculator(GameProfile.CS2, FeeMode.SIMPLE);

        check("精确反解 10000 分", 8697L, exact.sellerReceivesCents(10000));
        check("近似反解 10000 分", 8695L, simple.sellerReceivesCents(10000));

        // Steam 市场最低档位：买家付 0.03，卖家得 0.01
        check("最低档位 3 分", 1L, exact.sellerReceivesCents(3));
        check("2 分无法成交", 0L, exact.sellerReceivesCents(2));
        check("1 分无法成交", 0L, exact.sellerReceivesCents(1));
        check("0 分", 0L, exact.sellerReceivesCents(0));
        check("负数按 0 处理", 0L, exact.sellerReceivesCents(-100));

        check("正向换算（8697 -> 10000）", 10000L, exact.buyerPaysCents(8697));
        check("正向换算（100 -> 115）", 115L, exact.buyerPaysCents(100));
    }

    private static void testFeeRoundTrip() {
        // buyerPays 严格递增，因此「正向再反向」必须精确回到原点
        FeeCalculator exact = new FeeCalculator(GameProfile.CS2, FeeMode.EXACT);
        int mismatches = 0;
        String firstMismatch = null;
        for (long sellerCents = 1; sellerCents <= 3000; sellerCents++) {
            long buyerPays = exact.buyerPaysCents(sellerCents);
            long recovered = exact.sellerReceivesCents(buyerPays);
            if (recovered != sellerCents) {
                mismatches++;
                if (firstMismatch == null) {
                    firstMismatch = "seller=" + sellerCents + " -> buyer=" + buyerPays
                            + " -> back=" + recovered;
                }
            }
        }
        checkLong("往返换算 1..3000 分无偏差", 0L, mismatches);
        if (firstMismatch != null) {
            failures.add("首个往返偏差：" + firstMismatch);
        }
    }

    private static void testReachablePrices() {
        FeeCalculator exact = new FeeCalculator(GameProfile.CS2, FeeMode.EXACT);

        // 卖家收入 99 分 -> 买家支付 99+4+9 = 112；卖家收入 100 分 -> 115
        // 因此 112 可达，113 与 114 是空档，115 可达
        checkTrue("112 分是真实档位", exact.isReachableBuyerPrice(112));
        checkTrue("113 分不是真实档位", !exact.isReachableBuyerPrice(113));
        checkTrue("114 分不是真实档位", !exact.isReachableBuyerPrice(114));
        checkTrue("115 分是真实档位", exact.isReachableBuyerPrice(115));
        checkTrue("3 分是真实档位", exact.isReachableBuyerPrice(3));
        checkTrue("2 分低于最低价", !exact.isReachableBuyerPrice(2));

        checkLong("113 分的下档位", 112L, exact.nearestReachableBuyerPriceBelow(113));
        checkLong("113 分的上档位", 115L, exact.nearestReachableBuyerPriceAbove(113));
        checkLong("10000 分本身可达", 10000L, exact.nearestReachableBuyerPriceBelow(10000));

        // 回归：档位校验必须始终走精确公式。
        // 曾经因为用近似到手价（8695）去反查，把真实可达的 10000 分误报成空档位。
        FeeCalculator simpleCalc = new FeeCalculator(GameProfile.CS2, FeeMode.SIMPLE);
        checkTrue("近似模式下 10000 分仍判定为可达", simpleCalc.isReachableBuyerPrice(10000));
        checkTrue("近似模式下 113 分仍判定为不可达", !simpleCalc.isReachableBuyerPrice(113));
        checkLong("近似模式下 10000 分的下档位", 10000L,
                simpleCalc.nearestReachableBuyerPriceBelow(10000));
        checkLong("近似模式下 113 分的上档位", 115L,
                simpleCalc.nearestReachableBuyerPriceAbove(113));
    }

    private static void testCalculation() {
        // 基准算例：BUFF 购入 70 元，Steam 挂单 100 元，目标余额 1000 元
        CalcResult result = calculate("70", "100", "1000",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);

        check("单件到手余额", 8697L, result.sellerReceivesCents());
        check("单件买家实付", 10000L, result.buyerPaysCents());
        check("单件 Steam 手续费", 434L, result.steamFeePerItem().cents());
        check("单件游戏手续费", 869L, result.gameFeePerItem().cents());

        check("需购买件数", 12L, result.itemCount());
        check("总成本", 84000L, result.totalCostCents());
        check("实际到手余额", 104364L, result.actualBalanceCents());
        check("超出目标", 4364L, result.surplus().cents());

        checkDecimal("整体折扣百分比", "80.49", result.discountPercent());
        checkDecimal("整体折扣（几折）", "8.05", result.discountZhe());
        checkDecimal("单件折扣与整体一致", "0.804875", result.unitDiscountRatio());
        checkDecimal("性价比", "1.2424", result.valueRatio());

        // 折合折扣 = 总成本 / 实际到手余额，用整数关系独立验算一遍
        checkDecimal("折扣等于成本除以余额",
                new BigDecimal(result.totalCostCents())
                        .divide(new BigDecimal(result.actualBalanceCents()), 6, RoundingMode.HALF_UP)
                        .toPlainString(),
                result.discountRatio());

        // 目标能整除时不应多买
        CalcResult exact5 = calculate("70", "100", "434.85",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        check("整除目标：件数", 5L, exact5.itemCount());
        check("整除目标：无多余", 0L, exact5.surplus().cents());

        // 目标恰好等于单件到手
        CalcResult one = calculate("70", "100", "86.97",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        check("目标等于单件到手：件数", 1L, one.itemCount());

        // 目标极小也要买一件
        CalcResult tiny = calculate("70", "100", "0.01",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        check("目标极小：件数", 1L, tiny.itemCount());
    }

    private static void testSellerPriceType() {
        // 直接给到手价 86.97，结果应与买家支付价 100 完全一致（不再扣手续费）
        CalcResult result = calculate("70", "86.97", "1000",
                PriceType.SELLER_RECEIVE, GameProfile.CS2, FeeMode.EXACT);

        check("seller 模式：单件到手", 8697L, result.sellerReceivesCents());
        check("seller 模式：件数", 12L, result.itemCount());
        check("seller 模式：总成本", 84000L, result.totalCostCents());
        check("seller 模式：实际到手", 104364L, result.actualBalanceCents());
        checkDecimal("seller 模式：折扣", "80.49", result.discountPercent());
        checkTrue("seller 模式不需要档位校验", result.isMarketPriceReachable());

        // TF2 走同一套算法，费率同为 15%，因此结果应与 CS2 相同
        CalcResult tf2 = calculate("70", "86.97", "1000",
                PriceType.SELLER_RECEIVE, GameProfile.TF2, FeeMode.EXACT);
        check("TF2 结果与 CS2 一致", result.actualBalanceCents(), tf2.actualBalanceCents());
    }

    private static void testSimpleMode() {
        CalcResult exact = calculate("70", "100", "1000",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        CalcResult simple = calculate("70", "100", "1000",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.SIMPLE);

        check("近似模式：单件到手", 8695L, simple.sellerReceivesCents());
        check("近似模式：件数", 12L, simple.itemCount());
        check("近似模式：实际到手", 104340L, simple.actualBalanceCents());
        checkDecimal("近似模式：折扣", "80.51", simple.discountPercent());
        checkTrue("精确模式到手更高", exact.sellerReceivesCents() > simple.sellerReceivesCents());
    }

    private static void testEdgeCases() {
        // 售价低于 0.03 时卖家实得为 0，应明确报错而不是除零
        checkThrows("售价 0.02 无法倒余额", IllegalStateException.class,
                () -> calculate("70", "0.02", "1000",
                        PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT));

        checkThrows("购入价为 0", IllegalArgumentException.class,
                () -> new CalcRequest(Money.ofCents(0), Money.parse("100"),
                        PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT, Money.parse("1000")));
        checkThrows("购入价为负", IllegalArgumentException.class,
                () -> new CalcRequest(Money.ofCents(-100), Money.parse("100"),
                        PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT, Money.parse("1000")));
        checkThrows("目标余额为 0", IllegalArgumentException.class,
                () -> new CalcRequest(Money.parse("70"), Money.parse("100"),
                        PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT, Money.ofCents(0)));
        checkThrows("购入价为 null", IllegalArgumentException.class,
                () -> new CalcRequest(null, Money.parse("100"),
                        PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT, Money.parse("1000")));

        // 单件计算（GUI 在用户还没填目标余额时预览用）
        CalcResult unit = BalanceCalculator.calculateUnit(new CalcRequest(
                Money.parse("70"), Money.parse("100"), PriceType.BUYER_PAID,
                GameProfile.CS2, FeeMode.EXACT, Money.parse("1")));
        check("单件计算：件数", 1L, unit.itemCount());
        check("单件计算：到手", 8697L, unit.sellerReceivesCents());
        checkDecimal("单件计算：折扣", "80.49", unit.discountPercent());
    }

    private static void testCeilDiv() {
        check("ceilDiv(10,3)", 4L, BalanceCalculator.ceilDiv(10, 3));
        check("ceilDiv(9,3)", 3L, BalanceCalculator.ceilDiv(9, 3));
        check("ceilDiv(1,3)", 1L, BalanceCalculator.ceilDiv(1, 3));
        check("ceilDiv(0,5)", 0L, BalanceCalculator.ceilDiv(0, 5));
        check("ceilDiv(100000,8697)", 12L, BalanceCalculator.ceilDiv(100000, 8697));
        checkThrows("ceilDiv 除数为 0", ArithmeticException.class,
                () -> BalanceCalculator.ceilDiv(10, 0));
    }

    private static void testArgParser() {
        ArgParser.Options longForm = ArgParser.parse(
                new String[]{"--buy", "70", "--sell", "100", "--target", "1000"});
        CalcRequest request = longForm.toRequest();
        check("长参数：购入价", 7000L, request.purchasePrice().cents());
        check("长参数：售价", 10000L, request.marketPrice().cents());
        check("长参数：目标", 100000L, request.targetBalance().cents());
        check("长参数：默认游戏", "cs2", request.gameProfile().id());
        check("长参数：默认价格语义", PriceType.BUYER_PAID, request.priceType());
        check("长参数：默认模式", FeeMode.EXACT, request.feeMode());

        ArgParser.Options equalsForm = ArgParser.parse(
                new String[]{"--buy=70", "--sell=100", "--target=1000"});
        check("等号写法：目标", 100000L, equalsForm.toRequest().targetBalance().cents());

        ArgParser.Options positional = ArgParser.parse(new String[]{"70", "100", "1000"});
        check("位置参数：售价", 10000L, positional.toRequest().marketPrice().cents());

        ArgParser.Options mixed = ArgParser.parse(
                new String[]{"--game", "tf2", "--price-type", "seller", "70", "86.97", "1000"});
        check("混合写法：游戏", "tf2", mixed.gameProfile().id());
        check("混合写法：价格语义", PriceType.SELLER_RECEIVE, mixed.priceType());
        check("混合写法：售价", 8697L, mixed.toRequest().marketPrice().cents());

        ArgParser.Options simple = ArgParser.parse(
                new String[]{"--fee-mode=simple", "70", "100", "1000"});
        check("模式参数", FeeMode.SIMPLE, simple.feeMode());

        checkTrue("--help 被识别", ArgParser.parse(new String[]{"--help"}).help());
        checkTrue("-h 被识别", ArgParser.parse(new String[]{"-h"}).help());
        checkTrue("--gui 被识别", ArgParser.parse(new String[]{"--gui"}).gui());

        checkThrows("未知参数", IllegalArgumentException.class,
                () -> ArgParser.parse(new String[]{"--nope"}));
        checkThrows("缺少取值", IllegalArgumentException.class,
                () -> ArgParser.parse(new String[]{"--buy"}));
        checkThrows("多余位置参数", IllegalArgumentException.class,
                () -> ArgParser.parse(new String[]{"70", "100", "1000", "extra"}));
        checkThrows("缺少必填参数", IllegalArgumentException.class,
                () -> ArgParser.parse(new String[]{"70"}).toRequest());
        checkThrows("未知游戏参数", IllegalArgumentException.class,
                () -> ArgParser.parse(new String[]{"--game", "dota2", "70", "100", "1000"}));
    }

    private static void testReportRendering() {
        check("显示宽度：中文按 2 列", 4, ConsoleReport.displayWidth("中文"));
        check("显示宽度：ASCII 按 1 列", 3, ConsoleReport.displayWidth("abc"));
        check("显示宽度：人民币符号按 1 列", 1, ConsoleReport.displayWidth("¥"));
        check("显示宽度：全角括号按 2 列", 4, ConsoleReport.displayWidth("（）"));

        check("折扣格式化（划算）", "80.49% （8.05 折，划算）",
                ConsoleReport.formatDiscount(new BigDecimal("0.804875")));
        check("折扣格式化（不划算）", "115.00% （11.50 折，不划算）",
                ConsoleReport.formatDiscount(new BigDecimal("1.15")));
        check("性价比格式化", "1.2424（每花 ¥1 到手 ¥1.2424 余额）",
                ConsoleReport.formatValueRatio(new BigDecimal("1.2424285714")));

        CalcResult result = calculate("70", "100", "1000",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        String report = ConsoleReport.render(result);

        checkTrue("报告包含游戏档案", report.contains("Counter-Strike 2"));
        checkTrue("报告包含费率 15%", report.contains("15%"));
        checkTrue("报告包含单件到手", report.contains("¥86.97"));
        checkTrue("报告包含需购买数量", report.contains("12 件"));
        checkTrue("报告包含总成本", report.contains("¥840.00"));
        checkTrue("报告包含实际到手", report.contains("¥1,043.64"));
        checkTrue("报告包含折合折扣", report.contains("80.49%"));
        checkTrue("报告包含目标余额", report.contains("¥1,000.00"));
        checkTrue("报告包含超出目标", report.contains("¥43.64"));
        checkTrue("可达档位不产生警告", ConsoleReport.warnings(result).isEmpty());

        // 不可达档位必须提醒
        CalcResult unreachable = calculate("70", "1.13", "100",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.EXACT);
        checkTrue("不可达档位产生警告", ConsoleReport.warnings(unreachable).contains("不是 Steam 市场的真实挂单档位"));
        check("不可达档位：按不超过该价的最高档位取到手", 99L, unreachable.sellerReceivesCents());
        check("不可达档位：下档位", 112L, unreachable.nearestReachableBelow().cents());
        check("不可达档位：上档位", 115L, unreachable.nearestReachableAbove().cents());

        // 近似模式必须提醒，但不得把真实可达的档位误报为空档位
        CalcResult simple = calculate("70", "100", "1000",
                PriceType.BUYER_PAID, GameProfile.CS2, FeeMode.SIMPLE);
        String simpleWarnings = ConsoleReport.warnings(simple);
        checkTrue("近似模式产生警告", simpleWarnings.contains("simple 近似模式"));
        checkTrue("近似模式不误报空档位", !simpleWarnings.contains("不是 Steam 市场的真实挂单档位"));
        checkTrue("近似模式提示精确到手值", simpleWarnings.contains("¥86.97"));

        checkTrue("摘要行包含件数", ConsoleReport.renderSummaryLine(result).contains("12 件"));
    }

    // ── 断言工具 ────────────────────────────────────────────────────────────

    private static CalcResult calculate(String buy, String sell, String target,
                                        PriceType priceType, GameProfile profile, FeeMode mode) {
        return BalanceCalculator.calculate(new CalcRequest(
                Money.parse(buy), Money.parse(sell), priceType, profile, mode, Money.parse(target)));
    }

    private static void check(String name, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            failures.add(name + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void checkLong(String name, long expected, long actual) {
        if (expected == actual) {
            passed++;
        } else {
            failures.add(name + "：期望 " + expected + "，实际 " + actual);
        }
    }

    private static void checkTrue(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name + "：条件不成立");
        }
    }

    private static void checkDecimal(String name, String expected, BigDecimal actual) {
        BigDecimal expectedValue = new BigDecimal(expected);
        BigDecimal rounded = actual == null
                ? null
                : actual.setScale(expectedValue.scale(), RoundingMode.HALF_UP);
        if (rounded != null && expectedValue.compareTo(rounded) == 0) {
            passed++;
        } else {
            failures.add(name + "：期望约 " + expected + "，实际 " + actual);
        }
    }

    private static void checkThrows(String name, Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            failures.add(name + "：期望抛出 " + type.getSimpleName() + "，但没有抛出");
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                passed++;
            } else {
                failures.add(name + "：期望抛出 " + type.getSimpleName()
                        + "，实际抛出 " + t.getClass().getSimpleName() + "（" + t.getMessage() + "）");
            }
        }
    }
}
