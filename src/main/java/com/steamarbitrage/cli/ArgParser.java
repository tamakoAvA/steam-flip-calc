package com.steamarbitrage.cli;

import com.steamarbitrage.core.CalcRequest;
import com.steamarbitrage.core.FeeMode;
import com.steamarbitrage.core.GameProfile;
import com.steamarbitrage.core.Money;
import com.steamarbitrage.core.PriceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 命令行参数解析。
 *
 * <p>同时支持 {@code --key value} 与 {@code --key=value} 两种写法，
 * 并允许用位置参数依次给出「购入价 售价 目标余额」。
 */
public final class ArgParser {

    private static final Set<String> VALUE_OPTIONS = Set.of(
            "--buy", "--purchase", "--sell", "--target",
            "--price-type", "--price", "--game", "--fee-mode");

    public static final String USAGE = String.join("\n",
            "Steam 倒余额计算器",
            "",
            "用法：",
            "  run.ps1                                  交互模式（推荐首次使用）",
            "  run.ps1 --buy 70 --sell 100 --target 1000",
            "  run.ps1 70 100 1000                       位置参数写法",
            "  run-gui.ps1                               图形界面",
            "",
            "参数：",
            "  --buy <元>            饰品购入价（第三方平台，如 BUFF / 悠悠有品）",
            "  --sell <元>           Steam 售价，含义由 --price-type 决定",
            "  --target <元>         想要获得的 Steam 余额",
            "  --game <cs2|tf2>      游戏手续费档案（默认 cs2）",
            "  --price-type <类型>   buyer  买家支付价，即市场挂单价；程序自动扣手续费（默认）",
            "                        seller 卖家到手价；程序不再扣手续费",
            "  --fee-mode <模式>     exact  按 Steam 真实手续费公式精确反解（默认）",
            "                        simple 粗略近似：售价 ÷ 1.15 向下取整",
            "  --gui                 启动图形界面",
            "  -h, --help            显示本帮助",
            "",
            "位置参数（在没有写 --buy / --sell / --target 时按顺序填充）：",
            "  第一个 = 饰品购入价，第二个 = Steam 售价，第三个 = 目标余额",
            "",
            "示例：",
            "  run.ps1 --buy 70 --sell 100 --target 1000",
            "  run.ps1 --buy 70 --sell 86.97 --target 1000 --price-type seller",
            "  run.ps1 --buy 70 --sell 100 --target 1000 --game tf2",
            "  run.ps1 --buy 70 --sell 100 --target 1000 --fee-mode simple");

    private ArgParser() {
    }

    /**
     * 解析参数。
     *
     * @throws IllegalArgumentException 出现未知参数、缺少取值或多余位置参数
     */
    public static Options parse(String[] args) {
        Options options = new Options();
        List<String> positional = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String token = args[i];
            if (token == null || token.isEmpty()) {
                continue;
            }
            if (!token.startsWith("-")) {
                positional.add(token);
                continue;
            }

            String key = token;
            String value = null;
            int eq = token.indexOf('=');
            if (eq > 0) {
                key = token.substring(0, eq);
                value = token.substring(eq + 1);
            }

            if (VALUE_OPTIONS.contains(key)) {
                if (value == null) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("参数 " + key + " 缺少取值");
                    }
                    String next = args[i + 1];
                    // 下一个 token 本身就是个选项时，说明用户漏写了取值。
                    // 若直接吞掉它，后面的参数会整体错位，报出毫不相干的错误。
                    if (next.startsWith("--")) {
                        throw new IllegalArgumentException(
                                "参数 " + key + " 缺少取值（后面紧跟的是 " + next + "）");
                    }
                    value = args[++i];
                }
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("参数 " + key + " 缺少取值");
                }
            }

            switch (key) {
                case "-h", "--help", "-?" -> options.help = true;
                case "--gui" -> options.gui = true;
                case "--buy", "--purchase" -> options.buy = value;
                case "--sell" -> options.sell = value;
                case "--target" -> options.target = value;
                case "--price-type", "--price" -> options.priceType = PriceType.byId(value);
                case "--game" -> options.gameProfile = GameProfile.byId(value);
                case "--fee-mode" -> options.feeMode = FeeMode.byId(value);
                default -> throw new IllegalArgumentException("未知参数：" + token);
            }
        }

        int index = 0;
        if (options.buy == null && index < positional.size()) {
            options.buy = positional.get(index++);
        }
        if (options.sell == null && index < positional.size()) {
            options.sell = positional.get(index++);
        }
        if (options.target == null && index < positional.size()) {
            options.target = positional.get(index++);
        }
        if (index < positional.size()) {
            throw new IllegalArgumentException(
                    "多余的参数：" + String.join(" ", positional.subList(index, positional.size())));
        }

        return options;
    }

    /** 解析结果。 */
    public static final class Options {

        private boolean help;
        private boolean gui;
        private String buy;
        private String sell;
        private String target;
        private PriceType priceType = PriceType.defaultType();
        private GameProfile gameProfile = GameProfile.defaultProfile();
        private FeeMode feeMode = FeeMode.defaultMode();

        public boolean help() {
            return help;
        }

        public boolean gui() {
            return gui;
        }

        public PriceType priceType() {
            return priceType;
        }

        public GameProfile gameProfile() {
            return gameProfile;
        }

        public FeeMode feeMode() {
            return feeMode;
        }

        /**
         * 转换成核心计算请求。
         *
         * @throws IllegalArgumentException 缺少必填参数或金额无法解析
         */
        public CalcRequest toRequest() {
            List<String> missing = new ArrayList<>();
            if (buy == null) {
                missing.add("--buy（饰品购入价）");
            }
            if (sell == null) {
                missing.add("--sell（Steam 售价）");
            }
            if (target == null) {
                missing.add("--target（目标余额）");
            }
            if (!missing.isEmpty()) {
                throw new IllegalArgumentException("缺少必填参数：" + String.join("、", missing));
            }

            return new CalcRequest(
                    Money.parse(buy),
                    Money.parse(sell),
                    priceType,
                    gameProfile,
                    feeMode,
                    Money.parse(target));
        }

        @Override
        public String toString() {
            return "Options[buy=" + buy + ", sell=" + sell + ", target=" + target
                    + ", priceType=" + priceType.id()
                    + ", game=" + gameProfile.id()
                    + ", feeMode=" + feeMode.id().toLowerCase(Locale.ROOT) + "]";
        }
    }
}
