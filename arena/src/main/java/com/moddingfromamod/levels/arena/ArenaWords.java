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
        return "Battles need a difficulty above Peaceful: on Peaceful, monsters vanish as soon as they come.";
    }

    public static String nobody() {
        return "Nobody is in the arena or the prep room to fight.";
    }

    public static String notAnArena() {
        return "This world is not an arena.";
    }

    /** The sign above the start button, one line each (a sign holds four short lines). */
    public static final List<String> SIGN = List.of("Press the", "button to", "start a", "battle!");

    /** Every fixed line, for a test that reads them all. */
    static List<String> all() {
        return List.of(barWave(2, 5, 3), barWave(5, 5, 1), barNext(1, 5, 5), barNext(2, 5, 1), barWon(), barLost(),
                started(5, 5), started(1, 3), comingIn(1, 3), waveTitle(2, 5), waveSubtitle(), cleared(2, 10),
                wonTitle(), wonSubtitle(5), wonSubtitle(1), wonChat(5), wonChat(1), lostTitle(),
                lostSubtitle(ArenaMatch.Why.TIME_UP), lostSubtitle(ArenaMatch.Why.NOBODY_LEFT),
                lostChat(ArenaMatch.Why.TIME_UP, 3), lostChat(ArenaMatch.Why.NOBODY_LEFT, 2), ready(), busy(),
                noWaves(), peaceful(), nobody(), notAnArena(), String.join(" ", SIGN));
    }
}
