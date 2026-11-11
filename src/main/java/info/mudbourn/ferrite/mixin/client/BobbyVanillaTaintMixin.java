package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.BobbyLightTaint;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Replaces Bobby's per-value light taint on vanilla light engines.
@Mixin(value = LightEngine.class, priority = 1100, remap = false)
public abstract class BobbyVanillaTaintMixin {

    @Inject(method = "bobby_setTainted", at = @At("HEAD"), cancellable = true, require = 0)
    private void ferrite$setTainted(long pos, int delta, CallbackInfo ci) {
        if (BobbyLightTaint.setTainted(this, pos, delta)) {
            ci.cancel();
        }
    }
}
