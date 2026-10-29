package info.mudbourn.ferrite.spatial;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

// Skips the getEntityCollisions walk in levels holding no hard collider entities.
public final class ColliderSkip {

    public static final boolean ENABLED = !"false".equals(System.getProperty("ferrite.entityquery.colliderskip"));
    public static final int ORACLE_RATE = Integer.getInteger("ferrite.entityquery.colliderskip.oracle", 16);

    private static final Map<ServerLevel, AtomicInteger> COUNTS = new ConcurrentHashMap<>();

    public static long eligible;
    public static long skipped;
    public static long oracleWalks;
    public static long oracleNonEmpty;
    public static int sampleCounter;

    private ColliderSkip() {
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (isHardCollider(entity)) {
                COUNTS.computeIfAbsent(level, l -> new AtomicInteger()).incrementAndGet();
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (isHardCollider(entity)) {
                AtomicInteger count = COUNTS.get(level);
                if (count != null) {
                    count.decrementAndGet();
                }
            }
        });
        ServerWorldEvents.UNLOAD.register((server, level) -> COUNTS.remove(level));
    }

    public static boolean isHardCollider(Entity entity) {
        return entity instanceof AbstractBoat || entity instanceof Shulker || entity instanceof HappyGhast;
    }

    public static boolean isWideningSource(Entity source) {
        return source instanceof AbstractBoat || source instanceof AbstractMinecart;
    }

    public static boolean levelHasHardColliders(ServerLevel level) {
        AtomicInteger count = COUNTS.get(level);
        return count != null && count.get() > 0;
    }
}
