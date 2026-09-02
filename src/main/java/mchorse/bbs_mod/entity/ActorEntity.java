package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.ItemPickupAnimationS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActorEntity extends LivingEntity implements IEntityFormProvider
{
    public static DefaultAttributeContainer.Builder createActorAttributes()
    {
        return LivingEntity.createLivingAttributes()
            .add(EntityAttributes.ATTACK_DAMAGE, 1D)
            .add(EntityAttributes.MOVEMENT_SPEED, 0.1D)
            .add(EntityAttributes.ATTACK_SPEED)
            .add(EntityAttributes.LUCK);
    }

    public Replay replay;
    public Film film;
    public float currentTick;
    private boolean replayItemsDropped;
    private final List<ItemStack> runtimeInventory = new ArrayList<>();
    private final Set<UUID> pickedUpEntityIds = new HashSet<>();

    private boolean despawn;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    public MCEntity getFormEntity()
    {
        return this.entity;
    }

    @Override
    public int getEntityId()
    {
        return this.getId();
    }

    @Override
    public Form getForm()
    {
        return this.form;
    }

    @Override
    public void setForm(Form form)
    {
        Form lastForm = this.form;

        this.form = form;

        if (!this.getEntityWorld().isClient())
        {
            if (lastForm != null) lastForm.onDemorph(this);
            if (form != null) form.onMorph(this);
        }
    }

    @Override
    public boolean shouldRender(double distance)
    {
        double d = this.getBoundingBox().getAverageSideLength();

        if (Double.isNaN(d))
        {
            d = 1D;
        }

        return distance < (d * 256D) * (d * 256D);
    }

    public Iterable<ItemStack> getHandItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.MAINHAND), this.getEquippedStack(EquipmentSlot.OFFHAND));
    }

    public Iterable<ItemStack> getArmorItems()
    {
        return List.of(this.getEquippedStack(EquipmentSlot.FEET), this.getEquippedStack(EquipmentSlot.LEGS), this.getEquippedStack(EquipmentSlot.CHEST), this.getEquippedStack(EquipmentSlot.HEAD));
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot)
    {
        return this.equipment.getOrDefault(slot, ItemStack.EMPTY);
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack)
    {
        this.equipment.put(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public Arm getMainArm()
    {
        return Arm.RIGHT;
    }

    @Override
    public void tick()
    {
        super.tick();

        this.tickHandSwing();

        if (this.form != null)
        {
            this.form.update(this.entity);
        }

        if (this.getEntityWorld().isClient() || this.isDead())
        {
            return;
        }

        /* Pickup items */
        Box box = this.getBoundingBox().expand(1D, 0.5D, 1D);
        List<Entity> list = this.getEntityWorld().getOtherEntities(this, box);

        for (Entity entity : list)
        {
            if (entity instanceof ItemEntity itemEntity)
            {
                UUID entityId = itemEntity.getUuid();
                ItemStack itemStack = itemEntity.getStack();
                int i = itemStack.getCount();

                if (!entity.isRemoved() && !itemEntity.cannotPickup() && !this.pickedUpEntityIds.contains(entityId))
                {
                    this.pickedUpEntityIds.add(entityId);
                    this.runtimeInventory.add(itemStack.copy());
                    ((ServerWorld) this.getEntityWorld()).getChunkManager().sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), i));
                    entity.discard();
                }
            }
        }
    }

    public Replay findReplay()
    {
        if (this.replay != null)
        {
            return this.replay;
        }

        for (ActionPlayer player : BBSMod.getActions().getPlayers())
        {
            for (Map.Entry<String, LivingEntity> entry : player.getActors().entrySet())
            {
                if (entry.getValue() == this)
                {
                    this.film = player.film;
                    this.replay = (Replay) player.film.replays.get(entry.getKey());
                    this.currentTick = player.getTick();
                    return this.replay;
                }
            }
        }

        return null;
    }

    @Override
    public void takeKnockback(double strength, double x, double z)
    {
        /* Replay actors are driven by keyframes; vanilla damage knockback
         * causes an unwanted jump/hop in random directions on lethal hits. */
        if (this.replay != null || this.findReplay() != null)
        {
            return;
        }

        super.takeKnockback(strength, x, z);
    }

    @Override
    public void onDeath(DamageSource damageSource)
    {
        /* Keep actor grounded and still during the death roll animation */
        this.setVelocity(0D, Math.min(0D, this.getVelocity().y), 0D);

        super.onDeath(damageSource);

        this.findReplay();

        boolean dropEnabled = this.replay != null && this.replay.dropItemsOnDeath.get();
        System.out.println("[BBS Actor onDeath] onDeath triggered! isClient=" + this.getEntityWorld().isClient()
            + ", alreadyDropped=" + this.replayItemsDropped
            + ", replay=" + (this.replay != null ? this.replay.getId() : "null")
            + ", dropItemsOnDeath=" + dropEnabled);

        if (!this.getEntityWorld().isClient()
            && !this.replayItemsDropped
            && dropEnabled)
        {
            this.dropReplayItems();
            this.replayItemsDropped = true;
        }
    }

    private void dropReplayItems()
    {
        System.out.println("[BBS Actor dropReplayItems] Executing item drop! tick=" + this.currentTick);

        List<ItemStack> toDrop = new ArrayList<>();
        List<ItemStack> matchedPool = new ArrayList<>();

        // 1. Collect all items picked up dynamically during runtime
        for (ItemStack stack : this.runtimeInventory)
        {
            if (stack != null && !stack.isEmpty())
            {
                ItemStack copy = stack.copy();
                toDrop.add(copy);
                matchedPool.add(copy.copy());
            }
        }

        // 2. Collect keyframe items (hotbar 0-8, offhand, armor)
        List<ItemStack> keyframeItems = new ArrayList<>();

        if (this.replay != null && this.replay.keyframes != null)
        {
            float tick = this.currentTick;

            for (int i = 0; i < this.replay.keyframes.hotbar.size(); i++)
            {
                ItemStack stack = this.replay.keyframes.hotbar.get(i).interpolate(tick, ItemStack.EMPTY);

                if (!stack.isEmpty())
                {
                    keyframeItems.add(stack.copy());
                }
            }

            ItemStack offHand = this.replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY);
            if (!offHand.isEmpty()) keyframeItems.add(offHand.copy());

            ItemStack armorHead = this.replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY);
            if (!armorHead.isEmpty()) keyframeItems.add(armorHead.copy());

            ItemStack armorChest = this.replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY);
            if (!armorChest.isEmpty()) keyframeItems.add(armorChest.copy());

            ItemStack armorLegs = this.replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY);
            if (!armorLegs.isEmpty()) keyframeItems.add(armorLegs.copy());

            ItemStack armorFeet = this.replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY);
            if (!armorFeet.isEmpty()) keyframeItems.add(armorFeet.copy());
        }

        // 3. Fallback to equipment slots if no keyframe items
        if (keyframeItems.isEmpty())
        {
            for (EquipmentSlot slot : EquipmentSlot.values())
            {
                ItemStack equipped = this.getEquippedStack(slot);

                if (equipped != null && !equipped.isEmpty())
                {
                    keyframeItems.add(equipped.copy());
                }
            }
        }

        // 4. Reconcile keyframe items against runtime pickups to prevent duplicate drops
        for (ItemStack kStack : keyframeItems)
        {
            if (kStack.isEmpty())
            {
                continue;
            }

            int needed = kStack.getCount();

            for (ItemStack matched : matchedPool)
            {
                if (matched.isEmpty())
                {
                    continue;
                }

                if (matched.isOf(kStack.getItem()) && ItemStack.areEqual(matched, kStack))
                {
                    int take = Math.min(matched.getCount(), needed);
                    matched.decrement(take);
                    needed -= take;

                    if (needed <= 0)
                    {
                        break;
                    }
                }
            }

            if (needed > 0)
            {
                ItemStack add = kStack.copy();
                add.setCount(needed);
                toDrop.add(add);
            }
        }

        // 5. Spawn drops
        for (ItemStack stack : toDrop)
        {
            if (stack != null && !stack.isEmpty())
            {
                this.dropItemStack(stack);
            }
        }

        this.runtimeInventory.clear();
        this.pickedUpEntityIds.clear();
    }

    private void dropItemStack(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(
            this.getEntityWorld(),
            this.getX(),
            this.getY() + 0.5D,
            this.getZ(),
            stack
        );

        float minX = this.replay != null ? this.replay.dropVelocityMinX.get() : -0.1F;
        float maxX = this.replay != null ? this.replay.dropVelocityMaxX.get() : 0.1F;
        float minY = this.replay != null ? this.replay.dropVelocityMinY.get() : 0.1F;
        float maxY = this.replay != null ? this.replay.dropVelocityMaxY.get() : 0.25F;
        float minZ = this.replay != null ? this.replay.dropVelocityMinZ.get() : -0.1F;
        float maxZ = this.replay != null ? this.replay.dropVelocityMaxZ.get() : 0.1F;

        double velocityX = minX + this.random.nextDouble() * (maxX - minX);
        double velocityY = minY + this.random.nextDouble() * (maxY - minY);
        double velocityZ = minZ + this.random.nextDouble() * (maxZ - minZ);

        itemEntity.setVelocity(velocityX, velocityY, velocityZ);
        itemEntity.setToDefaultPickupDelay();

        System.out.println("[BBS Actor dropItemStack] Spawning ItemEntity for: " + stack.getItem() + " x" + stack.getCount()
            + " at (" + this.getX() + ", " + (this.getY() + 0.5D) + ", " + this.getZ() + ") with vel (" + velocityX + ", " + velocityY + ", " + velocityZ + ")");

        this.getEntityWorld().spawnEntity(itemEntity);
    }

    @Override
    public void checkDespawn()
    {
        super.checkDespawn();

        if (this.despawn)
        {
            this.discard();
        }
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player)
    {
        super.onStartedTrackingBy(player);

        ServerNetwork.sendEntityForm(player, this);
    }

    @Override
    public void readCustomData(ReadView view)
    {
        super.readCustomData(view);

        this.despawn = view.getBoolean("despawn", false);
    }

    @Override
    public void writeCustomData(WriteView view)
    {
        super.writeCustomData(view);

        view.putBoolean("despawn", true);
    }
}