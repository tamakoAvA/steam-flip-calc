package com.steamarbitrage.test;

import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;
import com.steamarbitrage.gui.Section;
import com.steamarbitrage.gui.SwingApp;
import com.steamarbitrage.gui.view.InputPanel;
import com.steamarbitrage.gui.view.SectionCard;
import com.steamarbitrage.gui.view.SideDrawer;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
 * <p>实例化 {@link SwingApp} 并驱动它的计算与布局流程，但<b>不显示窗口</b>，
 * 因此不会在桌面上留下残留程序。重点验证：
 * <ol>
 *   <li>结构化面板显示的数字确实来自 {@code CalcResult}</li>
 *   <li>默认上下顺序为「购买建议 → 单件指标 → 按目标余额倒推」</li>
 *   <li>图钉能把卡片搬到固定区、再点一次能搬回来</li>
 *   <li>侧边开关能隐藏 / 恢复卡片，且隐藏时自动取消置顶</li>
 *   <li>响应式断点按窗口宽度切换指标卡列数</li>
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
            invoke(app, "buildRoot");

            InputPanel input = (InputPanel) field(app, "inputPanel");
            SectionCard advice = (SectionCard) field(app, "advicePanel");
            SectionCard singleItem = (SectionCard) field(app, "singleItemPanel");
            SectionCard target = (SectionCard) field(app, "targetPanel");
            SideDrawer drawer = (SideDrawer) field(app, "sideDrawer");
            JPanel content = (JPanel) field(app, "contentPanel");
            JPanel pinned = (JPanel) field(app, "pinnedPanel");
            JScrollPane pinnedScroll = (JScrollPane) field(app, "pinnedScroll");

            // ── 用例 A：默认输入的数值 ──
            compute(app);
            CalcResult result = lastResult(app);
            check("计算：单件到手", 8697L, result.sellerReceivesCents());
            check("计算：件数", 12L, result.itemCount());
            check("计算：总成本", 84000L, result.totalCostCents());
            check("计算：实际到手余额", 104364L, result.actualBalanceCents());

            // ── 用例 B：各面板显示的数字 ──
            String metrics = textOf(app, "metricsPanel");
            checkTrue("指标卡显示「8.05 折」", metrics.contains("8.05 折"));
            checkTrue("指标卡显示「12 件」", metrics.contains("12 件"));
            checkTrue("指标卡显示总成本", metrics.contains("¥840.00"));

            String adviceText = textOf(app, "advicePanel");
            checkTrue("建议区显示两种买法",
                    adviceText.contains("方式 A · 少倒 + 补差价")
                            && adviceText.contains("方式 B · 多倒 + 全额余额"));
            checkTrue("建议区标注推荐方式 B", adviceText.contains("★ 推荐：方式 B"));
            checkTrue("建议区给出边际折合", adviceText.contains("61.11%"));

            String singleText = textOf(app, "singleItemPanel");
            checkTrue("单件指标显示到手余额", singleText.contains("¥86.97"));
            checkTrue("单件指标显示 Steam 手续费", singleText.contains("¥4.34"));
            checkTrue("单件指标显示性价比", singleText.contains("1.2424"));

            String targetText = textOf(app, "targetPanel");
            checkTrue("目标倒推显示目标余额", targetText.contains("¥1,000.00"));
            checkTrue("目标倒推显示需购买数量", targetText.contains("12 件"));
            checkTrue("目标倒推显示超出目标", targetText.contains("¥43.64"));

            // ── 用例 C：默认上下顺序「购买建议 → 单件指标 → 目标倒推」──
            int adviceIndex = indexOf(content, advice);
            int singleIndex = indexOf(content, singleItem);
            int targetIndex = indexOf(content, target);
            checkTrue("购买建议排在单件指标之前",
                    adviceIndex >= 0 && singleIndex >= 0 && adviceIndex < singleIndex);
            checkTrue("单件指标排在目标倒推之前", singleIndex < targetIndex);
            checkTrue("默认没有卡片被置顶", pinned.getComponentCount() == 0);
            checkTrue("默认置顶区隐藏", !pinnedScroll.isVisible());

            // ── 用例 D：图钉把卡片搬到固定区 ──
            advice.header().togglePinForTest();
            checkTrue("置顶后卡片进入固定区", indexOf(pinned, advice) >= 0);
            checkTrue("置顶后离开滚动区", indexOf(content, advice) < 0);
            checkTrue("置顶后固定区可见", pinnedScroll.isVisible());
            checkTrue("图钉状态已标记", advice.isPinned());

            advice.header().togglePinForTest();
            checkTrue("取消置顶后回到滚动区", indexOf(content, advice) >= 0);
            checkTrue("取消置顶后固定区清空", pinned.getComponentCount() == 0);
            checkTrue("取消置顶后固定区隐藏", !pinnedScroll.isVisible());

            // 多张同时置顶时按默认顺序排列
            target.header().togglePinForTest();
            advice.header().togglePinForTest();
            checkTrue("置顶两张：购买建议排在目标倒推之前",
                    indexOf(pinned, advice) < indexOf(pinned, target));
            checkTrue("置顶两张：滚动区不再包含它们",
                    indexOf(content, advice) < 0 && indexOf(content, target) < 0);
            advice.header().togglePinForTest();
            target.header().togglePinForTest();
            checkTrue("全部取消置顶后固定区清空", pinned.getComponentCount() == 0);

            // ── 用例 E：侧边开关 ──
            checkTrue("开关默认全开", drawer.isSectionSelected(Section.SINGLE_ITEM)
                    && drawer.isSectionSelected(Section.ADVICE)
                    && drawer.isSectionSelected(Section.TARGET));

            drawer.toggleForTest(Section.SINGLE_ITEM);
            checkTrue("关闭开关后卡片隐藏", !singleItem.isVisible());
            checkTrue("隐藏的卡片不在滚动区", indexOf(content, singleItem) < 0);
            checkTrue("其余卡片不受影响", advice.isVisible() && target.isVisible());

            drawer.toggleForTest(Section.SINGLE_ITEM);
            checkTrue("重新打开后卡片恢复", singleItem.isVisible());
            checkTrue("恢复后回到滚动区", indexOf(content, singleItem) >= 0);

            // 隐藏已置顶的卡片时应当自动取消置顶
            advice.header().togglePinForTest();
            drawer.toggleForTest(Section.ADVICE);
            checkTrue("隐藏已置顶卡片时自动取消置顶", !advice.isPinned());
            checkTrue("隐藏后固定区清空", pinned.getComponentCount() == 0);
            drawer.toggleForTest(Section.ADVICE);
            checkTrue("恢复显示后回到滚动区", indexOf(content, advice) >= 0);

            // ── 用例 F：抽屉展开 / 收起 ──
            int collapsedWidth = drawer.getPreferredSize().width;
            check("抽屉默认收起宽度", 0, collapsedWidth);
            drawer.setExpanded(true);
            drawer.finishAnimation();
            check("抽屉展开宽度", SideDrawer.EXPANDED_WIDTH, drawer.getPreferredSize().width);
            drawer.setExpanded(false);
            drawer.finishAnimation();
            check("抽屉收起后回到 0", 0, drawer.getPreferredSize().width);

            // ── 用例 G：响应式断点 ──
            invokeWithInt(app, "applyResponsiveLayout", 1300);
            check("宽屏 1300px：指标卡 4 列", 4, metricColumns(app));
            invokeWithInt(app, "applyResponsiveLayout", 900);
            check("窄屏 900px：指标卡 2 列", 2, metricColumns(app));
            invokeWithInt(app, "applyResponsiveLayout", 1300);
            check("回到宽屏：恢复 4 列", 4, metricColumns(app));

            // ── 用例 H：残缺输入不崩溃 ──
            input.targetField().setText("");
            compute(app);
            checkTrue("空输入清空指标卡", textOf(app, "metricsPanel").contains("—"));
            checkTrue("空输入时没有计算结果", lastResult(app) == null);

            input.targetField().setText("abc");
            compute(app);
            checkTrue("非法输入仍无结果", lastResult(app) == null);

            input.resetToDefaults();
            compute(app);
            checkTrue("恢复默认后重新出结果", lastResult(app) != null);

            // ── 用例 I：界面持有的结果与命令行同源输入一致 ──
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

    private static int indexOf(Container parent, Object child) {
        Component[] components = parent.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == child) {
                return i;
            }
        }
        return -1;
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
