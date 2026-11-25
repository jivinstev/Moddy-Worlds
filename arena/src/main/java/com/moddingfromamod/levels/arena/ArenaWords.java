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

    /**
     * Chat, once a battle, to a player seen in Creative or Spectator during it (at its start, or when he switches): his
     * run of this battle is not recorded.
     */
    public static String notRecorded(ArenaUnranked.Mode mode) {
        return "You are in " + modeName(mode) + ": this battle will not go on the Champions board.";
    }

    /** The control panel's best line, for the player who pressed last while he is in Creative or Spectator. */
    public static String bestNotRecorded(String name, ArenaUnranked.Mode mode) {
        return name + ": in " + modeName(mode) + ", runs not recorded";
    }

    private static String modeName(ArenaUnranked.Mode mode) {
        return mode == ArenaUnranked.Mode.SPECTATOR ? "Spectator" : "Creative";
    }

    /** "wave 5" or "boss 2": how far a battle has to get, as the ladder counts. */
    private static String stepName(ArenaLadder.Count count, int after) {
        return count == ArenaLadder.Count.BOSSES ? "boss " + after + " is beaten" : "wave " + after + " is cleared";
    }

    /**
     * Chat, once per wish per prep, when a wish he puts on his hotbar would not come out as it is at the step the battle
     * is on (a battle starts at the first): "Your Meteor is locked in a battle until wave 5 is cleared.", "Your Dragon
     * will be smaller at first. It grows as you clear waves." Null when there is nothing to say.
     */
    public static String pinNotice(String name, ArenaPinNotice.Notice n, ArenaLadder.Count count) {
        if (n == null || n.type() == ArenaPinNotice.Type.NONE) {
            return null;
        }
        String your = name == null || name.isBlank() ? "This wish" : "Your " + name;
        boolean bosses = count == ArenaLadder.Count.BOSSES;
        return switch (n.type()) {
            case LOCKED -> your + " is locked in a battle until " + stepName(count, n.after()) + ".";
            case TOO_MUCH -> n.after() > 0 ? your + " is too much for now. It opens when " + stepName(count, n.after())
                    + "." : your + " is too much for this arena right now.";
            case FITTED -> {
                List<String> how = new java.util.ArrayList<>();
                for (ArenaPinNotice.Held h : n.held()) {
                    how.add(switch (h) {
                        case SMALLER -> "smaller";
                        case WEAKER -> "weaker";
                        case FEWER -> "fewer";
                        case GENTLER -> "gentler";
                    });
                }
                String what = how.size() == 1 ? how.get(0) : String.join(", ", how.subList(0, how.size() - 1))
                        + " and " + how.get(how.size() - 1);
                String grows = n.held().contains(ArenaPinNotice.Held.SMALLER) ? "It grows" : "It gets stronger";
                yield your + " will be " + what + " at first. " + grows + " as you "
                        + (bosses ? "beat bosses." : "clear waves.");
            }
            default -> null;
        };
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
            case EASY -> "Fewer monsters, and weaker.";
            case NORMAL -> "The usual monsters.";
            case HARD -> "More monsters, and tougher.";
            case EXTREME -> "The most, and the toughest!";
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

    // ── the unlock ladder ───────────────────────────────────────────────────────────────────────────────────────────

    /** The ladder's board title. */
    static final String LADDER_TITLE = "WISH LADDER";

    /** "1 heart", "2.5 hearts", "20 hearts", from health points (two to a heart). */
    static String hearts(double halfHearts) {
        double h = halfHearts / 2.0;
        String n = h == Math.rint(h) ? Long.toString((long) h) : String.format(java.util.Locale.ROOT, "%.1f", h);
        return n + (h == 1.0 ? " heart" : " hearts");
    }

    private static String number(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * One limit, in a child's words and the game's own numbers: "Health up to 20 hearts", "Up to 2 at once". A number the
     * arena has no words for (one a newer core added) is still shown, by its name.
     */
    static String limit(String fact, double most) {
        return switch (fact) {
            case "healthHalfHearts" -> "Health up to " + hearts(most);
            case "attackDamageHalfHearts" -> "Hits up to " + hearts(most);
            case "scaleTimes" -> "Size up to " + number(most) + " times";
            case "builderSizeStep" -> "Builder size up to " + number(most) + " of 4";
            case "builderStrengthStep" -> "Power strength up to " + number(most) + " of 3";
            case "countMade" -> "Up to " + number(most) + " at once";
            case "amountItems" -> "Up to " + number(most) + " items";
            case "radiusBlocks" -> "Power reach up to " + number(most) + (most == 1 ? " block" : " blocks");
            case "durationTicks" -> "Powers last up to " + seconds((int) Math.round(most / 20.0));
            case "movementSpeedAttribute" -> "Speed up to " + number(most);
            case "extentBlocks" -> "Buildings up to " + number(most) + " blocks long";
            case "armorPoints" -> "Armour up to " + number(most);
            case "knockbackResistanceFraction" -> "Knockback resistance up to " + Math.round(most * 100) + "%";
            default -> fact + " up to " + number(most);
        };
    }

    /** A number with no limit any more: "No limit on health". */
    static String noLimit(String fact) {
        return switch (fact) {
            case "healthHalfHearts" -> "No limit on health";
            case "attackDamageHalfHearts" -> "No limit on hits";
            case "scaleTimes", "builderSizeStep" -> "No limit on size";
            case "builderStrengthStep" -> "No limit on power strength";
            case "countMade" -> "No limit on how many at once";
            case "amountItems" -> "No limit on items";
            case "radiusBlocks" -> "No limit on power reach";
            case "durationTicks" -> "No limit on how long powers last";
            case "movementSpeedAttribute" -> "No limit on speed";
            case "extentBlocks" -> "No limit on building size";
            case "armorPoints" -> "No limit on armour";
            case "knockbackResistanceFraction" -> "No limit on knockback resistance";
            default -> "No limit on " + fact;
        };
    }

    /** What a locked kind or tag is called ({@code kind:<id>} or {@code tag:<id>}, as the ladder names them). */
    static String lockName(String kindOrTag) {
        String id = kindOrTag.substring(kindOrTag.indexOf(':') + 1);
        boolean tag = kindOrTag.startsWith("tag:");
        if (tag) {
            return switch (id) {
                case "world_rule" -> "World rules";
                case "changes_blocks" -> "Wishes that change blocks";
                case "summons" -> "Wishes that call creatures";
                case "fights" -> "Fighting wishes";
                case "hostile" -> "Creatures that attack";
                case "boss" -> "Boss creatures";
                case "builds" -> "Wishes that build";
                case "gives_items" -> "Wishes that give items";
                case "decorative" -> "Decorations";
                case "harmless" -> "Harmless wishes";
                default -> id;
            };
        }
        return switch (id) {
            case "creature" -> "Creatures";
            case "ride" -> "Rides";
            case "item" -> "Items";
            case "machine" -> "Machines";
            case "magic_block" -> "Magic blocks";
            case "building" -> "Buildings";
            case "picture" -> "Pictures";
            case "event" -> "Events";
            case "rule" -> "World rules";
            case "supplies" -> "Supplies";
            default -> "Other wishes";
        };
    }

    /** How far into a battle a step or a lock waits for, short: "wave 5", "boss 1". */
    static String opensAt(ArenaLadder.Count count, int after) {
        return (count == ArenaLadder.Count.BOSSES ? "boss " : "wave ") + after;
    }

    /** A locked make's words (under its tile, and when pressed): when, in a battle, it opens. */
    public static String ladderLocked(ArenaLadder.Count count, int after) {
        return count == ArenaLadder.Count.BOSSES ? "Locked: opens in a battle, after you beat boss " + after + "."
                : "Locked: opens in a battle, after wave " + after + ".";
    }

    /** Where the battle is on the ladder: "Before a battle: the first step", "This battle: 3 waves cleared". */
    static String ladderWhere(ArenaLadder.Count count, boolean inBattle, int reached) {
        if (!inBattle) {
            return "Before a battle: the first step";
        }
        if (count == ArenaLadder.Count.BOSSES) {
            return "This battle: " + (reached == 0 ? "no boss beaten yet" : reached == 1 ? "1 boss beaten"
                    : reached + " bosses beaten");
        }
        return "This battle: " + (reached == 0 ? "no wave cleared yet" : reached == 1 ? "1 wave cleared"
                : reached + " waves cleared");
    }

    /**
     * The wish ladder board's words, few and big, to be read from across the prep room: the title, where the battle is
     * on the ladder, at most {@value #BOARD_NOW_LINES} of what wishes can have now (health, hits, size and how many at
     * once first), one line of what is locked, one of what the next step brings. The full list is
     * {@code /moddy wishes} and the Arena Guide's.
     */
    record LadderBoardWords(String title, String where, List<String> now, String locked, String next) {
    }

    /** How many of the limits now the board shows. */
    static final int BOARD_NOW_LINES = 4;

    /** The facts the board shows first, when the step limits them: what matters most to a fighter. */
    static final List<String> BOARD_FIRST = List.of("healthHalfHearts", "attackDamageHalfHearts", "scaleTimes",
            "countMade");

    /** One limit, short, for the board: "Health: 20 hearts", "Size: x1.5", "2 at once". */
    static String boardLimit(String fact, double most) {
        return switch (fact) {
            case "healthHalfHearts" -> "Health: " + hearts(most);
            case "attackDamageHalfHearts" -> "Hits: " + hearts(most);
            case "scaleTimes" -> "Size: x" + number(most);
            case "countMade" -> number(most) + " at once";
            case "builderSizeStep" -> "Builder size: " + number(most) + " of 4";
            case "builderStrengthStep" -> "Power: " + number(most) + " of 3";
            case "amountItems" -> "Items: " + number(most);
            case "radiusBlocks" -> "Reach: " + number(most) + (most == 1 ? " block" : " blocks");
            case "durationTicks" -> "Powers: " + seconds((int) Math.round(most / 20.0));
            case "movementSpeedAttribute" -> "Speed: " + number(most);
            case "extentBlocks" -> "Buildings: " + number(most) + " long";
            case "armorPoints" -> "Armour: " + number(most);
            case "knockbackResistanceFraction" -> "Knockback: " + Math.round(most * 100) + "%";
            default -> fact + ": " + number(most);
        };
    }

    /** Where the battle is, short, for the board: "Before a battle: step 1", "This battle: 2 waves cleared". */
    static String boardWhere(ArenaLadder.Count count, boolean inBattle, int reached) {
        if (!inBattle) {
            return "Before a battle: step 1";
        }
        if (count == ArenaLadder.Count.BOSSES) {
            return "This battle: " + reached + (reached == 1 ? " boss beaten" : " bosses beaten");
        }
        return "This battle: " + reached + (reached == 1 ? " wave cleared" : " waves cleared");
    }

    /** The wish ladder board's words now ({@link LadderBoardWords}); in prep, the first step. */
    static LadderBoardWords ladderBoard(ArenaLadder ladder, boolean inBattle, int reached) {
        int at = inBattle ? reached : 0;
        java.util.Map<String, Double> limits = ladder.limits(at);
        List<String> order = new java.util.ArrayList<>(BOARD_FIRST);
        for (String k : com.moddingfromamod.wishbuilder.MakeFacts.NUMBERS) {
            if (!order.contains(k)) {
                order.add(k);
            }
        }
        for (String k : limits.keySet()) {
            if (!order.contains(k)) {
                order.add(k);
            }
        }
        List<String> now = new java.util.ArrayList<>();
        for (String k : order) {
            if (limits.containsKey(k) && now.size() < BOARD_NOW_LINES) {
                now.add(boardLimit(k, limits.get(k)));
            }
        }
        if (now.isEmpty()) {
            now.add("No limits!");
        }
        java.util.Map<String, Integer> lockedNow = ladder.stillLocked(at);
        String locked = null;
        if (!lockedNow.isEmpty()) {
            List<String> names = new java.util.ArrayList<>();
            for (String k : lockedNow.keySet()) {
                names.add(lockName(k));
            }
            locked = (inBattle ? "Locked: " : "Battle locks: ") + String.join(", ", names);
        }
        ArenaLadder.Step next = ladder.next(at);
        String nextLine = null;
        if (next != null) {
            nextLine = "After " + opensAt(ladder.progress(), next.after()) + ": "
                    + riseOne(ladder.rise(at, next.after()));
        }
        return new LadderBoardWords(LADDER_TITLE, boardWhere(ladder.progress(), inBattle, at), List.copyOf(now), locked,
                nextLine);
    }

    /** What a rise brings, in a few words: "events open", "health 30 hearts", "no limits!". */
    static String riseOne(ArenaLadder.Rise r) {
        if (!r.opened().isEmpty()) {
            return lockName(r.opened().get(0)).toLowerCase(java.util.Locale.ROOT) + " open";
        }
        if (r.top() && r.rose().values().stream().allMatch(v -> Double.isNaN(v[1]))) {
            return "no limits!";
        }
        for (String k : BOARD_FIRST) {
            double[] v = r.rose().get(k);
            if (v != null && !Double.isNaN(v[1])) {
                return boardLimit(k, v[1]).replace(":", "").toLowerCase(java.util.Locale.ROOT);
            }
        }
        for (java.util.Map.Entry<String, double[]> e : r.rose().entrySet()) {
            if (!Double.isNaN(e.getValue()[1])) {
                return boardLimit(e.getKey(), e.getValue()[1]).replace(":", "").toLowerCase(java.util.Locale.ROOT);
            }
        }
        return "stronger wishes";
    }

    /** What a rise brings, in one short line: "Health up to 40 hearts, hits up to 3.5 hearts and more". */
    static String riseShort(ArenaLadder.Rise r) {
        List<String> bits = new java.util.ArrayList<>();
        for (String o : r.opened()) {
            bits.add(lockName(o));
        }
        for (java.util.Map.Entry<String, double[]> e : r.rose().entrySet()) {
            double to = e.getValue()[1];
            bits.add(Double.isNaN(to) ? noLimit(e.getKey()) : limit(e.getKey(), to));
        }
        if (r.top() && bits.size() > 2) {
            return "No limits at all!";
        }
        if (bits.isEmpty()) {
            return "Nothing new";
        }
        String line = (bits.size() <= 2 ? String.join(", ", bits) : bits.get(0) + ", " + bits.get(1) + " and more")
                .toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    /** The moment's title, when a boss beaten raises his ladder. */
    static String ladderTitle() {
        return "Stronger wishes!";
    }

    /** The moment's subtitle: what the battle just reached. */
    static String ladderSubtitle(ArenaLadder.Count count, int reached) {
        return count == ArenaLadder.Count.BOSSES ? "You beat boss " + reached : "Wave " + reached + " cleared";
    }

    /**
     * The moment in chat: one or two short lines, what rose (the new numbers) and what opened. Empty when nothing did.
     */
    static List<String> ladderRose(ArenaLadder.Rise r) {
        List<String> out = new java.util.ArrayList<>();
        if (r.isEmpty()) {
            return out;
        }
        List<String> bits = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, double[]> e : r.rose().entrySet()) {
            double to = e.getValue()[1];
            if (!Double.isNaN(to) && bits.size() < 3) {
                bits.add(limit(e.getKey(), to).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (r.top() && r.rose().values().stream().allMatch(v -> Double.isNaN(v[1]))) {
            out.add("Your wishes can be as strong as you like now: no more limits!");
        } else if (!bits.isEmpty()) {
            out.add("Your wishes can be stronger now: " + String.join(", ", bits)
                    + (r.rose().size() > bits.size() ? ", and more." : "."));
        }
        if (!r.opened().isEmpty()) {
            List<String> names = new java.util.ArrayList<>();
            for (String o : r.opened()) {
                names.add(lockName(o));
            }
            out.add(String.join(" and ", names) + (names.size() == 1 && !names.get(0).endsWith("s") ? " is" : " are")
                    + " open now!");
        }
        return out;
    }

    /** {@code /moddy wishes}: the limits every fighter's wishes have now, and what the next step brings. */
    static List<String> ladderWishes(ArenaLadder ladder, boolean inBattle, int reached) {
        int at = inBattle ? reached : 0;
        List<String> out = new java.util.ArrayList<>();
        java.util.Map<String, Double> now = ladder.limits(at);
        List<String> bits = new java.util.ArrayList<>();
        for (String k : com.moddingfromamod.wishbuilder.MakeFacts.NUMBERS) {
            if (now.containsKey(k)) {
                bits.add(limit(k, now.get(k)).toLowerCase(java.util.Locale.ROOT));
            }
        }
        for (java.util.Map.Entry<String, Integer> e : ladder.stillLocked(at).entrySet()) {
            bits.add(lockName(e.getKey()).toLowerCase(java.util.Locale.ROOT) + " locked" + (inBattle ? "" : " in a battle")
                    + " until " + opensAt(ladder.progress(), e.getValue()));
        }
        String where = ladderWhere(ladder.progress(), inBattle, at);
        out.add(bits.isEmpty() ? "Wish limits: none. " + where + "."
                : "Wish limits (" + where + "): " + String.join(", ", bits) + ". A bigger wish is made to fit.");
        ArenaLadder.Step next = ladder.next(at);
        if (next != null) {
            out.add("After " + opensAt(ladder.progress(), next.after()) + (inBattle ? "" : " of a battle") + ": "
                    + riseShort(ladder.rise(at, next.after())) + ".");
        }
        if (!inBattle) {
            out.add("Every battle starts at the first step, and wishes grow stronger as it goes on.");
        }
        return out;
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

    // ── the hand-over in the prep room ({@link ArenaHandOver}) ──────────────────────────────────────────────────────

    /**
     * The action bar, when a make finished in the prep room reaches him: what it is and where it went. {@code name} is
     * its hotbar wish's name or the item's ("Dragon"); empty when it has none. {@code slot} is 0 to 8 (shown 1 to 9). Says
     * nothing of whether it is held back in a battle: the pin notice says that, once.
     */
    public static String handOverReady(String name, ArenaHandOver.Went went, int slot) {
        boolean item = went == ArenaHandOver.Went.REAL_ITEM;
        String your = name == null || name.isBlank() ? (item ? "Your item" : "Your wish") : "Your " + name.strip();
        return switch (went) {
            case HOTBAR -> your + " is ready! Slot " + (slot + 1) + ". Use it in the arena.";
            case WISH_BAG -> your + " is ready! Your hotbar is full, so it is in your Wish Bag.";
            case REAL_ITEM -> your + " is ready! It is in your inventory.";
        };
    }

    /** Chat, the first time a make is handed over to him in this world: the difference between a wish and an item. */
    public static String handOverFirst() {
        return "In the prep room, what you make comes to you. A thing you hold, like a sword, is a real item: it goes in"
                + " your inventory. A thing that appears or happens, like a creature, a building or an event, is a wish"
                + " on your hotbar: aim and use it in the arena to make it appear. Each use costs one of the wave's"
                + " hotbar wishes.";
    }

    /**
     * Chat, when the arena asked to keep a make and it was not kept ({@code kind} the core's: {@code picture},
     * {@code creature}, ...): one short, honest line.
     */
    public static String handOverNotKept(String kind, ArenaHandOver.NotKeptWhy why) {
        return switch (why) {
            case NO_REPLAY_RECIPE -> "picture".equals(kind)
                    ? "Your picture went up where you aimed: a picture cannot be kept as a wish."
                    : "That one happened right away: it cannot be kept as a wish.";
            case A_CHANGE -> "Your change was made where your thing stands.";
            case NOTHING_TAUGHT -> "The Magician had nothing to put on your hotbar this time, so nothing was made.";
            case OTHER -> "That one could not be kept as a wish.";
        };
    }
}
