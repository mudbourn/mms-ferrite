package info.mudbourn.ferrite.entity;

import info.mudbourn.ferrite.RustBridge;
import info.mudbourn.ferrite.monitor.MonitorLog;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Runs mob cramming for the whole level in one native call on the first pushEntities of a tick.
public final class CrammingDispatcher {

    public static volatile boolean ENABLED = false;

    private static final Logger LOGGER = LoggerFactory.getLogger("mms_ferrite");
    private static final long OVERFLOW_LOG_MIN_GAP_NS = 1_000_000_000L;
    private static final long DIAG_INTERVAL_NS = 5_000_000_000L;

    private static final List<LivingEntity> MOB_SCRATCH = new ArrayList<>(1024);
    private static final double[] ACCUM_DX = new double[CrammingHandoff.MAX_ENTITIES];
    private static final double[] ACCUM_DZ = new double[CrammingHandoff.MAX_ENTITIES];
    private static final int[] NEIGHBOR_COUNT = new int[CrammingHandoff.MAX_ENTITIES];
    private static final int[] CROWDED_COUNT = new int[CrammingHandoff.MAX_ENTITIES];

    private static long lastProcessedTick = Long.MIN_VALUE;
    private static long overflowTick = Long.MIN_VALUE;
    private static long lastOverflowLogNs;
    private static long diagBatches;
    private static long diagMobs;
    private static long diagPushed;
    private static long diagDamaged;
    private static long diagLastLogNs = System.nanoTime();

    private CrammingDispatcher() {
    }

    // True when the native batch owns this tick and vanilla pushEntities must be cancelled.
    public static boolean onTickCramming(LivingEntity caller) {
        if (!ENABLED || !RustBridge.NATIVE_AVAILABLE) {
            return false;
        }
        if (!(caller.level() instanceof ServerLevel world)) {
            return false;
        }

        long tick = world.getGameTime();
        if (tick == overflowTick) {
            return false;
        }
        if (tick == lastProcessedTick) {
            return true;
        }
        lastProcessedTick = tick;
        if (!runBatch(world, tick)) {
            return false;
        }
        maybeLogDiag();
        return true;
    }

    // False when the level holds too many mobs and vanilla must run for the whole tick.
    private static boolean runBatch(ServerLevel world, long tick) {
        MOB_SCRATCH.clear();
        for (Entity e : world.getAllEntities()) {
            if (e instanceof Mob && e.isAlive() && !e.isRemoved()) {
                MOB_SCRATCH.add((LivingEntity) e);
            }
        }
        int count = MOB_SCRATCH.size();
        if (count == 0) {
            return true;
        }
        if (count > CrammingHandoff.MAX_ENTITIES) {
            overflowTick = tick;
            lastProcessedTick = Long.MIN_VALUE;
            maybeLogOverflow(count);
            return false;
        }

        CrammingHandoff.buildRequests(MOB_SCRATCH);
        RustBridge.computeCramming(CrammingHandoff.REQUEST_BUF, CrammingHandoff.RESULT_BUF, count);
        CrammingHandoff.readResults(count, ACCUM_DX, ACCUM_DZ, NEIGHBOR_COUNT, CROWDED_COUNT);

        int maxCramming = world.getGameRules().get(GameRules.MAX_ENTITY_CRAMMING);
        int pushed = 0;
        int damaged = 0;
        for (int i = 0; i < count; i++) {
            LivingEntity e = MOB_SCRATCH.get(i);
            double dx = ACCUM_DX[i];
            double dz = ACCUM_DZ[i];
            if (dx != 0.0 || dz != 0.0) {
                e.push(dx, 0.0, dz);
                pushed++;
            }

            if (maxCramming > 0 && CROWDED_COUNT[i] > maxCramming - 1 && e.getRandom().nextInt(4) == 0) {
                e.hurtServer(world, world.damageSources().cramming(), 6.0F);
                damaged++;
            }
        }

        diagBatches++;
        diagMobs += count;
        diagPushed += pushed;
        diagDamaged += damaged;
        return true;
    }

    private static void maybeLogOverflow(int count) {
        long now = System.nanoTime();
        if (now - lastOverflowLogNs < OVERFLOW_LOG_MIN_GAP_NS) {
            return;
        }
        lastOverflowLogNs = now;
        LOGGER.warn(
            "[cramming-dispatch] {} mobs exceeds MAX_ENTITIES={}, falling back to vanilla this tick",
            count,
            CrammingHandoff.MAX_ENTITIES
        );
    }

    private static void maybeLogDiag() {
        long now = System.nanoTime();
        if (now - diagLastLogNs < DIAG_INTERVAL_NS) {
            return;
        }
        diagLastLogNs = now;
        MonitorLog.info(
            "[cramming-dispatch] batches={} mobsTotal={} pushed={} damaged={}",
            diagBatches,
            diagMobs,
            diagPushed,
            diagDamaged
        );
        diagBatches = 0;
        diagMobs = 0;
        diagPushed = 0;
        diagDamaged = 0;
    }
}
