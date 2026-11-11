package info.mudbourn.ferrite.client;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.RustBridge;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.DataLayer;

// Darkens or restores Bobby's fake chunk light one whole layer at a time.
public final class BobbyLightTaint {

    public static volatile boolean ENABLED = true;

    private static final int LAYER_BYTES = 2048;
    private static final Field[] MISSING = new Field[0];
    private static final Map<Class<?>, Field[]> MAPS = new ConcurrentHashMap<>();

    private static volatile boolean nativeKernel = RustBridge.NATIVE_AVAILABLE;

    private BobbyLightTaint() {
    }

    // Returns false when Bobby's own code should run instead.
    public static boolean setTainted(Object engine, long pos, int delta) {
        if (!ENABLED) {
            return false;
        }
        Field[] fields = MAPS.computeIfAbsent(engine.getClass(), BobbyLightTaint::lookup);
        if (fields.length == 0) {
            return false;
        }
        Long2ObjectMap<DataLayer> current = map(fields[0], engine);
        Long2ObjectMap<DataLayer> original = map(fields[1], engine);
        if (current == null || original == null) {
            return false;
        }
        if (delta == 0) {
            DataLayer restored = original.remove(pos);
            if (restored != null) {
                current.put(pos, restored);
            }
            return true;
        }
        DataLayer source = original.get(pos);
        if (source == null) {
            source = current.get(pos);
            if (source == null) {
                return true;
            }
            original.put(pos, source);
        }
        current.put(pos, shift(source, delta));
        return true;
    }

    private static DataLayer shift(DataLayer source, int delta) {
        byte[] dst = new byte[LAYER_BYTES];
        if (source.isDefinitelyHomogenous()) {
            int value = clamp(source.get(0, 0, 0) + delta);
            Arrays.fill(dst, (byte) (value | (value << 4)));
            return new DataLayer(dst);
        }
        byte[] src = source.getData();
        if (!nativeKernel || !shiftNative(src, dst, delta)) {
            shiftJava(src, dst, delta);
        }
        return new DataLayer(dst);
    }

    private static boolean shiftNative(byte[] src, byte[] dst, int delta) {
        try {
            return RustBridge.shiftNibbles(src, dst, delta);
        } catch (LinkageError e) {
            nativeKernel = false;
            MmsFerrite.LOGGER.warn("Native light shift unavailable, using Java: {}", e.toString());
            return false;
        }
    }

    private static void shiftJava(byte[] src, byte[] dst, int delta) {
        for (int i = 0; i < LAYER_BYTES; i++) {
            int packed = src[i];
            int low = clamp((packed & 0x0F) + delta);
            int high = clamp(((packed >> 4) & 0x0F) + delta);
            dst[i] = (byte) (low | (high << 4));
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(15, value));
    }

    @SuppressWarnings("unchecked")
    private static Long2ObjectMap<DataLayer> map(Field field, Object engine) {
        try {
            return (Long2ObjectMap<DataLayer>) field.get(engine);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Field[] lookup(Class<?> type) {
        try {
            Field current = find(type, "bobbySectionData");
            Field original = find(type, "bobbyOriginalSectionData");
            return new Field[] {current, original};
        } catch (ReflectiveOperationException | RuntimeException e) {
            MmsFerrite.LOGGER.warn("Bobby light taint fix is off for {}: {}", type.getName(), e.toString());
            return MISSING;
        }
    }

    private static Field find(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                continue;
            }
        }
        throw new NoSuchFieldException(name);
    }
}
