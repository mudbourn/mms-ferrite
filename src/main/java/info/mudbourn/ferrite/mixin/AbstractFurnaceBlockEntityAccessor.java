package info.mudbourn.ferrite.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {

    @Accessor("litTimeRemaining")
    int ferrite$getLitTimeRemaining();

    @Accessor("cookingTimer")
    int ferrite$getCookingTimer();
}
