package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * <b>The arena's champions</b>: each player's best run, kept for each settings the control panel can be set to, and how
 * they rank. Pure: no Minecraft. Only a player's id, his name and his scores are kept; nothing he typed.
 *
 * <p>The champions are per <b>settings</b> ({@link Key}: the difficulty and the number of waves; the difficulty brings
 * its own number of monsters), so players compete at the same game: fastest to beat 5 waves on Normal is a race of its own, and so is Easy. Inside one
 * settings, for a battle with a number of waves:
 * <ol>
 *   <li>a run that beat every wave ranks above one that did not, the fastest first;</li>
 *   <li>then the furthest wave reached;</li>
 *   <li>then a wave cleared above a wave he fell in;</li>
 *   <li>then the fastest to get there;</li>
 *   <li>then who got there first.</li>
 * </ol>
 * For Endless there is no beating every wave, so it is the furthest wave, then cleared, then the fastest. A player has
 * one entry for each settings: a run replaces it only when it ranks above it ({@link #merge}).
 */
public final class ArenaChampions {

    private ArenaChampions() {
    }

    /** How many rows the Champions board shows. */
    public static final int SHOWN = 5;
    /** How many entries are kept for one settings (the best). */
    public static final int KEPT_PER_KEY = 20;

    /** The settings a run was played at: what the champions are kept by. */
    public record Key(String difficulty, int waves) {
        public static Key of(ArenaSettings s) {
            return new Key(s.difficulty().id(), s.waves());
        }

        public boolean endless() {
            return waves == ArenaSettings.ENDLESS;
        }
    }

    /**
     * How far one player got in one battle: the furthest wave he reached, whether he cleared it, whether he beat every
     * wave (a battle with a number of waves, won), and the ticks from the first wave's start to when he reached that
     * wave (cleared: when it was cleared; won: the whole battle).
     */
    public record Reach(int wave, boolean cleared, boolean won, long ticks) {
    }

    /** One player's best at one settings. {@code seq} orders runs that tie on everything else (the first wins). */
    public record Entry(String id, String name, Key key, int wave, boolean cleared, boolean won, long ticks, long seq) {
        public Reach reach() {
            return new Reach(wave, cleared, won, ticks);
        }
    }

    /** The order inside one settings: best first. */
    public static final Comparator<Entry> RANK = Comparator
            .comparing((Entry e) -> !e.won())
            .thenComparing(e -> e.won() ? 0 : -e.wave())
            .thenComparing(e -> e.won() || e.cleared() ? 0 : 1)
            .thenComparingLong(Entry::ticks)
            .thenComparingLong(Entry::seq);

    /** True when {@code a} ranks above {@code b} (the sequence not counted: a tie is not better). */
    public static boolean better(Entry a, Entry b) {
        Entry a0 = new Entry(a.id(), a.name(), a.key(), a.wave(), a.cleared(), a.won(), a.ticks(), 0);
        Entry b0 = new Entry(b.id(), b.name(), b.key(), b.wave(), b.cleared(), b.won(), b.ticks(), 0);
        return RANK.compare(a0, b0) < 0;
    }

    /**
     * The champions after {@code run}: it replaces the same player's entry at the same settings when it ranks above it
     * (his name is brought up to date either way), or is added; each settings keeps its {@value #KEPT_PER_KEY} best. A
     * run that reached no wave is not kept. Returns a new list; {@code all} is not changed.
     */
    public static List<Entry> merge(List<Entry> all, Entry run) {
        List<Entry> out = new ArrayList<>(all);
        if (run == null || run.wave() <= 0) {
            return out;
        }
        boolean found = false;
        for (int i = 0; i < out.size(); i++) {
            Entry e = out.get(i);
            if (e.id().equals(run.id())) {
                if (e.key().equals(run.key())) {
                    found = true;
                    out.set(i, better(run, e) ? run : withName(e, run.name()));
                } else if (!e.name().equals(run.name())) {
                    out.set(i, withName(e, run.name()));
                }
            }
        }
        if (!found) {
            out.add(run);
        }
        List<Entry> same = new ArrayList<>(at(out, run.key()));
        if (same.size() > KEPT_PER_KEY) {
            out.removeAll(same.subList(KEPT_PER_KEY, same.size()));
        }
        return out;
    }

    /**
     * The champions saved before 0.1.6, when a run's settings also held the monsters size: the size is gone from the
     * key, so one player may have several entries at one key now. Each player keeps his best at each key (the best by
     * {@link #RANK}; a tie keeps the first run), under the name of his latest run; each key keeps its
     * {@value #KEPT_PER_KEY} best. Nothing else changes (the time, the wave, the run's sequence). Returns a new list.
     */
    public static List<Entry> migrate(List<Entry> all) {
        java.util.Map<List<Object>, Entry> best = new java.util.LinkedHashMap<>();
        java.util.Map<String, Entry> latest = new java.util.HashMap<>();
        for (Entry e : all) {
            best.merge(List.of(e.id(), e.key()), e, (a, b) -> RANK.compare(b, a) < 0 ? b : a);
            latest.merge(e.id(), e, (a, b) -> b.seq() > a.seq() ? b : a);
        }
        List<Entry> out = new ArrayList<>();
        for (Entry e : best.values()) {
            out.add(withName(e, latest.get(e.id()).name()));
        }
        java.util.Set<Key> keys = new java.util.LinkedHashSet<>();
        for (Entry e : out) {
            keys.add(e.key());
        }
        for (Key k : keys) {
            List<Entry> same = new ArrayList<>(at(out, k));
            if (same.size() > KEPT_PER_KEY) {
                out.removeAll(same.subList(KEPT_PER_KEY, same.size()));
            }
        }
        return out;
    }

    private static Entry withName(Entry e, String name) {
        return new Entry(e.id(), name, e.key(), e.wave(), e.cleared(), e.won(), e.ticks(), e.seq());
    }

    /** Every entry at {@code key}, best first. */
    public static List<Entry> at(List<Entry> all, Key key) {
        return all.stream().filter(e -> e.key().equals(key)).sorted(RANK).toList();
    }

    /** The top {@value #SHOWN} at {@code key}. */
    public static List<Entry> top(List<Entry> all, Key key) {
        List<Entry> a = at(all, key);
        return a.subList(0, Math.min(SHOWN, a.size()));
    }

    /** Player {@code id}'s entry at {@code key}, or null. */
    public static Entry bestOf(List<Entry> all, String id, Key key) {
        for (Entry e : all) {
            if (e.id().equals(id) && e.key().equals(key)) {
                return e;
            }
        }
        return null;
    }

    // ── words ───────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Ticks as minutes and seconds: "0:42", "12:05". */
    public static String time(long ticks) {
        long s = Math.max(0, ticks) / ArenaMatch.TICKS_PER_SECOND;
        return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** What a run did, in a few words: "Won all 5", "Wave 7". */
    public static String result(Entry e) {
        return e.won() ? "Won all " + e.wave() : "Wave " + e.wave();
    }

    /** The settings, in words: "Normal, 5 waves", "Hard, Endless". */
    public static String settingsWords(Key k) {
        ArenaDifficulty d = ArenaDifficulty.byId(k.difficulty());
        String diff = d == null ? k.difficulty() : d.word();
        return diff + ", " + (k.endless() ? "Endless" : k.waves() + " waves");
    }

    /** The control panel's line for one player's best at the settings: "Best for Steve: won in 4:12". */
    public static String bestLine(String name, Entry e) {
        if (e == null) {
            return name + ": no best at these settings yet";
        }
        return "Best for " + name + ": " + (e.won() ? "won in " + time(e.ticks())
                : "wave " + e.wave() + " in " + time(e.ticks()));
    }
}
