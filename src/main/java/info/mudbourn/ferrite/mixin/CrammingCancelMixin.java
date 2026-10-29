package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.entity.CrammingDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class CrammingCancelMixin {

    @Inject(method = "pushEntities()V", at = @At("HEAD"), cancellable = true)
    private void ferrite$onTickCramming(CallbackInfo ci) {
        if (!((Object) this instanceof Mob)) {
            return;
        }
        if (CrammingDispatcher.onTickCramming((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
