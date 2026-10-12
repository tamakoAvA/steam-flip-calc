package com.steamarbitrage.gui.view;

/**
 * 输入卡片：参数输入区 + 置顶按钮。
 *
 * <p>输入区平时就在滚动内容的最上方，但往下看明细与建议时会被滚出视野；
 * 用图钉把它固定住，就能一边调参数一边看结果，不必来回滚动。
 */
public final class InputCard extends SectionCard {

    public InputCard(InputPanel inputPanel) {
        super("输入参数");
        setBody(inputPanel);
    }
}
