package info.mudbourn.ferrite.client;

// Scales the per-frame chunk writing time of Xaero's World Map and Minimap.
public final class XaeroWriteBudget {

    public static volatile boolean ENABLED = true;

    private static final long SCALE_DIVISOR = 2L;

    private XaeroWriteBudget() {
    }

    public static long scale(long nanosPerFrameMs) {
        return ENABLED ? nanosPerFrameMs / SCALE_DIVISOR : nanosPerFrameMs;
    }
}
