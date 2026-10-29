package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.hopper.ExtractHint;
import info.mudbourn.ferrite.monitor.HopperHintMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

// Starts the source container scan at the hinted slot and wraps to slot 0 for the rest.
@Mixin(HopperBlockEntity.class)
public abstract class HopperHintExtractRouteMixin {

    @Inject(
        method = "suckInItems(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;)Z",
        at = @At(
            value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/world/level/block/entity/HopperBlockEntity;getSourceContainer(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/Hopper;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/Container;"
        ),
        locals = LocalCapture.CAPTURE_FAILHARD,
        cancellable = true
    )
    private static void ferrite$hintedExtract(
        Level level,
        Hopper hopper,
        CallbackInfoReturnable<Boolean> cir,
        BlockPos pos,
        BlockState state,
        Container container
    ) {
        if (!HopperHintMonitor.USE_HINT) {
            return;
        }
        if (!(container instanceof ExtractHint hinted)) {
            HopperHintMonitor.onNoContainer();
            return;
        }

        long start = System.nanoTime();
        Direction side = Direction.DOWN;
        int[] slots = HopperBlockEntityInvoker.ferrite$invokeGetSlots(container, side);

        int hint = hinted.ferrite$getExtractHint();
        if (hint < 0 || hint >= slots.length) {
            hint = 0;
        }

        for (int i = hint; i < slots.length; i++) {
            if (HopperBlockEntityInvoker.ferrite$invokeTryTakeInItemFromSlot(hopper, container, slots[i], side)) {
                HopperHintMonitor.onHit(i, System.nanoTime() - start);
                cir.setReturnValue(true);
                return;
            }
        }
        for (int i = 0; i < hint; i++) {
            if (HopperBlockEntityInvoker.ferrite$invokeTryTakeInItemFromSlot(hopper, container, slots[i], side)) {
                HopperHintMonitor.onWrap(System.nanoTime() - start);
                cir.setReturnValue(true);
                return;
            }
        }
        HopperHintMonitor.onAllFail(System.nanoTime() - start);
        cir.setReturnValue(false);
    }
}
