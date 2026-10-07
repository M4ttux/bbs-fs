package mchorse.bbs_mod.ui.film.replays.kits;

import net.minecraft.item.ItemStack;

public record ResolvedKit(
    ItemStack head,
    ItemStack chest,
    ItemStack legs,
    ItemStack feet,
    ItemStack mainHand,
    ItemStack offHand
)
{
    public ResolvedKit copy()
    {
        return new ResolvedKit(
            this.head == null ? ItemStack.EMPTY : this.head.copy(),
            this.chest == null ? ItemStack.EMPTY : this.chest.copy(),
            this.legs == null ? this.legs == null ? ItemStack.EMPTY : this.legs.copy() : this.legs.copy(),
            this.feet == null ? ItemStack.EMPTY : this.feet.copy(),
            this.mainHand == null ? ItemStack.EMPTY : this.mainHand.copy(),
            this.offHand == null ? ItemStack.EMPTY : this.offHand.copy()
        );
    }
}
