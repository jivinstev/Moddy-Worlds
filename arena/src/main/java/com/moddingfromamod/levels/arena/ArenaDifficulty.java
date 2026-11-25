package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>How hard a battle is</b>, picked on the control panel: four steps, each a row of data. A difficulty says HOW
 * MANY mobs come, WHICH and HOW TOUGH. Pure: no Minecraft.
 *
 * <p>Each step is:
 * <ul>
 *   <li>{@code size}: how many monsters a wave brings, on a scale of {@value ArenaSettings#MIN_SIZE} to
 *       {@value ArenaSettings#MAX_SIZE} where {@value ArenaSettings#DEFAULT_SIZE} is the rules file's waves exactly:
 *       every count is multiplied by size / 5 ({@link ArenaSettings#scaleWave}). Normal is 5; Easy 3 (x0.6, as its
 *       health), Hard 6 (x1.2) and Extreme 8 (x1.6, about its x1.5 health), so the default first wave is 2, 3, 4 and 5
 *       monsters, one more each step. (Until 0.1.6 the size was its own MONSTERS selector, 1 to 10.) A rules file may
 *       set other sizes ({@code waveSizes}, {@link ArenaSettings.Limits});</li>
 *   <li>{@code health}: every wave mob's most health is multiplied by it (Normal 1: exactly as the game makes them);</li>
 *   <li>{@code swaps}: a mob kind that becomes another from a wave on, in order, the first that fits (a group is
 *       swapped once, never twice). The count stays: one husk becomes one vindicator. Two groups of one kind after the
 *       swaps are counted as one.</li>
 * </ul>
 * Normal is the rules file's waves exactly. Every swap's mob is a vanilla 1.21.8 mob that walks, stays in the arena
 * and none of {@link ArenaWaves#REFUSED} (a test reads them all). A wave table that names none of a step's mobs gets
 * only that step's health.
 */
public enum ArenaDifficulty {

    /** Fewer (size 3) and only husks, and weaker: 0.6 of their health (the generator's later kinds come as husks too). */
    EASY("easy", "Easy", 3, 0.6, List.of(
            new Swap("minecraft:pillager", "minecraft:husk", 1),
            new Swap("minecraft:witch", "minecraft:husk", 1),
            new Swap("minecraft:vindicator", "minecraft:husk", 1),
            new Swap("minecraft:piglin_brute", "minecraft:husk", 1),
            new Swap("minecraft:ravager", "minecraft:husk", 1))),
    /** The rules file's waves, exactly (size 5). */
    NORMAL("normal", "Normal", 5, 1.0, List.of()),
    /** A fifth more of them (size 6), tougher: a quarter more health, and from wave 4 the husks are vindicators. */
    HARD("hard", "Hard", 6, 1.25, List.of(
            new Swap("minecraft:husk", "minecraft:vindicator", 4))),
    /**
     * The most (size 8) and the toughest: half again the health; wave 2's husks are pillagers, from wave 3 the husks are vindicators, and from
     * wave 5 the vindicators are ravagers.
     */
    EXTREME("extreme", "Extreme", 8, 1.5, List.of(
            new Swap("minecraft:husk", "minecraft:vindicator", 3),
            new Swap("minecraft:husk", "minecraft:pillager", 2),
            new Swap("minecraft:vindicator", "minecraft:ravager", 5)));

    /** A mob kind {@code from} that comes as {@code to} instead, from wave {@code fromWave} on (counting from 1). */
    public record Swap(String from, String to, int fromWave) {
    }

    public static final ArenaDifficulty DEFAULT = NORMAL;

    private final String id;
    private final String word;
    private final int size;
    private final double health;
    private final List<Swap> swaps;

    ArenaDifficulty(String id, String word, int size, double health, List<Swap> swaps) {
        this.id = id;
        this.word = word;
        this.size = size;
        this.health = health;
        this.swaps = List.copyOf(swaps);
    }

    /** Its name in a rules file and a saved world: {@code easy}, {@code normal}, {@code hard}, {@code extreme}. */
    public String id() {
        return id;
    }

    /** Its name on the board: Easy, Normal, Hard, Extreme. */
    public String word() {
        return word;
    }

    /**
     * How many monsters a wave brings at this difficulty, unless a rules file says otherwise: on the 1 to 10 scale
     * where 5 is the rules file's waves exactly ({@link ArenaSettings#scaleWave}).
     */
    public int size() {
        return size;
    }

    /** What every wave mob's most health is multiplied by. */
    public double health() {
        return health;
    }

    public List<Swap> swaps() {
        return swaps;
    }

    /** The step named {@code id} (any case, spaces round it ignored), or null when there is none. */
    public static ArenaDifficulty byId(String id) {
        if (id == null) {
            return null;
        }
        String s = id.trim().toLowerCase(Locale.ROOT);
        for (ArenaDifficulty d : values()) {
            if (d.id.equals(s)) {
                return d;
            }
        }
        return null;
    }

    /** "easy, normal, hard, extreme" */
    static String ids() {
        List<String> out = new ArrayList<>();
        for (ArenaDifficulty d : values()) {
            out.add(d.id);
        }
        return String.join(", ", out);
    }

    /** The kind a mob of {@code mob} comes as in wave {@code wave} at this difficulty. */
    public String kindIn(int wave, String mob) {
        for (Swap s : swaps) {
            if (s.from().equals(mob) && wave >= s.fromWave()) {
                return s.to();
            }
        }
        return mob;
    }

    /** The waves at this difficulty: each group's kind swapped, the counts and timings unchanged. Pure. */
    public ArenaWaves apply(ArenaWaves waves) {
        if (swaps.isEmpty()) {
            return waves;
        }
        List<ArenaWaves.Wave> out = new ArrayList<>();
        for (int n = 1; n <= waves.count(); n++) {
            out.add(applyWave(n, waves.wave(n)));
        }
        return waves.withWaves(out);
    }

    /** Wave {@code n} at this difficulty ({@link #apply}'s rule for one wave). Pure. */
    public ArenaWaves.Wave applyWave(int n, ArenaWaves.Wave wave) {
        if (swaps.isEmpty()) {
            return wave;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ArenaWaves.Group g : wave.groups()) {
            counts.merge(kindIn(n, g.mob()), g.count(), Integer::sum);
        }
        List<ArenaWaves.Group> groups = new ArrayList<>();
        counts.forEach((mob, count) -> groups.add(new ArenaWaves.Group(mob, count)));
        return new ArenaWaves.Wave(groups);
    }
}
