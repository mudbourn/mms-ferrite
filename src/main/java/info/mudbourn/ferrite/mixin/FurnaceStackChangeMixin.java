package info.mudbourn.ferrite.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Re-asks the ticker gate whenever a furnace inventory slot changes.
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class FurnaceStackChangeMixin {

    @Inject(method = "setItem(ILnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void ferrite$reevalTickerOnStackChange(int slot, ItemStack stack, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(self.getBlockPos());
        ((WorldChunkInvoker) chunk).ferrite$updateBlockEntityTicker(self);
    }
}
