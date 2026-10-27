package com.micatechnologies.minecraft.ldib.client;

import com.micatechnologies.minecraft.ldib.network.LdibNetwork;
import com.micatechnologies.minecraft.ldib.entity.EntityBike;
import com.micatechnologies.minecraft.ldib.network.PacketGrabBike;
import com.micatechnologies.minecraft.ldib.network.PacketRingBell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

/**
 * The client-side "grab / dock" keybind (default <b>G</b>, rebindable in Controls). Pressing it asks
 * the server to do the context-sensitive grab/dock ({@link com.micatechnologies.minecraft.ldib.RideableActions})
 * — pick up a placed bike, pocket the one you're riding, or park it at a nearby rack/dock. Client-only,
 * installed from {@link com.micatechnologies.minecraft.ldib.LdibClientProxy}.
 */
public class LdibKeyHandler {

    public static final KeyBinding GRAB =
        new KeyBinding("key.ldib.grab_dock", Keyboard.KEY_G, "key.categories.ldib");

    /** The handlebar bell (default <b>B</b>). Only does anything while riding; the server rings it. */
    public static final KeyBinding BELL =
        new KeyBinding("key.ldib.bell", Keyboard.KEY_B, "key.categories.ldib");

    public static void register() {
        ClientRegistry.registerKeyBinding(GRAB);
        ClientRegistry.registerKeyBinding(BELL);
        MinecraftForge.EVENT_BUS.register(new LdibKeyHandler());
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (GRAB.isPressed()) {
            LdibNetwork.CHANNEL.sendToServer(new PacketGrabBike());
        }
        while (BELL.isPressed()) {
            // Checked here as well as on the server only to save a packet per stray press on foot.
            if (Minecraft.getMinecraft().player != null
                && Minecraft.getMinecraft().player.getRidingEntity() instanceof EntityBike) {
                LdibNetwork.CHANNEL.sendToServer(new PacketRingBell());
            }
        }
    }
}
