package com.moddingfromamod.levels.arena;

/**
 * <b>One battle in the arena, as a state machine</b>: IDLE → COUNTDOWN → WAVE 1 → BREATHER → WAVE 2 → … → WON or LOST →
 * RESETTING → IDLE. Pure: no Minecraft here; {@link ArenaModule} feeds it, once a tick, how many of the wave's mobs are
 * left and how many fighters are still in, and carries out the {@link Event} it answers with (spawn a wave, say a line,
 * reset the arena).
 *
 * <p>The rules, all here:
 * <ul>
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
    private ArenaWaves table;
    private int wave;
    private int ticksLeft;
    private int phaseTicks;
    private int mobsTotal;
    private int mobsLeft;
    private boolean resetDone;
    private Why why;

    public Phase phase() {
        return phase;
    }

    /** The wave now being fought or next to come (1-based; 0 before the countdown ends). */
    public int wave() {
        return wave;
    }

    public ArenaWaves table() {
        return table;
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

    /** Starts a battle with this table; false (nothing changes) while one is already on. */
    public boolean start(ArenaWaves waves) {
        if (phase != Phase.IDLE || waves == null) {
            return false;
        }
        table = waves;
        wave = 0;
        why = null;
        mobsTotal = 0;
        mobsLeft = 0;
        enter(Phase.COUNTDOWN, waves.countdownSeconds() * TICKS_PER_SECOND);
        return true;
    }

    /** The server stopped during a battle: it is over; the arena is reset once things have loaded. */
    public void endedByRestart() {
        table = null;
        wave = 0;
        why = null;
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
                    enter(Phase.WAVE, table.waveSeconds() * TICKS_PER_SECOND);
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
                    if (wave >= table.count()) {
                        enter(Phase.WON, END_HOLD_TICKS);
                        return Event.of(Kind.WON, wave, 0);
                    }
                    enter(Phase.BREATHER, table.breatherSeconds() * TICKS_PER_SECOND);
                    return Event.of(Kind.WAVE_CLEARED, wave, table.breatherSeconds());
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
        enter(Phase.LOST, END_HOLD_TICKS);
        return new Event(Kind.LOST, wave, 0, w);
    }

    private void enter(Phase p, int ticks) {
        phase = p;
        ticksLeft = ticks;
        phaseTicks = ticks;
    }

    /** What the boss bar shows now: its words and how full it is; null when there is no battle to show. */
    public record Bar(String name, float progress) {
    }

    public Bar bar() {
        int of = table == null ? 0 : table.count();
        return switch (phase) {
            case COUNTDOWN, BREATHER -> new Bar(ArenaWords.barNext(wave + 1, of, secondsLeft()),
                    phaseTicks <= 0 ? 0f : (float) ticksLeft / phaseTicks);
            case WAVE -> new Bar(ArenaWords.barWave(wave, of, mobsLeft),
                    mobsTotal <= 0 ? 0f : (float) mobsLeft / mobsTotal);
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
