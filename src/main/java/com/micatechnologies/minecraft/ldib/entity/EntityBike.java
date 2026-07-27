package com.micatechnologies.minecraft.ldib.entity;

import com.micatechnologies.minecraft.ldib.LdibConfig;
import com.micatechnologies.minecraft.ldib.LdibConstants;
import com.micatechnologies.minecraft.ldib.physics.BatteryModel;
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
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

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

    /**
     * Battery charge, {@code 0}–{@code 1}, for the powered variants. Synced because it changes
     * <b>movement results</b> — a rider whose client thought the battery was full would predict a
     * faster bike than the server is simulating and rubber-band, exactly the failure the config sync
     * exists to prevent. Always {@code 1} on a pedal bicycle, which has no battery to run down.
     */
    private static final DataParameter<Float> CHARGE =
        EntityDataManager.createKey(EntityBike.class, DataSerializers.FLOAT);

    /**
     * Ground speed in blocks/second, synced so a client watching <b>someone else</b> ride can carry
     * the bike forward between position updates instead of waiting to be told where it went.
     *
     * <p>This is the fix for the worst thing about watching another player ride. An observing client
     * runs {@link #onUpdate()} for that bike like any other, but the rider's {@code moveForward} /
     * {@code moveStrafing} are only ever populated on the riding client — so the handling model ran
     * with no input, the bike sat perfectly still for three ticks, and then the tracker teleported it a
     * whole block. Six times a second. Nothing downstream could smooth that, because the bike genuinely
     * was not moving between the jumps.</p>
     *
     * <p>With the speed in hand an observer can integrate {@code (speed, heading)} exactly as the
     * server does and only ever needs the tracker to correct the drift, which is small and can be eased
     * in ({@link #applyServerCorrection()}). It earns its bandwidth twice over: the wheel spin, the
     * lean and the riding sound all read {@link #speed()}, and all three were reading zero on every
     * screen but the rider's.</p>
     */
    private static final DataParameter<Float> SPEED =
        EntityDataManager.createKey(EntityBike.class, DataSerializers.FLOAT);

    /** Forward ground speed in blocks/second — the one state variable the physics model owns. */
    private double bikeSpeed;

    /**
     * The server's running charge, at full precision. {@link #CHARGE} carries a deliberately coarser
     * copy: a ridden bike drains a little every tick, and syncing every one of those would be a
     * data-watcher packet per bike per tick for a number nobody can see move that finely.
     */
    private double chargeExact = BatteryModel.FULL;

    /** How far {@link #chargeExact} must drift from the synced value before it is worth a packet. */
    private static final double CHARGE_SYNC_STEP = 0.005D;

    /**
     * How far {@link #bikeSpeed} must drift from the synced {@link #SPEED} before it is worth a packet.
     *
     * <p>Not free, and worth understanding why: a dirty data manager makes {@code EntityTrackerEntry}
     * send that entity's update <b>every tick</b> rather than on its usual interval. Coarse enough that
     * a bike at a steady cruise goes quiet (and a parked one never speaks at all), while accelerating
     * hard costs a few extra updates — which is exactly when the extra position accuracy is worth
     * having anyway. 0.25 b/s of speed error is 0.0125 blocks/tick of drift, and the correction takes
     * it back out long before it is visible.</p>
     */
    private static final float SPEED_SYNC_STEP = 0.25F;

    /**
     * How many ticks a client spends easing out the difference between where it thinks a
     * server-authoritative bike is and where the server says it is. Matched to the tracker interval
     * ({@code Ldib.registerEntities}) so one correction finishes just as the next update lands — the
     * error is always being spread over exactly the window it accumulated in, with no overlap to
     * fight and no idle gap to sit visibly wrong in.
     */
    public static final int TRACKER_UPDATE_INTERVAL = 3;

    /**
     * Error (blocks) large enough that easing it out would look worse than admitting it: past this the
     * client snaps. A real teleport, a chunk reload, or a bike shoved by something the client never
     * simulated all land here, and gliding a bike smoothly across four blocks of world would be a
     * strictly stranger thing to watch than a cut.
     */
    private static final double CORRECTION_SNAP_DISTANCE = 4.0D;

    /** Position error still to be eased out on a client, and how many ticks are left to do it in. */
    private double correctionX;
    private double correctionY;
    private double correctionZ;
    private float correctionYaw;
    private int correctionTicks;

    /** Max horizontal distance (blocks) any single {@link #move} sub-step covers; the per-tick move is
     *  split into ceil(perTick / this) small steps for accurate collision at speed. 0.25 = 1-2 steps at
     *  current top speeds, more only if a much faster variant is ever added. */
    private static final double MAX_MOVE_STEP = 0.25D;

    /**
     * Speed below which a riderless bike counts as parked and skips the handling model. Shared with
     * {@code BikePhysics}, which uses the same threshold to decide a bike is too slow to steer, so the
     * two cannot drift apart on what "stopped" means.
     */
    private static final double IDLE_SPEED = BikePhysics.MIN_ROLLING_SPEED;

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

    /** Degrees the front wheel/handlebars turn per degree/tick of heading change, capped by
     *  {@link #MAX_STEER_DEG}. A touch stronger than the lean scale so the bars read as clearly
     *  cranked over — an owner-tunable starting point (the steer is bigger and cruder than a real
     *  bike's, which barely turns the bars at speed). */
    private static final float STEER_PER_YAW_RATE = 4.0F;
    private static final float MAX_STEER_DEG = 30.0F;

    /** Cosmetic front-assembly steer angle, in degrees — the fork/wheel/stem/bars/headlight turn by
     *  this much so a turn looks ridden. Eased toward its target like {@link #bikeLean}; purely
     *  presentational and never fed into physics; read by {@code RenderBike}. */
    private float bikeSteer;
    private float prevBikeSteer;

    /**
     * How far the rider may turn their view away from the way the bike is pointing, in degrees either
     * side. Enforced on <b>both</b> sides so a rider's head can never be reported somewhere anatomy
     * doesn't go; a shade under vanilla's own boat limit (105°), which is the closest thing to prior
     * art for "sitting on a thing while looking around".
     *
     * <p>A constant rather than a config value on purpose: the server clamps to it and the riding
     * client stops itself at it, so a client and server that disagreed would fight over the rider's
     * yaw. How firmly the view springs back to centre is the part riders actually want to tune, and
     * that ({@link LdibConfig#viewRecenterStrength}) is a client-only setting precisely because it
     * only ever moves a view <i>inside</i> this shared limit. Read by {@code client/RiderLook}.</p>
     */
    public static final float MAX_LOOK_YAW = 100.0F;

    /**
     * A rider who left the saddle last tick and whose landing spot still needs checking, or
     * {@code null}. Server-side only and one-shot: see {@link #rescueStuckDismount()} for why the
     * check cannot happen at the moment they dismount.
     */
    private Entity pendingDismount;

    /**
     * Candidate dismount spots in the bike's own frame, as {@code {alongForward, alongRight}} block
     * offsets, in the order they are tried. Sideways first — you step off a bike to the side, and it
     * is also the direction least likely to be blocked by whatever the bike is parked against — then
     * the diagonals, then straight back/front, then one ring wider. Mirrors the intent of vanilla's
     * own dismount search, which this only ever runs <i>after</i>, as a rescue.
     */
    private static final int[][] DISMOUNT_OFFSETS = {
        {0, 1}, {0, -1},
        {-1, 1}, {-1, -1}, {1, 1}, {1, -1},
        {-1, 0}, {1, 0},
        {0, 2}, {0, -2}, {-2, 0}, {2, 0},
    };

    /** Vertical offsets tried within each candidate column: step up one, level, then down a short drop. */
    private static final int[] DISMOUNT_HEIGHTS = {1, 0, -1, -2, -3};

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
        this.dataManager.register(CHARGE, (float) BatteryModel.FULL);
        this.dataManager.register(SPEED, 0.0F);
    }

    /**
     * Battery charge, {@code 0} (flat) to {@code 1} (full); always {@code 1} on a variant with no
     * battery.
     *
     * <p>This is the <b>synced</b> value, and the handling model reads it on both sides on purpose.
     * The server's {@link #chargeExact} is finer, but if the two sides ran the physics off different
     * numbers the client's prediction would drift from the server's simulation — the same reason the
     * physics config is synced at all. Both sides agreeing on a slightly coarse charge beats each
     * being precisely right about a different one.</p>
     */
    public double charge() {
        return this.dataManager.get(CHARGE);
    }

    /** Set the charge (server-side; clamped), pushing it to clients immediately. */
    public void setCharge(double charge) {
        this.chargeExact = BatteryModel.clamp(charge);
        this.dataManager.set(CHARGE, (float) this.chargeExact);
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

    /**
     * Hand this bike to {@code player} as an item (inventory if room, else dropped at their feet).
     * The battery goes with it — pocketing a half-flat e-bike and putting it back down must not
     * quietly top it up, or the charge would mean nothing.
     */
    public void giveAsItem(EntityPlayer player) {
        ItemStack stack = new ItemStack(LdibItems.forVariant(variant()));
        com.micatechnologies.minecraft.ldib.item.ItemBike.setCharge(stack, charge());
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

    /**
     * Seat the rider, square their body with the bike, and otherwise <b>leave their view alone</b>.
     *
     * <p>This used to assign {@code passenger.rotationYaw = this.rotationYaw} outright, which tracked
     * steering perfectly and made looking around impossible — the assignment ran every tick and ate
     * whatever the mouse had done since the last one. Nudging the yaw from here instead was no better:
     * this method runs on the 20 Hz tick and mouse look runs per frame, so anything done to a view here
     * either juddered or fought the mouse. Where the rider is looking is now owned entirely by
     * {@code client/RiderLook}, on the frame, where the input is.</p>
     *
     * <p>What stays is the part that genuinely belongs to the bike: {@code setRenderYawOffset} keeps
     * the rider's <i>body</i> square with it, so looking over your shoulder turns your head rather than
     * swivelling you out of the saddle. And the server — which has no frames and no camera — clamps the
     * reported yaw, so that limit is enforced by the authority rather than trusted to a client.</p>
     *
     * <p>Nothing here feeds back into movement: {@link #onUpdate()} steers from {@code moveStrafing},
     * never from where the rider happens to be looking.</p>
     */
    @Override
    public void updatePassenger(Entity passenger) {
        if (!this.isPassenger(passenger)) {
            return;
        }
        passenger.setPosition(this.posX,
            this.posY + this.getMountedYOffset() + passenger.getYOffset(), this.posZ);
        passenger.setRenderYawOffset(this.rotationYaw);
        if (!this.world.isRemote) {
            clampRiderView(passenger);
        }
    }

    /**
     * Hold the rider's reported view within {@link #MAX_LOOK_YAW} of the heading. <b>Server-side
     * only.</b>
     *
     * <p>The riding client already keeps itself inside this limit per frame, so on a well-behaved
     * client this never moves anything; it is here so a client that does not cannot report a head
     * screwed all the way round. Deliberately <i>not</i> run on observing clients either — a remote
     * rider's yaw arrives already clamped by the server, and re-clamping it against a bike heading
     * that may be a tick behind would only jitter their head for no gain.</p>
     */
    private void clampRiderView(Entity rider) {
        float offset = MathHelper.wrapDegrees(rider.rotationYaw - this.rotationYaw);
        float correction = MathHelper.clamp(offset, -MAX_LOOK_YAW, MAX_LOOK_YAW) - offset;
        if (correction != 0.0F) {
            rider.prevRotationYaw += correction;
            rider.rotationYaw += correction;
            rider.setRotationYawHead(rider.rotationYaw);
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

    /**
     * The handling to run this tick: the variant's tuning, scaled back toward its unpowered self by
     * whatever assist the battery can still deliver. A variant with no battery — or one whose range is
     * configured to 0, disabling the whole mechanic — gets its tuning untouched.
     */
    private BikeTuning assistedTuning() {
        BikeVariant variant = variant();
        BikeTuning powered = variant.tuning();
        if (!variant.hasBattery() || variant.rangeBlocks() <= 0.0D) {
            return powered;
        }
        double assist = BatteryModel.assist(charge(), LdibConfig.batteryReserveFraction);
        return powered.withAssist(variant.unpoweredTuning(), assist);
    }

    /**
     * Spend charge for {@code distanceBlocks} covered under power, syncing only once the change is
     * big enough to be worth a packet (or the battery has just gone flat, which riders should see the
     * instant it happens because the bike is about to feel different).
     */
    private void drainBattery(double distanceBlocks) {
        this.chargeExact = BatteryModel.drain(this.chargeExact, distanceBlocks, variant().rangeBlocks());
        if (Math.abs(this.chargeExact - this.dataManager.get(CHARGE)) >= CHARGE_SYNC_STEP
            || this.chargeExact <= BatteryModel.EMPTY) {
            this.dataManager.set(CHARGE, (float) this.chargeExact);
        }
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        // Someone got off last tick: vanilla has now chosen their spot, so it can be vetted.
        if (this.pendingDismount != null) {
            rescueStuckDismount();
        }

        // Who owns this bike's movement this tick?
        //
        // The server always does its own simulating, and so does the client of whoever is riding —
        // that client is authoritative over its own bike via CPacketVehicleMove, and predicting is the
        // only way its own ride feels immediate. Every OTHER client is a spectator: it has no input to
        // simulate from (moveForward/moveStrafing are populated only on the riding client), so it
        // follows the server instead, integrating the synced speed and easing out the difference.
        //
        // canPassengerSteer() is vanilla's own "is this the local player's vehicle" test, and it is
        // false on a dedicated server for every bike — hence pairing it with the isRemote check rather
        // than using it alone.
        boolean simulate = !this.world.isRemote || canPassengerSteer();

        double throttle = 0.0D;
        double steer = 0.0D;
        Entity controller = getControllingPassenger();
        if (simulate && controller instanceof EntityPlayer) {
            EntityPlayer rider = (EntityPlayer) controller;
            // moveForward: +1 W (pedal), -1 S (brake, then back up). moveStrafing: +1 A (left),
            // -1 D (right). A left turn decreases yaw, and BikePhysics adds steer to heading, so
            // negate strafing.
            throttle = MathHelper.clamp(rider.moveForward, -1.0F, 1.0F);
            steer = -MathHelper.clamp(rider.moveStrafing, -1.0F, 1.0F);
        }

        // Brake light: S is the brake only while there is forward motion to scrub off — once the bike
        // is stopped the same key is walking it backwards, and no bike lights up for that. Set
        // server-side; the synced flag lights the brake light on every observer's client.
        if (!this.world.isRemote) {
            this.dataManager.set(BRAKING, throttle < 0.0D && this.bikeSpeed > IDLE_SPEED);
        }

        // A parked bike — nobody aboard, already stopped — would step the model straight back to the
        // state it is already in: zero speed stays zero under drag, and BikePhysics leaves the heading
        // untouched below its own speed threshold. Skipping it is therefore behaviourally identical,
        // and it spares every idle bike in the world a per-tick BikeTuning allocation (two on a
        // powered variant, which also builds an unpowered baseline to blend against). Every bike
        // ticks, not just ridden ones, so that is the difference between a stocked share fleet costing
        // nothing and it costing a few hundred short-lived objects every tick. Gravity and the world
        // move below still run, so a bike whose ground is mined out still falls.
        if (!simulate) {
            // A spectator's copy: the server owns the speed, and the heading arrives with the position
            // updates. Running the handling model here would only invent a different answer from the
            // one about to be corrected in.
            this.bikeSpeed = this.dataManager.get(SPEED);
        } else if (controller != null || Math.abs(this.bikeSpeed) > IDLE_SPEED) {
            int subSteps = Math.max(1, LdibConfig.physicsSubSteps);
            double dt = LdibConstants.SECONDS_PER_TICK / subSteps;
            BikeTuning tuning = assistedTuning();
            BikeState state = new BikeState(this.bikeSpeed, this.rotationYaw);
            for (int i = 0; i < subSteps; i++) {
                state = BikePhysics.step(state, throttle, steer, tuning, dt);
            }
            this.bikeSpeed = state.speed;
            this.rotationYaw = (float) state.headingDegrees;
        }

        // Publish the speed for everyone else's dead reckoning, coarsely (see SPEED_SYNC_STEP) — but
        // never coarsely enough to leave a bike that has just stopped still reported as rolling, which
        // would have observers watching it glide on the spot.
        if (!this.world.isRemote) {
            float synced = this.dataManager.get(SPEED);
            boolean stopped = Math.abs(this.bikeSpeed) <= IDLE_SPEED;
            if (Math.abs(this.bikeSpeed - synced) >= SPEED_SYNC_STEP || (stopped && synced != 0.0F)) {
                this.dataManager.set(SPEED, stopped ? 0.0F : (float) this.bikeSpeed);
            }
        }

        // Spend battery for the distance just covered under power. Server-side only: the synced charge
        // is the truth, and a client running down its own copy would only race the server's. Backing up
        // is legwork on every variant, and it arrives here as a negative distance that
        // BatteryModel.drain ignores — so a reversing rider spends nothing, and cannot regenerate
        // either.
        if (!this.world.isRemote && throttle > 0.0D && variant().hasBattery()) {
            drainBattery(this.bikeSpeed * LdibConstants.SECONDS_PER_TICK);
        }

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

        // Cosmetic front-wheel steer: same eased-toward-target treatment and same sign convention as
        // the lean (negate the yaw rate) so the front assembly turns INTO the turn along with the
        // lean — A/left cranks the bars left, D/right cranks them right. Presentational only; the
        // physics heading is unchanged.
        // +yawRate (opposite sign to the lean): a Y-axis steer and a Z-axis lean have opposite
        // handedness under the renderer's scale(-1,-1,1), so the bars turn INTO the turn only with this
        // sign. Confirmed in-game (was steering the wrong way with the lean's sign).
        float steerTarget = MathHelper.clamp(yawRate * STEER_PER_YAW_RATE, -MAX_STEER_DEG, MAX_STEER_DEG);
        this.prevBikeSteer = this.bikeSteer;
        this.bikeSteer += (steerTarget - this.bikeSteer) * LEAN_SMOOTHING;

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

        // How high a kerb this bike rides up, refreshed from config each tick rather than set once in
        // the constructor: the physics config is pushed to clients on join (PacketSyncConfig), which
        // happens long after any bike already sitting in a loaded chunk was built, and a client whose
        // bikes stepped differently from the server's would desync exactly where the terrain is
        // interesting. It is a static field read, not a Configuration lookup — the per-tick cost is a
        // float store.
        this.stepHeight = (float) LdibConfig.stepHeight;

        // Sub-step the world move() when moving fast: several small move() calls this tick instead of
        // one big jump. At MVP/e-bike/scooter speeds this is 1-2 steps; it keeps collision accurate at
        // speed (no clipping past a wall corner) and keeps each step small — a defensive margin for the
        // server's per-packet "moved too quickly" check as faster variants arrive (master plan,
        // Appendix A.1). It does NOT change the net per-tick displacement, so the ride's speed is
        // unchanged. Distinct from physicsSubSteps, which sub-steps the handling model, not the move.
        double horizontal = Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        int moveSteps = Math.max(1, (int) Math.ceil(horizontal / MAX_MOVE_STEP));
        double stepX = this.motionX / moveSteps;
        double stepY = this.motionY / moveSteps;
        double stepZ = this.motionZ / moveSteps;
        boolean hitWall = false;
        for (int i = 0; i < moveSteps; i++) {
            this.move(MoverType.SELF, stepX, stepY, stepZ);
            if (this.collidedHorizontally) {
                hitWall = true; // stop at the wall rather than grinding the remaining sub-steps into it
                break;
            }
        }

        if (this.onGround) {
            this.motionY = 0.0D;
        }
        // Ran into a wall: bleed off speed rather than grinding along it at full pedal. Only where the
        // handling model is actually being run — a spectator's copy clipping a corner the server rode
        // cleanly past must not invent a slowdown the server never had.
        if (hitWall && simulate) {
            this.bikeSpeed *= 0.5D;
        }

        // Last: fold in a slice of whatever the server last said, on top of the motion above rather
        // than instead of it.
        applyServerCorrection();

        // Keep any riders seated and any nearby entities from clipping through.
        this.setRotation(this.rotationYaw, this.rotationPitch);
    }

    // --- Following someone else's ride --------------------------------------------------------

    /**
     * Take a position update from the entity tracker as an <b>error to work off</b> rather than a place
     * to be.
     *
     * <p>{@code Entity}'s own implementation of this is a bare {@code setPosition} + {@code
     * setRotation} — only {@code EntityLivingBase} and the vanilla vehicles override it to interpolate,
     * so a plain entity simply teleports on every update. Storing the difference instead lets
     * {@link #applyServerCorrection()} spread it across the ticks until the next one, so the bike is
     * always moving and never jumping.</p>
     *
     * <p>Client-only, exactly as the method it overrides: Forge strips it from a dedicated server, so
     * a server never carries a correction and never needs to.</p>
     */
    @SideOnly(Side.CLIENT)
    @Override
    public void setPositionAndRotationDirect(double x, double y, double z, float yaw, float pitch,
                                             int posRotationIncrements, boolean teleport) {
        double errorX = x - this.posX;
        double errorY = y - this.posY;
        double errorZ = z - this.posZ;
        boolean farOff = errorX * errorX + errorY * errorY + errorZ * errorZ
            > CORRECTION_SNAP_DISTANCE * CORRECTION_SNAP_DISTANCE;
        if (farOff) {
            // Applies even to the bike we are riding: whatever put it this far from where the server
            // has it, the prediction is no longer predicting the same bike, and quietly staying wrong
            // forever is the worse failure. Vanilla's own rubber-band for a rejected vehicle move
            // arrives by a different route (SPacketMoveVehicle), so this is purely the backstop.
            this.correctionTicks = 0;
            this.setPosition(x, y, z);
            this.setRotation(yaw, pitch);
            return;
        }
        // Otherwise our own bike is predicted locally, and its position is what the server is being
        // told rather than the other way round; letting the tracker drag it about would fight that.
        if (canPassengerSteer()) {
            return;
        }
        this.correctionX = errorX;
        this.correctionY = errorY;
        this.correctionZ = errorZ;
        this.correctionYaw = MathHelper.wrapDegrees(yaw - this.rotationYaw);
        // Trust the tracker's own cadence when it offers one — it is the number of ticks until the next
        // update, which is precisely the window this error should be spread over.
        this.correctionTicks = Math.max(1,
            posRotationIncrements > 0 ? posRotationIncrements : TRACKER_UPDATE_INTERVAL);
        this.rotationPitch = pitch;
    }

    /** Fold this tick's slice of the outstanding server correction into the bike's position. */
    private void applyServerCorrection() {
        if (this.correctionTicks <= 0) {
            return;
        }
        double sliceX = this.correctionX / this.correctionTicks;
        double sliceY = this.correctionY / this.correctionTicks;
        double sliceZ = this.correctionZ / this.correctionTicks;
        float sliceYaw = this.correctionYaw / this.correctionTicks;
        this.correctionX -= sliceX;
        this.correctionY -= sliceY;
        this.correctionZ -= sliceZ;
        this.correctionYaw -= sliceYaw;
        this.correctionTicks--;
        // setPosition rather than move(): the server has already decided this is a legal place to be,
        // and re-running collision on a correction is how a bike ends up wedged on the wrong side of
        // the wall it is being nudged back through.
        this.setPosition(this.posX + sliceX, this.posY + sliceY, this.posZ + sliceZ);
        this.rotationYaw += sliceYaw;
    }

    @Override
    public boolean shouldDismountInWater(Entity rider) {
        return false;
    }

    // --- Dismount placement ------------------------------------------------------------------

    /**
     * Note that this rider has just left the saddle, so their landing spot can be checked next tick.
     *
     * <p>The check cannot happen here. Vanilla runs the dismount in a fixed order:
     * {@code EntityLivingBase.dismountRidingEntity()} calls {@code super}, which is what invokes
     * <i>this</i> method, and only <b>afterwards</b> calls {@code dismountEntity(vehicle)} to choose
     * where the rider ends up. Anything positioned here is overwritten a few frames of execution
     * later, so the rescue has to wait until vanilla has had its turn.</p>
     */
    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.world.isRemote && passenger instanceof EntityPlayer) {
            this.pendingDismount = passenger;
        }
    }

    /**
     * Move a rider who vanilla dropped inside the world geometry to somewhere they can actually stand.
     *
     * <p>Vanilla's {@code EntityLivingBase.dismountEntity} searches nine spots around the vehicle and
     * places the rider at the first that is clear and has solid ground — which is good behaviour, and
     * is left alone whenever it works. What it does <i>not</i> do is check its own last resort: if
     * every candidate is blocked it falls through to the vehicle's own position with no collision test
     * at all, which is how you end up standing in a wall after parking a bike in a tight spot. A bike
     * is 1 block tall and its rider is 1.8, so it can be ridden into gaps that cannot be dismounted
     * into — this mod hits that fallback more readily than vanilla's own rideables do.</p>
     *
     * <p>So: only act when the rider is genuinely stuck, and then search a wider ring than vanilla
     * does. Server-side only — the resulting {@code setPositionAndUpdate} teleports the client.</p>
     */
    private void rescueStuckDismount() {
        Entity rider = this.pendingDismount;
        this.pendingDismount = null; // one shot, whatever the outcome
        if (rider == null || rider.isDead || rider.isRiding()) {
            return;
        }
        // Vanilla found somewhere legitimate — leave it be. Only its unchecked fallback is a problem.
        if (!this.world.collidesWithAnyBlock(rider.getEntityBoundingBox())) {
            return;
        }
        Vec3d spot = findDismountSpot(rider);
        if (spot != null) {
            rider.setPositionAndUpdate(spot.x, spot.y, spot.z);
        }
    }

    /**
     * The first spot around this bike where {@code rider} fits and has something to stand on, or
     * {@code null} if the bike is buried deeply enough that there is nowhere to put them.
     */
    private Vec3d findDismountSpot(Entity rider) {
        AxisAlignedBB riderBox = rider.getEntityBoundingBox();
        double halfWidth = (riderBox.maxX - riderBox.minX) / 2.0D;
        double height = riderBox.maxY - riderBox.minY;

        // The bike's own frame: forward is where it points, right is 90° clockwise of that.
        double yawRad = Math.toRadians(this.rotationYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        for (int[] offset : DISMOUNT_OFFSETS) {
            double x = this.posX + forwardX * offset[0] + rightX * offset[1];
            double z = this.posZ + forwardZ * offset[0] + rightZ * offset[1];
            for (int dy : DISMOUNT_HEIGHTS) {
                double y = Math.floor(this.posY) + dy;
                AxisAlignedBB candidate = new AxisAlignedBB(
                    x - halfWidth, y, z - halfWidth,
                    x + halfWidth, y + height, z + halfWidth);
                if (this.world.collidesWithAnyBlock(candidate)) {
                    continue;
                }
                if (hasFooting(x, y, z)) {
                    return new Vec3d(x, y, z);
                }
            }
        }
        return null;
    }

    /** Whether there is a solid top face (or liquid — better a swim than a fall) directly below. */
    private boolean hasFooting(double x, double y, double z) {
        BlockPos below = new BlockPos(x, y - 0.5D, z);
        net.minecraft.block.state.IBlockState state = this.world.getBlockState(below);
        return state.isSideSolid(this.world, below, EnumFacing.UP) || state.getMaterial().isLiquid();
    }

    // --- Persistence -------------------------------------------------------------------------

    @Override
    protected void writeEntityToNBT(NBTTagCompound compound) {
        compound.setInteger("Variant", variant().id());
        compound.setBoolean("Share", isShare());
        compound.setFloat("Yaw", this.rotationYaw);
        compound.setDouble("Speed", this.bikeSpeed);
        compound.setDouble("Charge", this.chargeExact);
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound compound) {
        this.dataManager.set(VARIANT, compound.getInteger("Variant"));
        this.dataManager.set(SHARE, compound.getBoolean("Share"));
        this.rotationYaw = compound.getFloat("Yaw");
        this.bikeSpeed = compound.getDouble("Speed");
        this.dataManager.set(SPEED, (float) this.bikeSpeed);
        // A bike saved before batteries existed has no tag; it comes back charged rather than flat.
        setCharge(compound.hasKey("Charge") ? compound.getDouble("Charge") : BatteryModel.FULL);
    }

    /**
     * Current ground speed in blocks/second, <b>signed</b> — negative while backing up. Read by the
     * renderer for wheel spin, by the HUD, and by the ride sound.
     *
     * <p>Meaningful on every client, not just the rider's: a bike being ridden by someone else gets
     * this from the synced {@link #SPEED} rather than from a local simulation it has no input for.</p>
     */
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

    /** Interpolated cosmetic front-assembly steer angle, in degrees — read by the renderer each frame
     *  to turn the fork/wheel/stem/bars/headlight into the turn. */
    public float steerAngle(float partialTicks) {
        return this.prevBikeSteer + (this.bikeSteer - this.prevBikeSteer) * partialTicks;
    }
}
