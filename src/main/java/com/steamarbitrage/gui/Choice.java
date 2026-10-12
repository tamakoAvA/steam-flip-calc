package com.steamarbitrage.gui;

/**
 * 下拉框条目：界面显示中文说明，实际携带领域枚举值。
 *
 * @param label 显示文本
 * @param value 真实取值
 */
public record Choice<T>(String label, T value) {

    @Override
    public String toString() {
        return label;
    }
}
