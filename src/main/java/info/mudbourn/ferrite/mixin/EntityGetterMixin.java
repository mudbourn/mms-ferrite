package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.MmsFerrite;
import info.mudbourn.ferrite.spatial.ColliderSkip;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityGetter.class)
public interface EntityGetterMixin {

    @Inject(method = "getEntityCollisions", at = @At("HEAD"), cancellable = true)
    default void ferrite$colliderSkip(Entity source, AABB testArea, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (!ColliderSkip.ENABLED) {
            return;
        }
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        if (source != null && ColliderSkip.isWideningSource(source)) {
            return;
        }
        if (ColliderSkip.levelHasHardColliders(level)) {
            return;
        }
        ColliderSkip.eligible++;
        if (ColliderSkip.ORACLE_RATE > 0 && ++ColliderSkip.sampleCounter % ColliderSkip.ORACLE_RATE == 0) {
            ColliderSkip.oracleWalks++;
            return;
        }
        ColliderSkip.skipped++;
        cir.setReturnValue(List.of());
    }

    @Inject(method = "getEntityCollisions", at = @At("RETURN"))
    default void ferrite$colliderOracle(Entity source, AABB testArea, CallbackInfoReturnable<List<VoxelShape>> cir) {
        if (!ColliderSkip.ENABLED) {
            return;
        }
        if (!((Object) this instanceof ServerLevel level)) {
            return;
        }
        if (source != null && ColliderSkip.isWideningSource(source)) {
            return;
        }
        if (ColliderSkip.levelHasHardColliders(level)) {
            return;
        }
        if (!cir.getReturnValue().isEmpty()) {
            ColliderSkip.oracleNonEmpty++;
            MmsFerrite.LOGGER.warn(
                "[collider-skip] MISMATCH: eligible query returned {} shapes, source={} box={}",
                cir.getReturnValue().size(),
                source,
                testArea
            );
        }
    }
}
