package com.moddingfromamod.levels.arena;

import java.util.List;

/**
 * <b>Every word a player reads about a battle</b>, in one place so they can be read (and tested) together: plain words a
 * child reads, plain characters only. Pure.
 */
public final class ArenaWords {

    private ArenaWords() {
    }

    static String seconds(int n) {
        return n == 1 ? "1 second" : n + " seconds";
    }

    /** Boss bar during a wave. */
    public static String barWave(int wave, int of, int left) {
        return "Wave " + wave + " of " + of + ": " + (left == 1 ? "1 monster left" : left + " monsters left");
    }

    /** Boss bar during the countdown and the breathers. */
    public static String barNext(int wave, int of, int secondsLeft) {
        return "Wave " + wave + " of " + of + " in " + seconds(secondsLeft);
    }

    public static String barWon() {
        return "You won!";
    }

    public static String barLost() {
        return "Battle lost";
    }

    /** Chat, to everyone in the place, when the button is pressed. */
    public static String started(int of, int countdown) {
        return "A battle is starting! " + (of == 1 ? "1 wave" : of + " waves") + " of monsters. The first comes in "
                + seconds(countdown) + ".";
    }

    /** The action bar during the countdown and the breathers. */
    public static String comingIn(int wave, int secondsLeft) {
        return "Wave " + wave + " in " + secondsLeft + "...";
    }

    public static String waveTitle(int wave, int of) {
        return "Wave " + wave + " of " + of;
    }

    public static String waveSubtitle() {
        return "Here they come!";
    }

    public static String cleared(int wave, int breather) {
        return "Wave " + wave + " cleared! The next wave comes in " + seconds(breather) + ".";
    }

    public static String wonTitle() {
        return "You won!";
    }

    public static String wonSubtitle(int of) {
        return of == 1 ? "The wave is cleared." : "All " + of + " waves cleared.";
    }

    public static String wonChat(int of) {
        return "You won the battle! " + (of == 1 ? "The wave is" : "All " + of + " waves are")
                + " cleared. The arena will be tidied for the next battle.";
    }

    public static String lostTitle() {
        return "Battle lost";
    }

    public static String lostSubtitle(ArenaMatch.Why why) {
        return why == ArenaMatch.Why.TIME_UP ? "Time ran out." : "The monsters won this time.";
    }

    public static String lostChat(ArenaMatch.Why why, int wave) {
        String what = why == ArenaMatch.Why.TIME_UP
                ? "Time ran out on wave " + wave + "."
                : "Everyone in it was knocked out or left.";
        return "The battle is over. " + what + " Press the button in the prep room to try again.";
    }

    /** Chat, to everyone in the place, once the arena is put back. */
    public static String ready() {
        return "The arena is tidy again. Press the button in the prep room to start a battle.";
    }

    public static String busy() {
        return "A battle is already on. Wait for it to end.";
    }

    public static String noWaves() {
        return "This arena has no waves in its rules, so a battle cannot start.";
    }

    public static String peaceful() {
        return "Battles need the game's difficulty above Peaceful: on Peaceful, monsters vanish as soon as they come.";
    }

    public static String nobody() {
        return "Nobody is in the arena or the prep room to fight.";
    }

    public static String notAnArena() {
        return "This world is not an arena.";
    }

    // ── wishes in the arena ─────────────────────────────────────────────────────────────────────────────────────────

    static String magicianWishes(int n) {
        return n == 1 ? "1 Magician wish" : n + " Magician wishes";
    }

    /** A typed wish or a new make, refused during a battle (chat). */
    public static String battleInstantOnly() {
        return "A battle is on, so only the wishes on your hotbar work now. New wishes are made in the prep room"
                + " before a battle.";
    }

    /** A kept thing fired again too soon in a battle (the action bar, so pressing again does not fill the chat). */
    public static String resting(int seconds) {
        return "That wish is resting. It is ready again in " + seconds(seconds) + ".";
    }

    /** A typed wish refused in prep: this prep's Magician wishes are used up (chat). */
    public static String magicianUsedUp(int budget) {
        if (budget <= 0) {
            return "Magician wishes are not used in this arena. Builder and Apprentice wishes are free.";
        }
        String used = budget == 1 ? "your Magician wish" : "all " + magicianWishes(budget);
        return "You have used " + used + " for this prep. You get " + budget
                + " more after the next battle. Builder and Apprentice wishes are still free.";
    }

    /** How many Magician wishes are left this prep ({@code /moddy wishes}, and after one is used). */
    public static String magicianLeft(int left, int budget) {
        return "Magician wishes left before the next battle: " + left + " of " + budget + ".";
    }

    /** A Magician wish has started and is counted (the action bar). */
    public static String magicianCounted(int left, int budget) {
        return "That uses 1 Magician wish. " + magicianLeft(left, budget);
    }

    /** A counted Magician wish did not happen or was undone, so its count comes back (chat). */
    public static String magicianGivenBack(int left, int budget) {
        return "That Magician wish did not happen, so it does not count. " + magicianLeft(left, budget);
    }

    /** The same, said inside the core's own line when a wish is undone. */
    public static String magicianUndone(int left, int budget) {
        return "It does not count. " + magicianLeft(left, budget);
    }

    /** {@code /moddy wishes} in prep, after how many Magician wishes are left. */
    public static String prepWishes() {
        return "Builder and Apprentice wishes are free. A wish with Add to hotbar on its page in My wishes can be fired"
                + " in a battle.";
    }

    /** {@code /moddy wishes} during a battle. */
    public static List<String> battleWishes(int cooldownSeconds) {
        return List.of("A battle is on: fire your wishes from your hotbar. Each one rests " + seconds(cooldownSeconds)
                + " after you use it.", "New wishes are made in the prep room before a battle.");
    }

    /** What the watch's and the wish box's titles add in prep. */
    public static String titlePrep(int left) {
        return " - " + left + " Magician " + (left == 1 ? "wish" : "wishes") + " left";
    }

    /** What the watch's and the wish box's titles add during a battle. */
    public static String titleBattle() {
        return " - Battle: use your hotbar";
    }

    // ── the battle arena control panel ──────────────────────────────────────────────────────────────────────────────

    public static final String PANEL_TITLE = "BATTLE ARENA CONTROL PANEL";
    public static final String PANEL_SIZE = "WAVE SIZE";
    public static final String PANEL_DIFFICULTY = "DIFFICULTY";
    public static final String PANEL_START = "START BATTLE";

    static String monsters(int n) {
        return n == 1 ? "1 monster" : n + " monsters";
    }

    /** What the wave size means, under the selectors: the first wave's and the last wave's monsters (one line each). */
    public static List<String> sizeMeans(ArenaWaves waves) {
        if (waves == null) {
            return List.of("No waves in the rules");
        }
        int[] fl = waves.firstAndLastSize();
        if (waves.count() == 1) {
            return List.of("1 wave: " + monsters(fl[0]));
        }
        return List.of("First wave: " + monsters(fl[0]), "Last wave: " + monsters(fl[1]));
    }

    /** The battle's state, under the start button: what the board shows now. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of) {
        return switch (phase) {
            case IDLE -> "Ready! Press START.";
            case COUNTDOWN -> "Battle on: wave 1 next";
            case WAVE -> "Battle on: wave " + wave + " of " + of;
            case BREATHER -> "Battle on: wave " + (wave + 1) + " next";
            case WON -> "You won!";
            case LOST -> "Battle lost";
            case RESETTING -> "Tidying the arena...";
        };
    }

    /** The lines of words one place on the board shows, for these settings, waves and battle. */
    public static List<String> panel(ArenaPanel.Words w, ArenaSettings settings, ArenaWaves waves,
                                     ArenaMatch.Phase phase, int wave, int of) {
        return switch (w) {
            case TITLE -> List.of(PANEL_TITLE);
            case SIZE_LABEL -> List.of(PANEL_SIZE);
            case SIZE_VALUE -> List.of(String.valueOf(settings.size()));
            case SIZE_DOWN_SIGN, DIFFICULTY_DOWN_SIGN -> List.of("-");
            case SIZE_UP_SIGN, DIFFICULTY_UP_SIGN -> List.of("+");
            case DIFFICULTY_LABEL -> List.of(PANEL_DIFFICULTY);
            case DIFFICULTY_VALUE -> List.of(settings.difficulty().word());
            case SIZE_MEANS -> sizeMeans(waves);
            case START_LABEL -> List.of(PANEL_START);
            case STATE -> List.of(panelState(phase, wave, of));
        };
    }

    /** What a difficulty means, said when it is picked. */
    public static String difficultyMeans(ArenaDifficulty d) {
        return switch (d) {
            case EASY -> "Weaker monsters.";
            case NORMAL -> "The usual monsters.";
            case HARD -> "Tougher monsters, and strong ones sooner.";
            case EXTREME -> "The toughest monsters, and the strongest come early!";
        };
    }

    /** The action bar, when the wave size is changed. */
    public static String sizeChanged(int size, ArenaWaves waves) {
        if (waves == null) {
            return "Wave size " + size + ".";
        }
        int[] fl = waves.firstAndLastSize();
        if (waves.count() == 1) {
            return "Wave size " + size + ": the wave has " + monsters(fl[0]) + ".";
        }
        return "Wave size " + size + ": the first wave has " + monsters(fl[0]) + ", the last has " + fl[1] + ".";
    }

    /** The action bar, when the difficulty is changed. */
    public static String difficultyChanged(ArenaDifficulty d) {
        return "Difficulty: " + d.word() + ". " + difficultyMeans(d);
    }

    /** The action bar, when the wave size cannot go further that way. */
    public static String sizeAtEnd(boolean up, int max) {
        if (!up) {
            return "The wave size is already the smallest.";
        }
        return max >= ArenaSettings.MAX_SIZE ? "The wave size is already the biggest."
                : "This arena's rules let the wave size go up to " + max + ".";
    }

    /** The action bar, when the difficulty cannot go further that way. */
    public static String difficultyAtEnd(boolean up, ArenaDifficulty max) {
        if (!up) {
            return ArenaDifficulty.EASY.word() + " is already the easiest.";
        }
        return max == ArenaDifficulty.EXTREME ? ArenaDifficulty.EXTREME.word() + " is already the hardest."
                : "This arena's rules let the difficulty go up to " + max.word() + ".";
    }

    public static String sizeFixed(int size) {
        return "This arena's rules keep the wave size at " + size + ".";
    }

    public static String difficultyFixed(ArenaDifficulty d) {
        return "This arena's rules keep the difficulty at " + d.word() + ".";
    }

    /** The action bar, when a selector is pressed during a battle. */
    public static String panelBusy() {
        return "A battle is on. Change the wave size and difficulty when it is over.";
    }

    /** Every fixed line, for a test that reads them all. */
    static List<String> all() {
        return List.of(barWave(2, 5, 3), barWave(5, 5, 1), barNext(1, 5, 5), barNext(2, 5, 1), barWon(), barLost(),
                started(5, 5), started(1, 3), comingIn(1, 3), waveTitle(2, 5), waveSubtitle(), cleared(2, 10),
                wonTitle(), wonSubtitle(5), wonSubtitle(1), wonChat(5), wonChat(1), lostTitle(),
                lostSubtitle(ArenaMatch.Why.TIME_UP), lostSubtitle(ArenaMatch.Why.NOBODY_LEFT),
                lostChat(ArenaMatch.Why.TIME_UP, 3), lostChat(ArenaMatch.Why.NOBODY_LEFT, 2), ready(), busy(),
                noWaves(), peaceful(), nobody(), notAnArena(), battleInstantOnly(), resting(7),
                resting(1), magicianUsedUp(3), magicianUsedUp(1), magicianUsedUp(0), magicianLeft(2, 3),
                magicianCounted(0, 3), magicianGivenBack(1, 3), magicianUndone(3, 3), prepWishes(),
                String.join(" ", battleWishes(10)), titlePrep(2), titlePrep(1), titleBattle(), PANEL_TITLE, PANEL_SIZE,
                PANEL_DIFFICULTY, PANEL_START, panelBusy(), sizeFixed(5), sizeAtEnd(true, 10), sizeAtEnd(true, 7),
                sizeAtEnd(false, 10), difficultyAtEnd(true, ArenaDifficulty.EXTREME),
                difficultyAtEnd(true, ArenaDifficulty.HARD), difficultyAtEnd(false, ArenaDifficulty.EXTREME));
    }

    /** Every line the panel and its selectors can say, for a test that reads them all. */
    static List<String> allPanel(ArenaWaves waves) {
        List<String> out = new java.util.ArrayList<>();
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            out.add(difficultyChanged(d));
            out.add(difficultyFixed(d));
            out.add(d.word());
        }
        for (int s = ArenaSettings.MIN_SIZE; s <= ArenaSettings.MAX_SIZE; s++) {
            ArenaWaves at = new ArenaSettings(s, ArenaDifficulty.NORMAL).apply(waves);
            out.add(sizeChanged(s, at));
            out.addAll(sizeMeans(at));
        }
        out.addAll(sizeMeans(null));
        for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
            out.add(panelState(p, 2, 5));
        }
        return out;
    }
}
