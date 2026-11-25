package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>One battle's waves, as the control panel set them</b>: the rules file's waves ({@link ArenaWaves}), then the
 * generator's past their end ({@link ArenaGenerator}), as many as the WAVES setting asks for (or without end), each at
 * the difficulty's wave size ({@link ArenaSettings#scaleWave}) and with its swaps ({@link ArenaDifficulty#applyWave}),
 * and every {@code bossEvery}-th wave a boss wave: a boss ({@link ArenaBosses}) and a small escort of that wave's
 * monsters. Pure: no Minecraft, and every wave is made from its number when it is asked for, so an Endless battle needs
 * no list.
 *
 * <p>With the default rules and settings (5 waves, Normal: wave size 5) waves 1 to 4 are the rules file's exactly and
 * wave 5 is the first boss wave: Rumble the Ravager and a third of the rules file's wave 5 (a husk, a pillager, a witch and a
 * vindicator).
 */
public final class ArenaRun {

    /** How much longer a boss wave may last than another wave (and at most {@link ArenaWaves#MAX_WAVE_SECONDS}). */
    public static final int BOSS_TIME = 2;

    private final ArenaWaves base;
    private final ArenaSettings settings;
    private final int size;
    private final int count;
    private final int bossEvery;

    private ArenaRun(ArenaWaves base, ArenaSettings settings, int size, int count, int bossEvery) {
        this.base = base;
        this.settings = settings;
        this.size = size;
        this.count = count;
        this.bossEvery = bossEvery;
    }

    /** The battle {@code settings} (already within the rules' limits) bring with these base waves. */
    public static ArenaRun of(ArenaWaves base, ArenaSettings settings) {
        ArenaSettings s = settings == null ? ArenaSettings.DEFAULT : settings;
        return new ArenaRun(base, s, s.size(base.limits()), s.waves(), base.bossEvery());
    }

    /** Exactly the table's own waves at size 5 and Normal, as many as it writes, and no boss (for a test). */
    public static ArenaRun exactly(ArenaWaves table) {
        return new ArenaRun(table, ArenaSettings.DEFAULT, ArenaSettings.DEFAULT_SIZE, table.count(), 0);
    }

    public ArenaSettings settings() {
        return settings;
    }

    /** The wave size this battle's monsters come at: its difficulty's (or the rules' for it). */
    public int waveSize() {
        return size;
    }

    /** How many waves the battle has; {@link ArenaSettings#ENDLESS} (0) when it is Endless. */
    public int count() {
        return count;
    }

    public boolean endless() {
        return count == ArenaSettings.ENDLESS;
    }

    /** True when wave {@code n} is the last (never, in an Endless battle). */
    public boolean isLast(int n) {
        return !endless() && n >= count;
    }

    public int countdownSeconds() {
        return base.countdownSeconds();
    }

    public int breatherSeconds() {
        return base.breatherSeconds();
    }

    /** The break after wave {@code n} is cleared: longer after a boss wave. */
    public int breatherAfter(int n) {
        return isBossWave(n) ? base.bossBreatherSeconds() : base.breatherSeconds();
    }

    /** How long wave {@code n} may last: a boss wave {@value #BOSS_TIME} times the others. */
    public int waveSeconds(int n) {
        return isBossWave(n) ? Math.min(ArenaWaves.MAX_WAVE_SECONDS, base.waveSeconds() * BOSS_TIME)
                : base.waveSeconds();
    }

    /** Each player's lives in this battle. */
    public int lives() {
        return base.lives();
    }

    public boolean isBossWave(int n) {
        return ArenaBosses.isBossWave(n, bossEvery);
    }

    /** Wave {@code n}'s boss, or null. */
    public ArenaBosses.Boss boss(int n) {
        return ArenaBosses.boss(n, bossEvery);
    }

    /** The boss waves of this battle up to wave {@code upTo} (for the board: "Boss waves: 5, 10"). */
    public List<Integer> bossWaves(int upTo) {
        List<Integer> out = new ArrayList<>();
        int last = endless() ? upTo : Math.min(count, upTo);
        for (int n = 1; n <= last; n++) {
            if (isBossWave(n)) {
                out.add(n);
            }
        }
        return out;
    }

    /** Wave {@code n} as the rules file writes it or the generator makes it, before the wave size and the difficulty. */
    public ArenaWaves.Wave baseWave(int n) {
        return n <= base.count() ? base.wave(n) : base.generator().wave(n, base.waves());
    }

    /**
     * Wave {@code n}'s monsters (the boss not counted): its base wave at the size and the difficulty; on a boss wave,
     * the escort of that.
     */
    public ArenaWaves.Wave wave(int n) {
        ArenaWaves.Wave w = settings.difficulty().applyWave(n, ArenaSettings.scaleWave(baseWave(n), size));
        return isBossWave(n) ? ArenaBosses.escort(w) : w;
    }

    /** How many monsters wave {@code n} brings, the boss counted. */
    public int size(int n) {
        return wave(n).size() + (isBossWave(n) ? 1 : 0);
    }

    /** What wave {@code n}'s monsters' most health is multiplied by: the difficulty's, times the generator's. */
    public double health(int n) {
        return settings.difficulty().health() * base.generator().health(n, base.count());
    }

    /** What wave {@code n}'s boss's most health is multiplied by: the difficulty's. */
    public double bossHealth() {
        return settings.difficulty().health();
    }
}
