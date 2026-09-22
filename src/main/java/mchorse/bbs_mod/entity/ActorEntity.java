package mchorse.bbs_mod.entity;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.network.ServerNetwork;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
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

    private boolean despawn;
    private MCEntity entity = new MCEntity(this);
    private Form form;

    /**
     * Which replay of which film put this body here. A client needs the pairing to know that this
     * entity is a replay's body rather than a creature, and it has to be able to learn that from
     * the entity alone - the map of actors is broadcast when they spawn, which is of no use to
     * anyone who starts seeing one later.
     */
    private String filmId = "";
    private String replayId = "";

    private boolean pickUpItems = true;
    private Replay replay;
    private float currentTick;
    private boolean replayItemsDropped;
    private final List<ItemStack> runtimeInventory = new ArrayList<>();
    private final Set<UUID> pickedUpEntityIds = new HashSet<>();

    private Map<EquipmentSlot, ItemStack> equipment = new HashMap<>();

    public ActorEntity(EntityType<? extends LivingEntity> entityType, World world)
    {
        super(entityType, world);
    }

    public void setReplay(String filmId, String replayId)
    {
        this.filmId = filmId;
        this.replayId = replayId;
    }

    public void setReplay(Replay replay)
    {
        this.replay = replay;
    }

    public void setCurrentTick(float currentTick)
    {
        this.currentTick = currentTick;
    }

    public Replay findReplay()
    {
        if (this.replay != null)
        {
            return this.replay;
        }

        if (this.filmId.isEmpty() || this.replayId.isEmpty())
        {
            return null;
        }

        ActionPlayer player = BBSMod.getActions().getPlayer(this.filmId);

        if (player != null && player.getFilm() != null)
        {
            this.replay = (Replay) player.getFilm().replays.get(this.replayId);
            this.currentTick = player.getTick();
        }

        return this.replay;
    }

    public String getFilmId()
    {
        return this.filmId;
    }

    public String getReplayId()
    {
        return this.replayId;
    }

    /* Not getEntity(): LivingEntity itself declares one in 1.21.11, returning a LivingEntity. */
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

        /* The body changed, so the box around it has to change too */
        this.calculateDimensions();
    }

    /**
     * The form's own hitbox, when it declares one. An actor exists so blows land on it, and a box
     * of vanilla's player size around a four-block model means only its ankles can be hit - the
     * flag promised a body in the world and delivered a shin. Same properties the picking box in
     * the editor already reads, so the two agree.
     */
    @Override
    protected EntityDimensions getBaseDimensions(EntityPose pose)
    {
        if (this.form == null || !this.form.hitbox.get())
        {
            return super.getBaseDimensions(pose);
        }

        float width = this.form.hitboxWidth.get();
        float height = this.form.hitboxHeight.get();

        if (pose == EntityPose.CROUCHING)
        {
            height *= this.form.hitboxSneakMultiplier.get();
        }

        /* Since 1.21.1 the eye height rides the dimensions instead of an override of its own */
        return EntityDimensions.changing(width, height).withEyeHeight(this.form.hitboxEyeHeight.get());
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

        if (this.getEntityWorld().isClient())
        {
            return;
        }

        if (this.isDead() || !this.pickUpItems)
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

                if (!entity.isRemoved() && !itemEntity.cannotPickup() && !this.pickedUpEntityIds.contains(entityId))
                {
                    this.pickedUpEntityIds.add(entityId);
                    this.runtimeInventory.add(itemEntity.getStack().copy());

                    ((ServerWorld) this.getEntityWorld()).getChunkManager().sendToOtherNearbyPlayers(entity, new ItemPickupAnimationS2CPacket(entity.getId(), this.getId(), itemEntity.getStack().getCount()));

                    entity.discard();
                }
            }
        }
    }

    public void setPickUpItems(boolean pickUpItems)
    {
        this.pickUpItems = pickUpItems;
    }

    /** Put back everything this body swept up, where it now stands. */
    public void dropPickedUp()
    {
        if (this.replayItemsDropped || this.runtimeInventory.isEmpty() || this.getEntityWorld().isClient())
        {
            return;
        }

        for (ItemStack stack : this.runtimeInventory)
        {
            ItemEntity item = new ItemEntity(this.getEntityWorld(), this.getX(), this.getY() + 0.5D, this.getZ(), stack);

            item.setToDefaultPickupDelay();
            this.getEntityWorld().spawnEntity(item);
        }

        this.runtimeInventory.clear();
        this.pickedUpEntityIds.clear();
    }

    @Override
    public void onDeath(DamageSource damageSource)
    {
        // Fix: fijar velocidad para evitar saltos aleatorios durante animacion de muerte
        this.setVelocity(0D, Math.min(0D, this.getVelocity().y), 0D);

        super.onDeath(damageSource);

        this.findReplay(); // Asegurar que this.replay y this.currentTick esten actualizados

        boolean dropEnabled = this.replay != null && this.replay.dropItemsOnDeath.get();

        if (!this.getEntityWorld().isClient() && !this.replayItemsDropped && dropEnabled)
        {
            this.dropReplayItems();
            this.replayItemsDropped = true;
        }
    }

    @Override
    public void takeKnockback(double strength, double x, double z)
    {
        // Actores de replay son dirigidos por keyframes; ignorar knockback de daño
        if (this.replay != null || this.findReplay() != null)
        {
            return;
        }

        super.takeKnockback(strength, x, z);
    }

    private void dropReplayItems()
    {
        List<ItemStack> toDrop = new ArrayList<>();
        List<ItemStack> matchedPool = new ArrayList<>();

        // 1. Items recogidos en runtime
        for (ItemStack stack : this.runtimeInventory)
        {
            if (stack != null && !stack.isEmpty())
            {
                ItemStack copy = stack.copy();
                toDrop.add(copy);
                matchedPool.add(copy.copy());
            }
        }

        // 2. Items de los keyframes al tick actual
        List<ItemStack> keyframeItems = new ArrayList<>();

        if (this.replay != null && this.replay.keyframes != null)
        {
            float tick = this.currentTick;

            for (int i = 0; i < this.replay.keyframes.hotbar.size(); i++)
            {
                ItemStack s = this.replay.keyframes.hotbar.get(i).interpolate(tick, ItemStack.EMPTY);
                if (!s.isEmpty()) keyframeItems.add(s.copy());
            }

            ItemStack offHand = this.replay.keyframes.offHand.interpolate(tick, ItemStack.EMPTY);
            if (!offHand.isEmpty()) keyframeItems.add(offHand.copy());

            ItemStack head = this.replay.keyframes.armorHead.interpolate(tick, ItemStack.EMPTY);
            if (!head.isEmpty()) keyframeItems.add(head.copy());
            ItemStack chest = this.replay.keyframes.armorChest.interpolate(tick, ItemStack.EMPTY);
            if (!chest.isEmpty()) keyframeItems.add(chest.copy());
            ItemStack legs = this.replay.keyframes.armorLegs.interpolate(tick, ItemStack.EMPTY);
            if (!legs.isEmpty()) keyframeItems.add(legs.copy());
            ItemStack feet = this.replay.keyframes.armorFeet.interpolate(tick, ItemStack.EMPTY);
            if (!feet.isEmpty()) keyframeItems.add(feet.copy());
        }

        // Fallback: usar equipment slots si no hay keyframes
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

        // 3. Reconciliacion: no dropear duplicados
        for (ItemStack kStack : keyframeItems)
        {
            if (kStack.isEmpty()) continue;

            int needed = kStack.getCount();

            for (ItemStack matched : matchedPool)
            {
                if (!matched.isEmpty() && matched.isOf(kStack.getItem()) && ItemStack.areEqual(matched, kStack))
                {
                    int take = Math.min(matched.getCount(), needed);
                    matched.decrement(take);
                    needed -= take;
                    if (needed <= 0) break;
                }
            }

            if (needed > 0)
            {
                ItemStack add = kStack.copy();
                add.setCount(needed);
                toDrop.add(add);
            }
        }

        // 4. Spawnear drops
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
        if (stack.isEmpty()) return;

        ItemEntity itemEntity = new ItemEntity(
            this.getEntityWorld(),
            this.getX(), this.getY() + 0.5D, this.getZ(),
            stack
        );

        float minX = this.replay != null ? this.replay.dropVelocityMinX.get() : -0.1F;
        float maxX = this.replay != null ? this.replay.dropVelocityMaxX.get() : 0.1F;
        float minY = this.replay != null ? this.replay.dropVelocityMinY.get() : 0.1F;
        float maxY = this.replay != null ? this.replay.dropVelocityMaxY.get() : 0.25F;
        float minZ = this.replay != null ? this.replay.dropVelocityMinZ.get() : -0.1F;
        float maxZ = this.replay != null ? this.replay.dropVelocityMaxZ.get() : 0.1F;

        itemEntity.setVelocity(
            minX + this.random.nextDouble() * (maxX - minX),
            minY + this.random.nextDouble() * (maxY - minY),
            minZ + this.random.nextDouble() * (maxZ - minZ)
        );
        itemEntity.setToDefaultPickupDelay();

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

        /* Who this body belongs to, told to whoever just came within sight of it. The cast map is
         * broadcast when the actors spawn and never again, so a player who joined, changed
         * dimension or simply walked over later had no way of pairing this entity with its replay. */
        if (!this.replayId.isEmpty())
        {
            ServerNetwork.sendActor(player, this.filmId, this.replayId, this.getId());
        }
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