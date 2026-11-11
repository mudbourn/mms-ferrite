package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.BobbyLightStore;
import info.mudbourn.ferrite.client.BobbyLightTaint;
import net.minecraft.world.level.chunk.DataLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Hooks Bobby's fake chunk light on ScalableLux's light engines.
@Pseudo
@Mixin(
    targets = {
        "ca.spottedleaf.starlight.common.light.StarLightInterface$1",
        "ca.spottedleaf.starlight.common.light.StarLightInterface$2"
    },
    priority = 1100,
    remap = false
)
public abstract class BobbyStarlightLightMixin {

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
