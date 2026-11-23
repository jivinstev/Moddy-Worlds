package com.moddingfromamod.bank;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.moddingfromamod.store.WishStoreFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>The wish bank on disk</b> — {@code <world>/wishes/wish-bank.json} (
 * §16). Minecraft-free (only files), so its rules are Gate A tests ({@code WishBankStoreTest}).
 *
 * <pre>
 * {"store":"wish-bank","v":1,
 *  "params":{"start":10,"refillMinutes":30,"cap":10,"price":1},
 *  "players":[{"ownerId":"…","balance":7,"lastRefillAt":1759…,
 *              "ledger":[{"wish":"<uuid>","price":1,"state":"charged","at":1759…}]}]}
 * </pre>
 *
 * <p>A WORLD store, not a shelf one: the bank is per world (the Isle has one, a plain world none), so
 * it lives with the world's other stores, keyed by {@code ownerId}. {@code params} is a SNAPSHOT of
 * the rules the last save ran under, for a reader with no rules file to hand; the mod always uses the rules in force.
 *
 * <p>The three rules of {@code WISH_STORES.md} §2 through {@link WishStoreFile}: an unreadable file is
 * a REFUSAL, never an empty bank (an empty bank would hand every child a fresh ten — and, written
 * back, erase what was there); a refused file is never written over (the {@link WishStoreFile.Guard}
 * latch, per instance); a file with no version reads as legacy.
 *
 * <p>The LEDGER is what makes a refund and a free re-wish answerable by wish id: a wish charged once
 * is never charged again (a {@code /moddy redirect} re-wish reuses the id),
 * and a refund happens once. It keeps the newest {@link #LEDGER_MAX} per player.
 */
public final class WishBankStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("moddingfromamod/wish-bank");

    public static final String STORE = "wish-bank";
    public static final int SCHEMA = 1;
    public static final String FILE = "wish-bank.json";
    static final int LEDGER_MAX = 64;

    /** What happened to a wish's charge. */
    public enum State {
        /** Paid, and not given back. */
        CHARGED,
        /** Paid, then given back (it failed or was stopped). */
        REFUNDED,
        /** Never charged on purpose (a wellbeing-flagged wish on an empty bank is never refused). */
        WAIVED;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static State of(String s) {
            for (State v : values()) {
                if (v.key().equals(s)) {
                    return v;
                }
            }
            return null;
        }
    }

    /** One charge's line. {@code price} is in HALVES (#1785: a wish with no model costs half). */
    public record Ledger(UUID wish, int price, State state, long at) {
    }

    /** One player's account and ledger. */
    public record Entry(WishBank.Account account, List<Ledger> ledger) {

        public Entry {
            ledger = List.copyOf(ledger);
        }

        public Ledger find(UUID wish) {
            for (Ledger l : ledger) {
                if (l.wish().equals(wish)) {
                    return l;
                }
            }
            return null;
        }

        /** This entry with {@code l} replacing any record of the same wish, newest last, trimmed. */
        public Entry with(WishBank.Account a, Ledger l) {
            List<Ledger> out = new ArrayList<>();
            for (Ledger x : ledger) {
                if (l == null || !x.wish().equals(l.wish())) {
                    out.add(x);
                }
            }
            if (l != null) {
                out.add(l);
            }
            while (out.size() > LEDGER_MAX) {
                out.remove(0);
            }
            return new Entry(a, out);
        }
    }

    private final Path file;
    private final WishStoreFile.Guard guard = new WishStoreFile.Guard();
    private final Map<UUID, Entry> players = new LinkedHashMap<>();
    private boolean loaded;

    public WishBankStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return file;
    }

    /** Read the file. Returns the refusal reason, or null. A refused store holds nothing and saves nothing. */
    public synchronized String load() {
        loaded = true;
        players.clear();
        guard.reset();
        var read = guard.observe(WishStoreFile.readFile(file, STORE, SCHEMA));
        if (read instanceof WishStoreFile.Read.Refused r) {
            LOGGER.error("could not read the wish bank at {}: {}. Nobody can wish on this world until a "
                    + "grown-up looks at it, and nothing will be saved over it.", file, r.reason());
            return r.reason();
        }
        JsonObject root = WishStoreFile.rootOf(read);
        JsonElement arr = root == null ? null : root.get("players");
        if (arr != null && arr.isJsonArray()) {
            for (JsonElement el : arr.getAsJsonArray()) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    UUID id = UUID.fromString(o.get("ownerId").getAsString());
                    // HALF A CHARGE (#1785), stored so the OLD reader still works (COMPAT_SHIMS'
                    // cheapest answer, no schema bump): `balance` stays WHOLE wishes, and the half
                    // left over rides in its own field, written only when there is one. A file from
                    // before has no `half`, and reads exactly as it did.
                    int halves = o.get("balance").getAsInt() * WishBank.HALVES
                            + (o.has("half") && o.get("half").getAsBoolean() ? 1 : 0);
                    WishBank.Account a = new WishBank.Account(halves, o.get("lastRefillAt").getAsLong());
                    List<Ledger> ledger = new ArrayList<>();
                    if (o.has("ledger") && o.get("ledger").isJsonArray()) {
                        for (JsonElement le : o.getAsJsonArray("ledger")) {
                            JsonObject l = le.getAsJsonObject();
                            State st = State.of(l.get("state").getAsString());
                            if (st != null) {
                                // `price` is whole wishes; a half charge also writes `halves`.
                                int ph = l.has("halves") ? l.get("halves").getAsInt()
                                        : l.get("price").getAsInt() * WishBank.HALVES;
                                ledger.add(new Ledger(UUID.fromString(l.get("wish").getAsString()),
                                        ph, st, l.has("at") ? l.get("at").getAsLong() : 0L));
                            }
                        }
                    }
                    players.put(id, new Entry(a, ledger));
                } catch (RuntimeException e) {
                    LOGGER.warn("skipped a damaged wish-bank record: {}", e.toString());
                }
            }
        }
        return null;
    }

    /** False when the last load was refused: the bank is closed and the file left alone. */
    public synchronized boolean usable() {
        if (!loaded) {
            load();
        }
        return guard.mayWrite();
    }

    public synchronized String refusalReason() {
        return guard.refusalReason();
    }

    public synchronized Entry get(UUID player) {
        if (!loaded) {
            load();
        }
        return players.get(player);
    }

    public synchronized Map<UUID, Entry> all() {
        if (!loaded) {
            load();
        }
        return Map.copyOf(players);
    }

    /** Put and save. Returns false when the latch refused the write (the change is not kept either). */
    public synchronized boolean put(UUID player, Entry e, WishBank.Params params) {
        if (!usable()) {
            LOGGER.error("NOT saving the wish bank — {} could not be read ({}). Left alone on purpose.",
                    file, guard.refusalReason());
            return false;
        }
        Entry before = players.put(player, e);
        if (!save(params)) {
            if (before == null) {
                players.remove(player);
            } else {
                players.put(player, before);
            }
            return false;
        }
        return true;
    }

    private boolean save(WishBank.Params params) {
        JsonObject payload = new JsonObject();
        if (params != null) {
            JsonObject p = new JsonObject();
            p.addProperty("start", params.start());
            p.addProperty("refillMinutes", params.refillMinutes());
            p.addProperty("cap", params.cap());
            p.addProperty("price", params.price());
            payload.add("params", p);
        }
        JsonArray arr = new JsonArray();
        for (Map.Entry<UUID, Entry> e : players.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("ownerId", e.getKey().toString());
            int halves = e.getValue().account().halves();
            o.addProperty("balance", halves / WishBank.HALVES);
            if (halves % WishBank.HALVES != 0) {
                o.addProperty("half", true);
            }
            o.addProperty("lastRefillAt", e.getValue().account().lastRefillAt());
            JsonArray ledger = new JsonArray();
            for (Ledger l : e.getValue().ledger()) {
                JsonObject lo = new JsonObject();
                lo.addProperty("wish", l.wish().toString());
                lo.addProperty("price", l.price() / WishBank.HALVES);
                if (l.price() % WishBank.HALVES != 0) {
                    lo.addProperty("halves", l.price());
                }
                lo.addProperty("state", l.state().key());
                lo.addProperty("at", l.at());
                ledger.add(lo);
            }
            o.add("ledger", ledger);
            arr.add(o);
        }
        payload.add("players", arr);
        try {
            WishStoreFile.writeFile(file, STORE, SCHEMA, payload);
            return true;
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn("could not save the wish bank to {}: {}", file, ex.toString());
            return false;
        }
    }
}
