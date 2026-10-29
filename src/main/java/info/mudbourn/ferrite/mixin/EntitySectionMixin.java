package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.spatial.CellHolder;
import info.mudbourn.ferrite.spatial.EntityCellIndex;
import info.mudbourn.ferrite.spatial.SectionExtents;
import info.mudbourn.ferrite.spatial.SectionGrid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.ClassInstanceMultiMap;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Position filter and bitset grid in front of both EntitySection.getEntities overloads.
@Mixin(EntitySection.class)
public abstract class EntitySectionMixin implements SectionExtents {

    // Sections at or above this many entities get a grid.
    @Unique
    private static final int GRID_MIN = 32;

    @Shadow
    private ClassInstanceMultiMap<EntityAccess> storage;

    @Unique
    private float ferrite$maxHalfXZ = 0.0f;
    @Unique
    private float ferrite$maxHeight = 0.0f;
    @Unique
    private int ferrite$originX;
    @Unique
    private int ferrite$originY;
    @Unique
    private int ferrite$originZ;
    @Unique
    private SectionGrid ferrite$grid;

    @Override
    public void ferrite$setOrigin(int x, int y, int z) {
        ferrite$originX = x;
        ferrite$originY = y;
        ferrite$originZ = z;
    }

    @Override
    public int ferrite$originX() {
        return ferrite$originX;
    }

    @Override
    public int ferrite$originY() {
        return ferrite$originY;
    }

    @Override
    public int ferrite$originZ() {
        return ferrite$originZ;
    }

    @Override
    public SectionGrid ferrite$grid() {
        return ferrite$grid;
    }

    @Override
    public void ferrite$setGrid(SectionGrid grid) {
        ferrite$grid = grid;
    }

    @Override
    public float ferrite$maxHalfXZ() {
        return ferrite$maxHalfXZ;
    }

    @Override
    public float ferrite$maxHeight() {
        return ferrite$maxHeight;
    }

    @Override
    public void ferrite$growExtents(float halfXZ, float height) {
        if (halfXZ > ferrite$maxHalfXZ) {
            ferrite$maxHalfXZ = halfXZ;
        }
        if (height > ferrite$maxHeight) {
            ferrite$maxHeight = height;
        }
    }

    @Inject(method = "add", at = @At("HEAD"))
    private void ferrite$onAdd(EntityAccess entity, CallbackInfo ci) {
        long packed = entity.blockPosition().asLong();
        if (entity instanceof CellHolder holder) {
            holder.ferrite$setPackedPos(packed);
        }
        AABB bb = entity.getBoundingBox();
        ferrite$growExtents((float) (Math.max(bb.getXsize(), bb.getZsize()) * 0.5), (float) bb.getYsize());
        if (ferrite$grid != null) {
            ferrite$grid.onAdd(entity, ferrite$list().size(), packed);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void ferrite$onRemove(EntityAccess entity, CallbackInfoReturnable<Boolean> cir) {
        if (ferrite$grid != null) {
            ferrite$grid.markDirty();
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private List<EntityAccess> ferrite$list() {
        return ((ClassInstanceMultiMapAccessor<EntityAccess>) (Object) storage).ferrite$allInstances();
    }

    @Inject(
        method = "getEntities(Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ferrite$filteredPlain(
        AABB bb,
        AbortableIterationConsumer<EntityAccess> consumer,
        CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir
    ) {
        if (!EntityCellIndex.ENABLED) {
            return;
        }
        List<EntityAccess> list = ferrite$list();
        if (ferrite$grid == null && list.size() >= GRID_MIN) {
            ferrite$grid = new SectionGrid(list.size() + 64, ferrite$originX, ferrite$originY, ferrite$originZ);
            ferrite$grid.rebuild(list);
        }
        if (ferrite$grid == null) {
            cir.setReturnValue(ferrite$run(storage, null, bb, consumer));
            return;
        }
        if (ferrite$grid.dirty()) {
            if (list.size() < GRID_MIN / 2) {
                ferrite$grid = null;
                cir.setReturnValue(ferrite$run(storage, null, bb, consumer));
                return;
            }
            if (list.size() > ferrite$grid.capacity()) {
                ferrite$grid = new SectionGrid(list.size() + 64, ferrite$originX, ferrite$originY, ferrite$originZ);
            }
            ferrite$grid.rebuild(list);
        }
        if (ferrite$grid.dirty()) {
            cir.setReturnValue(ferrite$run(storage, null, bb, consumer));
            return;
        }
        cir.setReturnValue(ferrite$gridRun(list, bb, consumer));
    }

    // Grid-backed plain query: visits only candidate indices in vanilla order.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private AbortableIterationConsumer.Continuation ferrite$gridRun(
        List<EntityAccess> list,
        AABB bb,
        AbortableIterationConsumer consumer
    ) {
        boolean oracle = EntityCellIndex.ORACLE_RATE > 0
            && ++EntityCellIndex.queryCounter % EntityCellIndex.ORACLE_RATE == 0;
        int minX = Mth.floor(bb.minX - ferrite$maxHalfXZ) - 1 - ferrite$originX;
        int maxX = Mth.floor(bb.maxX + ferrite$maxHalfXZ) + 1 - ferrite$originX;
        int minZ = Mth.floor(bb.minZ - ferrite$maxHalfXZ) - 1 - ferrite$originZ;
        int maxZ = Mth.floor(bb.maxZ + ferrite$maxHalfXZ) + 1 - ferrite$originZ;
        int minY = Mth.floor(bb.minY - ferrite$maxHeight) - 1 - ferrite$originY;
        int maxY = Mth.floor(bb.maxY) + 1 - ferrite$originY;

        if (oracle) {
            List<EntityAccess> actual = new ArrayList<>();
            ferrite$grid.query(minX, minY, minZ, maxX, maxY, maxZ, index -> {
                EntityAccess e = list.get(index);
                if (e.getBoundingBox().intersects(bb)) {
                    actual.add(e);
                }
                return true;
            });
            List<EntityAccess> expected = new ArrayList<>();
            for (EntityAccess e : list) {
                if (e.getBoundingBox().intersects(bb)) {
                    expected.add(e);
                }
            }
            EntityCellIndex.oracleChecks++;
            if (!expected.equals(actual)) {
                EntityCellIndex.oracleMismatches++;
                if (EntityCellIndex.oracleMismatches <= 8) {
                    MmsFerrite.LOGGER.warn(
                        "[entity-query-cache] GRID MISMATCH: expected {} actual {} box={}",
                        expected.size(),
                        actual.size(),
                        bb
                    );
                }
            }
            for (EntityAccess e : expected) {
                EntityCellIndex.delivered++;
                if (consumer.accept(e).shouldAbort()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }
            return AbortableIterationConsumer.Continuation.CONTINUE;
        }

        boolean completed = ferrite$grid.query(minX, minY, minZ, maxX, maxY, maxZ, index -> {
            EntityAccess e = list.get(index);
            EntityCellIndex.scanned++;
            if (e.getBoundingBox().intersects(bb)) {
                EntityCellIndex.delivered++;
                return !consumer.accept(e).shouldAbort();
            }
            return true;
        });
        return completed
            ? AbortableIterationConsumer.Continuation.CONTINUE
            : AbortableIterationConsumer.Continuation.ABORT;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(
        method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)Lnet/minecraft/util/AbortableIterationConsumer$Continuation;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void ferrite$filteredTyped(
        EntityTypeTest type,
        AABB bb,
        AbortableIterationConsumer consumer,
        CallbackInfoReturnable<AbortableIterationConsumer.Continuation> cir
    ) {
        if (!EntityCellIndex.ENABLED) {
            return;
        }
        Collection<? extends EntityAccess> found = storage.find(type.getBaseClass());
        EntityCellIndex.typedQueries++;
        if (found.isEmpty()) {
            cir.setReturnValue(AbortableIterationConsumer.Continuation.CONTINUE);
            return;
        }
        EntityCellIndex.typedScanned += found.size();
        cir.setReturnValue(ferrite$run(found, type, bb, consumer));
    }

    // One loop for both overloads; a null type is the plain overload.
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Unique
    private AbortableIterationConsumer.Continuation ferrite$run(
        Collection<? extends EntityAccess> list,
        EntityTypeTest type,
        AABB bb,
        AbortableIterationConsumer consumer
    ) {
        boolean oracle = EntityCellIndex.ORACLE_RATE > 0
            && ++EntityCellIndex.queryCounter % EntityCellIndex.ORACLE_RATE == 0;

        int minX = Mth.floor(bb.minX - ferrite$maxHalfXZ) - 1;
        int maxX = Mth.floor(bb.maxX + ferrite$maxHalfXZ) + 1;
        int minZ = Mth.floor(bb.minZ - ferrite$maxHalfXZ) - 1;
        int maxZ = Mth.floor(bb.maxZ + ferrite$maxHalfXZ) + 1;
        int minY = Mth.floor(bb.minY - ferrite$maxHeight) - 1;
        int maxY = Mth.floor(bb.maxY) + 1;

        for (EntityAccess entity : list) {
            EntityCellIndex.scanned++;
            long packed = entity instanceof CellHolder holder ? holder.ferrite$packedPos() : Long.MIN_VALUE;
            boolean pass = true;
            if (packed != Long.MIN_VALUE) {
                int x = BlockPos.getX(packed);
                int y = BlockPos.getY(packed);
                int z = BlockPos.getZ(packed);
                pass = x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
            }
            if (!pass) {
                EntityCellIndex.filteredOut++;
                if (oracle) {
                    EntityCellIndex.oracleChecks++;
                    if (entity.getBoundingBox().intersects(bb) && (type == null || type.tryCast(entity) != null)) {
                        EntityCellIndex.oracleMismatches++;
                        MmsFerrite.LOGGER.warn(
                            "[entity-query-cache] MISMATCH: filter skipped intersecting entity {} pos={} box={}",
                            entity,
                            BlockPos.of(packed),
                            bb
                        );
                    }
                }
                continue;
            }
            if (type != null) {
                Object cast = type.tryCast(entity);
                if (cast == null) {
                    continue;
                }
                if (entity.getBoundingBox().intersects(bb)) {
                    EntityCellIndex.delivered++;
                    if (consumer.accept(cast).shouldAbort()) {
                        return AbortableIterationConsumer.Continuation.ABORT;
                    }
                }
            } else if (entity.getBoundingBox().intersects(bb)) {
                EntityCellIndex.delivered++;
                if (consumer.accept(entity).shouldAbort()) {
                    return AbortableIterationConsumer.Continuation.ABORT;
                }
            }
        }
        return AbortableIterationConsumer.Continuation.CONTINUE;
    }
}
