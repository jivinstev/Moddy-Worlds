package com.moddingfromamod.levels.arena;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moddingfromamod.levels.arena.ArenaRoles.Facts;
import com.moddingfromamod.levels.arena.ArenaRoles.Role;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Every wished creature takes a battle role, chosen from what the game says about it, and never none. */
class ArenaRolesTest {

    private static final ArenaCreatures N = ArenaCreatures.DEFAULT;
    private static final List<String> ARENAS_OWN = List.of("moddyarena_wave", "moddyarena_panel",
            "moddyarena_champions", ArenaRoleKeeper.SIGN_TAG);

    @Test
    void aCreatureThatHitsAsHardAsAZombieFights() {
        assertEquals(Role.FIGHTER, ArenaRoles.choose(new Facts(3, 20, 0.6, false, true), N), "a cat or a zombie's punch");
        assertEquals(Role.FIGHTER, ArenaRoles.choose(new Facts(15, 100, 1.4, false, true), N),
                "big and tough, but it fights: a fighter first");
        assertEquals(Role.FIGHTER, ArenaRoles.choose(new Facts(1, 20, 0.6, true, true), N),
                "a monster a wish summoned fights, however soft its punch");
        assertEquals(Role.HEALER, ArenaRoles.choose(new Facts(0, 20, 0.6, true, true), N),
                "but a monster with no attack at all is no Fighter: it heals");
        assertEquals(Role.TANK, ArenaRoles.choose(new Facts(0, 80, 0.6, true, true), N),
                "or, when tough, it is a Tank");
        assertEquals(Role.FIGHTER, ArenaRoles.choose(new Facts(3, 20, 0.6, false, false), N),
                "it fights where it stands when it cannot walk");
    }

    @Test
    void aBigOrToughPeacefulCreatureIsATank() {
        assertEquals(Role.TANK, ArenaRoles.choose(new Facts(0, 40, 0.6, false, true), N), "tough");
        assertEquals(Role.TANK, ArenaRoles.choose(new Facts(2, 20, 1.3, false, true), N), "big, and soft-hitting");
        assertEquals(Role.TANK, ArenaRoles.choose(new Facts(0, 60, 2.0, false, false), N), "big and held still");
    }

    @Test
    void anyOtherCreatureHealsOrTanksWhereItStands() {
        assertEquals(Role.HEALER, ArenaRoles.choose(new Facts(0, 20, 0.65, false, true), N), "a gentle cat");
        assertEquals(Role.HEALER, ArenaRoles.choose(new Facts(2.9, 39.9, 1.29, false, true), N), "just under each line");
        assertEquals(Role.TANK, ArenaRoles.choose(new Facts(0, 8, 0.4, false, false), N),
                "it cannot follow anyone to heal him, so it draws the monsters where it stands");
    }

    @Test
    void theLinesMoveWithTheRules() {
        ArenaCreatures soft = new ArenaCreatures(1.0, 100, 3.0, 2, 4, 6, 8, 1);
        assertEquals(Role.FIGHTER, ArenaRoles.choose(new Facts(2, 20, 0.6, false, true), soft));
        assertEquals(Role.HEALER, ArenaRoles.choose(new Facts(0, 60, 2.0, false, true), soft));
    }

    /** The guarantee: whatever the facts and whatever the rules, a creature has a role. */
    @Test
    void neverNoRole() {
        Random r = new Random(7);
        Set<Role> got = EnumSet.noneOf(Role.class);
        for (int i = 0; i < 20_000; i++) {
            Facts f = new Facts(pick(r, 0, 60), pick(r, 0.5, 1200), pick(r, 0, 6), r.nextInt(8) == 0, r.nextBoolean());
            ArenaCreatures n = new ArenaCreatures(0.5 + r.nextDouble() * 49.5, 10 + r.nextDouble() * 990,
                    0.5 + r.nextDouble() * 4.5, 0.5 + r.nextDouble() * 1.5, 2 + r.nextInt(19), 2 + r.nextDouble() * 14,
                    3 + r.nextDouble() * 13, r.nextInt(4));
            Role role = ArenaRoles.choose(f, n);
            assertNotNull(role, f + " " + n);
            got.add(role);
            if (role == Role.HEALER) {
                assertTrue(f.canWalk(), "a healer follows its player, so it walks: " + f);
            }
            if (!f.canWalk() && role != Role.FIGHTER) {
                assertEquals(Role.TANK, role, "a creature that cannot walk and does not fight holds the monsters: " + f);
            }
        }
        assertEquals(EnumSet.allOf(Role.class), got, "every role is reached");
    }

    /** Extreme values (none, infinite, not a number) still give a role. */
    @Test
    void oddFactsStillGiveARole() {
        double[] odd = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE};
        for (double a : odd) {
            for (double h : odd) {
                for (double w : odd) {
                    assertNotNull(ArenaRoles.choose(new Facts(a, h, w, false, true), N));
                    assertNotNull(ArenaRoles.choose(new Facts(a, h, w, false, false), N));
                }
            }
        }
    }

    private static double pick(Random r, double lo, double hi) {
        return lo + r.nextDouble() * (hi - lo);
    }

    @Test
    void onlyAWishedCreatureGetsARole() {
        assertTrue(wished(Set.of("wishmob", "wishmob_buddy_the_cat")), "a wished creature");
        assertTrue(wished(Set.of("moddy_wished")), "a mob a wish summoned");
        assertFalse(wished(Set.of()), "a mob that wandered in, or a /summon: no tags");
        assertFalse(wished(Set.of("his_own_tag")));
        assertFalse(ArenaRoles.wished(false, null, ARENAS_OWN));
        assertTrue(ArenaRoles.wished(true, null, ARENAS_OWN), "the core says a wish made it, with no tags read");
        assertFalse(wished(Set.of("wishmob", "moddyarena_wave")), "never a wave's monster");
        assertFalse(wished(Set.of("moddy_wished", ArenaRoleKeeper.SIGN_TAG)), "never a sign");
    }

    /** The arena's decision on a body's tags, with the core's own documented reader deciding "a wish made it". */
    private static boolean wished(Set<String> tags) {
        return ArenaRoles.wished(com.moddingfromamod.world.Wished.byTags(tags), tags, ARENAS_OWN);
    }

    @Test
    void itHelpsItsOwnPlayerElseTheNearest() {
        UUID a = new UUID(0, 1);
        UUID b = new UUID(0, 2);
        UUID c = new UUID(0, 3);
        List<ArenaRoles.Spot> in = List.of(new ArenaRoles.Spot(a, 10, 64, 0), new ArenaRoles.Spot(b, 2, 64, 0));
        assertEquals(b, ArenaRoles.owner(0, 64, 0, null, null, in), "the nearest");
        assertEquals(a, ArenaRoles.owner(0, 64, 0, a, null, in), "the one it already helps, while he is in");
        assertEquals(a, ArenaRoles.owner(0, 64, 0, null, a, in), "the player who tamed it");
        assertEquals(b, ArenaRoles.owner(0, 64, 0, c, c, in), "its own player is not in: the nearest");
        assertNull(ArenaRoles.owner(0, 64, 0, a, a, List.of()), "nobody in");
        List<ArenaRoles.Spot> tie = List.of(new ArenaRoles.Spot(b, 2, 64, 0), new ArenaRoles.Spot(a, -2, 64, 0));
        assertEquals(a, ArenaRoles.owner(0, 64, 0, null, null, tie), "two as near: the lower id");
        assertEquals(a, ArenaRoles.owner(0, 64, 0, null, null, List.of(tie.get(1), tie.get(0))), "whatever the order");
    }

    @Test
    void aHealGivesWhatIsMissingAndNothingAtFullHealth() {
        assertEquals(2f, ArenaRoles.heal(10, 20, 2));
        assertEquals(0.5f, ArenaRoles.heal(19.5f, 20, 2), "no more than he is missing");
        assertEquals(0f, ArenaRoles.heal(20, 20, 2), "full: no heal, no hearts");
        assertEquals(0f, ArenaRoles.heal(0, 20, 2), "knocked out: no heal");
    }

    @Test
    void oneHealAtATimeWhateverTheHealers() {
        int every = N.healTicks();
        assertEquals(80, every, "every 4 seconds");
        assertTrue(ArenaRoles.mayHeal(-1, 0, every));
        assertFalse(ArenaRoles.mayHeal(100, 100 + every - 1, every), "a second healer does not heal him again");
        assertTrue(ArenaRoles.mayHeal(100, 100 + every, every));
    }

    @Test
    void theReachOfAHealAndATaunt() {
        assertTrue(ArenaRoles.inHealReach(6 * 6, N));
        assertFalse(ArenaRoles.inHealReach(6.1 * 6.1, N));
        assertTrue(ArenaRoles.taunts(8 * 8, N));
        assertFalse(ArenaRoles.taunts(8.1 * 8.1, N));
        assertFalse(ArenaRoles.letsGo(11 * 11, N), "a held monster is kept a little past the taunt's reach");
        assertTrue(ArenaRoles.letsGo(12.1 * 12.1, N), "and let go when it gets well away");
        assertTrue(ArenaRoles.walkBack(3.1 * 3.1));
        assertFalse(ArenaRoles.walkBack(2.9 * 2.9));
        assertEquals(0, ArenaRoles.resistanceAmplifier(1), "Resistance I");
        assertEquals(-1, ArenaRoles.resistanceAmplifier(0), "none");
    }

    @Test
    void aNameAChatLineCanCarry() {
        assertEquals("Buddy the Cat", ArenaRoles.plainName("Buddy the Cat"));
        assertEquals("SPIKOR King", ArenaRoles.plainName("§6SPIKOR§r  King ✨"));
        assertEquals("creature", ArenaRoles.plainName(""));
        assertEquals("creature", ArenaRoles.plainName("✨✨"));
        assertEquals("creature", ArenaRoles.plainName(null));
        assertTrue(ArenaRoles.plainName("x".repeat(80)).length() <= 32);
    }

    @Test
    void theRoleTagsAreTheArenas() {
        assertEquals("moddyarena_healer", ArenaRoles.roleTag(Role.HEALER));
        for (Role r : Role.values()) {
            assertTrue(ArenaRoles.roleTag(r).startsWith(ArenaMod.MODID + "_"));
            assertTrue(r.word().chars().allMatch(c -> c >= 32 && c < 127));
            assertTrue(ArenaRoleKeeper.signNbt(r, "t").contains(r.word()));
        }
        assertTrue(Role.TANK.draws() && !Role.HEALER.draws() && !Role.FIGHTER.draws());
    }

    /** What a role line says, one for each role, naming the creature. */
    @Test
    void theRoleLines() {
        assertEquals("Your Buddy the Cat is a Healer! It will keep you healthy.",
                ArenaWords.roleTaken(Role.HEALER, "Buddy the Cat"));
        for (Role r : Role.values()) {
            String l = ArenaWords.roleTaken(r, "Rex");
            assertTrue(l.startsWith("Your Rex is a " + r.word() + "!"), l);
        }
    }

    /** A role's sign: its symbol and word, over the creature's name plate, and lifted clear of a sign close by. */
    @Test
    void aSignIsReadableAndClearOfTheNamePlateAndOtherSigns() {
        org.junit.jupiter.api.Assertions.assertEquals("\u2694 Fighter", ArenaRoles.signText(ArenaRoles.Role.FIGHTER));
        for (ArenaRoles.Role r : ArenaRoles.Role.values()) {
            org.junit.jupiter.api.Assertions.assertEquals(1, r.symbol().length(), "one glyph, in the basic plane");
        }
        double plain = ArenaRoles.signY(64, 2.0, false);
        double named = ArenaRoles.signY(64, 2.0, true);
        org.junit.jupiter.api.Assertions.assertTrue(plain > 66.4, "above the top, clear of where a name plate floats");
        org.junit.jupiter.api.Assertions.assertTrue(named > plain, "higher still over a name plate");
        java.util.List<double[]> placed = new java.util.ArrayList<>();
        placed.add(new double[] {0, 66.6, 0});
        double next = ArenaRoles.signLift(0.5, 0.5, 66.6, placed);
        org.junit.jupiter.api.Assertions.assertTrue(next >= 66.6 + ArenaRoles.SIGN_ROW, "a close sign goes up a row");
        org.junit.jupiter.api.Assertions.assertEquals(66.6, ArenaRoles.signLift(5, 5, 66.6, placed), 1e-9,
                "a far one stays");
        placed.add(new double[] {0.5, next, 0.5});
        org.junit.jupiter.api.Assertions.assertTrue(ArenaRoles.signLift(0, 0.3, 66.6, placed) >= next + ArenaRoles.SIGN_ROW,
                "a third goes up again");
    }

    /** Two signs side by side never cover each other: "close" is at least a sign's width, a row at least its height. */
    @Test
    void signsAreKeptApartByTheirOwnSize() {
        for (ArenaRoles.Role r : ArenaRoles.Role.values()) {
            double wide = ArenaPanel.width(ArenaRoles.signText(r)) * 1.17 * ArenaRoleKeeper.SIGN_SCALE;
            org.junit.jupiter.api.Assertions.assertTrue(ArenaRoles.SIGN_NEAR >= wide, r + " is " + wide + " wide");
        }
        org.junit.jupiter.api.Assertions.assertTrue(ArenaRoles.SIGN_ROW >= ArenaPanel.LINE * ArenaRoleKeeper.SIGN_SCALE * 1.2);
    }
}
