package com.moddingfromamod.levels.arena;

/**
 * <b>What the arena does with each thing in it</b> when it tidies: between two waves (the mend) and when a battle is
 * over (the reset). The owner's rule for a player's things: <b>nothing of his is ever taken away</b>. What he dropped
 * when he was knocked out, what he threw, and an arrow or trident he can pick up again stay where they are between
 * waves, so he can go and get them, and are moved to the prep room when the battle is over (an arrow or trident as the
 * item it is picked up as), never removed. Pure: no Minecraft; {@link ArenaBattle} says what each entity is.
 */
final class ArenaTidy {

    private ArenaTidy() {
    }

    /** What a thing in the arena is, as far as tidying cares. */
    enum Thing {
        /** a player, a mount carrying one, or a board's words: never touched */
        UNTOUCHED,
        /** a dropped item that is a player's: dropped when he was knocked out (or died), or thrown by him */
        PLAYERS_ITEM,
        /** an arrow or trident a player shot or threw that he may pick up again */
        PLAYERS_PICKUP,
        /** a thing a fight leaves flying or falling: other projectiles, lit TNT, a falling block, a cloud, fangs */
        LOOSE,
        /** any other dropped item (a monster's drop), or experience */
        DROP,
        /** a monster of a wave */
        WAVE_MOB,
        /** a creature a player's wish made (also a tamed one) */
        WISHED,
        /** a pet someone owns that no wish made */
        PET,
        /** anything else in the arena (what a wish put there, a stray mob) */
        OTHER
    }

    /** What is done with it. */
    enum Act { KEEP, REMOVE, TO_PREP_ROOM }

    /**
     * Between two waves: only the loose things a fight leaves flying are tidied. Living creatures fight on, and every
     * dropped thing stays for a fighter to pick up in the break (a player's own for good; the rest until the battle's end).
     */
    static Act betweenWaves(Thing t) {
        return t == Thing.LOOSE ? Act.REMOVE : Act.KEEP;
    }

    /**
     * When the battle is over: a player's own things go to the prep room; a pet someone owns and the untouched stay;
     * everything else (monsters, their drops, experience, wished creatures, loose things, what a wish put there) goes.
     */
    static Act atBattleEnd(Thing t) {
        return switch (t) {
            case PLAYERS_ITEM, PLAYERS_PICKUP -> Act.TO_PREP_ROOM;
            case UNTOUCHED, PET -> Act.KEEP;
            default -> Act.REMOVE;
        };
    }
}
