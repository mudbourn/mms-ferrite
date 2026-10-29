package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.spatial.SectionExtents;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin {

    @Inject(method = "createSection", at = @At("RETURN"))
    private void ferrite$stampOrigin(long sectionPos, CallbackInfoReturnable<EntitySection<?>> cir) {
        ((SectionExtents) cir.getReturnValue()).ferrite$setOrigin(
            SectionPos.x(sectionPos) << 4,
            SectionPos.y(sectionPos) << 4,
            SectionPos.z(sectionPos) << 4
        );
    }
}
