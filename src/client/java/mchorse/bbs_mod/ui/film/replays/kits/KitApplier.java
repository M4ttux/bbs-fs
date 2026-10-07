package mchorse.bbs_mod.ui.film.replays.kits;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.item.ItemStack;

public class KitApplier
{
    private static void set(KeyframeChannel<ItemStack> channel, ItemStack stack)
    {
        channel.removeAll();
        channel.insert(0F, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    public static void apply(Replay replay, ResolvedKit kit)
    {
        if (replay == null || kit == null)
        {
            return;
        }

        ReplayKeyframes k = replay.keyframes;

        set(k.armorHead, kit.head());
        set(k.armorChest, kit.chest());
        set(k.armorLegs, kit.legs());
        set(k.armorFeet, kit.feet());
        set(k.offHand, kit.offHand());

        if (!k.hotbar.isEmpty())
        {
            set(k.hotbar.get(0), kit.mainHand());
        }

        k.selectedSlot.removeAll();
        k.selectedSlot.insert(0F, 0);
    }
}
