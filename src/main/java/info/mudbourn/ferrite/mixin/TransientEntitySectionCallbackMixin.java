package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.spatial.CellHolder;
import info.mudbourn.ferrite.spatial.EntityCellIndex;
import info.mudbourn.ferrite.spatial.SectionExtents;
import info.mudbourn.ferrite.spatial.SectionGrid;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Client-level twin of EntitySectionCallbackMixin so integrated-server clients filter correctly.
@Mixin(targets = "net.minecraft.world.level.entity.TransientEntitySectionManager$Callback")
public abstract class TransientEntitySectionCallbackMixin {

    @Shadow
    @Final
    private EntityAccess entity;
    @Shadow
    private EntitySection<?> currentSection;

    @Inject(method = "onMove()V", at = @At("HEAD"))
    private void ferrite$updateCell(CallbackInfo ci) {
        if (!EntityCellIndex.ENABLED || !(entity instanceof CellHolder holder)) {
            return;
        }
        long oldPacked = holder.ferrite$packedPos();
        long newPacked = entity.blockPosition().asLong();
        holder.ferrite$setPackedPos(newPacked);

        SectionExtents section = (SectionExtents) currentSection;
        AABB bb = entity.getBoundingBox();
        section.ferrite$growExtents((float) (Math.max(bb.getXsize(), bb.getZsize()) * 0.5), (float) bb.getYsize());
        SectionGrid grid = section.ferrite$grid();
        if (grid != null) {
            grid.onMove(entity, oldPacked, newPacked);
        }
    }
}
