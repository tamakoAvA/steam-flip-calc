package com.steamarbitrage.test;

import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;
import com.steamarbitrage.gui.SwingApp;
import com.steamarbitrage.gui.view.InputPanel;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 图形界面自检。
 *
 * <p>实例化 {@link SwingApp} 并驱动它的计算流程，但<b>不显示窗口</b>，
 * 因此不会在桌面上留下残留程序。重点验证四件事：
 * <ol>
 *   <li>结构化面板显示的数字确实来自 {@code CalcResult}</li>
 *   <li>下拉框的显示顺序与它携带的枚举值对得上</li>
 *   <li>响应式断点按窗口宽度正确切换列数</li>
 *   <li>输入残缺时清空面板而不是崩溃</li>
 * </ol>
 *
 * <p>无图形显示的环境下直接跳过并以 0 退出，不会让整套自测失败。
 */
public final class GuiSelfCheck {

    private static int passed;
    private static final List<String> failures = new ArrayList<>();

    private GuiSelfCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("跳过 GUI 自检：当前环境没有图形显示。");
            System.exit(0);
        }

        SwingUtilities.invokeAndWait(GuiSelfCheck::check);

        System.out.println("=".repeat(60));
        System.out.printf("GUI 自检完成：通过 %d 项，失败 %d 项%n", passed, failures.size());
        if (!failures.isEmpty()) {
            System.out.println("-".repeat(60));
            for (String failure : failures) {
                System.out.println("  ✗ " + failure);
            }
        }
        System.out.println("=".repeat(60));
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void check() {
        try {
            Object app = newApp();
            // 组装界面容器（不显示窗口），使各面板字段与 splitPanel 就绪
            invoke(app, "buildRoot");
            InputPanel input = (InputPanel) field(app, "inputPanel");

            // ── 用例 A：默认输入（CS2 / 买家支付价 / 精确模式）──
            compute(app);
            CalcResult result = lastResult(app);
            check("GUI 计算：单件到手", 8697L, result.sellerReceivesCents());
            check("GUI 计算：件数", 12L, result.itemCount());
            check("GUI 计算：总成本", 84000L, result.totalCostCents());
            check("GUI 计算：实际到手余额", 104364L, result.actualBalanceCents());

            String metrics = textOf(app, "metricsPanel");
            checkTrue("指标卡显示「8.05 折」", metrics.contains("8.05 折"));
            checkTrue("指标卡显示「12 件」", metrics.contains("12 件"));
            checkTrue("指标卡显示总成本", metrics.contains("¥840.00"));
            checkTrue("指标卡显示实际到手余额", metrics.contains("¥1,043.64"));

            String detail = textOf(app, "detailPanel");
            checkTrue("明细区显示卖家到手余额", detail.contains("¥86.97"));
            checkTrue("明细区显示 Steam 手续费", detail.contains("¥4.34"));
            checkTrue("明细区显示游戏手续费", detail.contains("¥8.69"));
            checkTrue("明细区显示性价比", detail.contains("1.2424"));
            checkTrue("明细区显示目标余额", detail.contains("¥1,000.00"));

            String advice = textOf(app, "advicePanel");
            checkTrue("建议区显示两种买法",
                    advice.contains("方式 A · 少倒 + 补差价")
                            && advice.contains("方式 B · 多倒 + 全额余额"));
            checkTrue("建议区标注推荐方式 B", advice.contains("★ 推荐：方式 B"));
            checkTrue("建议区给出边际折合", advice.contains("61.11%"));
            checkTrue("建议区给出方式 A 的补差", advice.contains("¥43.33"));
            checkTrue("建议区给出方式 A 的现金支出", advice.contains("¥813.33"));

            // ── 用例 B：下拉框切到 TF2 + 卖家到手价 ──
            input.selectGame(1);
            input.selectPriceType(1);
            input.sellField().setText("86.97");
            compute(app);

            CalcResult tf2 = lastResult(app);
            check("下拉框第 2 项游戏是 TF2", "tf2", tf2.request().gameProfile().id());
            check("下拉框第 2 项语义是卖家到手价", PriceType.SELLER_RECEIVE, tf2.request().priceType());
            check("切换后件数仍为 12", 12L, tf2.itemCount());
            check("切换后实付仍为 100", 10000L, tf2.buyerPaysCents());
            checkTrue("明细区标签随语义变为「Steam 到手价」",
                    textOf(app, "detailPanel").contains("Steam 到手价"));

            // ── 用例 C：切回买家支付价 + 近似模式，应出现注意事项 ──
            input.selectPriceType(0);
            input.sellField().setText("100");
            input.selectFeeMode(1);
            compute(app);
            checkTrue("近似模式显示注意事项",
                    textOf(app, "advicePanel").contains("simple 近似模式"));

            // ── 用例 D：输入残缺时不崩溃，面板回到占位态 ──
            input.selectFeeMode(0);
            input.targetField().setText("");
            compute(app);
            checkTrue("空输入清空指标卡", textOf(app, "metricsPanel").contains("—"));
            checkTrue("空输入时没有计算结果", lastResult(app) == null);

            input.targetField().setText("abc");
            compute(app);
            checkTrue("非法输入仍无计算结果", lastResult(app) == null);

            input.targetField().setText("0");
            compute(app);
            checkTrue("零值输入仍无计算结果", lastResult(app) == null);

            input.resetToDefaults();
            compute(app);
            checkTrue("恢复默认后重新出结果", lastResult(app) != null);

            // ── 用例 E：响应式断点 ──
            invokeWithInt(app, "applyResponsiveLayout", 1300);
            check("宽屏 1300px：指标卡 4 列", 4, metricColumns(app));
            invokeWithInt(app, "applyResponsiveLayout", 900);
            check("窄屏 900px：指标卡 2 列", 2, metricColumns(app));
            invokeWithInt(app, "applyResponsiveLayout", 1300);
            check("回到宽屏：恢复 4 列", 4, metricColumns(app));

            // ── 用例 F：界面持有的结果与命令行同源输入完全一致 ──
            CalcResult expected = BalanceCalculator.calculate(new CalcRequest(
                    Money.parse("70"), Money.parse("100"), PriceType.BUYER_PAID,
                    GameProfile.CS2, FeeMode.EXACT, Money.parse("1000")));
            check("GUI 报告与命令行逐字一致",
                    ConsoleReport.render(expected), ConsoleReport.render(lastResult(app)));
        } catch (Throwable t) {
            failures.add("GUI 自检抛出异常：" + t);
        }
    }

    // ── 反射驱动 ────────────────────────────────────────────────────────────

    private static Object newApp() throws Exception {
        Constructor<SwingApp> constructor = SwingApp.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static void invokeWithInt(Object target, String name, int value) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, int.class);
        method.setAccessible(true);
        method.invoke(target, value);
    }

    private static void compute(Object app) throws Exception {
        invoke(app, "compute");
    }

    private static CalcResult lastResult(Object app) throws Exception {
        return (CalcResult) field(app, "lastResult");
    }

    /** 递归收集某个面板下所有 JLabel / JTextArea 的文本。 */
    private static String textOf(Object app, String panelField) throws Exception {
        StringBuilder sb = new StringBuilder();
        collectText((Component) field(app, panelField), sb);
        return sb.toString();
    }

    private static void collectText(Component component, StringBuilder sb) {
        if (component instanceof JLabel label && label.getText() != null) {
            sb.append(label.getText()).append('\n');
        }
        if (component instanceof JTextArea area) {
            sb.append(area.getText()).append('\n');
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectText(child, sb);
            }
        }
    }

    private static int metricColumns(Object app) throws Exception {
        JPanel metrics = (JPanel) field(app, "metricsPanel");
        return ((GridLayout) metrics.getLayout()).getColumns();
    }

    // ── 断言 ────────────────────────────────────────────────────────────────

    private static void check(String name, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            failures.add(name + "：\n      期望 >>> " + expected + "\n      实际 >>> " + actual);
        }
    }

    private static void checkTrue(String name, boolean condition) {
        if (condition) {
            passed++;
        } else {
            failures.add(name + "：条件不成立");
        }
    }
}
