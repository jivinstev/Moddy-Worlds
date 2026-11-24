package com.moddingfromamod.levels.arena;

/**
 * <b>One battle in the arena, as a state machine</b>: IDLE → COUNTDOWN → WAVE 1 → BREATHER → WAVE 2 → … → WON or LOST →
 * RESETTING → IDLE. Pure: no Minecraft here; {@link ArenaModule} feeds it, once a tick, how many of the wave's mobs are
 * left and how many fighters are still in, and carries out the {@link Event} it answers with (spawn a wave, say a line,
 * reset the arena).
 *
 * <p>The rules, all here:
 * <ul>
 *   <li>Between two waves there is a break (the BREATHER): {@code breatherSeconds} after a wave, longer after a boss
 *       wave ({@code bossBreatherSeconds}); when every fighter says he is ready ({@link #ready}) it ends in 3 seconds.</li>
 *   <li>Only one battle at a time: {@link #start} while one is on is refused.</li>
 *   <li>A wave is cleared when none of its mobs is left; after the last wave the battle is WON.</li>
 *   <li>The battle is LOST when no fighter is still in (during the countdown: nobody in the place), or when a wave
 *       outlasts its time limit.</li>
 *   <li>WON and LOST are held for {@link #END_HOLD_TICKS} so the words can be read, then the arena is reset (one
 *       {@link Kind#RESET}), then the machine is IDLE again ({@link Kind#READY}).</li>
 *   <li>After a restart in the middle of a battle there is no battle any more: {@link #endedByRestart} goes straight
 *       to RESETTING, after {@link #RESTART_WAIT_TICKS} so the arena's mobs have loaded and can be cleared.</li>
 * </ul>
 */
public final class ArenaMatch {

    public static final int TICKS_PER_SECOND = 20;
    /** How long WON or LOST shows before the arena resets: 5 seconds. */
    public static final int END_HOLD_TICKS = 5 * TICKS_PER_SECOND;
    /** After a restart, how long to wait before resetting, so the arena's mobs have loaded: 5 seconds. */
    public static final int RESTART_WAIT_TICKS = 5 * TICKS_PER_SECOND;
    /** How long a break still lasts once every fighter is ready: 3 seconds, so the next wave is never a surprise. */
    public static final int READY_TICKS = 3 * TICKS_PER_SECOND;

    public enum Phase { IDLE, COUNTDOWN, WAVE, BREATHER, WON, LOST, RESETTING }

    /** Why a battle was lost. */
    public enum Why { NOBODY_LEFT, TIME_UP }

    public enum Kind {
        /** a whole second of the countdown or the breather is left ({@link Event#seconds}) */
        COUNTDOWN,
        /** wave {@link Event#wave} begins: spawn it */
        WAVE_START,
        /** wave {@link Event#wave} is cleared; the next comes in {@link Event#seconds} */
        WAVE_CLEARED,
        /** every wave cleared */
        WON,
        /** lost, {@link Event#why} */
        LOST,
        /** reset the arena now */
        RESET,
        /** the arena is ready for the next battle */
        READY
    }

    /** What a tick asks the world to do. */
    public record Event(Kind kind, int wave, int seconds, Why why) {
        static Event of(Kind k, int wave, int seconds) {
            return new Event(k, wave, seconds, null);
        }
    }

    private Phase phase = Phase.IDLE;
    private ArenaRun table;
    private int wave;
    private int ticksLeft;
    private int phaseTicks;
    private int mobsTotal;
    private int mobsLeft;
    private boolean resetDone;
    private Why why;
    /** Ticks since the first wave began (0 before it). */
    private long elapsed;
    private long waveStartedAt;
    private int clearedWave;
    private long clearedAt;
    private boolean won;
    /** The boss of this wave, while it is alive: its name and how much of its health is left. */
    private String bossName;
    private float bossHealth;

    public Phase phase() {
        return phase;
    }

    /** The wave now being fought or next to come (1-based; 0 before the countdown ends). */
    public int wave() {
        return wave;
    }

    public ArenaRun table() {
        return table;
    }

    /** How many waves this battle has (0: Endless, or no battle). */
    public int of() {
        return table == null ? 0 : table.count();
    }

    /** Ticks since the first wave began. */
    public long elapsed() {
        return elapsed;
    }

    /**
     * How far the battle has got, for a fighter whose battle ends now (knocked out of his last life, logged out, or
     * the battle over): the wave being fought if it is not cleared (and the ticks when it began), else the last wave
     * cleared (and the ticks when it was); won when every wave of a battle with a number of waves is cleared.
     */
    public ArenaChampions.Reach reach() {
        if (wave > clearedWave) {
            return new ArenaChampions.Reach(wave, false, false, waveStartedAt);
        }
        return new ArenaChampions.Reach(clearedWave, true, won, clearedAt);
    }

    /**
     * What the boss of this wave is doing, told once a tick by the world: its name and the part of its health left,
     * or a null name when there is no boss (or it is beaten). The boss bar then shows the boss.
     */
    public void boss(String name, float health) {
        bossName = name;
        bossHealth = health;
    }

    public Why why() {
        return why;
    }

    /** True while a battle is on or being cleared away (anything but IDLE). */
    public boolean busy() {
        return phase != Phase.IDLE;
    }

    /** True while fighting is on: the countdown, a wave, a breather. */
    public boolean fighting() {
        return phase == Phase.COUNTDOWN || phase == Phase.WAVE || phase == Phase.BREATHER;
    }

    /** True during a break between two waves (the breather), when the fighters may say they are ready. */
    public boolean inBreak() {
        return phase == Phase.BREATHER;
    }

    /**
     * Every fighter is ready: the break ends in {@link #READY_TICKS}, never later than it would have. True when that
     * made it shorter; false outside a break, or when it was ending sooner anyway.
     */
    public boolean ready() {
        if (phase != Phase.BREATHER || ticksLeft <= READY_TICKS) {
            return false;
        }
        ticksLeft = READY_TICKS;
        return true;
    }

    /** Whether every fighter still in is ready: there is at least one, and each of them said so. Pure. */
    public static <T> boolean allReady(java.util.Collection<T> fighters, java.util.Collection<T> ready) {
        return !fighters.isEmpty() && ready.containsAll(fighters);
    }

    /** Starts a battle with this table; false (nothing changes) while one is already on. */
    public boolean start(ArenaWaves waves) {
        return waves != null && start(ArenaRun.exactly(waves));
    }

    /** Starts a battle of these waves; false (nothing changes) while one is already on. */
    public boolean start(ArenaRun waves) {
        if (phase != Phase.IDLE || waves == null) {
            return false;
        }
        table = waves;
        wave = 0;
        why = null;
        mobsTotal = 0;
        mobsLeft = 0;
        elapsed = 0;
        waveStartedAt = 0;
        clearedWave = 0;
        clearedAt = 0;
        won = false;
        bossName = null;
        enter(Phase.COUNTDOWN, waves.countdownSeconds() * TICKS_PER_SECOND);
        return true;
    }

    /** The server stopped during a battle: it is over; the arena is reset once things have loaded. */
    public void endedByRestart() {
        table = null;
        wave = 0;
        why = null;
        bossName = null;
        resetDone = false;
        enter(Phase.RESETTING, RESTART_WAIT_TICKS);
    }

    /** The wave just spawned this many mobs (what the boss bar counts down from). */
    public void spawned(int total) {
        mobsTotal = total;
        mobsLeft = total;
    }

    /**
     * One tick. {@code mobsLeft}: how many of the current wave's mobs are still alive in the arena (asked only during a
     * wave). {@code fightersIn}: during the countdown, how many players are in the place; after it, how many of the
     * battle's fighters are still in it.
     */
    public Event tick(int mobsLeft, int fightersIn) {
        if (wave > 0 && (phase == Phase.WAVE || phase == Phase.BREATHER)) {
            elapsed++;
        }
        switch (phase) {
            case IDLE:
                return null;
            case COUNTDOWN:
            case BREATHER: {
                if (fightersIn <= 0) {
                    return lose(Why.NOBODY_LEFT);
                }
                ticksLeft--;
                if (ticksLeft <= 0) {
                    wave++;
                    waveStartedAt = elapsed;
                    bossName = null;
                    enter(Phase.WAVE, table.waveSeconds(wave) * TICKS_PER_SECOND);
                    mobsTotal = 0;
                    this.mobsLeft = 0;
                    return Event.of(Kind.WAVE_START, wave, 0);
                }
                if (ticksLeft % TICKS_PER_SECOND == 0) {
                    return Event.of(Kind.COUNTDOWN, wave + 1, ticksLeft / TICKS_PER_SECOND);
                }
                return null;
            }
            case WAVE: {
                this.mobsLeft = Math.max(0, mobsLeft);
                if (fightersIn <= 0) {
                    return lose(Why.NOBODY_LEFT);
                }
                if (mobsLeft <= 0) {
                    clearedWave = wave;
                    clearedAt = elapsed;
                    bossName = null;
                    if (table.isLast(wave)) {
                        won = true;
                        enter(Phase.WON, END_HOLD_TICKS);
                        return Event.of(Kind.WON, wave, 0);
                    }
                    int pause = table.breatherAfter(wave);
                    enter(Phase.BREATHER, pause * TICKS_PER_SECOND);
                    return Event.of(Kind.WAVE_CLEARED, wave, pause);
                }
                ticksLeft--;
                if (ticksLeft <= 0) {
                    return lose(Why.TIME_UP);
                }
                return null;
            }
            case WON:
            case LOST: {
                ticksLeft--;
                if (ticksLeft <= 0) {
                    resetDone = false;
                    enter(Phase.RESETTING, 0);
                }
                return null;
            }
            case RESETTING: {
                if (resetDone) {
                    phase = Phase.IDLE;
                    table = null;
                    return Event.of(Kind.READY, 0, 0);
                }
                if (ticksLeft > 0) {
                    ticksLeft--;
                    return null;
                }
                resetDone = true;
                return Event.of(Kind.RESET, wave, 0);
            }
            default:
                return null;
        }
    }

    private Event lose(Why w) {
        why = w;
        bossName = null;
        enter(Phase.LOST, END_HOLD_TICKS);
        return new Event(Kind.LOST, wave, 0, w);
    }

    private void enter(Phase p, int ticks) {
        phase = p;
        ticksLeft = ticks;
        phaseTicks = ticks;
    }

    /**
     * What the boss bar shows now: its words, how full it is, and whether it is a boss's health (then it is the only
     * thing it shows, in its own colour); null when there is no battle to show.
     */
    public record Bar(String name, float progress, boolean boss) {
        public Bar(String name, float progress) {
            this(name, progress, false);
        }
    }

    public Bar bar() {
        int of = of();
        return switch (phase) {
            case COUNTDOWN, BREATHER -> new Bar(table != null && table.isBossWave(wave + 1)
                    ? ArenaWords.barNextBoss(wave + 1, of, secondsLeft())
                    : ArenaWords.barNext(wave + 1, of, secondsLeft()),
                    phaseTicks <= 0 ? 0f : (float) ticksLeft / phaseTicks);
            case WAVE -> bossName != null
                    ? new Bar(ArenaWords.barBoss(bossName, wave, of), Math.max(0f, Math.min(1f, bossHealth)), true)
                    : new Bar(ArenaWords.barWave(wave, of, mobsLeft), mobsTotal <= 0 ? 0f : (float) mobsLeft / mobsTotal);
            case WON -> new Bar(ArenaWords.barWon(), 1f);
            case LOST -> new Bar(ArenaWords.barLost(), 0f);
            default -> null;
        };
    }

    /** Whole seconds left in this phase, rounded up. */
    public int secondsLeft() {
        return (ticksLeft + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }
}
