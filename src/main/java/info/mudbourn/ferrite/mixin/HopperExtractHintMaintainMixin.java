package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.hopper.ExtractHint;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Hoppers override setItem and removeItem, so they keep their own hint.
@Mixin(HopperBlockEntity.class)
public abstract class HopperExtractHintMaintainMixin implements Container, ExtractHint {

    @Inject(method = "setItem", at = @At("RETURN"))
    private void ferrite$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (!stack.isEmpty() && slot < this.ferrite$getExtractHint()) {
            this.ferrite$setExtractHint(slot);
        }
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void ferrite$onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot != this.ferrite$getExtractHint() || !this.getItem(slot).isEmpty()) {
            return;
        }
        int size = this.getContainerSize();
        int next = size;
        for (int i = slot + 1; i < size; i++) {
            if (!this.getItem(i).isEmpty()) {
                next = i;
                break;
            }
        }
        this.ferrite$setExtractHint(next);
    }
}
