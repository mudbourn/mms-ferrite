package info.mudbourn.ferrite.mixin.client;

import java.util.Map;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?, ?>> ferrite$renderers();

    @Accessor("playerRenderers")
    Map<PlayerModelType, ? extends AvatarRenderer<?>> ferrite$playerRenderers();

    @Accessor("mannequinRenderers")
    Map<PlayerModelType, ? extends AvatarRenderer<?>> ferrite$mannequinRenderers();
}
