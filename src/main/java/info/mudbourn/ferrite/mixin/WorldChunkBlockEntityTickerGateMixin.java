package info.mudbourn.ferrite.mixin;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Drops the ticker of exact vanilla signs with no editor and exact vanilla furnaces with nothing to burn or cook.
@Mixin(LevelChunk.class)
public abstract class WorldChunkBlockEntityTickerGateMixin {

    @Redirect(
        method = "updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;"
        )
    )
    private <T extends BlockEntity> BlockEntityTicker<T> ferrite$gateBlockEntityTicker(
        BlockState state,
        Level world,
        BlockEntityType<T> type,
        T blockEntity
    ) {
        Class<?> beClass = blockEntity.getClass();

        if (beClass == SignBlockEntity.class || beClass == HangingSignBlockEntity.class) {
            if (((SignBlockEntity) blockEntity).getPlayerWhoMayEdit() == null) {
                return null;
            }
            return state.getTicker(world, type);
        }

        if (beClass == FurnaceBlockEntity.class
            || beClass == BlastFurnaceBlockEntity.class
            || beClass == SmokerBlockEntity.class) {
            AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) blockEntity;
            AbstractFurnaceBlockEntityAccessor accessor = (AbstractFurnaceBlockEntityAccessor) furnace;
            if (accessor.ferrite$getLitTimeRemaining() == 0
                && accessor.ferrite$getCookingTimer() == 0
                && furnace.getItem(0).isEmpty()
                && furnace.getItem(1).isEmpty()) {
                return null;
            }
            return state.getTicker(world, type);
        }

        return state.getTicker(world, type);
    }
}
