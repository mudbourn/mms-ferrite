package info.mudbourn.ferrite.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Gate for periodic report lines; boot and command output bypass it.
public final class MonitorLog {

    private static final Logger LOG = LoggerFactory.getLogger("mms_ferrite");

    public static volatile boolean ENABLED = Boolean.getBoolean("ferrite.log.monitors.on");

    private MonitorLog() {
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
