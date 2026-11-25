package com.moddingfromamod.levels.arena;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The wished creatures' battle roles, carried out in the world</b> ({@link ArenaRoles} decides; this looks and acts).
 * Ticked by {@link ArenaBattle}: during a wave, every creature a player wished into the arena gets its role (a sign
 * above it, one line to its player, once a battle), and does it: a Fighter goes for the wave's monsters, a Healer stays
 * by its player and heals him, a Tank draws the monsters near it and stands up to them.
 *
 * <p><b>Nothing lasts past the wave.</b> At the wave's end, in a break, when the battle ends and whenever a creature is
 * outside the arena, its role stops completely: its sign and its role tag go, its walk is stopped, the Resistance a tank
 * was given is taken off, and every monster a tank held is let go (its target cleared, so it goes back to the players).
 * Nothing about a creature or a monster is changed for good: a monster's target and a tank's short Resistance are all a
 * role ever touches, and both are given back.
 *
 * <p>Server thread only.
 */
final class ArenaRoleKeeper {

    private static final Logger LOG = LoggerFactory.getLogger("moddingfromamod/arena");

    /** The tag on every role sign (a text display above a creature), so a sweep can always find one. */
    static final String SIGN_TAG = ArenaMod.MODID + "_rolesign";
    /** The tag on every creature while it has a role. */
    static final String ROLE_TAG = ArenaMod.MODID + "_role";

    private final MinecraftServer server;
    private final ArenaPlace place;
    /** The arena's own tags: never a wished creature. */
    private final List<String> arenasOwn;

    /** One creature with a role in this wave. */
    private static final class Held {
        ArenaRoles.Role role;
        UUID owner;
        UUID foe;
        long lastStrike = -1;
        UUID sign;
        boolean resisted;
    }

    private final Map<UUID, Held> held = new HashMap<>();
    /** Each creature's role this battle: chosen the first time it is seen in a wave, kept to the battle's end. */
    private final Map<UUID, ArenaRoles.Role> chosen = new HashMap<>();
    /** The creatures whose player has been told their role, this battle. */
    private final Set<UUID> told = new HashSet<>();
    /** The monsters a tank holds: monster to tank. */
    private final Map<UUID, UUID> holding = new HashMap<>();
    /** When each fighter was last healed (one heal at a time, whatever the number of healers). */
    private final Map<UUID, Long> healedAt = new HashMap<>();
    private ArenaCreatures numbers = ArenaCreatures.DEFAULT;
    private long ticks;

    ArenaRoleKeeper(MinecraftServer server, ArenaPlace place, List<String> arenasOwn) {
        this.server = server;
        this.place = place;
        this.arenasOwn = List.copyOf(arenasOwn);
    }

    /** A battle starts: its numbers (from the world's rules, fixed for the battle), and nobody has been told yet. */
    void battleStarted(ArenaCreatures numbers) {
        this.numbers = numbers == null ? ArenaCreatures.DEFAULT : numbers;
        chosen.clear();
        told.clear();
        healedAt.clear();
    }

    /** How many creatures have a role now (for a log line). */
    int active() {
        return held.size();
    }

    /**
     * One tick. {@code wave}: a wave is being fought now; else every role stops. {@code fighters}: the players in the
     * battle; {@code waveMobs}: the wave's monsters.
     */
    void tick(ServerLevel level, boolean wave, Collection<UUID> fighters, Collection<UUID> waveMobs) {
        ticks++;
        if (!wave) {
            if (!held.isEmpty() || !holding.isEmpty()) {
                int[] n = releaseAll(level);
                LOG.info("Moddy Arena: roles: the wave is over; {} creatures' roles stopped, {} monsters let go", n[0],
                        n[1]);
            }
            if (ticks % 100 == 0) {
                sweep(level);
            }
            return;
        }
        List<ServerPlayer> in = new ArrayList<>();
        List<ArenaRoles.Spot> spots = new ArrayList<>();
        for (UUID id : fighters) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && p.level() == level && p.isAlive() && !p.isSpectator()) {
                in.add(p);
                spots.add(new ArenaRoles.Spot(id, p.getX(), p.getY(), p.getZ()));
            }
        }
        List<LivingEntity> foes = new ArrayList<>();
        for (UUID id : waveMobs) {
            if (level.getEntity(id) instanceof LivingEntity f && f.isAlive()) {
                foes.add(f);
            }
        }
        AABB arena = arenaBox(level);
        List<Mob> creatures = level.getEntities(EntityTypeTest.forClass(Mob.class), arena,
                m -> m.isAlive() && ArenaRoles.wished(m.getTags(), arenasOwn) && !waveMobs.contains(m.getUUID()));
        Set<UUID> seen = new HashSet<>();
        for (Mob m : creatures) {
            seen.add(m.getUUID());
            Held h = held.computeIfAbsent(m.getUUID(), id -> new Held());
            if (h.role == null) {
                start(level, m, h, spots);
            }
            UUID tamed = m instanceof OwnableEntity o && o.getOwnerReference() != null
                    ? o.getOwnerReference().getUUID() : null;
            h.owner = ArenaRoles.owner(m.getX(), m.getY(), m.getZ(), h.owner, tamed, spots);
            if (!told.contains(m.getUUID()) && h.owner != null) {
                ServerPlayer p = server.getPlayerList().getPlayer(h.owner);
                if (p != null) {
                    p.sendSystemMessage(Component.literal(ArenaWords.roleTaken(h.role,
                            ArenaRoles.plainName(m.getName().getString()))));
                    told.add(m.getUUID());
                }
            }
            keepSign(level, m, h);
            switch (h.role) {
                case FIGHTER -> fight(level, m, h, foes);
                case HEALER -> heal(level, m, h, in);
                case TANK -> taunt(level, m, h, foes);
            }
        }
        for (Iterator<Map.Entry<UUID, Held>> it = held.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Held> e = it.next();
            if (!seen.contains(e.getKey())) {
                end(level, e.getKey(), e.getValue());
                it.remove();
            }
        }
        keepHold(level);
    }

    // ── taking a role ───────────────────────────────────────────────────────────────────────────────────────────────

    private void start(ServerLevel level, Mob m, Held h, List<ArenaRoles.Spot> spots) {
        ArenaRoles.Facts f = facts(m);
        h.role = chosen.computeIfAbsent(m.getUUID(), id -> ArenaRoles.choose(f, numbers));
        m.addTag(ROLE_TAG);
        m.addTag(ArenaRoles.roleTag(h.role));
        LOG.info("Moddy Arena: roles: a wished {} '{}' (attack {}, health {}, width {}, {}{}) is a {}",
                EntityType.getKey(m.getType()), ArenaRoles.plainName(m.getName().getString()), fmt(f.attack()),
                fmt(f.maxHealth()), fmt(f.width()), f.monster() ? "a monster, " : "",
                f.canWalk() ? "walks" : "cannot walk", h.role.word().toUpperCase(Locale.ROOT));
        // a cue as it takes its role: a few sparks where it stands, in its role's colour of particle
        level.sendParticles(h.role == ArenaRoles.Role.HEALER ? ParticleTypes.HEART
                        : h.role == ArenaRoles.Role.FIGHTER ? ParticleTypes.CRIT : ParticleTypes.ANGRY_VILLAGER,
                m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
    }

    /** The facts a role is chosen from, as the game reports them for this creature. */
    static ArenaRoles.Facts facts(Mob m) {
        AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
        boolean walks = !m.isNoAi() && speed != null && speed.getValue() > 0;
        return new ArenaRoles.Facts(attack == null ? 0 : attack.getValue(), m.getMaxHealth(), m.getBbWidth(),
                m.getType().getCategory() == MobCategory.MONSTER, walks);
    }

    // ── the roles ───────────────────────────────────────────────────────────────────────────────────────────────────

    /** A Fighter: walks to its foe ({@link ArenaAllies#pick}) and, beside it, strikes it once a second. */
    private void fight(ServerLevel level, Mob m, Held h, List<LivingEntity> foes) {
        if (m.getTarget() instanceof Player) {
            m.setTarget(null);   // a wished monster fights the wave, not the players
        }
        List<ArenaAllies.Foe> list = new ArrayList<>();
        Map<UUID, LivingEntity> byId = new HashMap<>();
        for (LivingEntity f : foes) {
            list.add(new ArenaAllies.Foe(f.getUUID(), f.getX(), f.getY(), f.getZ()));
            byId.put(f.getUUID(), f);
        }
        UUID id = ArenaAllies.pick(m.getX(), m.getY(), m.getZ(), h.foe, list);
        if (id == null) {
            h.foe = null;
            return;
        }
        LivingEntity foe = byId.get(id);
        if (!id.equals(h.foe)) {
            h.foe = id;
            LOG.info("Moddy Arena: roles: a Fighter {} goes for a {}", EntityType.getKey(m.getType()),
                    EntityType.getKey(foe.getType()));
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2,
                    0.2, 0.1, 0.2, 0.0);
        }
        boolean ridden = m.hasPassenger(p -> p instanceof Player);
        if (ArenaAllies.inReach(m.distanceToSqr(foe), m.getBbWidth(), foe.getBbWidth())) {
            if (ArenaAllies.mayStrike(h.lastStrike, ticks)) {
                AttributeInstance attack = m.getAttribute(Attributes.ATTACK_DAMAGE);
                float dmg = ArenaAllies.damage(attack == null ? 0 : attack.getValue());
                m.getLookControl().setLookAt(foe);
                m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                foe.hurtServer(level, m.damageSources().mobAttack(m), dmg);
                level.sendParticles(ParticleTypes.CRIT, foe.getX(), foe.getY() + foe.getBbHeight() * 0.6, foe.getZ(),
                        8, 0.3, 0.3, 0.3, 0.2);
                level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_ATTACK_STRONG,
                        SoundSource.NEUTRAL, 0.7f, 1.1f);
                h.lastStrike = ticks;
            }
        } else if (!ridden && (ticks % ArenaAllies.REPATH_TICKS == 0 || m.getNavigation().isDone())) {
            m.getNavigation().moveTo(foe, ArenaAllies.SPEED);
        }
    }

    /**
     * A Healer: stays by its player, and heals every fighter near it who is hurt, by {@link ArenaCreatures#healAmount},
     * at most once every {@link ArenaCreatures#healSeconds} for each fighter whatever the number of healers. Only
     * players in the battle are healed: never a monster, never another creature.
     */
    private void heal(ServerLevel level, Mob m, Held h, List<ServerPlayer> fighters) {
        ServerPlayer owner = h.owner == null ? null : server.getPlayerList().getPlayer(h.owner);
        boolean ridden = m.hasPassenger(p -> p instanceof Player);
        if (owner != null && owner.level() == level && !ridden) {
            if (ArenaRoles.walkBack(m.distanceToSqr(owner))) {
                if (ticks % ArenaRoles.REPATH_TICKS == 0 || m.getNavigation().isDone()) {
                    m.getNavigation().moveTo(owner, ArenaAllies.SPEED);
                }
            } else if (!m.getNavigation().isDone()) {
                m.getNavigation().stop();
            }
        }
        for (ServerPlayer p : fighters) {
            if (!ArenaRoles.inHealReach(m.distanceToSqr(p), numbers)
                    || !ArenaRoles.mayHeal(healedAt.getOrDefault(p.getUUID(), -1L), ticks, numbers.healTicks())) {
                continue;
            }
            float amount = ArenaRoles.heal(p.getHealth(), p.getMaxHealth(), numbers.healAmount());
            if (amount <= 0) {
                continue;
            }
            p.heal(amount);
            healedAt.put(p.getUUID(), ticks);
            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + p.getBbHeight() + 0.2, p.getZ(), 4, 0.4, 0.2,
                    0.4, 0.0);
            level.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.2, m.getZ(), 2, 0.2,
                    0.1, 0.2, 0.0);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL,
                    1.0f, 1.4f);
            LOG.debug("Moddy Arena: roles: a Healer healed {} by {}", p.getScoreboardName(), amount);
        }
    }

    /**
     * A Tank: once a second, every monster of the wave near it that no other tank holds turns to it (an angry
     * cue over it, and a clang), and while it holds any it has a short Resistance, given again while it holds them.
     */
    private void taunt(ServerLevel level, Mob m, Held h, List<LivingEntity> foes) {
        if (ticks % ArenaRoles.TAUNT_TICKS != 0) {
            return;
        }
        int turned = 0;
        for (LivingEntity f : foes) {
            if (!(f instanceof Mob monster) || holding.containsKey(f.getUUID())
                    || !ArenaRoles.taunts(m.distanceToSqr(f), numbers)) {
                continue;
            }
            holding.put(f.getUUID(), m.getUUID());
            aim(monster, m);
            turned++;
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, f.getX(), f.getY() + f.getBbHeight() + 0.3, f.getZ(), 3,
                    0.2, 0.1, 0.2, 0.0);
        }
        if (turned > 0) {
            level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL,
                    1.0f, 0.8f);
            LOG.info("Moddy Arena: roles: a {} {} draws {} monsters", h.role.word(), EntityType.getKey(m.getType()),
                    turned);
        }
        boolean holds = holding.containsValue(m.getUUID());
        int amp = ArenaRoles.resistanceAmplifier(numbers.tankResistance());
        if (holds && amp >= 0) {
            m.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, ArenaRoles.RESISTANCE_TICKS, amp, true, true));
            h.resisted = true;
        }
    }

    /** Every tick: a held monster keeps its tank as its target; one whose tank is gone or far is let go. */
    private void keepHold(ServerLevel level) {
        for (Iterator<Map.Entry<UUID, UUID>> it = holding.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> e = it.next();
            Entity monster = level.getEntity(e.getKey());
            Entity tank = level.getEntity(e.getValue());
            Held h = held.get(e.getValue());
            boolean keep = monster instanceof Mob mm && mm.isAlive() && tank instanceof LivingEntity t && t.isAlive()
                    && h != null && h.role != null && h.role.draws() && !ArenaRoles.letsGo(mm.distanceToSqr(t), numbers);
            if (!keep) {
                letGo(monster, tank);
                it.remove();
            } else if (((Mob) monster).getTarget() != tank) {
                aim((Mob) monster, (LivingEntity) tank);
            }
        }
    }

    private static void aim(Mob monster, LivingEntity at) {
        monster.setTarget(at);
        if (monster.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
            monster.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, at);
        }
    }

    /** A monster a tank held goes back to its own choosing: its target is cleared when it is still the tank. */
    private static void letGo(Entity monster, Entity tank) {
        if (!(monster instanceof Mob m)) {
            return;
        }
        if (tank != null && m.getTarget() == tank) {
            m.setTarget(null);
        }
        if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).filter(t -> t == tank).isPresent()) {
            m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    // ── the sign above it ───────────────────────────────────────────────────────────────────────────────────────────

    /** The role's sign floats just above the creature: a text display (what a stock client draws), moved with it. */
    private void keepSign(ServerLevel level, Mob m, Held h) {
        double x = m.getX();
        double y = m.getY() + m.getBbHeight() + 0.45;
        double z = m.getZ();
        Entity sign = h.sign == null ? null : level.getEntity(h.sign);
        if (sign == null || !sign.isAlive()) {
            String tag = SIGN_TAG + "_" + m.getUUID();
            run(level, String.format(Locale.ROOT, "summon minecraft:text_display %.3f %.3f %.3f ", x, y, z)
                    + signNbt(h.role, tag));
            AABB near = new AABB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
            h.sign = null;
            for (Display.TextDisplay d : level.getEntities(EntityTypeTest.forClass(Display.TextDisplay.class), near,
                    d -> d.getTags().contains(tag))) {
                h.sign = d.getUUID();
            }
            return;
        }
        if (sign.distanceToSqr(x, y, z) > 1.0e-4) {
            sign.teleportTo(x, y, z);
        }
    }

    /** The sign's data: the arena's tags, the role's word in its colour, the game's own grey backing, facing the eye. */
    static String signNbt(ArenaRoles.Role role, String tag) {
        return "{Tags:[\"" + SIGN_TAG + "\",\"" + tag + "\"],text:{text:\"" + role.word() + "\",color:\""
                + role.colour() + "\",bold:true},billboard:\"center\",see_through:false,shadow:1b,"
                + "teleport_duration:2,view_range:0.6f,brightness:{sky:15,block:15},"
                + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],"
                + "scale:[0.8f,0.8f,0.8f]}}";
    }

    // ── stopping ────────────────────────────────────────────────────────────────────────────────────────────────────

    /** One creature's role stops: its sign and tags go, its walk stops, its Resistance goes, its monsters are let go. */
    private void end(ServerLevel level, UUID id, Held h) {
        Entity sign = h.sign == null ? null : level.getEntity(h.sign);
        if (sign != null) {
            sign.discard();
        }
        for (Iterator<Map.Entry<UUID, UUID>> it = holding.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, UUID> e = it.next();
            if (e.getValue().equals(id)) {
                letGo(level.getEntity(e.getKey()), level.getEntity(id));
                it.remove();
            }
        }
        if (level.getEntity(id) instanceof Mob m) {
            untag(m);
            m.getNavigation().stop();
            if (h.resisted) {
                MobEffectInstance r = m.getEffect(MobEffects.RESISTANCE);
                if (r != null && r.getAmplifier() == ArenaRoles.resistanceAmplifier(numbers.tankResistance())
                        && r.getDuration() <= ArenaRoles.RESISTANCE_TICKS) {
                    m.removeEffect(MobEffects.RESISTANCE);
                }
            }
        }
    }

    /** Every role stops (the wave is over, the battle ended, the server stops). {creatures, monsters let go}. */
    int[] releaseAll(ServerLevel level) {
        int creatures = held.size();
        int monsters = holding.size();
        for (Map.Entry<UUID, Held> e : List.copyOf(held.entrySet())) {
            end(level, e.getKey(), e.getValue());
        }
        held.clear();
        for (Map.Entry<UUID, UUID> e : holding.entrySet()) {
            letGo(level.getEntity(e.getKey()), level.getEntity(e.getValue()));
        }
        holding.clear();
        sweep(level);
        return new int[] {creatures, monsters};
    }

    /** Any sign or role tag left in the place from before (a restart in a wave) is taken away. */
    private void sweep(ServerLevel level) {
        if (!held.isEmpty()) {
            return;
        }
        AABB box = placeBox(level);
        for (Entity e : level.getEntities(EntityTypeTest.forClass(Entity.class), box,
                e -> e.getTags().contains(SIGN_TAG) || e.getTags().contains(ROLE_TAG))) {
            if (e.getTags().contains(SIGN_TAG)) {
                e.discard();
            } else {
                untag(e);
            }
        }
    }

    private static void untag(Entity e) {
        e.removeTag(ROLE_TAG);
        for (ArenaRoles.Role r : ArenaRoles.Role.values()) {
            e.removeTag(ArenaRoles.roleTag(r));
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────────────────────────

    /** The arena's bounds: its floor and walls, up to the sky (not the prep room). */
    private AABB arenaBox(ServerLevel level) {
        ArenaPlace.Box r = place.resetBox(level.getMaxY());
        return new AABB(r.x0(), r.y0() - 1, r.z0(), r.x1() + 1, r.y1() + 1, r.z1() + 1);
    }

    private AABB placeBox(ServerLevel level) {
        ArenaPlace.Box f = place.footprint();
        return new AABB(f.x0(), f.y0() - 2, f.z0(), f.x1() + 1, level.getMaxY() + 1, f.z1() + 1);
    }

    private void run(ServerLevel level, String command) {
        CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput()
                .withPermission(net.minecraft.server.permissions.LevelBasedPermissionSet.OWNER);
        server.getCommands().performPrefixedCommand(src, command);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
