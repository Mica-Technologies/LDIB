package com.micatechnologies.minecraft.ldib.entity;

/**
 * How a rider sits on a rideable — the "seated vs standing" seam from the master plan's "variants are
 * data" design. A bicycle seats its rider; a Bird/Segway-style scooter has them <b>stand</b> on the
 * deck. This is pure entity-layer data (no Minecraft rendering types): {@link EntityBike} reads it to
 * decide {@code shouldRiderSit()} and where to place the passenger, so a new standing rideable is a
 * new {@link BikeVariant} pointing at {@link #STANDING}, not a new entity class.
 */
public enum RiderPose {

    // SEATED and STANDING are owner-verified in-game (2026-07-25) across all four bike/scooter
    // variants — the standing one started as a guess and rode correctly on both scooters, so treat
    // those two as settled rather than placeholder numbers. BOARD is derived from STANDING by the
    // measured difference in deck height (see its docs), so it inherits that verification rather than
    // being a fresh guess — but the 90° body turn is a look, and looks want an in-game eye.

    /** Seated on a saddle (bicycle, e-bike). */
    SEATED(0.45D, true, 0.0F),

    /** Standing upright on a deck (kick/scooter). Lower mount point; the rider is not seated. */
    STANDING(0.2D, false, 0.0F),

    /**
     * Standing <b>across</b> the board, surf/skate stance — the one-wheel. Feet sit fore and aft of
     * the wheel with the body turned 90° out of the direction of travel; only the head still looks
     * down the road.
     *
     * <p>The mount height is derived from {@link #STANDING} rather than guessed: the scooter's deck
     * sits at model {@code y = 4} and the one-wheel's foot pads at {@code y = 1.5} (see {@code
     * ModelOnewheel}), so the pads are 2.5 px — {@code 2.5/16} of a block — higher, and the offset the
     * owner already verified on the scooter is raised by exactly that.</p>
     */
    BOARD(0.2D + 2.5D / 16.0D, false, 90.0F);

    private final double mountOffset;
    private final boolean seated;
    private final float bodyYawOffset;

    RiderPose(double mountOffset, boolean seated, float bodyYawOffset) {
        this.mountOffset = mountOffset;
        this.seated = seated;
        this.bodyYawOffset = bodyYawOffset;
    }

    /**
     * Degrees to turn the rider's <b>body</b> away from the rideable's heading, and nothing else.
     *
     * <p>{@link EntityBike#updatePassenger} feeds this to {@code setRenderYawOffset}, which sets the
     * body's render yaw only — the head is drawn from the rider's own yaw <i>relative</i> to that, so a
     * turned body leaves the rider still looking wherever they are actually looking, which on a moving
     * board is down the road. That split is the whole trick: nothing about where the rider may look
     * changes, and {@link EntityBike#MAX_LOOK_YAW} still measures from the heading, not from the body.
     *
     * <p>{@code +90} is a regular (left-foot-forward) stance: the body faces 90° clockwise of travel,
     * which puts the rider's left side forward. Zero on every variant you face forward on.</p>
     */
    public float bodyYawOffset() {
        return bodyYawOffset;
    }

    /** Vertical offset (blocks) from the entity origin to the rider's mount point. */
    public double mountOffset() {
        return mountOffset;
    }

    /** Whether the rider renders in the vanilla sitting pose. */
    public boolean seated() {
        return seated;
    }
}
