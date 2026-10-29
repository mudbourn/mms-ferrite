package info.mudbourn.ferrite.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.Hopper;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(HopperBlockEntity.class)
public interface HopperBlockEntityInvoker {

    @Invoker("getSourceContainer")
    static Container ferrite$invokeGetSourceContainer(Level level, Hopper hopper, BlockPos pos, BlockState state) {
        throw new AssertionError();
    }

    @Invoker("getSlots")
    static int[] ferrite$invokeGetSlots(Container container, Direction side) {
        throw new AssertionError();
    }

    @Invoker("tryTakeInItemFromSlot")
    static boolean ferrite$invokeTryTakeInItemFromSlot(Hopper hopper, Container container, int slot, Direction side) {
        throw new AssertionError();
    }
}
