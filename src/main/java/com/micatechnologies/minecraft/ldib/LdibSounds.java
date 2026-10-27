package com.micatechnologies.minecraft.ldib;

import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.registries.IForgeRegistry;

/**
 * LDIB's sound events, registered on {@code RegistryEvent.Register<SoundEvent>} from {@link Ldib}. The
 * {@link SoundEvent}s themselves are common (server code names them); actually <i>playing</i> them —
 * the looping ride hum and the brake blip — is client-only and lives in
 * {@code com.micatechnologies.minecraft.ldib.client.sound}, reached through {@link LdibClientProxy}.
 *
 * <p>Each event's registry name maps to an entry in {@code assets/ldib/sounds.json}, which points at
 * the {@code .ogg} files under {@code assets/ldib/sounds/}.</p>
 */
public final class LdibSounds {

    public static SoundEvent RIDE;
    public static SoundEvent BRAKE;
    /** The handlebar bell. Played by the server ({@code PacketRingBell}) so everyone nearby hears it. */
    public static SoundEvent BELL;

    private LdibSounds() {
        throw new AssertionError("No instances.");
    }

    public static void register(IForgeRegistry<SoundEvent> registry) {
        RIDE = create("ride");
        BRAKE = create("brake");
        BELL = create("bell");
        registry.register(RIDE);
        registry.register(BRAKE);
        registry.register(BELL);
    }

    private static SoundEvent create(String name) {
        ResourceLocation id = new ResourceLocation(LdibConstants.MOD_NAMESPACE, name);
        return new SoundEvent(id).setRegistryName(id);
    }
}
