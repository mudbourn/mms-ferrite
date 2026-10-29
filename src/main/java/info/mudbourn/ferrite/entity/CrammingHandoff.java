package info.mudbourn.ferrite.entity;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

// Packs mobs into the native cramming input layout (40 bytes) and reads results (32 bytes).
public final class CrammingHandoff {

    public static final int MAX_ENTITIES = 2048;
    public static final int REQUEST_STRIDE = 40;
    public static final int RESULT_STRIDE = 32;

    public static final int FLAG_PUSHABLE = 1;
    public static final int FLAG_VEHICLE = 1 << 1;
    public static final int FLAG_PASSENGER = 1 << 2;
    public static final int FLAG_NO_PHYSICS = 1 << 3;

    public static final ByteBuffer REQUEST_BUF =
        ByteBuffer.allocateDirect(MAX_ENTITIES * REQUEST_STRIDE).order(ByteOrder.nativeOrder());
    public static final ByteBuffer RESULT_BUF =
        ByteBuffer.allocateDirect(MAX_ENTITIES * RESULT_STRIDE).order(ByteOrder.nativeOrder());

    private CrammingHandoff() {
    }

    public static void buildRequests(List<? extends LivingEntity> mobs) {
        int n = mobs.size();
        if (n > MAX_ENTITIES) {
            throw new IllegalStateException("cramming input exceeds MAX_ENTITIES: " + n);
        }

        REQUEST_BUF.clear();
        for (int i = 0; i < n; i++) {
            LivingEntity e = mobs.get(i);
            AABB aabb = e.getBoundingBox();

            byte flags = 0;
            if (e.isPushable()) {
                flags |= FLAG_PUSHABLE;
            }
            if (e.isVehicle()) {
                flags |= FLAG_VEHICLE;
            }
            if (e.isPassenger()) {
                flags |= FLAG_PASSENGER;
            }
            if (e.noPhysics) {
                flags |= FLAG_NO_PHYSICS;
            }

            float halfWidth = (float) ((aabb.maxX - aabb.minX) * 0.5);

            int rootVehicleId = e.getId();
            if (e.isPassenger()) {
                Entity root = e.getRootVehicle();
                rootVehicleId = root != null ? root.getId() : e.getId();
            }

            REQUEST_BUF.putInt(e.getId());
            REQUEST_BUF.put(flags);
            REQUEST_BUF.put((byte) 0);
            REQUEST_BUF.put((byte) 0);
            REQUEST_BUF.put((byte) 0);
            REQUEST_BUF.putDouble(e.getX());
            REQUEST_BUF.putDouble(e.getZ());
            REQUEST_BUF.putFloat(halfWidth);
            REQUEST_BUF.putFloat((float) aabb.minY);
            REQUEST_BUF.putFloat((float) aabb.maxY);
            REQUEST_BUF.putInt(rootVehicleId);
        }
        REQUEST_BUF.flip();
    }

    public static void readResults(
        int count,
        double[] outDx,
        double[] outDz,
        int[] outNeighborCount,
        int[] outCrowdedCount
    ) {
        if (count > MAX_ENTITIES) {
            throw new IllegalStateException("cramming count exceeds MAX_ENTITIES");
        }
        RESULT_BUF.position(0);
        for (int i = 0; i < count; i++) {
            int base = i * RESULT_STRIDE;
            outDx[i] = RESULT_BUF.getDouble(base + 8);
            outDz[i] = RESULT_BUF.getDouble(base + 16);
            outNeighborCount[i] = RESULT_BUF.getInt(base + 24);
            outCrowdedCount[i] = RESULT_BUF.getInt(base + 28);
        }
    }
}
