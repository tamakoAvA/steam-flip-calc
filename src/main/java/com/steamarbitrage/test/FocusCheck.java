package com.steamarbitrage.test;

import com.steamarbitrage.gui.SwingApp;
import com.steamarbitrage.gui.theme.Theme;
import com.steamarbitrage.gui.view.InputPanel;

import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 真实键盘输入下的焦点回归验证。
 *
 * <p>界面里「每输入一个字符就得重新点一下输入框」这种缺陷，靠断言组件树是测不出来的，
 * 必须真的敲键盘：这里用 {@link Robot} 点击输入框后逐个字符输入，
 * 再看 4 个字符是否全部落进同一个输入框。
 *
 * <p>会短暂弹出窗口几秒。无图形环境时直接跳过。
 *
 * <pre>
 * java -cp "out\classes;lib\*" com.steamarbitrage.test.FocusCheck
 * </pre>
 */
public final class FocusCheck {

    private static final String SAMPLE = "1000";

    private FocusCheck() {
    }

    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("跳过焦点验证：当前环境没有图形显示。");
            System.exit(0);
        }

        Theme.install();
        Object[] holder = new Object[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                Constructor<SwingApp> constructor = SwingApp.class.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object app = constructor.newInstance();
                holder[0] = app;
                Method show = SwingApp.class.getDeclaredMethod("show");
                show.setAccessible(true);
                show.invoke(app);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        Thread.sleep(2500);

        JFrame frame = findWindow();
        if (frame == null) {
            System.err.println("未找到主窗口");
            System.exit(1);
        }
        SwingUtilities.invokeAndWait(() -> {
            frame.setAlwaysOnTop(true);
            frame.setSize(1200, 760);
            frame.setLocation(40, 40);
            frame.toFront();
        });
        Thread.sleep(1200);

        InputPanel input = (InputPanel) field(holder[0], "inputPanel");
        JTextField target = input.targetField();
        SwingUtilities.invokeAndWait(() -> target.setText(""));
        Thread.sleep(300);

        // 用鼠标点击，让输入框真正拿到焦点
        Point location = target.getLocationOnScreen();
        Robot robot = new Robot();
        robot.mouseMove(location.x + 30, location.y + target.getHeight() / 2);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(600);

        System.out.println("点击后焦点 = " + focusName());

        for (char ch : SAMPLE.toCharArray()) {
            int code = KeyEvent.getExtendedKeyCodeForChar(ch);
            robot.keyPress(code);
            robot.keyRelease(code);
            Thread.sleep(260);
            System.out.printf("  敲入 '%c' 后焦点 = %s，输入框内容 = [%s]%n",
                    ch, focusName(), target.getText());
        }
        Thread.sleep(700);

        String text = target.getText();
        System.out.println();
        System.out.println("最终输入框内容 = [" + text + "]  期望 [" + SAMPLE + "]");
        System.out.println("最终焦点 = " + focusName());

        boolean ok = SAMPLE.equals(text);
        System.out.println(ok
                ? ">>> 通过：连续输入正常，焦点全程留在输入框"
                : ">>> 失败：只收到 " + text.length() + " 个字符，焦点被抢走了");

        SwingUtilities.invokeAndWait(frame::dispose);
        System.exit(ok ? 0 : 1);
    }

    private static String focusName() {
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (owner == null) {
            return "null";
        }
        if (owner instanceof JTextField field) {
            return "JTextField[" + field.getText() + "]";
        }
        return owner.getClass().getSimpleName();
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static JFrame findWindow() {
        for (Window window : Window.getWindows()) {
            if (window instanceof JFrame f && f.isVisible()
                    && "Steam 倒余额计算器".equals(f.getTitle())) {
                return f;
            }
        }
        return null;
    }
}
