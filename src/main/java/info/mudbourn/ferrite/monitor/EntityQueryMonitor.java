package info.mudbourn.ferrite.monitor;

import info.mudbourn.ferrite.spatial.ColliderSkip;
import info.mudbourn.ferrite.spatial.EntityCellIndex;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

// Drains the entity query and collider skip counters into one report line each.
public final class EntityQueryMonitor {

    private static final long REPORT_INTERVAL_NS = 5_000_000_000L;

    private static long lastReportNs = System.nanoTime();

    private EntityQueryMonitor() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> maybeReport());
    }

    public static long totalMismatches() {
        return EntityCellIndex.oracleMismatches;
    }

    public static long totalColliderNonEmpty() {
        return ColliderSkip.oracleNonEmpty;
    }

    private static void maybeReport() {
        long now = System.nanoTime();
        if (now - lastReportNs < REPORT_INTERVAL_NS) {
            return;
        }
        lastReportNs = now;

        if (ColliderSkip.ENABLED) {
            long eligible = ColliderSkip.eligible;
            long skipped = ColliderSkip.skipped;
            long walks = ColliderSkip.oracleWalks;
            ColliderSkip.eligible = 0;
            ColliderSkip.skipped = 0;
            ColliderSkip.oracleWalks = 0;
            if (eligible > 0) {
                MonitorLog.info(
                    "[collider-skip] eligible={} skipped={} ({}%) oracle: walks={} nonEmpty={}",
                    eligible,
                    skipped,
                    String.format("%.1f", 100.0 * skipped / eligible),
                    walks,
                    ColliderSkip.oracleNonEmpty
                );
            }
        }
        if (EntityCellIndex.ENABLED) {
            long scanned = EntityCellIndex.scanned;
            long filtered = EntityCellIndex.filteredOut;
            long delivered = EntityCellIndex.delivered;
            long checks = EntityCellIndex.oracleChecks;
            long typedQueries = EntityCellIndex.typedQueries;
            long typedScanned = EntityCellIndex.typedScanned;
            EntityCellIndex.scanned = 0;
            EntityCellIndex.filteredOut = 0;
            EntityCellIndex.delivered = 0;
            EntityCellIndex.oracleChecks = 0;
            EntityCellIndex.typedQueries = 0;
            EntityCellIndex.typedScanned = 0;
            if (scanned > 0 || typedQueries > 0) {
                MonitorLog.info(
                    "[entity-query-cache] scanned={} filtered={} ({}%) delivered={} typed: queries={} scanned={} oracle: checks={} mismatches={}",
                    scanned,
                    filtered,
                    String.format("%.1f", scanned == 0 ? 0.0 : 100.0 * filtered / scanned),
                    delivered,
                    typedQueries,
                    typedScanned,
                    checks,
                    EntityCellIndex.oracleMismatches
                );
            }
        }
    }
}
