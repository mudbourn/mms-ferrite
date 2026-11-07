package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.SoftValueMap;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Lets the GC clear shared bake results that a long-lived baker would pin.
@Mixin(targets = "net.minecraft.client.resources.model.ModelBakery$ModelBakerImpl")
public abstract class ModelBakerOperationCacheMixin {

    @Shadow
    @Final
    @Mutable
    private Map<Object, Object> operationCache;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ferrite$softOperationCache(CallbackInfo ci) {
        if (SoftValueMap.ENABLED) {
            operationCache = new SoftValueMap<>();
        }
    }
}
