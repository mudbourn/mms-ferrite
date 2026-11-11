package info.mudbourn.ferrite.mixin.client;

import info.mudbourn.ferrite.client.BobbyLightStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Releases a fake chunk's saved light once Bobby has written it to disk.
@Pseudo
@Mixin(targets = "de.johni0702.minecraft.bobby.ChunkSerializer", remap = false)
public abstract class BobbySerializerMixin {

    @Inject(method = "serialize", at = @At("RETURN"), require = 0)
    private static void ferrite$releaseLight(LevelChunk chunk, LevelLightEngine light, CallbackInfoReturnable<CompoundTag> cir) {
        BobbyLightStore.releaseSavedLight(chunk);
    }
}
