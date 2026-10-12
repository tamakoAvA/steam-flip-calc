package com.steamarbitrage.gui.view;

import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;
import com.steamarbitrage.gui.Choice;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.theme.UiFactory;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

/**
 * 输入区：两行三列。
 *
 * <p>第一行是三个档案类下拉框，第二行是三个金额输入框；
 * 每个字段都是「小标签在上、控件在下」的形式，比传统的左侧标签更省横向空间，
 * 也让两行在窄窗口下能整齐地压缩。
 *
 * <p>下拉项的文本刻意保持简短（详细说明放进 tooltip），
 * 否则三个下拉框并排会把窗口撑得很宽。
 */
public final class InputPanel extends JPanel {

    private static final String DEFAULT_PURCHASE = "70";
    private static final String DEFAULT_SELL = "100";
    private static final String DEFAULT_TARGET = "1000";

    private final JComboBox<Choice<GameProfile>> gameBox;
    private final JComboBox<Choice<PriceType>> priceTypeBox;
    private final JComboBox<Choice<FeeMode>> feeModeBox;

    private final JTextField purchaseField = UiFactory.moneyField(10);
    private final JTextField sellField = UiFactory.moneyField(10);
    private final JTextField targetField = UiFactory.moneyField(10);

    public InputPanel() {
        super(new GridBagLayout());
        setOpaque(false);

        DefaultComboBoxModel<Choice<GameProfile>> gameModel = new DefaultComboBoxModel<>();
        for (GameProfile profile : GameProfile.builtIns()) {
            gameModel.addElement(new Choice<>(
                    profile.displayName() + " · " + totalFeeText(profile), profile));
        }
        gameBox = UiFactory.comboBox(gameModel);
        gameBox.setToolTipText("不同游戏在 Steam 社区市场的手续费档案");

        DefaultComboBoxModel<Choice<PriceType>> priceModel = new DefaultComboBoxModel<>();
        priceModel.addElement(new Choice<>("买家支付价（市场挂单价）", PriceType.BUYER_PAID));
        priceModel.addElement(new Choice<>("卖家到手价（卖家收入）", PriceType.SELLER_RECEIVE));
        priceTypeBox = UiFactory.comboBox(priceModel);
        priceTypeBox.setToolTipText(
                "买家支付价：Steam 市场页面显示的挂单价，程序自动扣掉手续费\n"
                        + "卖家到手价：Steam 卖家收入，程序不再扣手续费");

        DefaultComboBoxModel<Choice<FeeMode>> feeModel = new DefaultComboBoxModel<>();
        feeModel.addElement(new Choice<>("精确反解（与 Steam 一致）", FeeMode.EXACT));
        feeModel.addElement(new Choice<>("粗略近似（÷1.15）", FeeMode.SIMPLE));
        feeModeBox = UiFactory.comboBox(feeModel);
        feeModeBox.setToolTipText(
                "精确反解：按 Steam 真实手续费公式逐分求解，与页面显示的卖家收入一致\n"
                        + "粗略近似：售价 ÷ 1.15 直接取整，可能与页面差几分钱");

        purchaseField.setText(DEFAULT_PURCHASE);
        sellField.setText(DEFAULT_SELL);
        targetField.setText(DEFAULT_TARGET);

        buildLayout();
    }

    private void buildLayout() {
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;

        c.gridy = 0;
        c.insets = new Insets(0, 0, Theme.GAP_MD, Theme.GAP_MD);
        c.gridx = 0;
        add(field("游戏手续费档案", gameBox), c);
        c.gridx = 1;
        add(field("Steam 售价含义", priceTypeBox), c);
        c.gridx = 2;
        c.insets = new Insets(0, 0, Theme.GAP_MD, 0);
        add(field("手续费换算模式", feeModeBox), c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, Theme.GAP_MD);
        c.gridx = 0;
        add(field("饰品购入价（元）", purchaseField), c);
        c.gridx = 1;
        add(field("Steam 售价（元）", sellField), c);
        c.gridx = 2;
        c.insets = new Insets(0, 0, 0, 0);
        add(field("目标 Steam 余额（元）", targetField), c);
    }

    /** 小标签在上、控件在下的字段组。 */
    private static JComponent field(String caption, JComponent input) {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.GAP_XS));
        panel.setOpaque(false);
        panel.add(UiFactory.label(caption, Theme.regular(Theme.SIZE_SMALL), Theme.TEXT_SECONDARY),
                BorderLayout.NORTH);
        panel.add(input, BorderLayout.CENTER);
        return panel;
    }

    /** 注册实时计算回调。 */
    public void onChange(Runnable listener) {
        DocumentListener documentListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                listener.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                listener.run();
            }
        };
        purchaseField.getDocument().addDocumentListener(documentListener);
        sellField.getDocument().addDocumentListener(documentListener);
        targetField.getDocument().addDocumentListener(documentListener);

        gameBox.addActionListener(e -> listener.run());
        priceTypeBox.addActionListener(e -> listener.run());
        feeModeBox.addActionListener(e -> listener.run());
    }

    /**
     * 读取当前输入。
     *
     * @throws IllegalArgumentException 金额为空或无法解析
     */
    public CalcRequest readRequest() {
        return new CalcRequest(
                Money.parse(purchaseField.getText()),
                Money.parse(sellField.getText()),
                selected(priceTypeBox),
                selected(gameBox),
                selected(feeModeBox),
                Money.parse(targetField.getText()));
    }

    /** 恢复默认示例值。 */
    public void resetToDefaults() {
        purchaseField.setText(DEFAULT_PURCHASE);
        sellField.setText(DEFAULT_SELL);
        targetField.setText(DEFAULT_TARGET);
        gameBox.setSelectedIndex(0);
        priceTypeBox.setSelectedIndex(0);
        feeModeBox.setSelectedIndex(0);
    }

    /** 程序化选择游戏档案；第 0 项为 CS2，第 1 项为 TF2。 */
    public void selectGame(int index) {
        gameBox.setSelectedIndex(index);
    }

    /** 程序化选择价格语义；0 = 买家支付价，1 = 卖家到手价。 */
    public void selectPriceType(int index) {
        priceTypeBox.setSelectedIndex(index);
    }

    /** 程序化选择换算模式；0 = 精确反解，1 = 粗略近似。 */
    public void selectFeeMode(int index) {
        feeModeBox.setSelectedIndex(index);
    }

    public JTextField purchaseField() {
        return purchaseField;
    }

    public JTextField sellField() {
        return sellField;
    }

    public JTextField targetField() {
        return targetField;
    }

    private static <T> T selected(JComboBox<Choice<T>> box) {
        Choice<T> choice = box.getItemAt(box.getSelectedIndex());
        return choice == null ? null : choice.value();
    }

    private static String totalFeeText(GameProfile profile) {
        return BigDecimal.valueOf(profile.totalFeeBp(), 2).stripTrailingZeros().toPlainString() + "%";
    }
}
