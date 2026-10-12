package com.steamarbitrage.gui;

import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.AdviceNarrator;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.gui.theme.CardPanel;
import com.steamarbitrage.gui.theme.ScrollablePanel;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;
import com.steamarbitrage.gui.view.AdvicePanel;
import com.steamarbitrage.gui.view.DetailPanel;
import com.steamarbitrage.gui.view.InputPanel;
import com.steamarbitrage.gui.view.MetricsPanel;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;

/**
 * 图形界面外壳。
 *
 * <p>负责窗口组装、响应式布局与事件分发；具体内容交给 {@code view} 包下的四个面板，
 * 配色与控件样式来自 {@code theme} 包。
 *
 * <h2>响应式</h2>
 * 窗口宽度跨过 {@value #WIDE_BREAKPOINT} 像素时切换布局：
 * 宽屏用「指标卡 1×4 + 明细与建议左右并排」，窄屏改为「指标卡 2×2 + 上下堆叠」，
 * 因此 1366×768 这类小屏也不会出现横向滚动条。
 * 窗口默认尺寸按屏幕可用区域的比例计算，并限制在屏幕内。
 */
public final class SwingApp {

    /** 宽屏断点：低于该宽度改用堆叠布局。 */
    private static final int WIDE_BREAKPOINT = 1150;

    private static final int MIN_WIDTH = 880;
    private static final int MIN_HEIGHT = 560;

    private final InputPanel inputPanel = new InputPanel();
    private final MetricsPanel metricsPanel = new MetricsPanel();
    private final DetailPanel detailPanel = new DetailPanel();
    private final AdvicePanel advicePanel = new AdvicePanel();

    private final JLabel statusLabel =
            UiFactory.label(" ", Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_SECONDARY);
    private final JButton copyButton = UiFactory.secondaryButton("复制文本报告");

    private JFrame frame;
    private JPanel splitPanel;
    private CalcResult lastResult;
    private boolean wideLayout;
    private boolean layoutInitialized;

    private SwingApp() {
    }

    /** 启动图形界面（非阻塞）。 */
    public static void launch() {
        Theme.install();
        SwingUtilities.invokeLater(() -> new SwingApp().show());
    }

    private void show() {
        frame = new JFrame("Steam 倒余额计算器");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(buildRoot());
        frame.pack();

        installListeners();
        applyScreenAwareSize();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        applyResponsiveLayout(frame.getWidth());
        compute();
    }

    /**
     * 按屏幕可用区域决定窗口大小：既让内容舒展，又保证不超出屏幕
     * （小屏或高 DPI 缩放下会自动收缩，内容仍可纵向滚动）。
     */
    private void applyScreenAwareSize() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        Dimension preferred = frame.getPreferredSize();

        int minWidth = Math.min(MIN_WIDTH, (int) (screen.width * 0.92));
        int minHeight = Math.min(MIN_HEIGHT, (int) (screen.height * 0.92));
        int maxWidth = Math.max(minWidth, (int) (screen.width * 0.94));
        int maxHeight = Math.max(minHeight, (int) (screen.height * 0.94));

        int width = clamp(preferred.width, minWidth, maxWidth);
        int height = clamp(preferred.height, minHeight, maxHeight);

        frame.setMinimumSize(new Dimension(minWidth, minHeight));
        frame.setSize(width, height);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── 界面组装 ────────────────────────────────────────────────────────────

    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE_BG);

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildScrollableContent(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        return root;
    }

    /** 顶部深蓝标题栏。 */
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(Theme.GAP_LG, 0));
        header.setBackground(Theme.BRAND_DARK);
        header.setBorder(new EmptyBorder(Theme.GAP_MD, Theme.GAP_LG, Theme.GAP_MD, Theme.GAP_LG));

        JPanel texts = UiFactory.transparentPanel(new BorderLayout(0, 2));
        texts.add(UiFactory.label("Steam 倒余额计算器", Theme.bold(19), Theme.TEXT_ON_DARK),
                BorderLayout.NORTH);
        texts.add(UiFactory.label("输入饰品购入价与 Steam 市场售价，算出折合折扣与所需件数",
                Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_ON_DARK_MUTED), BorderLayout.SOUTH);
        header.add(texts, BorderLayout.WEST);

        header.add(UiFactory.label("CS2 · TF2", Theme.bold(Theme.SIZE_SMALL), Theme.ACCENT),
                BorderLayout.EAST);
        return header;
    }

    private JComponent buildScrollableContent() {
        ScrollablePanel content = new ScrollablePanel(new GridBagLayout());
        content.setBackground(Theme.PAGE_BG);
        content.setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;

        c.gridy = 0;
        c.insets = new Insets(0, 0, Theme.GAP_MD, 0);
        content.add(buildInputCard(), c);

        c.gridy = 1;
        content.add(metricsPanel, c);

        c.gridy = 2;
        c.insets = new Insets(0, 0, 0, 0);
        splitPanel = UiFactory.transparentPanel(new GridLayout(1, 2, Theme.GAP_MD, 0));
        splitPanel.add(detailPanel);
        splitPanel.add(advicePanel);
        content.add(splitPanel, c);

        // 占位：内容不足一屏时把卡片顶到上方
        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        content.add(filler, c);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.setBackground(Theme.PAGE_BG);
        scroll.getViewport().setBackground(Theme.PAGE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getHorizontalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JComponent buildInputCard() {
        CardPanel card = UiFactory.card(null);
        card.add(inputPanel, BorderLayout.CENTER);
        return card;
    }

    private JComponent buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(Theme.GAP_MD, 0));
        bar.setBackground(Theme.CARD_BG);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                new EmptyBorder(Theme.GAP_SM, Theme.GAP_LG, Theme.GAP_SM, Theme.GAP_LG)));

        bar.add(statusLabel, BorderLayout.CENTER);

        JPanel buttons = UiFactory.transparentPanel(new FlowLayout(FlowLayout.RIGHT, Theme.GAP_SM, 0));
        copyButton.setToolTipText("把与命令行完全一致的文本报告复制到剪贴板（Ctrl+D）");
        copyButton.addActionListener(e -> copyReport());
        JButton resetButton = UiFactory.secondaryButton("恢复默认");
        resetButton.addActionListener(e -> {
            inputPanel.resetToDefaults();
            compute();
        });
        buttons.add(copyButton);
        buttons.add(resetButton);
        bar.add(buttons, BorderLayout.EAST);

        return bar;
    }

    private void installListeners() {
        inputPanel.onChange(this::compute);

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyResponsiveLayout(frame.getWidth());
            }
        });

        JRootPane rootPane = frame.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
        KeyStroke copyKey = KeyStroke.getKeyStroke(KeyEvent.VK_D,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
        inputMap.put(copyKey, "copyReport");
        actionMap.put("copyReport", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyReport();
            }
        });
    }

    // ── 响应式布局 ──────────────────────────────────────────────────────────

    private void applyResponsiveLayout(int width) {
        boolean wide = width >= WIDE_BREAKPOINT;
        metricsPanel.setColumns(wide ? 4 : 2);

        if (layoutInitialized && wide == wideLayout) {
            return;
        }
        layoutInitialized = true;
        wideLayout = wide;

        if (splitPanel != null) {
            splitPanel.setLayout(wide
                    ? new GridLayout(1, 2, Theme.GAP_MD, 0)
                    : new GridLayout(2, 1, 0, Theme.GAP_MD));
            splitPanel.revalidate();
            splitPanel.repaint();
        }
    }

    // ── 计算与交互 ──────────────────────────────────────────────────────────

    /** 读取输入并刷新全部面板；输入不完整时清空结果而不弹窗打断输入。 */
    private void compute() {
        try {
            CalcResult result = BalanceCalculator.calculate(inputPanel.readRequest());
            lastResult = result;

            metricsPanel.update(result);
            detailPanel.update(result);
            advicePanel.update(result);

            boolean hasCaution = !AdviceNarrator.cautions(result).isEmpty();
            statusLabel.setForeground(hasCaution ? Theme.WARNING : Theme.POSITIVE);
            statusLabel.setText("● " + ConsoleReport.renderSummaryLine(result)
                    + (hasCaution ? "　·　见下方注意事项" : ""));
            copyButton.setEnabled(true);
        } catch (RuntimeException e) {
            lastResult = null;
            metricsPanel.clear();
            detailPanel.clear();
            advicePanel.clear();

            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            statusLabel.setForeground(Theme.TEXT_MUTED);
            statusLabel.setText("等待有效输入：" + message);
            copyButton.setEnabled(false);
        }
    }

    /** 复制与命令行完全一致的文本报告。 */
    private void copyReport() {
        if (lastResult == null) {
            return;
        }
        StringBuilder text = new StringBuilder(ConsoleReport.render(lastResult));
        String warnings = ConsoleReport.warnings(lastResult);
        if (!warnings.isEmpty()) {
            text.append('\n').append(warnings);
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.toString()), null);

        statusLabel.setForeground(Theme.POSITIVE);
        statusLabel.setText("● 已复制完整文本报告到剪贴板");
        Timer restore = new Timer(1800, e -> compute());
        restore.setRepeats(false);
        restore.start();
    }
}
