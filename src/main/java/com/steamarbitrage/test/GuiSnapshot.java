package com.steamarbitrage.test;

import com.steamarbitrage.gui.SwingApp;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * 把图形界面截成 PNG，用于人工核对排版、留白与配色。
 *
 * <p>这是开发期的自查工具，不参与自测断言。用法：
 * <pre>
 * java -cp "out\classes;lib\*" com.steamarbitrage.test.GuiSnapshot &lt;输出路径&gt; &lt;宽&gt; &lt;高&gt;
 * </pre>
 *
 * <p>窗口会被临时置顶几秒以便稳定截屏，完成后立即关闭。
 */
public final class GuiSnapshot {

    private GuiSnapshot() {
    }

    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("无图形环境，跳过截图。");
            return;
        }

        String outPath = args.length > 0 ? args[0] : "out/screenshots/gui.png";
        int width = args.length > 1 ? Integer.parseInt(args[1]) : 1360;
        int height = args.length > 2 ? Integer.parseInt(args[2]) : 900;

        SwingApp.launch();
        Thread.sleep(3500);

        JFrame target = findMainWindow();
        if (target == null) {
            System.err.println("未找到主窗口，无法截图。");
            System.exit(1);
        }

        SwingUtilities.invokeAndWait(() -> {
            target.setAlwaysOnTop(true);
            target.setSize(width, height);
            target.setLocation(30, 30);
            target.toFront();
        });
        Thread.sleep(1500);

        // 把鼠标移出窗口，避免悬停高亮污染截图
        Robot robot = new Robot();
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        robot.mouseMove(screen.width - 4, screen.height - 4);
        Thread.sleep(600);

        // 视口回到顶部：窗口尺寸是在显示之后设置的，Swing 可能为保持焦点可见而自动滚动
        JScrollPane scroll = findScrollPane(target.getContentPane());
        if (scroll != null) {
            final JScrollPane target1 = scroll;
            SwingUtilities.invokeAndWait(
                    () -> target1.getViewport().setViewPosition(new java.awt.Point(0, 0)));
            Thread.sleep(500);
            System.out.printf("内容首选尺寸 %s  视口 %s%n",
                    target1.getViewport().getView().getPreferredSize(),
                    target1.getViewport().getExtentSize());
        }

        Rectangle bounds = target.getBounds();
        BufferedImage image = robot.createScreenCapture(bounds);

        File file = new File(outPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", file);
        System.out.printf("已保存 %s  图像 %d×%d  窗口 %d×%d%n",
                file.getAbsolutePath(), image.getWidth(), image.getHeight(),
                bounds.width, bounds.height);

        SwingUtilities.invokeAndWait(target::dispose);
        System.exit(0);
    }

    private static JFrame findMainWindow() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame frame
                    && frame.isVisible()
                    && "Steam 倒余额计算器".equals(frame.getTitle())) {
                return frame;
            }
        }
        return null;
    }

    private static JScrollPane findScrollPane(java.awt.Component component) {
        if (component instanceof JScrollPane scrollPane) {
            return scrollPane;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                JScrollPane found = findScrollPane(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
