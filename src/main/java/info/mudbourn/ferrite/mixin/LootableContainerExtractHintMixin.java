package info.mudbourn.ferrite.mixin;

import info.mudbourn.ferrite.hopper.ExtractHint;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Keeps the extract hint at or below the first non-empty slot of loot containers.
@Mixin(RandomizableContainerBlockEntity.class)
public abstract class LootableContainerExtractHintMixin implements Container, ExtractHint {

    @Unique
    private int ferrite$extractHint = 0;

    @Override
    public int ferrite$getExtractHint() {
        return this.ferrite$extractHint;
    }

    @Override
    public void ferrite$setExtractHint(int hint) {
        this.ferrite$extractHint = hint < 0 ? 0 : hint;
    }

    @Unique
    private void ferrite$advanceHintFrom(int from) {
        int size = this.getContainerSize();
        int next = size;
        for (int i = from; i < size; i++) {
            if (!this.getItem(i).isEmpty()) {
                next = i;
                break;
            }
        }
        this.ferrite$extractHint = next;
    }

    @Inject(method = "setItem", at = @At("RETURN"))
    private void ferrite$onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        if (!stack.isEmpty() && slot < this.ferrite$extractHint) {
            this.ferrite$extractHint = slot;
        }
    }

    @Inject(method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void ferrite$onRemoveItem(int slot, int amount, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == this.ferrite$extractHint && this.getItem(slot).isEmpty()) {
            this.ferrite$advanceHintFrom(slot + 1);
        }
    }

    @Inject(method = "removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;", at = @At("RETURN"))
    private void ferrite$onRemoveItemNoUpdate(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot == this.ferrite$extractHint && this.getItem(slot).isEmpty()) {
            this.ferrite$advanceHintFrom(slot + 1);
        }
    }
}
