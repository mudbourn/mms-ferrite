package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.BobbyLightStore;
import info.mudbourn.ferrite.client.BobbyLightTaint;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hooks Bobby's fake chunk light on vanilla light engines.
@Mixin(value = LightEngine.class, priority = 1100, remap = false)
public abstract class BobbyVanillaLightMixin {

    @Inject(method = "bobby_setTainted", at = @At("HEAD"), cancellable = true, require = 0)
    private void ferrite$setTainted(long pos, int delta, CallbackInfo ci) {
        if (BobbyLightTaint.setTainted(this, pos, delta)) {
            ci.cancel();
        }
    }

    @Inject(method = "bobby_addSectionData", at = @At("HEAD"), require = 0)
    private void ferrite$addSectionData(long pos, DataLayer data, CallbackInfo ci) {
        BobbyLightStore.forget(this, pos);
    }

    @Inject(method = "bobby_removeSectionData", at = @At("HEAD"), require = 0)
    private void ferrite$removeSectionData(long pos, CallbackInfo ci) {
        BobbyLightStore.forget(this, pos);
    }
}
