package com.steamarbitrage.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 一个游戏在 Steam 社区市场上的手续费档案。
 *
 * <p>Steam 社区市场对每笔成交收取两部分费用：
 * <ul>
 *   <li><b>Steam 手续费</b>：默认 5%，每笔至少 1 分</li>
 *   <li><b>游戏（发行商）手续费</b>：默认 10%，每笔至少 1 分</li>
 * </ul>
 * 三者关系为：
 * <pre>买家支付 = 卖家收入 + Steam 手续费 + 游戏手续费</pre>
 * 其中每项手续费都是 {@code max(floor(卖家收入 × 费率), 最低手续费)}。
 *
 * <p>费率以“万分比”整数保存（{@code 500} 表示 5%），因此全部计算都是整数运算，
 * 不会引入浮点误差。
 *
 * <p>目前内置 CS2 与 TF2 两个档案。两者在 Steam 上的实际费率相同
 * （Steam 5% + 游戏 10%），但仍然分开登记：不同游戏/不同时期的费率可能不同，
 * 分开后只需修改本类中的常量即可，无需改动任何计算代码。
 */
public final class GameProfile {

    /** 万分比基准：10000 表示 100%。 */
    static final int BP_SCALE = 10_000;

    private final String id;
    private final String displayName;
    private final int steamFeeBp;
    private final int gameFeeBp;
    private final long minSteamFeeCents;
    private final long minGameFeeCents;

    private GameProfile(String id,
                        String displayName,
                        int steamFeeBp,
                        int gameFeeBp,
                        long minSteamFeeCents,
                        long minGameFeeCents) {
        this.id = id;
        this.displayName = displayName;
        this.steamFeeBp = steamFeeBp;
        this.gameFeeBp = gameFeeBp;
        this.minSteamFeeCents = minSteamFeeCents;
        this.minGameFeeCents = minGameFeeCents;
    }

    /** 自定义档案，最低手续费均为 Steam 默认的 1 分。 */
    public static GameProfile of(String id, String displayName, int steamFeeBp, int gameFeeBp) {
        return new GameProfile(id, displayName, steamFeeBp, gameFeeBp, 1L, 1L);
    }

    /** Counter-Strike 2：Steam 5% + 游戏 10%。 */
    public static final GameProfile CS2 =
            of("cs2", "Counter-Strike 2 (CS2)", 500, 1000);

    /** Team Fortress 2：Steam 5% + 游戏 10%。 */
    public static final GameProfile TF2 =
            of("tf2", "Team Fortress 2 (TF2)", 500, 1000);

    private static final Map<String, GameProfile> REGISTRY;

    static {
        Map<String, GameProfile> registry = new LinkedHashMap<>();
        registry.put(CS2.id, CS2);
        registry.put(TF2.id, TF2);
        // 常见别名，方便命令行直接书写
        registry.put("csgo", CS2);
        registry.put("cs", CS2);
        registry.put("counter-strike", CS2);
        registry.put("counterstrike", CS2);
        registry.put("team-fortress-2", TF2);
        registry.put("teamfortress2", TF2);
        REGISTRY = Collections.unmodifiableMap(registry);
    }

    /** 内置档案列表，按登记顺序，供菜单展示。 */
    public static List<GameProfile> builtIns() {
        return List.of(CS2, TF2);
    }

    public static GameProfile defaultProfile() {
        return CS2;
    }

    /** 按 id 或别名查找，大小写不敏感。 */
    public static Optional<GameProfile> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(id.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * 按 id 或别名查找。
     *
     * @throws IllegalArgumentException 无法识别
     */
    public static GameProfile byId(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException(
                "无法识别的游戏：" + id + "（可用：" + availableIds() + "）"));
    }

    /** 可用于命令行的 id 列表，例如 {@code cs2, tf2}。 */
    public static String availableIds() {
        return "cs2, tf2";
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public int steamFeeBp() {
        return steamFeeBp;
    }

    public int gameFeeBp() {
        return gameFeeBp;
    }

    public int totalFeeBp() {
        return steamFeeBp + gameFeeBp;
    }

    public long minSteamFeeCents() {
        return minSteamFeeCents;
    }

    public long minGameFeeCents() {
        return minGameFeeCents;
    }

    /** 给定卖家收入（分），返回 Steam 手续费（分）。 */
    public long steamFeeCents(long sellerCents) {
        if (sellerCents <= 0) {
            return 0L;
        }
        return Math.max(sellerCents * steamFeeBp / BP_SCALE, minSteamFeeCents);
    }

    /** 给定卖家收入（分），返回游戏手续费（分）。 */
    public long gameFeeCents(long sellerCents) {
        if (sellerCents <= 0) {
            return 0L;
        }
        return Math.max(sellerCents * gameFeeBp / BP_SCALE, minGameFeeCents);
    }

    /** 给定卖家收入（分），返回买家需要支付的总额（分）。 */
    public long buyerPaysCents(long sellerCents) {
        if (sellerCents <= 0) {
            return 0L;
        }
        return sellerCents + steamFeeCents(sellerCents) + gameFeeCents(sellerCents);
    }

    /** 供报告使用的一句话费率描述。 */
    public String feeDescription() {
        StringBuilder sb = new StringBuilder();
        boolean hasSteam = steamFeeBp > 0;
        boolean hasGame = gameFeeBp > 0;
        if (hasSteam) {
            sb.append("Steam ").append(bpToPercent(steamFeeBp));
        }
        if (hasGame) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append("游戏 ").append(bpToPercent(gameFeeBp));
        }
        if (sb.length() == 0) {
            sb.append("无手续费");
        } else {
            sb.append(" = 合计 ").append(bpToPercent(totalFeeBp()));
        }
        long minFee = minSteamFeeCents + minGameFeeCents;
        if (hasSteam || hasGame) {
            sb.append("，每项最低 ").append(bpToAmount(minFee));
        }
        return sb.toString();
    }

    /** 供菜单展示的条目，例如 {@code 1) Counter-Strike 2 (CS2) [cs2] - Steam 5% + 游戏 10%}。 */
    public String menuLabel() {
        return displayName + " [" + id + "] - " + feeDescription();
    }

    static String bpToPercent(int bp) {
        return BigDecimal.valueOf(bp, 2).stripTrailingZeros().toPlainString() + "%";
    }

    private static String bpToAmount(long cents) {
        return Money.ofCents(cents).toGroupedString();
    }

    /** 全部内置档案 id 列表。 */
    public static List<String> builtInIds() {
        List<String> ids = new ArrayList<>();
        for (GameProfile profile : builtIns()) {
            ids.add(profile.id);
        }
        return ids;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
