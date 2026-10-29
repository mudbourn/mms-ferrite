package info.mudbourn.ferrite.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelChunk.class)
public interface WorldChunkInvoker {

    @Invoker("updateBlockEntityTicker")
    <T extends BlockEntity> void ferrite$updateBlockEntityTicker(T blockEntity);
}
