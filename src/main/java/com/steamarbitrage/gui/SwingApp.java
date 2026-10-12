package com.steamarbitrage.gui;

import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.AdviceNarrator;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.gui.theme.CardPanel;
import com.steamarbitrage.gui.theme.ScrollablePanel;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;
import com.steamarbitrage.gui.theme.VerticalLayout;
import com.steamarbitrage.gui.view.AdvicePanel;
import com.steamarbitrage.gui.view.InputCard;
import com.steamarbitrage.gui.view.InputPanel;
import com.steamarbitrage.gui.view.MetricsPanel;
import com.steamarbitrage.gui.view.SectionCard;
import com.steamarbitrage.gui.view.SideDrawer;
import com.steamarbitrage.gui.view.SingleItemPanel;
import com.steamarbitrage.gui.view.TargetPanel;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
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
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 图形界面外壳。
 *
 * <h2>布局</h2>
 * <pre>
 * 标题栏（含侧边抽屉开关）
 * ├─ 侧边抽屉（可折叠，控制三块内容的显示）
 * └─ 主区
 *    ├─ 置顶区：被「图钉」固定的卡片，不随内容滚动
 *    └─ 滚动区：输入区 + 核心指标卡 + 未置顶的内容卡片
 * 状态栏
 * </pre>
 *
 * <h2>三块内容</h2>
 * 购买建议、单件指标、按目标余额倒推都是 {@link SectionCard}，
 * 因此既能被侧边开关显示 / 隐藏，也能各自用角落的图钉固定在窗口顶部。
 *
 * <h2>响应式</h2>
 * 窗口宽度跨过 {@value #WIDE_BREAKPOINT} 像素时切换核心指标卡的列数；
 * 窗口初始尺寸按屏幕可用区域计算并限制在屏内。
 */
public final class SwingApp {

    /** 宽屏断点：低于该宽度时指标卡改为两行 2 张。 */
    private static final int WIDE_BREAKPOINT = 1150;

    private static final int MIN_WIDTH = 880;
    private static final int MIN_HEIGHT = 560;

    /** 置顶区最多占据窗口高度的比例，避免固定太多把滚动区挤没。 */
    private static final double PINNED_MAX_RATIO = 0.55;

    private final InputPanel inputPanel = new InputPanel();
    private final MetricsPanel metricsPanel = new MetricsPanel();
    private final AdvicePanel advicePanel = new AdvicePanel();
    private final SingleItemPanel singleItemPanel = new SingleItemPanel();
    private final TargetPanel targetPanel = new TargetPanel();
    private final SideDrawer sideDrawer = new SideDrawer();

    /** 带置顶按钮的输入卡片。 */
    private final InputCard inputCard = new InputCard(inputPanel);

    /** 全部可置顶的卡片，顺序即默认排列顺序：输入参数 → 购买建议 → 单件指标 → 按目标余额倒推。 */
    private final List<SectionCard> cards =
            List.of(inputCard, advicePanel, singleItemPanel, targetPanel);

    private final Set<SectionCard> pinnedCards = new HashSet<>();

    private final JLabel statusLabel =
            UiFactory.label(" ", Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_SECONDARY);
    private final JButton copyButton = UiFactory.secondaryButton("复制文本报告");
    private final JButton drawerButton = UiFactory.secondaryButton("显示区域");

    /** 复制成功提示的恢复计时器；复用同一个实例，避免连点时堆积计时器。 */
    private final Timer restoreTimer = new Timer(1800, e -> compute());

    private JFrame frame;
    private ScrollablePanel contentPanel;
    private ScrollablePanel pinnedPanel;
    private JScrollPane pinnedScroll;
    private CalcResult lastResult;

    private SwingApp() {
        restoreTimer.setRepeats(false);
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

    private void applyScreenAwareSize() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        Dimension preferred = frame.getPreferredSize();

        int minWidth = Math.min(MIN_WIDTH, (int) (screen.width * 0.92));
        int minHeight = Math.min(MIN_HEIGHT, (int) (screen.height * 0.92));
        int maxWidth = Math.max(minWidth, (int) (screen.width * 0.94));
        int maxHeight = Math.max(minHeight, (int) (screen.height * 0.94));

        frame.setMinimumSize(new Dimension(minWidth, minHeight));
        frame.setSize(clamp(preferred.width, minWidth, maxWidth),
                clamp(preferred.height, minHeight, maxHeight));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── 界面组装 ────────────────────────────────────────────────────────────

    private JComponent buildRoot() {
        JPanel body = new JPanel(new BorderLayout());
        body.setBackground(Theme.PAGE_BG);
        body.add(sideDrawer, BorderLayout.WEST);
        body.add(buildMainArea(), BorderLayout.CENTER);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Theme.PAGE_BG);
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(body, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);

        bindSections();
        rebuildSections();
        return root;
    }

    /** 顶部深蓝标题栏：侧边抽屉开关放在最左侧，标题紧随其后。 */
    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(Theme.GAP_LG, 0));
        header.setBackground(Theme.BRAND_DARK);
        header.setBorder(new EmptyBorder(Theme.GAP_MD, Theme.GAP_LG, Theme.GAP_MD, Theme.GAP_LG));

        drawerButton.putClientProperty("JButton.buttonType", "borderless");
        drawerButton.setForeground(Theme.TEXT_ON_DARK);
        drawerButton.setToolTipText("显示 / 隐藏内容区域");
        drawerButton.addActionListener(e -> {
            sideDrawer.toggle();
            updateDrawerButton();
        });

        JPanel texts = UiFactory.transparentPanel(new BorderLayout(0, 2));
        texts.add(UiFactory.label("Steam 倒余额计算器", Theme.bold(19), Theme.TEXT_ON_DARK),
                BorderLayout.NORTH);
        texts.add(UiFactory.label("输入饰品购入价与 Steam 市场售价，算出折合折扣与所需件数",
                Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_ON_DARK_MUTED), BorderLayout.SOUTH);

        // 抽屉开关紧跟在标题两行文字的右侧
        JPanel left = UiFactory.transparentPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP_LG, 0));
        left.add(texts);
        left.add(drawerButton);
        header.add(left, BorderLayout.WEST);

        header.add(UiFactory.label("CS2 · TF2", Theme.bold(Theme.SIZE_SMALL), Theme.ACCENT),
                BorderLayout.EAST);
        return header;
    }

    private void updateDrawerButton() {
        drawerButton.setText(sideDrawer.isExpanded() ? "收起区域" : "显示区域");
    }

    /** 主区：置顶区（固定）+ 滚动区。 */
    private JComponent buildMainArea() {
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Theme.PAGE_BG);

        pinnedPanel = new ScrollablePanel(new VerticalLayout(Theme.GAP_MD));
        pinnedPanel.setBackground(Theme.PAGE_BG);
        pinnedPanel.setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_LG, 0, Theme.GAP_LG));

        pinnedScroll = new JScrollPane(pinnedPanel);
        pinnedScroll.setBorder(null);
        pinnedScroll.setBackground(Theme.PAGE_BG);
        pinnedScroll.getViewport().setBackground(Theme.PAGE_BG);
        pinnedScroll.getVerticalScrollBar().setUnitIncrement(18);
        pinnedScroll.setVisible(false);
        main.add(pinnedScroll, BorderLayout.NORTH);

        contentPanel = new ScrollablePanel(new VerticalLayout(Theme.GAP_MD));
        contentPanel.setBackground(Theme.PAGE_BG);
        contentPanel.setBorder(new EmptyBorder(Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG, Theme.GAP_LG));

        JScrollPane scroll = new JScrollPane(contentPanel);
        scroll.setBorder(null);
        scroll.setBackground(Theme.PAGE_BG);
        scroll.getViewport().setBackground(Theme.PAGE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        main.add(scroll, BorderLayout.CENTER);

        return main;
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

    /** 把卡片的置顶按钮与侧边开关接到重排逻辑上。 */
    private void bindSections() {
        for (SectionCard card : cards) {
            card.onPin(pinned -> {
                if (pinned) {
                    pinnedCards.add(card);
                } else {
                    pinnedCards.remove(card);
                }
                rebuildSections();
            });
        }

        sideDrawer.onSectionChanged((section, visible) -> {
            SectionCard card = cardFor(section);
            card.setVisible(visible);
            if (!visible && pinnedCards.remove(card)) {
                // 隐藏时同时取消置顶，否则它会继续占着顶部空间
                card.setPinned(false);
            }
            rebuildSections();
        });
    }

    private SectionCard cardFor(Section section) {
        return switch (section) {
            case ADVICE -> advicePanel;
            case SINGLE_ITEM -> singleItemPanel;
            case TARGET -> targetPanel;
        };
    }

    /**
     * 按「显示状态 + 置顶状态」重新分配卡片：
     * 置顶的进固定区，其余的按默认顺序排在滚动区。
     */
    private void rebuildSections() {
        pinnedPanel.removeAll();
        contentPanel.removeAll();

        // 核心指标卡紧跟输入卡；输入卡被钉住时，指标卡留在滚动区顶部
        int metricsIndex = -1;

        for (SectionCard card : cards) {
            if (!card.isVisible()) {
                continue;
            }
            if (pinnedCards.contains(card)) {
                pinnedPanel.add(card);
            } else {
                contentPanel.add(card);
                if (card == inputCard) {
                    metricsIndex = contentPanel.getComponentCount();
                }
            }
        }

        contentPanel.add(metricsPanel, metricsIndex < 0 ? 0 : metricsIndex);

        updatePinnedHeight();
        pinnedPanel.revalidate();
        contentPanel.revalidate();
        pinnedPanel.repaint();
        contentPanel.repaint();

        // revalidate() 只是把布局排进事件队列，在「显示置顶区」这种
        // 从不可见变可见的场景下不足以及时生效，因此这里同步跑一次 validate()。
        if (frame != null) {
            frame.validate();
        }
    }

    /** 置顶区高度取内容高度，但不超过窗口高度的一定比例，超出时自身滚动。 */
    private void updatePinnedHeight() {
        boolean hasPinned = pinnedPanel.getComponentCount() > 0;
        pinnedScroll.setVisible(hasPinned);
        if (!hasPinned) {
            return;
        }

        int contentHeight = pinnedPanel.getPreferredSize().height;
        int maxHeight = frame == null
                ? contentHeight
                : Math.max(140, (int) (frame.getHeight() * PINNED_MAX_RATIO));
        pinnedScroll.setPreferredSize(new Dimension(0, Math.min(contentHeight, maxHeight)));
        pinnedScroll.revalidate();
    }

    private void installListeners() {
        inputPanel.onChange(this::compute);

        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                applyResponsiveLayout(frame.getWidth());
                updatePinnedHeight();
            }
        });

        JRootPane rootPane = frame.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()), "copyReport");
        actionMap.put("copyReport", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyReport();
            }
        });
    }

    // ── 响应式布局 ──────────────────────────────────────────────────────────

    private void applyResponsiveLayout(int width) {
        metricsPanel.setColumns(width >= WIDE_BREAKPOINT ? 4 : 2);
    }

    // ── 计算与交互 ──────────────────────────────────────────────────────────

    private void compute() {
        try {
            CalcResult result = BalanceCalculator.calculate(inputPanel.readRequest());
            lastResult = result;

            metricsPanel.update(result);
            advicePanel.update(result);
            singleItemPanel.update(result);
            targetPanel.update(result);

            // 卡片内容变化会改变首选高度，需要重新排布
            rebuildSections();

            boolean hasCaution = !AdviceNarrator.cautions(result).isEmpty();
            statusLabel.setForeground(hasCaution ? Theme.WARNING : Theme.POSITIVE);
            statusLabel.setText("● " + ConsoleReport.renderSummaryLine(result)
                    + (hasCaution ? "　·　见购买建议中的注意事项" : ""));
            copyButton.setEnabled(true);
        } catch (RuntimeException e) {
            lastResult = null;
            metricsPanel.clear();
            advicePanel.clear();
            singleItemPanel.clear();
            targetPanel.clear();
            rebuildSections();

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

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text.toString()), null);
        } catch (RuntimeException e) {
            // 剪贴板可能被其它程序占用（剪贴板管理器、远程桌面等），
            // 这时必须如实告知，否则用户会以为已经复制成功
            statusLabel.setForeground(Theme.DANGER);
            statusLabel.setText("● 复制失败：剪贴板被其它程序占用，请稍后重试");
            return;
        }

        statusLabel.setForeground(Theme.POSITIVE);
        statusLabel.setText("● 已复制完整文本报告到剪贴板");
        restoreTimer.restart();
    }
}
