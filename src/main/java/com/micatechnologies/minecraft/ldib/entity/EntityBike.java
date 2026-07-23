package com.micatechnologies.minecraft.ldib.entity;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.physics.BikePhysics;
import com.micatechnologies.minecraft.ldib.physics.BikeState;
import com.micatechnologies.minecraft.ldib.physics.BikeTuning;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import com.micatechnologies.minecraft.ldib.item.LdibItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * A rider-controlled bike.
 *
 * <p><b>This is the deliberate inverse of RCMC's coaster car.</b> A coaster rider is a passenger
 * with no control, so that entity does <i>not</i> override {@code getControllingPassenger()} (which
 * also exempts it from the server's "moved too quickly" kick). A bike rider <i>drives</i>, so this
 * entity <i>does</i> return its rider as the controlling passenger — which is what makes the vanilla
 * client send {@code CPacketVehicleMove} and lets WASD reach the vehicle. Because a bike tops out
 * around 7 blocks/s (~0.35 blocks/tick), it stays comfortably under that kick's threshold; a faster
 * variant would need the mitigation documented in the master plan's platform-constraints appendix.</p>
 *
 * <p>Movement is delegated to the pure-Java {@link BikePhysics} model: this class converts rider
 * input into {@code (throttle, steer)}, steps the model (sub-stepped for smooth steering), and turns
 * the resulting {@code (speed, heading)} into {@code motion} at the tick boundary. Everything
 * Minecraft-specific — mounting, seating, collision, gravity — lives here; none of it leaks into the
 * physics package. Rendering is entirely client-side (see {@code RenderBike}); a dedicated server
 * loads and ticks this class with no client types on the path.</p>
 */
public class EntityBike extends Entity {

    /** Which rideable this is (bicycle, e-bike, …). Synced so every client renders/handles it right. */
    private static final DataParameter<Integer> VARIANT =
        EntityDataManager.createKey(EntityBike.class, DataSerializers.VARINT);

    /** Forward ground speed in blocks/second — the one state variable the physics model owns. */
    private double bikeSpeed;

    public EntityBike(World world) {
        super(world);
        setSize(0.8F, 1.0F);
        this.preventEntitySpawning = true;
    }

    public EntityBike(World world, BikeVariant variant) {
        this(world);
        // entityInit() has already run inside super(world), so the key is registered by now.
        this.dataManager.set(VARIANT, variant.id());
    }

    @Override
    protected void entityInit() {
        this.dataManager.register(VARIANT, BikeVariant.BICYCLE.id());
    }

    /** This bike's variant — drives both its handling ({@link BikeVariant#tuning()}) and its look. */
    public BikeVariant variant() {
        return BikeVariant.byId(this.dataManager.get(VARIANT));
    }

    // --- Riding contract ---------------------------------------------------------------------

    @Override
    public boolean canBeRidden(Entity entity) {
        return true;
    }

    @Override
    public Entity getControllingPassenger() {
        return getPassengers().isEmpty() ? null : getPassengers().get(0);
    }

    @Override
    public boolean processInitialInteract(EntityPlayer player, EnumHand hand) {
        // Sneak-right-click picks the bike back up into item form — the only way to reclaim a bike
        // once it has been placed in the world. Needed so a bike that was placed and ridden can be
        // put back in a rack, handed off, or stored. Only if nobody is currently riding it.
        if (player.isSneaking()) {
            if (!this.world.isRemote && !this.isBeingRidden()) {
                giveAsItem(player);
                this.setDead();
            }
            return true;
        }
        if (!this.world.isRemote && !this.isBeingRidden()) {
            player.startRiding(this);
        }
        return true;
    }

    /** Hand this bike to {@code player} as an item (inventory if room, else dropped at their feet). */
    public void giveAsItem(EntityPlayer player) {
        ItemStack stack = new ItemStack(LdibItems.forVariant(variant()));
        if (!player.inventory.addItemStackToInventory(stack)) {
            player.dropItem(stack, false);
        }
    }

    @Override
    public double getMountedYOffset() {
        return 0.45D;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    public void updatePassenger(Entity passenger) {
        if (this.isPassenger(passenger)) {
            passenger.setPosition(this.posX,
                this.posY + this.getMountedYOffset() + passenger.getYOffset(), this.posZ);
            // Face the rider the way the bike is pointing so first-person view tracks steering.
            passenger.rotationYaw = this.rotationYaw;
            passenger.setRotationYawHead(this.rotationYaw);
        }
    }

    // --- Collision ---------------------------------------------------------------------------

    @Override
    public boolean canBeCollidedWith() {
        return !this.isDead;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox() {
        // Non-solid: riders and mobs pass through rather than being bulldozed. It is still
        // right-clickable to mount because canBeCollidedWith() is true.
        return null;
    }

    // --- Simulation --------------------------------------------------------------------------

    @Override
    public void onUpdate() {
        super.onUpdate();

        double throttle = 0.0D;
        double steer = 0.0D;
        Entity controller = getControllingPassenger();
        if (controller instanceof EntityPlayer) {
            EntityPlayer rider = (EntityPlayer) controller;
            // moveForward: +1 W (pedal), -1 S (brake). moveStrafing: +1 A (left), -1 D (right).
            // A left turn decreases yaw, and BikePhysics adds steer to heading, so negate strafing.
            throttle = MathHelper.clamp(rider.moveForward, -1.0F, 1.0F);
            steer = -MathHelper.clamp(rider.moveStrafing, -1.0F, 1.0F);
        }

        int subSteps = Math.max(1, LdibConfig.physicsSubSteps);
        double dt = LdibConstants.SECONDS_PER_TICK / subSteps;
        BikeTuning tuning = variant().tuning();
        BikeState state = new BikeState(this.bikeSpeed, this.rotationYaw);
        for (int i = 0; i < subSteps; i++) {
            state = BikePhysics.step(state, throttle, steer, tuning, dt);
        }
        this.bikeSpeed = state.speed;
        this.rotationYaw = (float) state.headingDegrees;

        // Turn (speed, heading) into this tick's horizontal motion. Minecraft forward for a yaw is
        // (-sin yaw, cos yaw).
        double perTick = this.bikeSpeed * LdibConstants.SECONDS_PER_TICK;
        double yawRad = Math.toRadians(this.rotationYaw);
        this.motionX = -Math.sin(yawRad) * perTick;
        this.motionZ = Math.cos(yawRad) * perTick;

        // Gravity so the bike settles onto and follows terrain; move() zeroes it on the ground.
        if (!this.onGround) {
            this.motionY -= 0.08D;
        }

        this.move(MoverType.SELF, this.motionX, this.motionY, this.motionZ);

        if (this.onGround) {
            this.motionY = 0.0D;
        }
        // Ran into a wall: bleed off speed rather than grinding along it at full pedal.
        if (this.collidedHorizontally) {
            this.bikeSpeed *= 0.5D;
        }

        // Keep any riders seated and any nearby entities from clipping through.
        this.setRotation(this.rotationYaw, this.rotationPitch);
    }

    @Override
    public boolean shouldDismountInWater(Entity rider) {
        return false;
    }

    // --- Persistence -------------------------------------------------------------------------

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("Variant", variant().id());
        compound.setFloat("Yaw", this.rotationYaw);
        compound.setDouble("Speed", this.bikeSpeed);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.dataManager.set(VARIANT, compound.getInteger("Variant"));
        this.rotationYaw = compound.getFloat("Yaw");
        this.bikeSpeed = compound.getDouble("Speed");
    }

    /** Current forward speed in blocks/second — read by the renderer for wheel spin. */
    public double speed() {
        return this.bikeSpeed;
    }
}
