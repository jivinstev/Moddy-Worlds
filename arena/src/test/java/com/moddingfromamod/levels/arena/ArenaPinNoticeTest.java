package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.moddingfromamod.levels.arena.ArenaLadder.Count;
import com.moddingfromamod.levels.arena.ArenaLadder.Opens;
import com.moddingfromamod.levels.arena.ArenaLadder.Step;
import com.moddingfromamod.levels.arena.ArenaPinNotice.Held;
import com.moddingfromamod.levels.arena.ArenaPinNotice.Notice;
import com.moddingfromamod.levels.arena.ArenaPinNotice.Type;
import com.moddingfromamod.wishbuilder.MakeFacts;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A wish put on the hotbar that the battle's first step would lock or hold back: what he is told, and when. */
class ArenaPinNoticeTest {

    private static final ArenaLadder L = ArenaLadder.DEFAULT;

    private static MakeFacts facts(String kind, Map<String, Double> numbers, Set<String> tags) {
        return new MakeFacts(kind, numbers, tags, true);
    }

    private static String say(String name, Notice n) {
        return ArenaWords.pinNotice(name, n, Count.WAVES);
    }

    @Test
    void aMeteorIsLockedUntilWaveFive() {
        Notice n = ArenaPinNotice.of(L, 0, facts("event", Map.of(MakeFacts.RADIUS_BLOCKS, 3.0), Set.of("fights")));
        assertEquals(new Notice(Type.LOCKED, 5, List.of()), n, "the lock first, whatever its numbers");
        assertEquals("Your Meteor is locked in a battle until wave 5 is cleared.", say("Meteor", n));
        assertEquals(Type.LOCKED, ArenaPinNotice.of(L, 4, facts("event", Map.of(), Set.of())).type(),
                "put on his hotbar in a break after wave 4: still locked");
        assertEquals(Type.NONE, ArenaPinNotice.of(L, 5, facts("event", Map.of(), Set.of())).type(), "and open after 5");
    }

    @Test
    void aWorldRuleWaitsForWaveTenByItsTag() {
        Notice n = ArenaPinNotice.of(L, 0, facts("rule", Map.of(), Set.of(MakeFacts.WORLD_RULE)));
        assertEquals(10, n.after());
        assertEquals("Your Rainy Day is locked in a battle until wave 10 is cleared.", say("Rainy Day", n));
    }

    @Test
    void aDragonTooBigAndTooStrongIsSmallerAndWeakerAtFirst() {
        Notice n = ArenaPinNotice.of(L, 0, facts("creature", Map.of(MakeFacts.SCALE_TIMES, 4.0,
                MakeFacts.HEALTH_HALF_HEARTS, 200.0, MakeFacts.COUNT_MADE, 1.0), Set.of("fights")));
        assertEquals(new Notice(Type.FITTED, 0, List.of(Held.SMALLER, Held.WEAKER), ArenaPinNotice.Later.ALL), n);
        assertEquals("Your Dragon will be smaller and weaker at first. It grows as you clear waves.", say("Dragon", n),
                "its size and strength both come back as steps open (core 1.0.11 fits size on the living creature)");
    }

    @Test
    void aGolemHeldOnlyByHealthGetsStronger() {
        Notice n = ArenaPinNotice.of(L, 0, facts("creature", Map.of(MakeFacts.HEALTH_HALF_HEARTS, 100.0),
                Set.of()));
        assertEquals("Your Rock Golem will be weaker at first. It gets stronger as you clear waves.",
                say("Rock Golem", n));
        assertEquals(Type.FITTED, ArenaPinNotice.of(L, 4, facts("creature", Map.of(MakeFacts.HEALTH_HALF_HEARTS,
                100.0), Set.of())).type(), "after wave 4 its 80 still holds it");
        assertEquals(Type.NONE, ArenaPinNotice.of(L, 5, facts("creature", Map.of(MakeFacts.HEALTH_HALF_HEARTS,
                100.0), Set.of())).type(), "after wave 5 it comes out whole");
    }

    @Test
    void aWishWithinTheStepSaysNothing() {
        assertEquals(Notice.NONE, ArenaPinNotice.of(L, 0, facts("creature", Map.of(MakeFacts.SCALE_TIMES, 1.5,
                MakeFacts.HEALTH_HALF_HEARTS, 40.0), Set.of())), "exactly at the limit: as it is");
        assertEquals(Notice.NONE, ArenaPinNotice.of(L, 0, facts("building", Map.of(), Set.of("builds"))),
                "no numbers the step names: as it is");
        assertNull(say("Cozy House", Notice.NONE));
        assertEquals(Notice.NONE, ArenaPinNotice.of(L, 0, null), "a recipe the core could not read: nothing");
        assertEquals(Notice.NONE, ArenaPinNotice.of(null, 0, facts("event", Map.of(), Set.of())));
    }

    @Test
    void cautiousFactsAreJudgedByTheirKindAlone() {
        MakeFacts cautious = new MakeFacts("creature", Map.of(), Set.copyOf(MakeFacts.TAGS), false);
        assertEquals(Notice.NONE, ArenaPinNotice.of(L, 0, cautious), "every tag, but not read: not locked by a tag");
        MakeFacts cautiousEvent = new MakeFacts("event", Map.of(), Set.copyOf(MakeFacts.TAGS), false);
        assertEquals(5, ArenaPinNotice.of(L, 0, cautiousEvent).after());
    }

    @Test
    void aNumberTheCoreCannotLowerIsTooMuchUntilAStepAllowsIt() {
        ArenaLadder l = new ArenaLadder(Count.WAVES, List.of(
                new Step(0, Map.of(MakeFacts.EXTENT_BLOCKS, 10.0, MakeFacts.SCALE_TIMES, 1.0), Opens.NONE),
                new Step(3, Map.of(MakeFacts.EXTENT_BLOCKS, 20.0), Opens.NONE),
                new Step(6, Map.of(MakeFacts.EXTENT_BLOCKS, 40.0), Opens.NONE)), Opens.NONE);
        Notice n = ArenaPinNotice.of(l, 0, facts("building", Map.of(MakeFacts.EXTENT_BLOCKS, 30.0,
                MakeFacts.SCALE_TIMES, 2.0), Set.of()));
        assertEquals(new Notice(Type.TOO_MUCH, 6, List.of()), n, "locked rather than fitted, so said first");
        assertEquals("Your Castle is too much for now. It opens when wave 6 is cleared.", say("Castle", n));
        Notice never = ArenaPinNotice.of(l, 0, facts("building", Map.of(MakeFacts.EXTENT_BLOCKS, 90.0), Set.of()));
        assertEquals(new Notice(Type.TOO_MUCH, 0, List.of()), never);
        assertEquals("Your Castle is too much for this arena right now.", say("Castle", never));
    }

    @Test
    void aLadderCountedInBossesSaysBosses() {
        ArenaLadder l = new ArenaLadder(Count.BOSSES, List.of(
                new Step(0, Map.of(MakeFacts.HEALTH_HALF_HEARTS, 40.0), Opens.NONE),
                new Step(1, Map.of(), new Opens(Set.of("event"), Set.of()))), new Opens(Set.of("event"), Set.of()));
        assertEquals("Your Meteor is locked in a battle until boss 1 is beaten.",
                ArenaWords.pinNotice("Meteor", ArenaPinNotice.of(l, 0, facts("event", Map.of(), Set.of())),
                        Count.BOSSES));
        assertEquals("Your Golem will be weaker at first. It gets stronger as you beat bosses.",
                ArenaWords.pinNotice("Golem", ArenaPinNotice.of(l, 0, facts("creature",
                        Map.of(MakeFacts.HEALTH_HALF_HEARTS, 90.0), Set.of())), Count.BOSSES));
    }

    @Test
    void manyHoldsReadAsOneList() {
        Notice n = new Notice(Type.FITTED, 0, List.of(Held.WEAKER, Held.FEWER, Held.GENTLER), ArenaPinNotice.Later.SOME);
        assertEquals("Your Wolf Pack will be weaker, fewer and gentler in this arena. It gets stronger as you clear"
                + " waves.", say("Wolf Pack", n));
        assertEquals("This wish will be weaker, fewer and gentler in this arena. It gets stronger as you clear waves.",
                say("", n), "a wish with no name");
    }

    @Test
    void theCoreProxysNameLosesItsPrefix() {
        assertEquals("Dragon", ArenaPinNotice.nameOf("✨ Wish: Dragon"));
        assertEquals("Meteor", ArenaPinNotice.nameOf("Meteor"));
        assertEquals("", ArenaPinNotice.nameOf("✨ Wish: "));
        assertEquals("", ArenaPinNotice.nameOf(null));
        assertEquals("Buddy the Golem", ArenaPinNotice.nameOf("\u2728 Wish: Buddy the Golem"));
        assertEquals("", ArenaPinNotice.nameOf("\u2728 Wish: A meteor that makes a crater!"),
                "a ready-made's label is a sentence, not a name: the line says This wish");
        assertEquals("", ArenaPinNotice.nameOf("Wish: A very long name for a thing"));
    }

    @Test
    void onlyWhatComesBackIsPromised() {
        Notice sword = ArenaPinNotice.of(L, 0, facts("item", Map.of(MakeFacts.ATTACK_DAMAGE_HALF_HEARTS, 40.0),
                Set.of("fights")));
        assertEquals(Type.FITTED, sword.type());
        assertEquals(ArenaPinNotice.Later.NONE, sword.later(), "nothing re-fits an item as steps open");
        assertEquals("Your Fire Sword will be weaker in this arena.", say("Fire Sword", sword));
        Notice small = ArenaPinNotice.of(L, 0, facts("creature", Map.of(MakeFacts.SCALE_TIMES, 4.0), Set.of()));
        assertEquals("Your Dragon will be smaller at first. It grows as you clear waves.", say("Dragon", small),
                "a creature held only in size grows back as steps open");
        Notice strong = ArenaPinNotice.of(L, 0, facts("item", Map.of(MakeFacts.BUILDER_STRENGTH_STEP, 3.0), Set.of()));
        assertEquals(Type.TOO_MUCH, strong.type(), "nothing lowers the strength pick at a fire: too much until a step allows it");
        assertEquals(Type.NONE, ArenaPinNotice.of(L, 0, facts("creature", Map.of(MakeFacts.BUILDER_STRENGTH_STEP, 2.0),
                Set.of())).type(), "a Builder creature's own How strong (2) is never held back by the default ladder");
        Notice ride = ArenaPinNotice.of(L, 0, facts("ride", Map.of(MakeFacts.HEALTH_HALF_HEARTS, 100.0), Set.of()));
        assertEquals(ArenaPinNotice.Later.ALL, ride.later(), "a ride's strength comes back like a creature's");
        for (Notice n : List.of(sword)) {
            assertFalse(say("X", n).contains("stronger") || say("X", n).contains("grows") || say("X", n).contains("at first"),
                    say("X", n));
        }
    }
}
