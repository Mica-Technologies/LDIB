package com.micatechnologies.minecraft.ldib.client.gui;

import com.micatechnologies.minecraft.ldib.api.ShareTariff;
import com.micatechnologies.minecraft.ldib.block.BikeShareStation;
import com.micatechnologies.minecraft.ldib.network.LdibNetwork;
import com.micatechnologies.minecraft.ldib.network.PacketKioskAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.math.BlockPos;

/**
 * The bike-share kiosk screen: shows how many bikes and free docks the station has and lets the player
 * check out a rental. Client-only; opened by {@link com.micatechnologies.minecraft.ldib.network.PacketOpenKiosk}
 * and it talks back to the server with {@link PacketKioskAction}. The live station counts are read from
 * the client's own world via {@link BikeShareStation} (pure block reads).
 */
public class GuiKiosk extends GuiScreen {

    private static final int CHECK_OUT_BUTTON = 0;

    private final BlockPos kiosk;
    private final boolean hasSession;
    private final ShareTariff tariff;

    public GuiKiosk(BlockPos kiosk, boolean hasSession, long startTick, ShareTariff tariff) {
        this.kiosk = kiosk;
        this.hasSession = hasSession;
        this.tariff = tariff;
    }

    @Override
    public void initGui() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        if (!hasSession) {
            this.buttonList.add(new GuiButton(CHECK_OUT_BUTTON, cx - 70, cy + 20, 140, 20, "Check out a bike"));
        }
        this.buttonList.add(new GuiButton(1, cx - 70, cy + 44, 140, 20, "Close"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CHECK_OUT_BUTTON) {
            LdibNetwork.CHANNEL.sendToServer(new PacketKioskAction(kiosk, PacketKioskAction.CHECK_OUT));
        }
        this.mc.displayGuiScreen(null);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = this.width / 2;
        int cy = this.height / 2;

        drawCenteredString(this.fontRenderer, "Bike Share Station", cx, cy - 64, 0x55D6C6);

        int bikes = BikeShareStation.countBikesAvailable(Minecraft.getMinecraft().world, kiosk);
        int free = BikeShareStation.countFreeDocks(Minecraft.getMinecraft().world, kiosk);
        drawCenteredString(this.fontRenderer, bikes + " bike" + (bikes == 1 ? "" : "s") + " available", cx, cy - 46, 0xFFFFFF);
        drawCenteredString(this.fontRenderer, free + " free dock" + (free == 1 ? "" : "s"), cx, cy - 34, 0xCCCCCC);

        if (tariff.isPaid()) {
            drawCenteredString(this.fontRenderer,
                String.format("Unlock %.2f  •  Bike %.2f/min", tariff.unlockFee, tariff.bikePerMinute()),
                cx, cy - 16, 0xFFD37F);
            drawCenteredString(this.fontRenderer,
                String.format("E-bike %.2f/min  •  Scooter %.2f/min", tariff.ebikePerMinute(), tariff.scooterPerMinute()),
                cx, cy - 5, 0xFFD37F);
        } else {
            drawCenteredString(this.fontRenderer, "Free to ride — no charge.", cx, cy - 10, 0x9CE6A0);
        }

        if (hasSession) {
            drawCenteredString(this.fontRenderer, "Rental active — take a bike from any dock here.", cx, cy + 6, 0x66FF66);
        } else {
            drawCenteredString(this.fontRenderer, "Check out, then take a bike from any dock.", cx, cy + 6, 0xAAAAAA);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
