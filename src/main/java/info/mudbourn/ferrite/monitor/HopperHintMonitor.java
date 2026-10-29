package info.mudbourn.ferrite.monitor;

import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;

// Counters and switches for the hopper extract hint and its stale hint validator.
public final class HopperHintMonitor {

    private static final long REPORT_INTERVAL_NS = 5_000_000_000L;
    private static final long VALIDATION_LOG_BUDGET = 20L;

    public static volatile boolean USE_HINT = Boolean.parseBoolean(
        System.getProperty("ferrite.hopper.extract.useHint", "true")
    );
    public static volatile boolean VALIDATE = Boolean.parseBoolean(
        System.getProperty("ferrite.hopper.extract.validate", "false")
    );

    private static final AtomicLong HITS = new AtomicLong();
    private static final AtomicLong WRAPPED = new AtomicLong();
    private static final AtomicLong FAILED = new AtomicLong();
    private static final AtomicLong NO_CONTAINER = new AtomicLong();
    private static final AtomicLong START_SUM = new AtomicLong();
    private static final AtomicLong TOTAL_NS = new AtomicLong();

    private static final AtomicLong VAL_TOTAL = new AtomicLong();
    private static final AtomicLong VAL_STALE = new AtomicLong();
    private static final AtomicLong VAL_NO_CONTAINER = new AtomicLong();
    private static final AtomicLong VAL_UNSUPPORTED = new AtomicLong();
    private static final AtomicLong VAL_LOG_BUDGET = new AtomicLong(VALIDATION_LOG_BUDGET);

    private static volatile long lastReportNs = System.nanoTime();

    private HopperHintMonitor() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> maybeReport());
    }

    public static void onHit(int hintAt, long elapsedNs) {
        HITS.incrementAndGet();
        START_SUM.addAndGet(hintAt);
        TOTAL_NS.addAndGet(elapsedNs);
    }

    public static void onWrap(long elapsedNs) {
        WRAPPED.incrementAndGet();
        TOTAL_NS.addAndGet(elapsedNs);
    }

    public static void onAllFail(long elapsedNs) {
        FAILED.incrementAndGet();
        TOTAL_NS.addAndGet(elapsedNs);
    }

    public static void onNoContainer() {
        NO_CONTAINER.incrementAndGet();
    }

    public static void onValidationNoContainer() {
        VAL_NO_CONTAINER.incrementAndGet();
    }

    public static void onValidationUnsupported() {
        VAL_UNSUPPORTED.incrementAndGet();
    }

    public static void onValidation(boolean stale, BlockPos pos, int hint, int firstNonEmpty) {
        VAL_TOTAL.incrementAndGet();
        if (stale) {
            VAL_STALE.incrementAndGet();
            if (VAL_LOG_BUDGET.getAndDecrement() > 0) {
                org.slf4j.LoggerFactory.getLogger("mms_ferrite").warn(
                    "[hopper-hint] STALE hint at {} hint={} but firstNonEmpty={}",
                    pos,
                    hint,
                    firstNonEmpty
                );
            }
        }
    }

    private static void maybeReport() {
        long now = System.nanoTime();
        if (now - lastReportNs < REPORT_INTERVAL_NS) {
            return;
        }

        long hits = HITS.getAndSet(0);
        long wrapped = WRAPPED.getAndSet(0);
        long failed = FAILED.getAndSet(0);
        long noContainer = NO_CONTAINER.getAndSet(0);
        long startSum = START_SUM.getAndSet(0);
        long totalNs = TOTAL_NS.getAndSet(0);

        long valTotal = VAL_TOTAL.getAndSet(0);
        long valStale = VAL_STALE.getAndSet(0);
        long valNoContainer = VAL_NO_CONTAINER.getAndSet(0);
        long valUnsupported = VAL_UNSUPPORTED.getAndSet(0);

        boolean hadHint = USE_HINT && (hits + wrapped + failed) > 0;
        boolean hadValidation = VALIDATE && (valTotal + valNoContainer + valUnsupported) > 0;
        if (!hadHint && !hadValidation) {
            return;
        }
        lastReportNs = now;

        if (hadHint) {
            long calls = hits + wrapped + failed;
            MonitorLog.info(
                "[hopper-hint] calls={} hit={} wrap={} fail={} noContainer={} avgStartIdx={} usPerCall={}",
                calls,
                hits,
                wrapped,
                failed,
                noContainer,
                String.format("%.2f", hits > 0 ? (double) startSum / hits : 0.0),
                String.format("%.2f", totalNs / 1_000.0 / Math.max(1L, calls))
            );
        }
        if (hadValidation) {
            MonitorLog.info(
                "[hopper-hint] valid total={} stale={} ({}%) noContainer={} unsupported={}",
                valTotal,
                valStale,
                String.format("%.3f", 100.0 * valStale / Math.max(1L, valTotal)),
                valNoContainer,
                valUnsupported
            );
        }
    }
}
