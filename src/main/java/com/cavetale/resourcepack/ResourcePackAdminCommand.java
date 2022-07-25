package com.cavetale.resourcepack;

import com.cavetale.core.command.AbstractCommand;
import com.cavetale.core.command.CommandArgCompleter;
import com.cavetale.core.command.CommandWarn;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.JoinConfiguration.noSeparators;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class ResourcePackAdminCommand extends AbstractCommand<ResourcePackPlugin> {
    protected static final String VOID_URL = "http://static.cavetale.com/resourcepacks/Void.zip";
    protected static final String VOID_HASH = "eace0b705db220d5467f28a25381176804e2687b";

    protected ResourcePackAdminCommand(final ResourcePackPlugin plugin) {
        super(plugin, "resourcepackadmin");
    }

    @Override
    protected void onEnable() {
        rootNode.addChild("players").denyTabCompletion()
            .description("Display player info")
            .senderCaller(this::players);
        rootNode.addChild("info").arguments("<player>")
            .description("View player load status")
            .completers(CommandArgCompleter.ONLINE_PLAYERS)
            .senderCaller(this::info);
        rootNode.addChild("reset").denyTabCompletion()
            .description("Reset your pack")
            .playerCaller(this::reset);
    }

    private void players(CommandSender sender) {
        Map<String, Boolean> nameMap = new HashMap<>();
        for (RemotePlayer player : Connect.get().getRemotePlayers()) {
            nameMap.put(player.getName(), plugin.loadedCache.contains(player.getUniqueId()));
        }
        if (nameMap.isEmpty()) throw new CommandWarn("No players to show!");
        List<String> names = new ArrayList<>(nameMap.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        for (String name : names) {
            sender.sendMessage(nameMap.get(name)
                               ? text(name + ": Loaded", GREEN)
                               : text(name + ": Not loaded", RED));
        }
    }

    private boolean info(CommandSender sender, String[] args) {
        if (args.length != 1) return false;
        RemotePlayer target = CommandArgCompleter.requireRemotePlayer(args[0]);
        plugin.getLoadStatus(target.getUniqueId(), status -> {
                sender.sendMessage(join(noSeparators(),
                                        status.title,
                                        text(": " + target.getName(), GRAY)));
            });
        return false;
    }

    protected void reset(Player player) {
        player.sendMessage(text("Sending void resource pack...", YELLOW));
        player.setResourcePack(VOID_URL, VOID_HASH, false, text("Empty resource pack", YELLOW));
    }
}
