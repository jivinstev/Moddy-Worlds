package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <b>How hard a battle is</b>, picked on the control panel: four steps, each a row of data. A difficulty changes WHICH
 * mobs come and HOW TOUGH they are, never how many (the wave size does that, {@link ArenaSettings}). Pure: no Minecraft.
 *
 * <p>Each step is:
 * <ul>
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

    /** Only husks, and weaker: 0.6 of their health. */
    EASY("easy", "Easy", 0.6, List.of(
            new Swap("minecraft:pillager", "minecraft:husk", 1),
            new Swap("minecraft:witch", "minecraft:husk", 1),
            new Swap("minecraft:vindicator", "minecraft:husk", 1))),
    /** The rules file's waves, exactly. */
    NORMAL("normal", "Normal", 1.0, List.of()),
    /** Tougher: a quarter more health, and from wave 4 the husks are vindicators. */
    HARD("hard", "Hard", 1.25, List.of(
            new Swap("minecraft:husk", "minecraft:vindicator", 4))),
    /**
     * The toughest: half again the health; wave 2's husks are pillagers, from wave 3 the husks are vindicators, and from
     * wave 5 the vindicators are ravagers.
     */
    EXTREME("extreme", "Extreme", 1.5, List.of(
            new Swap("minecraft:husk", "minecraft:vindicator", 3),
            new Swap("minecraft:husk", "minecraft:pillager", 2),
            new Swap("minecraft:vindicator", "minecraft:ravager", 5)));

    /** A mob kind {@code from} that comes as {@code to} instead, from wave {@code fromWave} on (counting from 1). */
    public record Swap(String from, String to, int fromWave) {
    }

    public static final ArenaDifficulty DEFAULT = NORMAL;

    private final String id;
    private final String word;
    private final double health;
    private final List<Swap> swaps;

    ArenaDifficulty(String id, String word, double health, List<Swap> swaps) {
        this.id = id;
        this.word = word;
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
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (ArenaWaves.Group g : waves.wave(n).groups()) {
                counts.merge(kindIn(n, g.mob()), g.count(), Integer::sum);
            }
            List<ArenaWaves.Group> groups = new ArrayList<>();
            counts.forEach((mob, count) -> groups.add(new ArenaWaves.Group(mob, count)));
            out.add(new ArenaWaves.Wave(groups));
        }
        return waves.withWaves(out);
    }
}
