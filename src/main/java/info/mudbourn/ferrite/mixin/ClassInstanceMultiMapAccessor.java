package info.mudbourn.ferrite.mixin;

import java.util.List;
import net.minecraft.util.ClassInstanceMultiMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClassInstanceMultiMap.class)
public interface ClassInstanceMultiMapAccessor<T> {

    @Accessor("allInstances")
    List<T> ferrite$allInstances();
}
