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

import javax.swing.JComboBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 图形界面自检。
 *
 * <p>只创建 {@link SwingApp} 的组件对象并驱动它的计算流程，不显示窗口，
 * 因此不会在桌面上留下任何残留程序。重点验证两件容易出错的事：
 * <ol>
 *   <li>下拉框的显示顺序与它携带的枚举值是否对得上（买家价 / 卖家价、CS2 / TF2）</li>
 *   <li>界面渲染出来的报告是否与命令行逐字一致</li>
 * </ol>
 *
 * <p>无图形显示的环境下会直接跳过并以 0 退出，不会让整套自测失败。
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

            // ── 用例 A：默认档位（CS2 / 买家支付价 / 精确模式）──
            setText(app, "purchaseField", "70");
            setText(app, "sellField", "100");
            setText(app, "targetField", "1000");
            compute(app);

            CalcResult expected = BalanceCalculator.calculate(new CalcRequest(
                    Money.parse("70"), Money.parse("100"), PriceType.BUYER_PAID,
                    GameProfile.CS2, FeeMode.EXACT, Money.parse("1000")));
            check("GUI 报告与命令行逐字一致", ConsoleReport.render(expected), reportText(app));

            // ── 用例 B：下拉框切到 TF2 与「卖家到手价」──
            setCombo(app, "gameBox", 1);
            setCombo(app, "priceTypeBox", 1);
            setText(app, "sellField", "86.97");
            compute(app);
            String tf2Report = reportText(app);

            checkTrue("下拉框第 2 项是 TF2", tf2Report.contains("Team Fortress 2 (TF2)"));
            checkTrue("下拉框第 2 项是卖家到手价", tf2Report.contains("卖家到手价（Steam 卖家收入）"));
            checkTrue("切换后件数仍为 12 件", tf2Report.contains("12 件"));
            checkTrue("切换后折扣仍为 80.49%", tf2Report.contains("80.49%"));
            checkTrue("切换后不再扣手续费", tf2Report.contains("Steam 到手价  : ¥86.97"));

            // ── 用例 C：下拉框切到近似模式 ──
            setCombo(app, "feeModeBox", 1);
            compute(app);
            String simpleReport = reportText(app);
            checkTrue("下拉框第 2 项是近似模式", simpleReport.contains("粗略近似"));
            checkTrue("近似模式给出精确值对照", simpleReport.contains("¥86.97"));

            // ── 用例 D：输入残缺时不应崩溃 ──
            setText(app, "targetField", "");
            compute(app);
            checkTrue("空输入显示等待提示", reportText(app).contains("等待有效输入"));
            setText(app, "targetField", "abc");
            compute(app);
            checkTrue("非法输入显示错误提示", reportText(app).contains("无法识别的金额"));
            setText(app, "targetField", "1000");
            compute(app);
            checkTrue("恢复合法输入后重新出结果", reportText(app).contains("需购买数量"));
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

    private static Object field(Object app, String name) throws Exception {
        Field field = SwingApp.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(app);
    }

    private static void setText(Object app, String name, String value) throws Exception {
        ((JTextField) field(app, name)).setText(value);
    }

    private static void setCombo(Object app, String name, int index) throws Exception {
        ((JComboBox<?>) field(app, name)).setSelectedIndex(index);
    }

    private static void compute(Object app) throws Exception {
        Method method = SwingApp.class.getDeclaredMethod("compute");
        method.setAccessible(true);
        method.invoke(app);
    }

    private static String reportText(Object app) throws Exception {
        return ((JTextArea) field(app, "reportArea")).getText();
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
