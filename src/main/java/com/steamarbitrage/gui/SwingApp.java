package com.steamarbitrage.gui;

import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Swing 图形界面。
 *
 * <p>只依赖 JDK 自带的 {@code java.desktop}，没有任何第三方 UI 库。
 * 三个输入框都装了 {@code DocumentListener}，边打字边出结果；
 * 报告正文直接复用 {@link ConsoleReport}，保证界面与命令行给出完全一致的数字。
 */
public final class SwingApp {

    private static final Color TEXT_NORMAL = new Color(0x1A, 0x1A, 0x1A);
    private static final Color TEXT_HINT = new Color(0x90, 0x90, 0x90);
    private static final Color STATUS_OK = new Color(0x11, 0x66, 0x22);
    private static final Color STATUS_WARN = new Color(0x99, 0x55, 0x00);
    private static final Color STATUS_ERROR = new Color(0xAA, 0x22, 0x22);

    private final JComboBox<Choice<GameProfile>> gameBox;
    private final JComboBox<Choice<PriceType>> priceTypeBox;
    private final JComboBox<Choice<FeeMode>> feeModeBox;

    private final JTextField purchaseField = new JTextField("70", 16);
    private final JTextField sellField = new JTextField("100", 16);
    private final JTextField targetField = new JTextField("1000", 16);

    private final JTextArea reportArea = new JTextArea();
    private final JLabel statusLabel = new JLabel(" ");

    private SwingApp() {
        DefaultComboBoxModel<Choice<GameProfile>> gameModel = new DefaultComboBoxModel<>();
        for (GameProfile profile : GameProfile.builtIns()) {
            gameModel.addElement(new Choice<>(
                    profile.displayName() + " — " + profile.feeDescription(), profile));
        }
        gameBox = new JComboBox<>(gameModel);

        DefaultComboBoxModel<Choice<PriceType>> priceModel = new DefaultComboBoxModel<>();
        priceModel.addElement(new Choice<>(
                "买家支付价（Steam 市场挂单价）— 程序自动扣手续费", PriceType.BUYER_PAID));
        priceModel.addElement(new Choice<>(
                "卖家到手价（Steam 卖家收入）— 程序不再扣手续费", PriceType.SELLER_RECEIVE));
        priceTypeBox = new JComboBox<>(priceModel);

        DefaultComboBoxModel<Choice<FeeMode>> feeModel = new DefaultComboBoxModel<>();
        feeModel.addElement(new Choice<>(
                "精确反解 — 与 Steam 页面显示的卖家收入一致", FeeMode.EXACT));
        feeModel.addElement(new Choice<>(
                "粗略近似 — 售价 ÷ 1.15 向下取整", FeeMode.SIMPLE));
        feeModeBox = new JComboBox<>(feeModel);
    }

    /** 启动图形界面（非阻塞）。 */
    public static void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // 系统外观不可用时保持 Swing 默认外观
        }
        SwingUtilities.invokeLater(() -> new SwingApp().show());
    }

    private void show() {
        JFrame frame = new JFrame("Steam 倒余额计算器");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(buildRoot());
        frame.pack();
        frame.setMinimumSize(new Dimension(840, 700));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        compute();
    }

    private JComponent buildRoot() {
        JPanel root = new JPanel(new BorderLayout());

        JPanel top = new JPanel(new BorderLayout());
        top.add(buildForm(), BorderLayout.CENTER);
        top.add(buildButtons(), BorderLayout.SOUTH);
        root.add(top, BorderLayout.NORTH);

        reportArea.setEditable(false);
        reportArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        reportArea.setMargin(new Insets(10, 12, 10, 12));
        reportArea.setBackground(new Color(0xFC, 0xFC, 0xFC));
        JScrollPane scroll = new JScrollPane(reportArea);
        scroll.setPreferredSize(new Dimension(800, 560));
        root.add(scroll, BorderLayout.CENTER);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 12, 8, 12));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        root.add(statusLabel, BorderLayout.SOUTH);

        installLiveUpdate();
        return root;
    }

    private JComponent buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 14, 4, 14));

        int row = 0;
        addRow(form, row++, "游戏手续费档案", gameBox);
        addRow(form, row++, "Steam 售价含义", priceTypeBox);
        addRow(form, row++, "手续费换算模式", feeModeBox);
        addRow(form, row++, "饰品购入价（元）", purchaseField);
        addRow(form, row++, "Steam 售价（元）", sellField);
        addRow(form, row, "目标 Steam 余额（元）", targetField);
        return form;
    }

    private static void addRow(JPanel panel, int row, String label, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 4, 3, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridx = 0;
        c.gridy = row;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(field, c);
    }

    private JComponent buildButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));

        JButton clearButton = new JButton("清空");
        clearButton.addActionListener(e -> {
            purchaseField.setText("");
            sellField.setText("");
            targetField.setText("");
        });

        panel.add(clearButton);
        return panel;
    }

    private void installLiveUpdate() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                compute();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                compute();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                compute();
            }
        };
        purchaseField.getDocument().addDocumentListener(listener);
        sellField.getDocument().addDocumentListener(listener);
        targetField.getDocument().addDocumentListener(listener);

        gameBox.addActionListener(e -> compute());
        priceTypeBox.addActionListener(e -> compute());
        feeModeBox.addActionListener(e -> compute());
    }

    /** 读取当前输入并刷新报告；输入不完整时只给出灰色提示，不弹窗打断输入。 */
    private void compute() {
        try {
            GameProfile game = selected(gameBox);
            PriceType priceType = selected(priceTypeBox);
            FeeMode feeMode = selected(feeModeBox);

            Money purchasePrice = Money.parse(purchaseField.getText());
            Money marketPrice = Money.parse(sellField.getText());
            Money target = Money.parse(targetField.getText());

            CalcResult result = BalanceCalculator.calculate(new CalcRequest(
                    purchasePrice, marketPrice, priceType, game, feeMode, target));

            reportArea.setForeground(TEXT_NORMAL);
            reportArea.setText(ConsoleReport.render(result));
            reportArea.setCaretPosition(0);

            String warnings = ConsoleReport.warnings(result);
            statusLabel.setForeground(warnings.isEmpty() ? STATUS_OK : STATUS_WARN);
            statusLabel.setText(" " + ConsoleReport.renderSummaryLine(result)
                    + (warnings.isEmpty() ? "" : "    见下方注意事项"));
        } catch (RuntimeException e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            reportArea.setForeground(TEXT_HINT);
            reportArea.setText("等待有效输入…\n\n" + message + "\n");
            reportArea.setCaretPosition(0);
            statusLabel.setForeground(STATUS_ERROR);
            statusLabel.setText(" " + message);
        }
    }

    private static <T> T selected(JComboBox<Choice<T>> box) {
        Choice<T> choice = box.getItemAt(box.getSelectedIndex());
        return choice == null ? null : choice.value();
    }

    /** 下拉框条目：显示中文说明，携带真正的枚举值。 */
    private record Choice<T>(String label, T value) {
        @Override
        public String toString() {
            return label;
        }
    }
}
