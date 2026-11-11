package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.XaeroWriteBudget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

// Scales Xaero's World Map chunk writing time per frame.
@Pseudo
@Mixin(targets = "xaero.map.MapWriter", remap = false)
public abstract class XaeroWorldMapWriteBudgetMixin {

    @ModifyConstant(method = "onRender", constant = @Constant(longValue = 86960L), require = 0)
    private long ferrite$scaleWriteBudget(long nanosPerFrameMs) {
        return XaeroWriteBudget.scale(nanosPerFrameMs);
    }
}
