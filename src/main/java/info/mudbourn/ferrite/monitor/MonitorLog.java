package info.mudbourn.ferrite.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Gate for periodic report lines; boot and command output bypass it.
public final class MonitorLog {

    private static final long SMALL_HEAP_BYTES = 3L * 1024 * 1024 * 1024;
    private static final Logger LOG = LoggerFactory.getLogger("mms_ferrite");

    public static final boolean SMALL_HEAP = Runtime.getRuntime().maxMemory() <= SMALL_HEAP_BYTES;
    public static volatile boolean ENABLED = initialState();

    private MonitorLog() {
    }

    private static boolean initialState() {
        if (Boolean.getBoolean("ferrite.log.monitors.off")) {
            return false;
        }
        if (Boolean.getBoolean("ferrite.log.monitors.on")) {
            return true;
        }
        return !SMALL_HEAP;
    }

    public static void info(String fmt, Object... args) {
        if (ENABLED) {
            LOG.info(fmt, args);
        }
    }

    public static void warn(String fmt, Object... args) {
        if (ENABLED) {
            LOG.warn(fmt, args);
        }
    }
}
