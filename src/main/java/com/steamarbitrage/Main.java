package com.steamarbitrage;

import com.steamarbitrage.cli.ArgParser;
import com.steamarbitrage.cli.ConsoleReport;
import com.steamarbitrage.cli.InteractiveShell;
import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.gui.SwingApp;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * 程序入口。
 *
 * <pre>
 * 无参数        → 中文交互模式
 * gui / --gui   → Swing 图形界面
 * 其他参数      → 单次计算并打印报告
 * </pre>
 *
 * <p>退出码：0 正常，2 参数或输入错误。
 */
public final class Main {

    private static final int EXIT_USAGE_ERROR = 2;

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = System.out;
        PrintStream err = System.err;

        try {
            if (args.length == 0) {
                new InteractiveShell(new Scanner(System.in, StandardCharsets.UTF_8), out).run();
                return;
            }

            if ("gui".equalsIgnoreCase(args[0])) {
                SwingApp.launch();
                return;
            }

            ArgParser.Options options = ArgParser.parse(args);

            if (options.help()) {
                out.println(ArgParser.USAGE);
                return;
            }
            if (options.gui()) {
                SwingApp.launch();
                return;
            }

            CalcResult result = BalanceCalculator.calculate(options.toRequest());
            out.println(ConsoleReport.render(result));

            String warnings = ConsoleReport.warnings(result);
            if (!warnings.isEmpty()) {
                out.print(warnings);
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            err.println("错误：" + e.getMessage());
            err.println("使用 --help 查看用法。");
            System.exit(EXIT_USAGE_ERROR);
        } catch (ArithmeticException e) {
            err.println("错误：金额超出可计算范围，请检查输入。");
            System.exit(EXIT_USAGE_ERROR);
        }
    }
}
