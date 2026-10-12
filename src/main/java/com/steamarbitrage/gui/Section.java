package com.steamarbitrage.gui;

/**
 * 可显示 / 隐藏的内容区域，对应侧边抽屉里的一组开关。
 *
 * <p>枚举顺序即默认的上下排列顺序。
 */
public enum Section {

    ADVICE("购买建议"),
    SINGLE_ITEM("单件指标"),
    TARGET("按目标余额倒推");

    private final String label;

    Section(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
