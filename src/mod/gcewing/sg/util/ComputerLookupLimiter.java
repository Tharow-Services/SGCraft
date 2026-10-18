package gcewing.sg.util;

import gcewing.sg.BaseConfiguration;
import gcewing.sg.tileentity.SGBaseTE;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

// Limits how often computers (OpenComputers and ComputerCraft, through a gate's interface) can look up a stargate by its
// address. A lookup loads the addressed chunk, and fails if no gate is there; scripts calling energyToDial or dial over
// and over were using that to load chunks at will and to sweep the world for gates (issue #163).
//
// Each gate gets: a short cooldown between lookups of different addresses; energyToDial's answer kept a while, so asking
// again for the same address (or dialling it) costs nothing; and a limit on lookups in a window, past which the gate's
// computers are locked out of lookups for a time, and the server log says where.
public final class ComputerLookupLimiter {

    public static long cooldownMillis = 1000;
    public static int maxLookups = 20;
    public static int windowSeconds = 60;
    public static int lockoutSeconds = 600;
    public static int cacheSeconds = 30;

    public static void configure(BaseConfiguration cfg) {
        cooldownMillis = Math.max(0, cfg.getInteger("computers", "addressLookupCooldownMillis", (int) cooldownMillis));
        maxLookups = Math.max(1, cfg.getInteger("computers", "maxAddressLookups", maxLookups));
        windowSeconds = Math.max(1, cfg.getInteger("computers", "addressLookupWindowSeconds", windowSeconds));
        lockoutSeconds = Math.max(0, cfg.getInteger("computers", "addressLookupLockoutSeconds", lockoutSeconds));
        cacheSeconds = Math.max(0, cfg.getInteger("computers", "energyToDialCacheSeconds", cacheSeconds));
    }

    // Where a gate is, as the key to its record
    private static final class GateKey {
        final int dimension;
        final BlockPos pos;

        GateKey(SGBaseTE gate) {
            this.dimension = gate.getWorld().provider.getDimension();
            this.pos = gate.getPos().toImmutable();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GateKey && ((GateKey) o).dimension == dimension && ((GateKey) o).pos.equals(pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(dimension, pos);
        }
    }

    // A gate's recent lookups, lockout, and last energyToDial answer
    private static final class Record {
        final Deque<Long> lookups = new ArrayDeque<>();
        long lockedUntil;
        String lastAddress;
        long lastAt;
        String cachedAddress;
        double cachedEnergy;
        long cachedAt;
    }

    private static final Map<GateKey, Record> records = new HashMap<>();

    private ComputerLookupLimiter() {}

    // energyToDial's last answer for this address from this gate, if still fresh, or null
    public static synchronized Double cachedEnergy(SGBaseTE gate, String address) {
        Record r = records.get(new GateKey(gate));
        long now = System.currentTimeMillis();
        if (r != null && address.equals(r.cachedAddress) && now - r.cachedAt <= cacheSeconds * 1000L) {
            return r.cachedEnergy;
        }
        return null;
    }

    public static synchronized void cache(SGBaseTE gate, String address, double energy) {
        Record r = records.computeIfAbsent(new GateKey(gate), k -> new Record());
        r.cachedAddress = address;
        r.cachedEnergy = energy;
        r.cachedAt = System.currentTimeMillis();
    }

    // Counts a lookup of address from this gate, or refuses it (IllegalArgumentException, which computers see as an
    // error) if the gate is locked out, or looked up a different address too recently
    public static synchronized void checkLookup(SGBaseTE gate, String address) {
        long now = System.currentTimeMillis();
        prune(now);
        GateKey key = new GateKey(gate);
        Record r = records.computeIfAbsent(key, k -> new Record());
        if (now < r.lockedUntil) {
            throw new IllegalArgumentException("addressLookupLocked");
        }
        // Dialling what was just looked up (energyToDial, then dial) is one use, not a second lookup
        boolean sameAsLast = address.equals(r.lastAddress) && now - r.lastAt <= cacheSeconds * 1000L;
        if (!sameAsLast && now - r.lastAt < cooldownMillis) {
            throw new IllegalArgumentException("addressLookupCooldown");
        }
        r.lastAddress = address;
        r.lastAt = now;
        if (sameAsLast) {
            return;
        }
        r.lookups.addLast(now);
        while (!r.lookups.isEmpty() && now - r.lookups.peekFirst() > windowSeconds * 1000L) {
            r.lookups.removeFirst();
        }
        if (r.lookups.size() > maxLookups) {
            r.lockedUntil = now + lockoutSeconds * 1000L;
            r.lookups.clear();
            System.out.printf("SGCraft: a computer at the stargate at %s in dimension %d looked up more than %d addresses in %d "
                    + "seconds (scanning for gates?); its address lookups are locked for %d seconds\n",
                key.pos, key.dimension, maxLookups, windowSeconds, lockoutSeconds);
            throw new IllegalArgumentException("addressLookupLocked");
        }
    }

    // Lets go of gates that haven't looked anything up for a while, and aren't locked out
    private static void prune(long now) {
        long idle = Math.max(windowSeconds, cacheSeconds) * 1000L + cooldownMillis;
        for (Iterator<Record> it = records.values().iterator(); it.hasNext(); ) {
            Record r = it.next();
            if (now >= r.lockedUntil && now - r.lastAt > idle && now - r.cachedAt > idle) {
                it.remove();
            }
        }
    }
}
