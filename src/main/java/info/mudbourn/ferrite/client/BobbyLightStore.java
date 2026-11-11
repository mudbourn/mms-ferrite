package info.mudbourn.ferrite.client;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.RustBridge;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;

// Keeps Bobby's untainted light layers compressed in native memory instead of the Java heap.
public final class BobbyLightStore {

    public static volatile boolean ENABLED = true;

    private static final int LAYER_BYTES = 2048;
    private static final String FAKE_CHUNK = "de.johni0702.minecraft.bobby.FakeChunk";

    private static final Map<Object, Long> OWNERS = new WeakHashMap<>();
    private static final Map<Long, WeakReference<Object>> ENGINES = new HashMap<>();
    private static long nextOwner = 1;
    private static volatile boolean nativeStore = RustBridge.NATIVE_AVAILABLE;

    private static Field fakeBlockLight;
    private static Field fakeSkyLight;
    private static volatile boolean fakeFieldsMissing;

    private BobbyLightStore() {
    }

    public static boolean active() {
        return ENABLED && nativeStore;
    }

    // Stores a layer; returns false when the caller must keep it on the heap.
    public static boolean put(Object engine, long pos, DataLayer layer) {
        if (!active() || layer.isDefinitelyHomogenous()) {
            return false;
        }
        try {
            return RustBridge.lightStorePut(owner(engine), pos, layer.getData());
        } catch (LinkageError e) {
            disable(e);
            return false;
        }
    }

    // Returns the stored layer bytes, or null when none is stored.
    public static byte[] get(Object engine, long pos) {
        if (!nativeStore) {
            return null;
        }
        byte[] out = new byte[LAYER_BYTES];
        try {
            return RustBridge.lightStoreGet(owner(engine), pos, out) ? out : null;
        } catch (LinkageError e) {
            disable(e);
            return null;
        }
    }

    public static void forget(Object engine, long pos) {
        if (!nativeStore) {
            return;
        }
        Long owner;
        synchronized (BobbyLightStore.class) {
            owner = OWNERS.get(engine);
        }
        if (owner == null) {
            return;
        }
        try {
            RustBridge.lightStoreRemove(owner, pos);
        } catch (LinkageError e) {
            disable(e);
        }
    }

    // Drops stored layers of every light engine except the given level's.
    public static void retainOnly(ClientLevel level) {
        Set<Object> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        if (level != null) {
            keep.add(level.getLightEngine().getLayerListener(LightLayer.SKY));
            keep.add(level.getLightEngine().getLayerListener(LightLayer.BLOCK));
        }
        synchronized (BobbyLightStore.class) {
            Iterator<Map.Entry<Long, WeakReference<Object>>> it = ENGINES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, WeakReference<Object>> entry = it.next();
                Object engine = entry.getValue().get();
                if (engine != null && keep.contains(engine)) {
                    continue;
                }
                clearOwner(entry.getKey());
                if (engine != null) {
                    OWNERS.remove(engine);
                }
                it.remove();
            }
        }
        logSize();
    }

    // Swaps a saved fake chunk's light arrays for empty ones, since Bobby reads them only when saving.
    public static void releaseSavedLight(LevelChunk chunk) {
        if (!ENABLED || fakeFieldsMissing || !chunk.getClass().getName().equals(FAKE_CHUNK)) {
            return;
        }
        try {
            if (fakeBlockLight == null) {
                fakeBlockLight = lightField(chunk.getClass(), "blockLight");
                fakeSkyLight = lightField(chunk.getClass(), "skyLight");
            }
            emptyArray(fakeBlockLight, chunk);
            emptyArray(fakeSkyLight, chunk);
        } catch (ReflectiveOperationException | RuntimeException e) {
            fakeFieldsMissing = true;
            MmsFerrite.LOGGER.warn("Bobby saved light release is off: {}", e.toString());
        }
    }

    private static void emptyArray(Field field, Object chunk) throws IllegalAccessException {
        DataLayer[] layers = (DataLayer[]) field.get(chunk);
        if (layers != null) {
            field.set(chunk, new DataLayer[layers.length]);
        }
    }

    private static Field lightField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static synchronized long owner(Object engine) {
        Long id = OWNERS.get(engine);
        if (id == null) {
            id = nextOwner++;
            OWNERS.put(engine, id);
            ENGINES.put(id, new WeakReference<>(engine));
        }
        return id;
    }

    private static void clearOwner(long owner) {
        if (!nativeStore) {
            return;
        }
        try {
            RustBridge.lightStoreClearOwner(owner);
        } catch (LinkageError e) {
            disable(e);
        }
    }

    private static void logSize() {
        if (!nativeStore) {
            return;
        }
        try {
            MmsFerrite.LOGGER.info(
                "Bobby light store: {} layers, {} KB",
                RustBridge.lightStoreCount(),
                RustBridge.lightStoreBytes() / 1024
            );
        } catch (LinkageError e) {
            disable(e);
        }
    }

    private static void disable(LinkageError e) {
        if (nativeStore) {
            nativeStore = false;
            MmsFerrite.LOGGER.warn("Native light store unavailable, Bobby light stays on the heap: {}", e.toString());
        }
    }
}
