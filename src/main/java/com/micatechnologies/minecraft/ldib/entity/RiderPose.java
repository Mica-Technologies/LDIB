package com.micatechnologies.minecraft.ldib.entity;

/**
 * How a rider sits on a rideable — the "seated vs standing" seam from the master plan's "variants are
 * data" design. A bicycle seats its rider; a Bird/Segway-style scooter has them <b>stand</b> on the
 * deck. This is pure entity-layer data (no Minecraft rendering types): {@link EntityBike} reads it to
 * decide {@code shouldRiderSit()} and where to place the passenger, so a new standing rideable is a
 * new {@link BikeVariant} pointing at {@link #STANDING}, not a new entity class.
 */
public enum RiderPose {

    /** Seated on a saddle (bicycle, e-bike). */
    SEATED(0.45D, true),

    /** Standing upright on a deck (kick/scooter). Lower mount point; the rider is not seated. */
    STANDING(0.2D, false);

    private final double mountOffset;
    private final boolean seated;

    RiderPose(double mountOffset, boolean seated) {
        this.mountOffset = mountOffset;
        this.seated = seated;
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
