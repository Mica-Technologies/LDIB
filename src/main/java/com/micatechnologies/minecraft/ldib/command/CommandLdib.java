package com.micatechnologies.minecraft.ldib.command;

import com.micatechnologies.minecraft.ldib.api.BikeShareApi;
import com.micatechnologies.minecraft.ldib.api.StationInfo;
import com.micatechnologies.minecraft.ldib.block.BikeShareNetwork;
import com.micatechnologies.minecraft.ldib.block.TileEntityBikeDock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * {@code /ldib stations list|rescan} — operator tools for the bike-share station registry.
 *
 * <p>{@code rescan} is how a world that predates the registry gets its stations listed without anyone
 * visiting each kiosk: every dock in a loaded chunk reports itself, which registers the kiosk it belongs
 * to and recounts it. Kiosks also join on their own the first time someone uses one, or a dock near them
 * changes, so this is a convenience rather than a requirement. It reads only loaded chunks; a station
 * nobody is near is picked up when someone is.</p>
 */
public class CommandLdib extends CommandBase {

    @Override
    public String getName() {
        return "ldib";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ldib stations <list|rescan>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2 || !"stations".equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        World world = sender.getEntityWorld();
        if ("rescan".equals(args[1])) {
            BikeShareNetwork network = BikeShareNetwork.get(world);
            List<BlockPos> docks = new ArrayList<>();
            for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
                if (te instanceof TileEntityBikeDock && !te.isInvalid()) {
                    docks.add(te.getPos());
                }
            }
            int dropped = network.pruneMissing(world);
            for (BlockPos dock : docks) {
                network.dockChanged(world, dock);
            }
            sender.sendMessage(new TextComponentString("Rescanned " + docks.size() + " loaded dock(s), dropped "
                + dropped + " missing kiosk(s); " + network.stations().size()
                + " station(s) now registered in this dimension."));
        } else if ("list".equals(args[1])) {
            List<StationInfo> stations = BikeShareApi.stations(world);
            sender.sendMessage(new TextComponentString(stations.size() + " station(s) in this dimension:"));
            for (StationInfo s : stations) {
                sender.sendMessage(new TextComponentString(String.format("  %d, %d, %d — %d bike(s), %d free dock(s)",
                    s.kiosk.getX(), s.kiosk.getY(), s.kiosk.getZ(), s.bikes, s.freeDocks)));
            }
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args,
                                          BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "stations");
        }
        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "list", "rescan");
        }
        return Collections.emptyList();
    }
}
