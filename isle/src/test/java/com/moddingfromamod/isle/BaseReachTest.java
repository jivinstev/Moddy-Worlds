package com.moddingfromamod.isle;

import com.moddingfromamod.world.Base;
import com.moddingfromamod.world.BaseReach;
import com.moddingfromamod.world.Bases;
import com.moddingfromamod.world.Region;
import com.moddingfromamod.world.WorldEffect;
import com.moddingfromamod.world.WorldRules;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moddingfromamod.marshal.Spot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * the reach rule itself and its per-tool table — asked of {@link Bases} since , with the
 * Isle's islands as the provider. Where it RUNS is {@code IslandReachGameTest},
 * through the real {@code tools/call}.
 */
class BaseReachTest {

    private static final String OW = BaseReach.OVERWORLD;

    /** Two children on neighbouring islands; A stands on his own, B is wherever the test says. */
    private static final class World implements Bases {
        final IslandRegistry reg = new IslandRegistry(IslandGrid.of(1024));
        final UUID a = UUID.randomUUID();
        final UUID b = UUID.randomUUID();
        final Island ia = reg.claim(a, 0).island();
        final Island ib = reg.claim(b, 0).island();
        Bases.Pos aAt = new Bases.Pos(OW, ia.x() + 0.5, 66, ia.z() + 0.5);

        IslandGrid grid() {
            return reg.grid();
        }

        @Override public String noun() {
            return "island";
        }

        @Override public Base baseOf(UUID child) {
            return reg.grid().base(reg.islandOf(child));
        }

        @Override public UUID ownerAt(String dimension, double x, double z) {
            Island i = reg.at(x, z);
            return i == null ? null : i.owner();
        }

        @Override public Bases.Pos positionOf(UUID child) {
            return a.equals(child) ? aAt : null;
        }

        Spot onA() {
            return new Spot(OW, ia.x() + 5, 70, ia.z() - 5);
        }

        Spot onB() {
            return new Spot(OW, ib.x(), 70, ib.z());
        }
    }

    private static JsonObject args(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static JsonObject cmd(String... lines) {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (String l : lines) {
            arr.add(l);
        }
        o.add("command", arr);
        return o;
    }

    @Test
    void aWishAimedAtANeighboursIslandIsRefusedAndHisOwnIsNot() {
        World w = new World();
        String r = BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), w);
        assertNotNull(r, "a castle aimed at B's island must not be built for A");
        assertTrue(r.contains("NOT HIS ISLAND") && r.contains("did not run") && r.contains("conjure_at"),
                "the refusal says what happened and what to do: " + r);
        assertTrue(r.contains("(" + w.ia.x() + ", " + w.ia.y() + ", " + w.ia.z() + ")"),
                "and where his island is: " + r);
        assertNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onA(), w));
        for (String t : List.of("sculpt_creature", "define_creature", "paint_surface")) {
            assertNotNull(BaseReach.refusal(t, new JsonObject(), w.a, w.onB(), w), t);
            assertNull(BaseReach.refusal(t, new JsonObject(), w.a, w.onA(), w), t);
        }
    }

    @Test
    void theSpawnIslandIsNobodysAndTheNetherIsNotAnIsland() {
        World w = new World();
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, new Spot(OW, 0, 64, 0), w),
                "the pack's arrival island at spawn belongs to no child");
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a,
                new Spot("minecraft:the_nether", w.ia.x(), 70, w.ia.z()), w));
    }

    @Test
    void anAnchorInTheMarginIsRefusedSoABuildCannotOverhangTheNeighbour() {
        World w = new World();
        Region cell = w.grid().cell(w.ia.x(), w.ia.z());
        Spot edge = new Spot(OW, cell.maxX() - 10, 70, w.ia.z());
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, edge, w),
                "10 blocks from the neighbour's cell, a 96-wide build would cross into it");
    }

    @Test
    void childRelativeOffsetsCountFromWhereHeStands() {
        World w = new World();
        assertNull(BaseReach.refusal("build_structure", args("{\"offsetForward\":8}"), w.a, null, w));
        assertNotNull(BaseReach.refusal("build_structure", args("{\"offsetForward\":900}"), w.a, null, w),
                "900 blocks in front of him is off his island whatever his facing");
        assertNull(BaseReach.refusal("conjure_at", args("{\"forward\":6,\"up\":2}"), w.a, null, w));
        assertNotNull(BaseReach.refusal("conjure_at", args("{\"forward\":700}"), w.a, null, w));
        assertNull(BaseReach.refusal("modify_mob", args("{\"scope\":\"group\",\"radius\":24}"), w.a, null, w));
        assertNotNull(BaseReach.refusal("modify_mob", args("{\"scope\":\"category\",\"target\":\"cow\"}"),
                w.a, null, w), "every cow, forever, is every island's cow");
    }

    @Test
    void anOffsetNeverMovesAnAimedBuildOffTheAimSoTheAimIsWhatIsChecked() {
        // #1396: with a locked spot, offsetForward is ignored and offsetY lifts it there — so a huge
        // offsetForward is not "in front of him", and a good aim stays good.
        World w = new World();
        Spot aimed = new Spot(OW, w.ia.x(), 70, w.ia.z());
        assertNull(BaseReach.refusal("build_structure", args("{\"offsetForward\":900}"), w.a, aimed, w),
                "it is built at the aimed spot, which is on his island");
        assertNull(BaseReach.refusal("build_structure", args("{\"offsetY\":10}"), w.a, aimed, w));
        w.aAt = new Bases.Pos(OW, w.ib.x(), 66, w.ib.z());
        assertNull(BaseReach.refusal("build_structure", args("{\"offsetForward\":0}"), w.a, aimed, w),
                "where he stands now does not matter once he aimed");
    }

    @Test
    void standingOnTheNeighboursIslandMovesNothingThere() {
        World w = new World();
        w.aAt = new Bases.Pos(OW, w.ib.x(), 66, w.ib.z());
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, null, w),
                "no locked spot: it would go in front of him, on B's island");
        assertNotNull(BaseReach.refusal("modify_mob", args("{\"scope\":\"one\"}"), w.a, null, w));
        assertNotNull(BaseReach.refusal("run_command", cmd("summon cow ~ ~ ~"), w.a, null, w));
        assertNull(BaseReach.refusal("give_items", args("{\"item\":\"minecraft:dirt\"}"), w.a, null, w),
                "his own inventory lands on nobody's island");
    }

    @Test
    void absoluteCoordinatesAreChecked() {
        World w = new World();
        assertNotNull(BaseReach.refusal("paint_surface",
                args("{\"x\":" + w.ib.x() + ",\"y\":70,\"z\":" + w.ib.z() + ",\"facing\":\"north\"}"), w.a, w.onA(), w));
        assertNull(BaseReach.refusal("paint_surface",
                args("{\"x\":" + (w.ia.x() + 3) + ",\"y\":70,\"z\":" + w.ia.z() + ",\"facing\":\"north\"}"),
                w.a, w.onA(), w));
    }

    @Test
    void theBiggestEventSpreadsLessThanTheMarginEveryAnchorKeeps() {
        // stage_event is ANCHORED with no span of its own because this holds: an anchor is kept
        // MARGIN blocks inside his cell, so an event spreading less than that stays on his island.
        // A bigger size would need a span check of its own (build_train had one until #1584).
        for (com.moddingfromamod.event.EventScale s : com.moddingfromamod.event.EventScale.values()) {
            org.junit.jupiter.api.Assertions.assertTrue(s.radius <= IslandGrid.MARGIN,
                    s + " spreads " + s.radius + " blocks, past the " + IslandGrid.MARGIN + "-block margin");
        }
    }

    @Test
    void worldWideToolsAreRefusedOnAnIslandsWorld() {
        World w = new World();
        for (String t : List.of("set_rule", "author_datapack", "change_drops")) {
            String r = BaseReach.refusal(t, new JsonObject(), w.a, w.onA(), w);
            assertNotNull(r, t);
            assertTrue(r.contains("every child's island"), r);
        }
    }

    @Test
    void readingAndHisOwnThingsAreNeverJudgedByPlace() {
        World w = new World();
        for (String t : List.of("look_around", "plan_wish", "photograph", "remove_build",
                "take_me_to", "finish_wish", "give_items", "modify_player")) {
            assertNull(BaseReach.refusal(t, new JsonObject(), w.a, w.onB(), w), t);
        }
    }

    @Test
    void offWorldsAndUnboundCallsAreNeverJudged() {
        World w = new World();
        assertNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), null),
                "no islands here (a plain world): the rule does not exist");
        assertNull(BaseReach.refusal("build_structure", new JsonObject(), null, w.onB(), w),
                "no wish bound: no caster (PlanFirst's rule)");
    }

    @Test
    void aChildWithNoIslandYetCannotPlaceAnything() {
        World w = new World();
        String r = BaseReach.refusal("build_structure", new JsonObject(), UUID.randomUUID(), w.onA(), w);
        assertNotNull(r);
        assertTrue(r.contains("no island yet"), r);
    }

    @Test
    void runCommandReadsTheGrammarsCoordinates() {
        World w = new World();
        int bx = w.ib.x();
        int bz = w.ib.z();
        assertNotNull(BaseReach.refusal("run_command", cmd("setblock " + bx + " 70 " + bz + " stone"), w.a, null, w));
        assertNotNull(BaseReach.refusal("run_command",
                cmd("say hi", "fill ~ ~ ~ ~5 ~5 ~5 stone", "tp @s " + bx + " 70 " + bz), w.a, null, w),
                "one command in a batch off his island refuses the batch");
        assertNull(BaseReach.refusal("run_command", cmd("fill ~ ~ ~ ~5 ~5 ~5 stone", "summon cow ~2 ~ ~",
                "time query daytime".replace("time query daytime", "give @s diamond 3")), w.a, null, w));
        assertNotNull(BaseReach.refusal("run_command", cmd("time set day"), w.a, null, w),
                "the time of day is every island's");
        assertNotNull(BaseReach.refusal("run_command", cmd("kill @e[type=cow]"), w.a, null, w));
        assertNull(BaseReach.refusal("run_command", cmd("kill @e[type=cow,distance=..20]"), w.a, null, w));
        // nobody in the world: commands run at the aimed spot
        w.aAt = null;
        assertNull(BaseReach.refusal("run_command", cmd("summon cow ~ ~ ~"), w.a, w.onA(), w));
        assertNotNull(BaseReach.refusal("run_command", cmd("summon cow ~ ~ ~"), w.a, w.onB(), w));
    }

    @Test
    void everyWorkerToolHasAnIslandDecision() throws Exception {
        // A tool with no entry is refused on an islands world (fail closed) — this keeps that
        // unreachable, the same "a new tool with no decision" gate PlanFirst and UndoContract have.
        List<String> undecided = new ArrayList<>();
        List<String> tools = workerTools();
        for (String t : tools) {
            if (!BaseReach.RULES.containsKey(t)) {
                undecided.add(t);
            }
        }
        assertEquals(List.of(), undecided, "worker tools with no island decision — add each to BaseReach.RULES");
        for (String t : BaseReach.RULES.keySet()) {
            assertTrue(tools.contains(t), "BaseReach.RULES names a tool the worker does not have: " + t);
        }
    }

    @Test
    void everyToolThatNeedsAPlanIsPlacedOrRefusedNeverWaivedAsReading() {
        // A tool PlanFirst says changes the world must not be filed as NONE here.
        for (String t : com.moddingfromamod.marshal.PlanFirst.NEEDS_PLAN) {
            BaseReach.Rule r = BaseReach.RULES.get(t);
            assertNotNull(r, t);
            assertTrue(r.kind() != BaseReach.Kind.NONE, t + " changes the world but is filed as reading");
        }
    }

    // ── the backstop reads the world's rules ────────────────────────────────────────────

    private static WorldRules rules(String rulesJson) {
        WorldRules.Parsed p = WorldRules.parse("{\"store\":\"world-rules\",\"v\":1,\"world\":\"isle\",\"rules\":["
                + rulesJson + "]}", null);
        assertTrue(p.ok(), p.errors().toString());
        return p.rules();
    }

    private static final String REACH = "{\"id\":\"own\",\"type\":\"base_reach\",\"effects\":[\"outside_own_base\","
            + "\"others_bases\",\"other_dimensions\"],\"message\":{\"rule\":\"a\",\"why\":\"b\"}}";

    @Test
    void noRulesFileIsExactlyT172sRulesNeverLooser() {
        World w = new World();
        for (String t : List.of("set_rule", "author_datapack", "change_drops")) {
            assertNotNull(BaseReach.refusal(t, new JsonObject(), w.a, w.onA(), w, null), t);
        }
        assertNotNull(BaseReach.refusal("run_command", cmd("time set day"), w.a, null, w, null));
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), w, null));
    }

    @Test
    void aWorldWideToolOrCommandRunsWhereTheRulesAllowWhatItChanges() {
        World w = new World();
        WorldRules onlyReach = rules(REACH);
        assertNull(BaseReach.refusal("run_command", cmd("time set day"), w.a, null, w, onlyReach),
                "time is not denied here, so the backstop lets it run");
        assertNull(BaseReach.refusal("set_rule", new JsonObject(), w.a, w.onA(), w, onlyReach));
        assertNotNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), w, onlyReach),
                "…and the base reach still holds");
        WorldRules noTime = rules("{\"id\":\"t\",\"type\":\"deny_world_effect\",\"effects\":[\"time\"],"
                + "\"message\":{\"rule\":\"a\",\"why\":\"b\"}}," + REACH);
        String r = BaseReach.refusal("run_command", cmd("weather clear", "time set day"), w.a, null, w, noTime);
        assertNotNull(r);
        assertEquals(WorldEffect.TIME, BaseReach.effectOf("run_command", cmd("time set day"), r),
                "a backstop catch is counted against the rule it broke: " + r);
        WorldRules noReach = rules("{\"id\":\"t\",\"type\":\"deny_world_effect\",\"effects\":[\"time\"],"
                + "\"message\":{\"rule\":\"a\",\"why\":\"b\"}}");
        assertNull(BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), w, noReach),
                "with no base_reach rule, a wish is not fenced to a base");
        assertNotNull(BaseReach.refusal("run_command", cmd("time set day"), w.a, null, w, noReach));
    }

    @Test
    void backstopCatchesMapToARule() {
        World w = new World();
        assertEquals(WorldEffect.WORLDWIDE_DATAPACKS_STANDING_RULES, BaseReach.effectOf("author_datapack",
                new JsonObject(), BaseReach.refusal("author_datapack", new JsonObject(), w.a, w.onA(), w)));
        assertEquals(WorldEffect.OUTSIDE_OWN_BASE, BaseReach.effectOf("build_structure", new JsonObject(),
                BaseReach.refusal("build_structure", new JsonObject(), w.a, w.onB(), w)));
        assertEquals(WorldEffect.OTHER_DIMENSIONS, BaseReach.effectOf("build_structure", new JsonObject(),
                BaseReach.refusal("build_structure", new JsonObject(), w.a, new Spot("minecraft:the_nether", 0, 64, 0), w)));
        assertNull(BaseReach.effectOf("build_structure", new JsonObject(), null));
    }

    private static List<String> workerTools() throws Exception {
        try (InputStream in = BaseReachTest.class.getResourceAsStream("/agent/allowed-tools.txt")) {
            assertNotNull(in, "the generated tool list is not on the classpath");
            List<String> out = new ArrayList<>();
            for (String s : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("[,\\r\\n]")) {
                String t = s.trim();
                if (t.startsWith("mcp__wishwatch__")) {
                    out.add(t.substring("mcp__wishwatch__".length()));
                }
            }
            assertTrue(out.size() > 30, "the tool list is suspiciously short: " + out);
            return out;
        }
    }

    // ── #1606: a command REPLAYED with no agent (a button, a template, a rule) ──────────────────

    /** The Isle's own shipped file (in this jar), so the test reads the rules the game reads. */
    private static WorldRules isle() throws java.io.IOException {
        WorldRules.Parsed p = WorldRules.parse(new String(java.util.Objects.requireNonNull(
                BaseReachTest.class.getResourceAsStream("/worlds/rules/isle.rules.json"), "isle.rules.json is not on the classpath")
                .readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), "isle");
        assertTrue(p.ok(), p.errors().toString());
        return p.rules();
    }

    @Test
    void aReplayedButtonIsHeldToTheIslesRulesFromWhereItRuns() throws java.io.IOException {
        World w = new World();
        WorldRules isle = isle();
        double x = w.ia.x() + 0.5;
        double z = w.ia.z() + 0.5;
        // On his own island, from where he pressed it: runs.
        assertNull(BaseReach.replayRefusal("setblock ~1 ~ ~ torch", w.a, x, z, OW, w, isle));
        assertNull(BaseReach.replayRefusal("summon minecraft:pig ~ ~ ~", w.a, x, z, OW, w, isle));
        // A button taught on another world: off his island, the shared sky, the goal list.
        assertEquals(WorldEffect.OUTSIDE_OWN_BASE,
                BaseReach.replayRefusal("tp @s 4500 80 3500", w.a, x, z, OW, w, isle));
        assertEquals(WorldEffect.OUTSIDE_OWN_BASE,
                BaseReach.replayRefusal("spreadplayers ~ ~ 10 5000 false @s", w.a, x, z, OW, w, isle));
        assertEquals(WorldEffect.TIME, BaseReach.replayRefusal("time set day", w.a, x, z, OW, w, isle));
        assertEquals(WorldEffect.TIME,
                BaseReach.replayRefusal("execute as @s run time set night", w.a, x, z, OW, w, isle));
        assertEquals(WorldEffect.GAMERULES_DIFFICULTY,
                BaseReach.replayRefusal("advancement grant @s everything", w.a, x, z, OW, w, isle));
        assertEquals(WorldEffect.OTHER_DIMENSIONS,
                BaseReach.replayRefusal("setblock ~ ~ ~ torch", w.a, x, z, "minecraft:the_nether", w, isle));
        // Measured from where it RUNS: the same relative command from B's island is off A's.
        assertEquals(WorldEffect.OUTSIDE_OWN_BASE, BaseReach.replayRefusal("setblock ~ ~ ~ torch", w.a,
                w.ib.x() + 0.5, w.ib.z() + 0.5, OW, w, isle));
    }

    @Test
    void aReplayOnAWorldWithoutBasesOrDenialsIsNeverJudged() throws java.io.IOException {
        World w = new World();
        assertNull(BaseReach.replayRefusal("time set day", w.a, 0, 0, OW, null, isle()), "a plain world has no bases");
        assertNull(BaseReach.replayRefusal("tp @s 4500 80 3500", w.a, 0, 0, OW, w, WorldRules.ALLOW_ALL));
        // No file read yet on a world with bases is CLOSED — never looser than the agent's path.
        assertEquals(WorldEffect.TIME, BaseReach.replayRefusal("time set day", w.a,
                w.ia.x() + 0.5, w.ia.z() + 0.5, OW, w, null));
    }
}
