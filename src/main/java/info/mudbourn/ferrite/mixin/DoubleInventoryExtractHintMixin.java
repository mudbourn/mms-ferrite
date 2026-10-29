package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.hopper.ExtractHint;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// Double chest hint: the first half's hint, or the second half's once the first is drained.
@Mixin(CompoundContainer.class)
public abstract class DoubleInventoryExtractHintMixin implements Container, ExtractHint {

    @Shadow
    @Final
    private Container container1;
    @Shadow
    @Final
    private Container container2;

    @Override
    public int ferrite$getExtractHint() {
        int firstSize = this.container1.getContainerSize();
        if (this.container1 instanceof ExtractHint first) {
            int hint = first.ferrite$getExtractHint();
            if (hint < firstSize) {
                return hint;
            }
        } else {
            return 0;
        }
        if (this.container2 instanceof ExtractHint second) {
            return firstSize + second.ferrite$getExtractHint();
        }
        return firstSize;
    }

    @Override
    public void ferrite$setExtractHint(int hint) {
    }
}
