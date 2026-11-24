package com.moddingfromamod.levels.arena;

import java.util.List;

/**
 * <b>Every word a player reads about a battle</b>, in one place so they can be read (and tested) together: plain words a
 * child reads, plain characters only. Pure. A number of waves {@code of} of 0 is an Endless battle.
 */
public final class ArenaWords {

    private ArenaWords() {
    }

    static String seconds(int n) {
        return n == 1 ? "1 second" : n + " seconds";
    }

    static String lives(int n) {
        return n == 1 ? "1 life" : n + " lives";
    }

    /** "Wave 2 of 5", or "Wave 12" in an Endless battle. */
    static String waveOf(int wave, int of) {
        return of <= 0 ? "Wave " + wave : "Wave " + wave + " of " + of;
    }

    /** Boss bar during a wave. */
    public static String barWave(int wave, int of, int left) {
        return waveOf(wave, of) + ": " + (left == 1 ? "1 monster left" : left + " monsters left");
    }

    /** Boss bar during the countdown and the breathers. */
    public static String barNext(int wave, int of, int secondsLeft) {
        return waveOf(wave, of) + " in " + seconds(secondsLeft);
    }

    /** Boss bar before a boss wave. */
    public static String barNextBoss(int wave, int of, int secondsLeft) {
        return "BOSS " + waveOf(wave, of).toLowerCase(java.util.Locale.ROOT) + " in " + seconds(secondsLeft);
    }

    /** Boss bar during a boss wave, while its boss stands: the boss's health. */
    public static String barBoss(String boss, int wave, int of) {
        return "BOSS: " + boss + " (" + waveOf(wave, of).toLowerCase(java.util.Locale.ROOT) + ")";
    }

    public static String barWon() {
        return "You won!";
    }

    public static String barLost() {
        return "Battle lost";
    }

    /** Chat, to everyone in the place, when the button is pressed. */
    public static String started(int of, int countdown, int lives) {
        String waves = of <= 0 ? "Endless waves" : of == 1 ? "1 wave" : of + " waves";
        return "A battle is starting! " + waves + " of monsters. The first comes in " + seconds(countdown) + ". You"
                + " each have " + lives(lives) + ".";
    }

    /** The action bar during the countdown and the breathers. */
    public static String comingIn(int wave, int secondsLeft) {
        return "Wave " + wave + " in " + secondsLeft + "...";
    }

    public static String waveTitle(int wave, int of) {
        return waveOf(wave, of);
    }

    public static String waveSubtitle() {
        return "Here they come!";
    }

    public static String bossTitle() {
        return "BOSS WAVE";
    }

    public static String bossSubtitle(String boss) {
        return boss + " is here!";
    }

    /** Chat, when a boss wave begins. */
    public static String bossChat(String boss, int wave) {
        return "Wave " + wave + " is a boss wave: " + boss + " is here! Beat the boss and the monsters with it.";
    }

    /** Chat, when a boss is beaten. */
    public static String bossBeaten(String boss) {
        return "You beat " + boss + "!";
    }

    public static String cleared(int wave, int breather) {
        return "Wave " + wave + " cleared! The next wave comes in " + seconds(breather) + ".";
    }

    /** A fighter knocked out with lives left (action bar and chat). */
    public static String knockedOut(int livesLeft) {
        return "Knocked out! " + lives(livesLeft) + " left. You are back in the prep room: go get them!";
    }

    /** A fighter knocked out of his last life. */
    public static String outOfLives() {
        return "Knocked out, and no lives left. You are out of this battle.";
    }

    /** The action bar during a wave, now and then: how many lives he has left. */
    public static String livesLeft(int n) {
        return n == 1 ? "1 life left" : n + " lives left";
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
                : "Everyone in it ran out of lives or left.";
        return "The battle is over. " + what + " Press the button in the prep room to try again.";
    }

    /** Chat, to a fighter, when his run goes on the Champions board. */
    public static String recorded(String result, String time, int rank) {
        return "Your run: " + result + " in " + time + (rank > 0 ? ". You are number " + rank
                + " on the Champions board!" : ".");
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
    public static final String PANEL_SIZE = "MONSTERS";
    public static final String PANEL_WAVES = "WAVES";
    public static final String PANEL_DIFFICULTY = "DIFFICULTY";
    public static final String PANEL_START = "START BATTLE";
    public static final String ENDLESS = "Endless";

    static String monsters(int n) {
        return n == 1 ? "1 monster" : n + " monsters";
    }

    /** The number of waves on the board: "5", "Endless". */
    public static String wavesValue(int waves) {
        return waves == ArenaSettings.ENDLESS ? ENDLESS : String.valueOf(waves);
    }

    /**
     * What the settings mean, under the start button: the first wave's monsters, and the boss waves (two lines).
     */
    public static List<String> sizeMeans(ArenaRun run) {
        if (run == null) {
            return List.of("No waves in the rules");
        }
        String first = "First wave: " + monsters(run.size(1));
        List<Integer> bosses = run.bossWaves(20);
        String boss;
        if (bosses.isEmpty()) {
            boss = "No boss waves";
        } else if (run.endless() || bosses.size() > 4) {
            boss = "A boss every " + bosses.get(0) + " waves";
        } else {
            StringBuilder b = new StringBuilder(bosses.size() == 1 ? "Boss wave: " : "Boss waves: ");
            for (int i = 0; i < bosses.size(); i++) {
                b.append(i == 0 ? "" : ", ").append(bosses.get(i));
            }
            boss = b.toString();
        }
        return List.of(first, boss);
    }

    /** The battle's state, under the start button: what the board shows now. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of) {
        return panelState(phase, wave, of, false);
    }

    /** The same; {@code boss}: the wave fought now (or the next one, in a breather) is a boss wave. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of, boolean boss) {
        return switch (phase) {
            case IDLE -> "Ready! Press START.";
            case COUNTDOWN -> "Battle on: wave 1 next";
            case WAVE -> boss ? "BOSS " + waveOf(wave, of).toLowerCase(java.util.Locale.ROOT) + "!"
                    : "Battle on: " + waveOf(wave, of).toLowerCase(java.util.Locale.ROOT);
            case BREATHER -> "Battle on: " + (boss ? "BOSS " : "") + "wave " + (wave + 1) + " next";
            case WON -> "You won!";
            case LOST -> "Battle lost";
            case RESETTING -> "Tidying the arena...";
        };
    }

    /** The lines of words one place on the board shows, for these settings, waves and battle. */
    public static List<String> panel(ArenaPanel.Words w, ArenaSettings settings, ArenaRun run,
                                     ArenaMatch.Phase phase, int wave, int of, boolean boss, String best) {
        return switch (w) {
            case TITLE -> List.of(PANEL_TITLE);
            case SIZE_LABEL -> List.of(PANEL_SIZE);
            case SIZE_VALUE -> List.of(String.valueOf(settings.size()));
            case SIZE_DOWN_SIGN, DIFFICULTY_DOWN_SIGN, WAVES_DOWN_SIGN -> List.of("-");
            case SIZE_UP_SIGN, DIFFICULTY_UP_SIGN, WAVES_UP_SIGN -> List.of("+");
            case DIFFICULTY_LABEL -> List.of(PANEL_DIFFICULTY);
            case DIFFICULTY_VALUE -> List.of(settings.difficulty().word());
            case WAVES_LABEL -> List.of(PANEL_WAVES);
            case WAVES_VALUE -> List.of(wavesValue(settings.waves()));
            case SIZE_MEANS -> sizeMeans(run);
            case START_LABEL -> List.of(PANEL_START);
            case STATE -> List.of(panelState(phase, wave, of, boss));
            case BEST -> List.of(best == null || best.isBlank() ? "Press a button to see your best" : best);
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

    /** The action bar, when the monsters size is changed. */
    public static String sizeChanged(int size, ArenaRun run) {
        if (run == null) {
            return "Monsters: " + size + ".";
        }
        return "Monsters: " + size + ". The first wave has " + monsters(run.size(1)) + ".";
    }

    /** The action bar, when the difficulty is changed. */
    public static String difficultyChanged(ArenaDifficulty d) {
        return "Difficulty: " + d.word() + ". " + difficultyMeans(d);
    }

    /** The action bar, when the number of waves is changed. */
    public static String wavesChanged(int waves) {
        if (waves == ArenaSettings.ENDLESS) {
            return "Waves: Endless. They keep coming until you lose!";
        }
        return "Waves: " + waves + ". A boss comes every 5th wave.";
    }

    /** The action bar, when the monsters size cannot go further that way. */
    public static String sizeAtEnd(boolean up, int max) {
        if (!up) {
            return "Monsters is already the smallest.";
        }
        return max >= ArenaSettings.MAX_SIZE ? "Monsters is already the biggest."
                : "This arena's rules let monsters go up to " + max + ".";
    }

    /** The action bar, when the difficulty cannot go further that way. */
    public static String difficultyAtEnd(boolean up, ArenaDifficulty max) {
        if (!up) {
            return ArenaDifficulty.EASY.word() + " is already the easiest.";
        }
        return max == ArenaDifficulty.EXTREME ? ArenaDifficulty.EXTREME.word() + " is already the hardest."
                : "This arena's rules let the difficulty go up to " + max.word() + ".";
    }

    /** The action bar, when the number of waves cannot go further that way ({@code most}: the last option). */
    public static String wavesAtEnd(boolean up, int least, int most, boolean limited) {
        if (!up) {
            return least + " waves is already the fewest.";
        }
        if (most == ArenaSettings.ENDLESS) {
            return "Endless is already the most.";
        }
        return limited ? "This arena's rules let the waves go up to " + most + "." : most + " is already the most.";
    }

    public static String sizeFixed(int size) {
        return "This arena's rules keep monsters at " + size + ".";
    }

    public static String difficultyFixed(ArenaDifficulty d) {
        return "This arena's rules keep the difficulty at " + d.word() + ".";
    }

    public static String wavesFixed(int waves) {
        return "This arena's rules keep the waves at " + wavesValue(waves) + ".";
    }

    /** The action bar, when a selector is pressed during a battle. */
    public static String panelBusy() {
        return "A battle is on. Change the settings when it is over.";
    }

    // ── the Champions board ─────────────────────────────────────────────────────────────────────────────────────────

    public static final String CHAMPIONS_TITLE = "ARENA CHAMPIONS";

    /** Under the title: which settings these champions played. */
    public static String championsAt(ArenaChampions.Key k) {
        return ArenaChampions.settingsWords(k);
    }

    /** The board with no champions at these settings yet. */
    public static String championsNone() {
        return "No champions here yet. Be the first!";
    }

    /** Every fixed line, for a test that reads them all. */
    static List<String> all() {
        return List.of(barWave(2, 5, 3), barWave(5, 5, 1), barWave(12, 0, 4), barNext(1, 5, 5), barNext(2, 5, 1),
                barNext(9, 0, 3), barNextBoss(5, 5, 3), barBoss("Rumble the Ravager", 5, 10), barWon(), barLost(),
                started(5, 5, 3), started(1, 3, 1), started(0, 5, 3), comingIn(1, 3), waveTitle(2, 5),
                waveTitle(30, 0), waveSubtitle(), bossTitle(), bossSubtitle("King Husk"), bossChat("King Husk", 20),
                bossBeaten("Captain Chop"), cleared(2, 10), knockedOut(2), knockedOut(1), outOfLives(),
                livesLeft(3), livesLeft(1), wonTitle(), wonSubtitle(5), wonSubtitle(1), wonChat(5), wonChat(1),
                lostTitle(), lostSubtitle(ArenaMatch.Why.TIME_UP), lostSubtitle(ArenaMatch.Why.NOBODY_LEFT),
                lostChat(ArenaMatch.Why.TIME_UP, 3), lostChat(ArenaMatch.Why.NOBODY_LEFT, 2),
                recorded("Won all 5", "3:12", 1), recorded("Wave 3", "1:10", 0), ready(), busy(),
                noWaves(), peaceful(), nobody(), notAnArena(), battleInstantOnly(), resting(7),
                resting(1), magicianUsedUp(3), magicianUsedUp(1), magicianUsedUp(0), magicianLeft(2, 3),
                magicianCounted(0, 3), magicianGivenBack(1, 3), magicianUndone(3, 3), prepWishes(),
                String.join(" ", battleWishes(10)), titlePrep(2), titlePrep(1), titleBattle(), PANEL_TITLE, PANEL_SIZE,
                PANEL_WAVES, PANEL_DIFFICULTY, PANEL_START, panelBusy(), sizeFixed(5), sizeAtEnd(true, 10),
                sizeAtEnd(true, 7), sizeAtEnd(false, 10), difficultyAtEnd(true, ArenaDifficulty.EXTREME),
                difficultyAtEnd(true, ArenaDifficulty.HARD), difficultyAtEnd(false, ArenaDifficulty.EXTREME),
                wavesFixed(10), wavesFixed(0), wavesAtEnd(false, 5, 0, false), wavesAtEnd(true, 5, 0, false),
                wavesAtEnd(true, 5, 10, true), CHAMPIONS_TITLE, championsNone());
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
            for (int n : ArenaSettings.Limits.NONE.waveOptions()) {
                ArenaRun at = new ArenaSettings(s, ArenaDifficulty.NORMAL, n).run(waves);
                out.add(sizeChanged(s, at));
                out.addAll(sizeMeans(at));
                out.add(wavesChanged(n));
                out.add(wavesValue(n));
                for (ArenaDifficulty d : ArenaDifficulty.values()) {
                    out.add(championsAt(ArenaChampions.Key.of(new ArenaSettings(s, d, n))));
                }
            }
        }
        out.addAll(sizeMeans(null));
        for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
            out.add(panelState(p, 2, 5));
            out.add(panelState(p, 5, 5, true));
            out.add(panelState(p, 25, 0, true));
        }
        return out;
    }
}
