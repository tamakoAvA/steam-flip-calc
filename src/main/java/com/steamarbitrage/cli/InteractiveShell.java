package com.steamarbitrage.cli;

import com.steamarbitrage.core.BalanceCalculator;
import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.CalcResult;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

/**
 * 中文问答式交互模式，适合直接用鼠标键盘算一两笔。
 *
 * <p>菜单项一律可用数字选择，输入 {@code q} 随时退出。
 */
public final class InteractiveShell {

    private final Scanner in;
    private final PrintStream out;
    private boolean quit;

    public InteractiveShell(Scanner in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    /** 进入交互循环，直到用户退出或输入结束。 */
    public void run() {
        printBanner();
        while (!quit) {
            try {
                runOnce();
            } catch (IllegalArgumentException | IllegalStateException e) {
                out.println();
                out.println("输入有误：" + e.getMessage());
            }
            if (quit) {
                break;
            }
            out.println();
            out.print("再算一次？直接回车继续，输入 q 退出：");
            String line = readLine();
            if (line == null || isQuit(line)) {
                break;
            }
            out.println();
        }
        out.println();
        out.println("已退出。祝倒余额顺利。");
    }

    private void runOnce() {
        GameProfile profile = askGame();
        if (quit) {
            return;
        }
        PriceType priceType = askPriceType();
        if (quit) {
            return;
        }
        FeeMode feeMode = askFeeMode();
        if (quit) {
            return;
        }
        Money purchasePrice = askMoney("饰品购入价（第三方平台，元）");
        if (quit) {
            return;
        }
        Money marketPrice = askMoney(priceType == PriceType.BUYER_PAID
                ? "Steam 市场挂单价（买家支付，元）"
                : "Steam 卖家到手价（元）");
        if (quit) {
            return;
        }
        Money target = askMoney("目标 Steam 余额（元）");
        if (quit) {
            return;
        }

        CalcResult result = BalanceCalculator.calculate(new CalcRequest(
                purchasePrice, marketPrice, priceType, profile, feeMode, target));

        out.println();
        out.println(ConsoleReport.render(result));
        String warnings = ConsoleReport.warnings(result);
        if (!warnings.isEmpty()) {
            out.print(warnings);
        }
    }

    private void printBanner() {
        out.println("=".repeat(66));
        out.println(" Steam 倒余额计算器 · 交互模式");
        out.println("=".repeat(66));
        out.println("说明：Steam 市场买家支付 = 卖家到手 × (1 + 手续费)，");
        out.println("      本工具负责在两种价格之间换算，并算出折扣与需购买件数。");
        out.println("      任何一步输入 q 回车即可退出。");
        out.println();
    }

    private GameProfile askGame() {
        List<GameProfile> profiles = GameProfile.builtIns();
        out.println("第 1 步 · 选择游戏：");
        for (int i = 0; i < profiles.size(); i++) {
            out.printf("  [%d] %s%n", i + 1, profiles.get(i).menuLabel());
        }
        while (true) {
            out.print("请输入序号或 id（直接回车 = 1）：");
            String line = readLine();
            if (line == null || isQuit(line)) {
                quit = true;
                return GameProfile.defaultProfile();
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                return profiles.get(0);
            }
            try {
                int index = Integer.parseInt(trimmed);
                if (index >= 1 && index <= profiles.size()) {
                    return profiles.get(index - 1);
                }
                out.println("序号超出范围，请重新输入。");
                continue;
            } catch (NumberFormatException ignored) {
                // 不是数字，按 id 再试一次
            }
            Optional<GameProfile> found = GameProfile.find(trimmed);
            if (found.isPresent()) {
                return found.get();
            }
            out.println("无法识别：" + trimmed + "，请重新输入。");
        }
    }

    private PriceType askPriceType() {
        out.println();
        out.println("第 2 步 · Steam 售价指的是哪一个价格？");
        out.println("  [1] 买家支付价（Steam 市场挂单价）—— 程序自动扣手续费");
        out.println("  [2] 卖家到手价（Steam 卖家收入）—— 程序不再扣手续费");
        while (true) {
            out.print("请输入序号（直接回车 = 1）：");
            String line = readLine();
            if (line == null || isQuit(line)) {
                quit = true;
                return PriceType.defaultType();
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals("1")) {
                return PriceType.BUYER_PAID;
            }
            if (trimmed.equals("2")) {
                return PriceType.SELLER_RECEIVE;
            }
            try {
                return PriceType.byId(trimmed);
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage() + "，请重新输入。");
            }
        }
    }

    private FeeMode askFeeMode() {
        out.println();
        out.println("第 3 步 · 手续费换算模式：");
        out.println("  [1] 精确反解 —— 按 Steam 真实公式，与页面显示一致");
        out.println("  [2] 粗略近似 —— 售价 ÷ 1.15，快但可能差几分钱");
        while (true) {
            out.print("请输入序号（直接回车 = 1）：");
            String line = readLine();
            if (line == null || isQuit(line)) {
                quit = true;
                return FeeMode.defaultMode();
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals("1")) {
                return FeeMode.EXACT;
            }
            if (trimmed.equals("2")) {
                return FeeMode.SIMPLE;
            }
            try {
                return FeeMode.byId(trimmed);
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage() + "，请重新输入。");
            }
        }
    }

    private Money askMoney(String label) {
        while (true) {
            out.print(label + "：");
            String line = readLine();
            if (line == null || isQuit(line)) {
                quit = true;
                return Money.ZERO;
            }
            if (line.trim().isEmpty()) {
                out.println("不能为空，请重新输入。");
                continue;
            }
            try {
                Money money = Money.parse(line);
                if (!money.isPositive()) {
                    out.println("必须大于 0，请重新输入。");
                    continue;
                }
                return money;
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage() + "，请重新输入。");
            }
        }
    }

    private String readLine() {
        return in.hasNextLine() ? in.nextLine() : null;
    }

    private static boolean isQuit(String line) {
        String trimmed = line.trim().toLowerCase(Locale.ROOT);
        return trimmed.equals("q") || trimmed.equals("quit") || trimmed.equals("exit")
                || trimmed.equals("退出");
    }
}
