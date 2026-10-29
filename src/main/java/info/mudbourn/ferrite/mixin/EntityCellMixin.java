package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.spatial.CellHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityCellMixin implements CellHolder {

    @Unique
    private long ferrite$packedPos = Long.MIN_VALUE;

    @Override
    public long ferrite$packedPos() {
        return ferrite$packedPos;
    }

    @Override
    public void ferrite$setPackedPos(long packed) {
        ferrite$packedPos = packed;
    }
}
