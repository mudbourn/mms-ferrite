package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.hopper.ExtractHint;
import info.mudbourn.ferrite.monitor.HopperHintMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Oracle: a hint above a non-empty slot is stale and counted.
@Mixin(HopperBlockEntity.class)
public abstract class HopperHintValidatorMixin {

    @Inject(
        method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z",
        at = @At("HEAD")
    )
    private static void ferrite$validateHint(Level level, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!HopperHintMonitor.VALIDATE) {
            return;
        }

        BlockPos pos = BlockPos.containing(hopper.getLevelX(), hopper.getLevelY() + 1.0, hopper.getLevelZ());
        BlockState state = level.getBlockState(pos);
        Container container = HopperBlockEntityInvoker.ferrite$invokeGetSourceContainer(level, hopper, pos, state);

        if (container == null) {
            HopperHintMonitor.onValidationNoContainer();
            return;
        }
        if (!(container instanceof ExtractHint hinted)) {
            HopperHintMonitor.onValidationUnsupported();
            return;
        }

        int hint = hinted.ferrite$getExtractHint();
        int size = container.getContainerSize();
        if (hint <= 0) {
            HopperHintMonitor.onValidation(false, pos, hint, -1);
            return;
        }
        if (hint > size) {
            HopperHintMonitor.onValidation(true, pos, hint, -1);
            return;
        }

        int firstNonEmpty = -1;
        for (int i = 0; i < hint; i++) {
            if (!container.getItem(i).isEmpty()) {
                firstNonEmpty = i;
                break;
            }
        }
        HopperHintMonitor.onValidation(firstNonEmpty >= 0, pos, hint, firstNonEmpty);
    }
}
