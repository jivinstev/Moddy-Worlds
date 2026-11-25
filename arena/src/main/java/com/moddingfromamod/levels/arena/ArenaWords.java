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

    /** Chat, when a wave is cleared: the break, and what it is for. */
    public static String cleared(int wave, int breather) {
        return "Wave " + wave + " cleared! Break time: the gate is open, and the next wave comes in "
                + seconds(breather) + ". Change your hotbar in the prep room, or press READY on the board.";
    }

    /** Chat, when the arena was mended in the break. */
    public static String mended() {
        return "The arena is mended for the next wave.";
    }

    /** The action bar, to a fighter who pressed READY while others are not yet. */
    public static String readyWaiting(int notYet) {
        return "You are ready! Waiting for " + (notYet == 1 ? "1 more fighter." : notYet + " more fighters.");
    }

    /** The action bar, to a player who presses READY but is not fighting in this battle. */
    public static String readyNotFighting() {
        return "You are not in this battle. The fighters press READY.";
    }

    /** Chat, to everyone in the place, when every fighter is ready. */
    public static String allReady(int seconds) {
        return "Everyone is ready! The next wave comes in " + seconds(seconds) + ".";
    }

    /** A fighter knocked out with lives left (action bar and chat). */
    public static String knockedOut(int livesLeft) {
        return "Knocked out! " + lives(livesLeft) + " left. You are back in the prep room for a moment, then back in"
                + " the arena: go get them!";
    }

    /** The action bar, to a player who fell out under the place and is put back in the prep room. */
    public static String fellOut() {
        return "Whoops, you fell out! Back to the prep room.";
    }

    /** Chat, when a battle starts: what the gate does. */
    public static String gateRule() {
        return "When a wave comes, the gate shuts and everyone fighting is taken into the arena. It opens again in"
                + " the break.";
    }

    /** The action bar, to a fighter moved into the arena as a wave starts. */
    public static String gateShut() {
        return "The gate is shut. Fight!";
    }

    /** The action bar, to a fighter moved back into the arena after his count. */
    public static String backIn() {
        return "Back in the arena. Fight!";
    }

    /** The action bar, to a fighter back on his feet in the prep room during a wave: his lives, and his count. */
    public static String backInCount(int livesLeft, int seconds) {
        return livesLeft(livesLeft) + ". Back into the arena in " + seconds + "...";
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

    static String hotbarWishes(int n) {
        return n == 1 ? "1 hotbar wish" : n + " hotbar wishes";
    }

    /** A hotbar wish fired in a battle with this wave's wishes used up (chat, once per try). */
    public static String outOfWishes(int perWave) {
        return "You have used your " + hotbarWishes(perWave) + " for this wave. You get " + perWave
                + " more when the next wave comes.";
    }

    /** The bar each fighter sees in a battle: his hotbar wishes left this wave. */
    public static String barWishes(int left, int perWave) {
        return "Your wishes this wave: " + left + " of " + perWave;
    }

    /** Chat, when a wave is cleared: the hotbar wishes start again. */
    public static String wishesBack(int perWave) {
        return "Your " + hotbarWishes(perWave) + " for the next wave are ready.";
    }

    /** Under the start button: how many hotbar wishes a wave allows. */
    public static String wishesMeans(int perWave) {
        return "Hotbar wishes: " + perWave + " a wave";
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
    public static String prepWishes(int perWave) {
        return "Builder and Apprentice wishes are free. A wish with Add to hotbar on its page in My wishes can be fired"
                + " in a battle, " + perWave + " each wave.";
    }

    /** {@code /moddy wishes} during a battle: how many hotbar wishes he has left this wave. */
    public static List<String> battleWishes(int cooldownSeconds, int left, int perWave) {
        return List.of("A battle is on: fire your wishes from your hotbar. You have " + left + " of " + perWave
                + " left this wave. Each one rests " + seconds(cooldownSeconds) + " after you use it.",
                "New wishes are made in the prep room before a battle.");
    }

    /** What the watch's and the wish box's titles add in prep. */
    public static String titlePrep(int left) {
        return " - " + left + " Magician " + (left == 1 ? "wish" : "wishes") + " left";
    }

    /** What the watch's and the wish box's titles add during a battle. */
    public static String titleBattle() {
        return " - Battle: use your hotbar";
    }

    // ── wished creatures in a battle ────────────────────────────────────────────────────────────────────────────────

    /** Chat, once, to the player a wished creature helps, when it takes its role in a battle. */
    public static String roleTaken(ArenaRoles.Role role, String name) {
        return switch (role) {
            case FIGHTER -> "Your " + name + " is a Fighter! It will fight the monsters with you.";
            case HEALER -> "Your " + name + " is a Healer! It will keep you healthy.";
            case TANK -> "Your " + name + " is a Tank! It will make the monsters chase it.";
        };
    }

    /** Chat, when a battle starts: the creatures wished into the arena help. */
    public static String creaturesHelp() {
        return "Creatures you wished into the arena help you: each one is a Fighter, a Healer or a Tank.";
    }

    // ── the battle arena control panel ──────────────────────────────────────────────────────────────────────────────

    public static final String PANEL_TITLE = "BATTLE ARENA CONTROL PANEL";
    public static final String PANEL_WAVES = "WAVES";
    public static final String PANEL_DIFFICULTY = "DIFFICULTY";
    public static final String PANEL_START = "START BATTLE";
    /** The start button's label in a break between waves: pressing it says you are ready. */
    public static final String PANEL_READY = "READY";
    public static final String ENDLESS = "Endless";

    static String monsters(int n) {
        return n == 1 ? "1 monster" : n + " monsters";
    }

    /** The number of waves on the board: "5", "Endless". */
    public static String wavesValue(int waves) {
        return waves == ArenaSettings.ENDLESS ? ENDLESS : String.valueOf(waves);
    }

    /** Under the selectors: how many monsters the first wave brings ({@code n} below 0: no waves in the rules). */
    public static String firstWave(int n) {
        return n < 0 ? "No waves in the rules" : "First wave: " + monsters(n);
    }

    /** The same, for a battle ({@code run} null: no waves in the rules). */
    public static String firstWave(ArenaRun run) {
        return firstWave(run == null ? -1 : run.size(1));
    }

    /** What the settings mean, under the start button: the boss waves (one line). */
    public static List<String> means(ArenaRun run) {
        return means(run, 0);
    }

    /** The same, and a second line with the hotbar wishes a wave allows ({@code perWave} above 0). */
    public static List<String> means(ArenaRun run, int perWave) {
        if (run == null) {
            return perWave > 0 ? List.of(wishesMeans(perWave)) : List.of();
        }
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
        return perWave > 0 ? List.of(boss, wishesMeans(perWave)) : List.of(boss);
    }

    /** The battle's state, under the start button: what the board shows now. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of) {
        return panelState(phase, wave, of, false);
    }

    /** The same; {@code boss}: the wave fought now (or the next one, in a breather) is a boss wave. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of, boolean boss) {
        return panelState(phase, wave, of, boss, -1);
    }

    /** The same, with the seconds left of a break ({@code secondsLeft} 0 or more): its timer on the board. */
    public static String panelState(ArenaMatch.Phase phase, int wave, int of, boolean boss, int secondsLeft) {
        if (phase == ArenaMatch.Phase.BREATHER && secondsLeft >= 0) {
            return "Break: " + (boss ? "BOSS " : "") + "wave " + (wave + 1) + " in " + secondsLeft + "s";
        }
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
        return panel(w, settings, run, phase, wave, of, boss, best, 0);
    }

    /** The same, with the hotbar wishes a wave allows under the start button ({@code perWave} above 0). */
    public static List<String> panel(ArenaPanel.Words w, ArenaSettings settings, ArenaRun run,
                                     ArenaMatch.Phase phase, int wave, int of, boolean boss, String best,
                                     int perWave) {
        return panel(w, settings, run, phase, wave, of, boss, best, perWave, -1);
    }

    /**
     * The same, with the seconds left of a break ({@code secondsLeft} 0 or more): in a break the start button reads
     * READY and the state line is the break's timer.
     */
    public static List<String> panel(ArenaPanel.Words w, ArenaSettings settings, ArenaRun run,
                                     ArenaMatch.Phase phase, int wave, int of, boolean boss, String best,
                                     int perWave, int secondsLeft) {
        return switch (w) {
            case TITLE -> List.of(PANEL_TITLE);
            case DIFFICULTY_DOWN_SIGN, WAVES_DOWN_SIGN -> List.of("-");
            case DIFFICULTY_UP_SIGN, WAVES_UP_SIGN -> List.of("+");
            case DIFFICULTY_LABEL -> List.of(PANEL_DIFFICULTY);
            case DIFFICULTY_VALUE -> List.of(settings.difficulty().word());
            case WAVES_LABEL -> List.of(PANEL_WAVES);
            case WAVES_VALUE -> List.of(wavesValue(settings.waves()));
            case FIRST_WAVE -> List.of(firstWave(run));
            case MEANS -> means(run, perWave);
            case START_LABEL -> List.of(phase == ArenaMatch.Phase.BREATHER ? PANEL_READY : PANEL_START);
            case STATE -> List.of(panelState(phase, wave, of, boss, secondsLeft));
            case BEST -> List.of(best == null || best.isBlank() ? "Press a button to see your best" : best);
        };
    }

    /** What a difficulty means, said when it is picked. */
    public static String difficultyMeans(ArenaDifficulty d) {
        return switch (d) {
            case EASY -> "Fewer and weaker monsters.";
            case NORMAL -> "The usual monsters.";
            case HARD -> "More monsters, tougher, and strong ones sooner.";
            case EXTREME -> "The most monsters and the toughest!";
        };
    }

    /** The action bar, when the difficulty is changed. */
    public static String difficultyChanged(ArenaDifficulty d) {
        return "Difficulty: " + d.word() + ". " + difficultyMeans(d);
    }

    /** The same, with how many monsters the first wave brings now ({@code run} null: not said). */
    public static String difficultyChanged(ArenaDifficulty d, ArenaRun run) {
        return run == null ? difficultyChanged(d)
                : difficultyChanged(d) + " First wave: " + monsters(run.size(1)) + ".";
    }

    /** The action bar, when the number of waves is changed. */
    public static String wavesChanged(int waves) {
        if (waves == ArenaSettings.ENDLESS) {
            return "Waves: Endless. They keep coming until you lose!";
        }
        return "Waves: " + waves + ". A boss comes every 5th wave.";
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
                bossBeaten("Captain Chop"), cleared(2, 10), mended(), readyWaiting(1), readyWaiting(2),
                allReady(3), readyNotFighting(), knockedOut(2), knockedOut(1), outOfLives(),
                fellOut(), gateRule(), gateShut(), backIn(), backInCount(2, 3), backInCount(1, 1),
                livesLeft(3), livesLeft(1), wonTitle(), wonSubtitle(5), wonSubtitle(1), wonChat(5), wonChat(1),
                lostTitle(), lostSubtitle(ArenaMatch.Why.TIME_UP), lostSubtitle(ArenaMatch.Why.NOBODY_LEFT),
                lostChat(ArenaMatch.Why.TIME_UP, 3), lostChat(ArenaMatch.Why.NOBODY_LEFT, 2),
                recorded("Won all 5", "3:12", 1), recorded("Wave 3", "1:10", 0), ready(), busy(),
                noWaves(), peaceful(), nobody(), notAnArena(), battleInstantOnly(), resting(7),
                resting(1), outOfWishes(5), outOfWishes(1), barWishes(3, 5), barWishes(0, 1), wishesBack(5),
                wishesBack(1), wishesMeans(5), magicianUsedUp(3), magicianUsedUp(1), magicianUsedUp(0), magicianLeft(2, 3),
                magicianCounted(0, 3), magicianGivenBack(1, 3), magicianUndone(3, 3), prepWishes(5),
                String.join(" ", battleWishes(10, 4, 5)), titlePrep(2), titlePrep(1), titleBattle(), PANEL_TITLE,
                PANEL_WAVES, PANEL_DIFFICULTY, PANEL_START, panelBusy(), difficultyAtEnd(true, ArenaDifficulty.EXTREME),
                difficultyAtEnd(true, ArenaDifficulty.HARD), difficultyAtEnd(false, ArenaDifficulty.EXTREME),
                wavesFixed(10), wavesFixed(0), wavesAtEnd(false, 5, 0, false), wavesAtEnd(true, 5, 0, false),
                wavesAtEnd(true, 5, 10, true), CHAMPIONS_TITLE, championsNone(), creaturesHelp(),
                roleTaken(ArenaRoles.Role.FIGHTER, "Buddy the Cat"), roleTaken(ArenaRoles.Role.HEALER, "Buddy the Cat"),
                roleTaken(ArenaRoles.Role.TANK, "Buddy the Cat"));
    }

    /** Every line the panel and its selectors can say, for a test that reads them all. */
    static List<String> allPanel(ArenaWaves waves) {
        List<String> out = new java.util.ArrayList<>();
        for (ArenaDifficulty d : ArenaDifficulty.values()) {
            out.add(difficultyChanged(d));
            out.add(difficultyFixed(d));
            out.add(d.word());
            for (int n : ArenaSettings.Limits.NONE.waveOptions()) {
                ArenaRun at = new ArenaSettings(d, n).run(waves);
                out.add(difficultyChanged(d, at));
                out.add(firstWave(at));
                out.addAll(means(at, ArenaWishes.DEFAULT_PER_WAVE));
                out.add(wavesChanged(n));
                out.add(wavesValue(n));
                out.add(championsAt(ArenaChampions.Key.of(new ArenaSettings(d, n))));
            }
        }
        out.add(firstWave((ArenaRun) null));
        out.addAll(means(null, ArenaWishes.DEFAULT_PER_WAVE));
        for (ArenaMatch.Phase p : ArenaMatch.Phase.values()) {
            out.add(panelState(p, 2, 5));
            out.add(panelState(p, 5, 5, true));
            out.add(panelState(p, 25, 0, true));
            out.add(panelState(p, 4, 10, true, 45));
            out.add(panelState(p, 24, 0, false, 15));
        }
        return out;
    }
}
