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

    /** Whether the rider is braking — synced so every observer's client can light the brake light. */
    private static final DataParameter<Boolean> BRAKING =
        EntityDataManager.createKey(EntityBike.class, DataSerializers.BOOLEAN);

    /**
     * Whether this is a <b>public bike-share</b> bike (fleet livery, docks only) rather than a
     * personal one (racks only). Synced so every client picks the right skin and so the dock/rack
     * gating agrees. Set when a dock dispenses or is stocked with a bike; personal bikes stay false.
     */
    private static final DataParameter<Boolean> SHARE =
        EntityDataManager.createKey(EntityBike.class, DataSerializers.BOOLEAN);

    /** Forward ground speed in blocks/second — the one state variable the physics model owns. */
    private double bikeSpeed;

    /**
     * Radians a wheel turns per block of ground rolled, for pure rolling (no slip): {@code angle =
     * distance / radius}. The modeled wheel radius is ~0.4 blocks (see {@code ModelRideable}), so
     * {@code 1 / 0.4 = 2.5} rad/block.
     */
    private static final float WHEEL_RADIANS_PER_BLOCK = 2.5F;

    /** Cosmetic wheel-spin angle, in radians — accumulated each tick, not derived from v*t, so it
     *  never snaps when speed changes mid-turn. Purely presentational; read by {@code RenderBike}. */
    private float wheelRotation;
    private float prevWheelRotation;

    /** Degrees of cosmetic lean per degree/tick of heading change, capped by {@link #MAX_LEAN_DEG}. */
    private static final float LEAN_PER_YAW_RATE = 3.0F;
    private static final float MAX_LEAN_DEG = 22.0F;

    /** How much of the way from current lean to the target lean to close each tick (exponential ease). */
    private static final float LEAN_SMOOTHING = 0.35F;

    /** Cosmetic lean-into-the-turn angle, in degrees — eased toward its target rather than stepping
     *  at tick boundaries. Purely presentational; read by {@code RenderBike}. */
    private float bikeLean;
    private float prevBikeLean;

    public EntityBike(World world) {
        super(world);
        setSize(0.8F, 1.0F);
        this.preventEntitySpawning = true;
    }

    public EntityBike(World world, BikeVariant variant) {
        this(world, variant, false);
    }

    public EntityBike(World world, BikeVariant variant, boolean share) {
        this(world);
        // entityInit() has already run inside super(world), so the keys are registered by now.
        this.dataManager.set(VARIANT, variant.id());
        this.dataManager.set(SHARE, share);
    }

    @Override
    protected void entityInit() {
        this.dataManager.register(VARIANT, BikeVariant.BICYCLE.id());
        this.dataManager.register(BRAKING, false);
        this.dataManager.register(SHARE, false);
    }

    /** This bike's variant — drives both its handling ({@link BikeVariant#tuning()}) and its look. */
    public BikeVariant variant() {
        return BikeVariant.byId(this.dataManager.get(VARIANT));
    }

    /** Whether this is a public bike-share (fleet) bike — docks only — vs a personal one (racks only). */
    public boolean isShare() {
        return this.dataManager.get(SHARE);
    }

    /** The skin to draw for this bike: the muted fleet livery when it's a share bike, else the variant's. */
    public net.minecraft.util.ResourceLocation texture() {
        return isShare() ? variant().shareTexture() : variant().texture();
    }

    /** Whether the rider is currently braking — read by the renderer to light the brake light. */
    public boolean isBraking() {
        return this.dataManager.get(BRAKING);
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
        if (this.world.isRemote) {
            return true;
        }
        // Sneak-right-click pockets the bike as an item — even the one you're riding: hop off first,
        // then pick it up, so it's one gesture to put your bike away.
        if (player.isSneaking()) {
            if (this.isPassenger(player)) {
                this.removePassengers();
            }
            if (!this.isBeingRidden()) {
                giveAsItem(player);
                this.setDead();
            }
            return true;
        }
        // If you're riding and the click landed on your own bike while you were looking at a rack or
        // dock, park there — so "ride up and right-click" works whether the ray hit the block or the
        // bike (right-clicking the rack/dock block itself is handled by the block too).
        if (this.isPassenger(player)) {
            tryParkAtLookedAt(player);
            return true;
        }
        if (!this.isBeingRidden()) {
            player.startRiding(this);
        }
        return true;
    }

    /** Attacking a parked bike pockets it as an item (like breaking a boat) — a discoverable pick-up. */
    @Override
    public boolean attackEntityFrom(net.minecraft.util.DamageSource source, float amount) {
        if (this.world.isRemote || this.isDead) {
            return false;
        }
        if (source.getTrueSource() instanceof EntityPlayer && !this.isBeingRidden()) {
            giveAsItem((EntityPlayer) source.getTrueSource());
            this.setDead();
            return true;
        }
        return false;
    }

    /** Park the ridden bike at the rack or dock the rider is looking at (within reach), if any. */
    private boolean tryParkAtLookedAt(EntityPlayer player) {
        net.minecraft.util.math.Vec3d eyes = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLook(1.0F);
        double reach = 4.5D;
        net.minecraft.util.math.RayTraceResult ray = this.world.rayTraceBlocks(
            eyes, eyes.add(look.x * reach, look.y * reach, look.z * reach));
        if (ray == null || ray.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
            return false;
        }
        net.minecraft.util.math.BlockPos hit = ray.getBlockPos();
        net.minecraft.block.Block block = this.world.getBlockState(hit).getBlock();
        if (block instanceof com.micatechnologies.minecraft.ldib.block.BlockBikeDock) {
            return ((com.micatechnologies.minecraft.ldib.block.BlockBikeDock) block)
                .tryDockRidden(this.world, hit, player);
        }
        if (block instanceof com.micatechnologies.minecraft.ldib.block.BlockBikeRack) {
            return ((com.micatechnologies.minecraft.ldib.block.BlockBikeRack) block)
                .tryLockRidden(this.world, hit, player);
        }
        return false;
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
        // Per-variant: a bike seats its rider high on the saddle; a scooter stands them on the deck.
        return variant().pose().mountOffset();
    }

    @Override
    public boolean shouldRiderSit() {
        return variant().pose().seated();
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

        // Brake light: the rider is braking when applying reverse throttle (S). Set server-side; the
        // synced flag lights the brake light on every observer's client.
        if (!this.world.isRemote) {
            this.dataManager.set(BRAKING, throttle < 0.0D);
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

        // Accumulate wheel spin from distance actually rolled this tick, rather than deriving it from
        // v*t — that way the angle never snaps when speed changes (e.g. braking mid-turn).
        this.prevWheelRotation = this.wheelRotation;
        this.wheelRotation +=
            (float) (this.bikeSpeed * LdibConstants.SECONDS_PER_TICK * WHEEL_RADIANS_PER_BLOCK);

        // Cosmetic lean into turns: ease toward a target derived from this tick's heading change,
        // rather than snapping straight to it, so the lean doesn't step at tick boundaries. Sign
        // verified in-game 2026-07-22: negate so the bike leans INTO the turn (A/left leans left,
        // D/right leans right).
        float yawRate = MathHelper.wrapDegrees(this.rotationYaw - this.prevRotationYaw);
        float leanTarget = MathHelper.clamp(-yawRate * LEAN_PER_YAW_RATE, -MAX_LEAN_DEG, MAX_LEAN_DEG);
        this.prevBikeLean = this.bikeLean;
        this.bikeLean += (leanTarget - this.bikeLean) * LEAN_SMOOTHING;

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
        compound.setBoolean("Share", isShare());
        compound.setFloat("Yaw", this.rotationYaw);
        compound.setDouble("Speed", this.bikeSpeed);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.dataManager.set(VARIANT, compound.getInteger("Variant"));
        this.dataManager.set(SHARE, compound.getBoolean("Share"));
        this.rotationYaw = compound.getFloat("Yaw");
        this.bikeSpeed = compound.getDouble("Speed");
    }

    /** Current forward speed in blocks/second — read by the renderer for wheel spin. */
    public double speed() {
        return this.bikeSpeed;
    }

    /** Interpolated cosmetic wheel-spin angle, in radians — read by the renderer each frame. */
    public float wheelRotation(float partialTicks) {
        return this.prevWheelRotation + (this.wheelRotation - this.prevWheelRotation) * partialTicks;
    }

    /** Interpolated cosmetic lean-into-the-turn angle, in degrees — read by the renderer each frame. */
    public float bikeLean(float partialTicks) {
        return this.prevBikeLean + (this.bikeLean - this.prevBikeLean) * partialTicks;
    }
}
