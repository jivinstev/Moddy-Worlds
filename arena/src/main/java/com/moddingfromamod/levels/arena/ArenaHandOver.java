package com.moddingfromamod.levels.arena;

/**
 * <b>Where a finished make goes in the arena</b>: the arena's answer to the core's {@code WorldMakes.landing} hook, and
 * what it does when the core tells it a make was handed over. The owner's rule: <b>a thing you hold is a real item; a
 * thing that happens or appears is a wish on your hotbar.</b>
 * <ul>
 *   <li>A make finished in the <b>prep room</b> (Builder, Apprentice or Magician) does not appear beside him: an item is
 *       handed over as the real item ({@code KEPT_AS_ITEM}); a creature, ride, building or event is kept and handed
 *       over as a hotbar wish ({@code KEPT}), drawn however the core draws a hotbar wish.</li>
 *   <li>A make aimed <b>inside the arena</b> lands as it always did (so he can still wish straight into the fight where
 *       the arena's rules allow a make at all), and so does one made anywhere <b>outside the place</b>.</li>
 *   <li>A thing made again from its own recipe (a hotbar fire, "Make another one") always lands: firing a wish makes
 *       the thing, never another wish.</li>
 * </ul>
 * The core's own "Add to hotbar" is never asked here and is unchanged. Nothing is charged when a make is kept: the
 * prep room's makes are free ({@link ArenaWishPolicy}), a Magician wish was counted when it was asked, and each fire of
 * the hotbar wish is one of the wave's wishes, as any hotbar fire is. Pure: no Minecraft. {@link ArenaModule} reads where
 * he stands and where he looks, and shows the hand-over on the {@link ArenaPlace#handOverSpot hand-over spot}.
 */
final class ArenaHandOver {

    private ArenaHandOver() {
    }

    /** Where a make is finished, as far as the hand-over cares. */
    enum Where {
        /** he stands in the prep room and does not aim into the arena */
        PREP_ROOM,
        /** he stands in the arena, or aims into it from the prep room */
        ARENA,
        /** anywhere else */
        OUTSIDE
    }

    /** The arena's answer, by name, as the core's {@code world.Landing} has it. */
    enum Answer {
        /** it appears where it was aimed, as always */
        LANDS,
        /** kept and handed over as a hotbar wish */
        KEPT,
        /** kept; an item is handed over as the real item */
        KEPT_AS_ITEM
    }

    /**
     * Where a make is finished, for a player standing at (x, y, z) who aims at the column (aimX, aimZ); {@code aimed}
     * false when he aims at nothing the arena can read (then where he stands decides). A player in the prep room who
     * aims into the arena (through the glass or the gate) makes it there.
     */
    static Where where(ArenaPlace place, double x, double y, double z, boolean aimed, int aimX, int aimZ) {
        if (place == null || !place.keeps(x, y, z)) {
            return Where.OUTSIDE;
        }
        if (!place.inPrepRoom(x, z)) {
            return Where.ARENA;
        }
        return aimed && place.arenaReach().contains(aimX, aimZ) ? Where.ARENA : Where.PREP_ROOM;
    }

    /**
     * The answer for a new make of {@code kind} (the core's make kind: {@code item}, {@code creature}, {@code ride},
     * {@code building}, {@code event}, ...) finished {@code where}; {@code replay} true for a thing made again from its own
     * recipe. In the prep room an item is kept as the real item and everything else as a hotbar wish; a kind the core
     * cannot keep (a picture, a change) lands anyway, and the core says so ({@link #notKeptKind}).
     */
    static Answer answer(Where where, String kind, boolean replay) {
        if (where != Where.PREP_ROOM || replay) {
            return Answer.LANDS;
        }
        return "item".equals(kind) ? Answer.KEPT_AS_ITEM : Answer.KEPT;
    }

    /** What the core says when a make the arena asked to keep was not kept ({@code NotKept.Why}, by name). */
    enum NotKeptWhy {
        /** no recipe can make it again (a picture, a rule, supplies, a real mob): it landed */
        NO_REPLAY_RECIPE,
        /** a change to a thing he already has: changed where it stands */
        A_CHANGE,
        /** a kept Magician wish taught nothing to put on the hotbar: nothing was made */
        NOTHING_TAUGHT,
        /** a reason this arena does not know yet */
        OTHER
    }

    /** {@link NotKeptWhy} from the core's name for it; a name this arena does not know is {@link NotKeptWhy#OTHER}. */
    static NotKeptWhy notKeptWhy(String name) {
        for (NotKeptWhy w : NotKeptWhy.values()) {
            if (w.name().equals(name)) {
                return w;
            }
        }
        return NotKeptWhy.OTHER;
    }

    /** What a hand-over was, for its line: a hotbar wish in a slot, a wish in the Wish Bag, or the real item. */
    enum Went {
        /** a hotbar wish, in {@code slot} (0 to 8) */
        HOTBAR,
        /** a wish in his Wish Bag: the hotbar was full */
        WISH_BAG,
        /** the real item, in his inventory */
        REAL_ITEM
    }

    /** Where a hand-over went, from the core's {@code HandedOver} (its slot and whether it is the real item). */
    static Went went(int slot, boolean realItem) {
        if (realItem) {
            return Went.REAL_ITEM;
        }
        return slot >= 0 && slot <= 8 ? Went.HOTBAR : Went.WISH_BAG;
    }

    /** How long the finished make shows on the hand-over spot before it flies to him: 1.5 seconds. */
    static final int SHOW_TICKS = 30;
}
