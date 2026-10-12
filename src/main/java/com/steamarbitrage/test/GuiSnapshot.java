package com.steamarbitrage.test;

import com.steamarbitrage.gui.SwingApp;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.view.SectionCard;
import com.steamarbitrage.gui.view.SideDrawer;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 把图形界面截成 PNG，用于人工核对排版、留白与配色。
 *
 * <p>开发期自查工具，不参与自测断言。用法：
 * <pre>
 * java -cp "out\classes;lib\*" com.steamarbitrage.test.GuiSnapshot &lt;输出路径&gt; &lt;宽&gt; &lt;高&gt; [状态]
 * </pre>
 * 状态可选 {@code default}（默认）、{@code drawer}（展开侧边抽屉）、
 * {@code pinned}（把购买建议固定在顶部）。
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
        String state = args.length > 3 ? args[3] : "default";

        Theme.install();

        Object[] appHolder = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Constructor<SwingApp> constructor = SwingApp.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object app = constructor.newInstance();
                appHolder[0] = app;

                Method show = SwingApp.class.getDeclaredMethod("show");
                show.setAccessible(true);
                show.invoke(app);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        Thread.sleep(2200);

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
        Thread.sleep(900);

        applyState(appHolder[0], state);
        Thread.sleep(900);

        Robot robot = new Robot();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        robot.mouseMove(screen.width - 4, screen.height - 4);
        Thread.sleep(500);

        JScrollPane scroll = findScrollPane(target.getContentPane());
        if (scroll != null) {
            SwingUtilities.invokeAndWait(
                    () -> scroll.getViewport().setViewPosition(new Point(0, 0)));
            Thread.sleep(500);
            System.out.printf("内容首选尺寸 %s  视口 %s%n",
                    scroll.getViewport().getView().getPreferredSize(),
                    scroll.getViewport().getExtentSize());
        }

        Rectangle bounds = target.getBounds();
        BufferedImage image = robot.createScreenCapture(bounds);

        File file = new File(outPath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", file);
        System.out.printf("已保存 %s  图像 %d×%d  状态 %s%n",
                file.getAbsolutePath(), image.getWidth(), image.getHeight(), state);

        SwingUtilities.invokeAndWait(target::dispose);
        System.exit(0);
    }

    private static void applyState(Object app, String state) throws Exception {
        if ("drawer".equals(state)) {
            SideDrawer drawer = (SideDrawer) field(app, "sideDrawer");
            SwingUtilities.invokeAndWait(() -> {
                drawer.setExpanded(true);
                drawer.finishAnimation();
            });
        } else if ("pinned".equals(state)) {
            SectionCard advice = (SectionCard) field(app, "advicePanel");
            SwingUtilities.invokeAndWait(advice.header()::togglePinForTest);
        } else if ("pinned-input".equals(state)) {
            SectionCard input = (SectionCard) field(app, "inputCard");
            SwingUtilities.invokeAndWait(input.header()::togglePinForTest);
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
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

    /** 只认可见的滚动面板：置顶区隐藏时不应被选中。 */
    private static JScrollPane findScrollPane(java.awt.Component component) {
        if (component instanceof JScrollPane scrollPane && scrollPane.isVisible()) {
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
