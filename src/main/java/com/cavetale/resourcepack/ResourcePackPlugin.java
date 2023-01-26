package com.cavetale.resourcepack;

import com.cavetale.core.back.Back;
import com.cavetale.core.command.RemotePlayer;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.connect.NetworkServer;
import com.cavetale.core.event.connect.ConnectMessageEvent;
import com.cavetale.core.font.DefaultFont;
import com.cavetale.mytems.Mytems;
import com.winthier.connect.Redis;
import com.winthier.connect.payload.PlayerServerPayload;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

public final class ResourcePackPlugin extends JavaPlugin implements Listener {
    protected static final String MESSAGE_ADD = "resourcepack:add";
    protected static final String MESSAGE_REMOVE = "resourcepack:remove";

    protected static ResourcePackPlugin instance;
    protected String url = "http://static.cavetale.com/resourcepacks/Cavetale.zip";
    protected String hash = "";
    protected Map<UUID, Integer> failedAttempts = new HashMap<>();
    protected Component message = text("Custom blocks and items, and awesome chat!", GREEN);
    protected Set<UUID> loadedCache = new HashSet<>();
    protected CoreServerResourcePack coreServerResourcePack = new CoreServerResourcePack();
    protected ResourcePackAdminCommand resourcePackAdminCommand = new ResourcePackAdminCommand(this);
    protected boolean doSendHelpMessage = false;

    @Override
    public void onLoad() {
        instance = this;
        coreServerResourcePack.register();
    }

    @Override
    public void onEnable() {
        loadHash();
        Bukkit.getScheduler().runTaskTimer(this, this::loadHashAsync, 0L, 20L * 60L);
        getServer().getPluginManager().registerEvents(this, this);
        resourcePackAdminCommand.enable();
        Bukkit.getScheduler().runTaskLater(this, () -> {
                for (RemotePlayer player : Connect.get().getRemotePlayers()) {
                    UUID uuid = player.getUniqueId();
                    if (Redis.get("ResourcePack." + uuid) != null) {
                        loadedCache.add(uuid);
                    }
                }
            }, 50L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[resourcepack:rp] Player expected");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 1 && args[0].equals("reset") && player.hasPermission("resourcepack.reset")) {
            resourcePackAdminCommand.reset(player);
            return true;
        }
        if (args.length != 0) return false;
        sendResourcePack(player);
        player.sendMessage(text("Sending resource pack...", GREEN));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (hash == null || hash.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;
                getLoadStatus(player.getUniqueId(), loadStatus -> {
                        switch (loadStatus) {
                        case NOT_LOADED:
                            loadedCache.remove(player.getUniqueId());
                            Connect.get().broadcastMessage(MESSAGE_REMOVE, "" + player.getUniqueId());
                            if (player.hasPermission("resourcepack.send")) {
                                sendResourcePack(player);
                            }
                            break;
                        case LOADED_OUTDATED:
                            loadedCache.remove(player.getUniqueId());
                            Connect.get().broadcastMessage(MESSAGE_REMOVE, "" + player.getUniqueId());
                            if (player.hasPermission("resourcepack.send.switch")) {
                                getLogger().info("Sending updated pack to " + player.getName());
                                sendResourcePack(player);
                            }
                            break;
                        case LOADED:
                            loadedCache.add(player.getUniqueId());
                            Connect.get().broadcastMessage(MESSAGE_ADD, "" + player.getUniqueId());
                        default:
                            break;
                        }
                    });
            }, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        failedAttempts.remove(uuid);
    }

    public void getLoadStatus(UUID uuid, Consumer<LoadStatus> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                String playerHash = Redis.get("ResourcePack." + uuid);
                LoadStatus result = playerHash == null
                    ? LoadStatus.NOT_LOADED
                    : (Objects.equals(hash, playerHash)
                       ? LoadStatus.LOADED
                       : LoadStatus.LOADED_OUTDATED);
                Bukkit.getScheduler().runTask(this, () -> callback.accept(result));
            });
    }

    private void loadHashAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                final String newHash = readHash();
                Bukkit.getScheduler().runTask(this, () -> {
                        if (newHash.isEmpty()) {
                            getLogger().warning("Hash not found!");
                        } else if (!newHash.equals(hash)) {
                            hash = newHash;
                            getLogger().info("New hash: '" + hash + "'");
                            for (Player player : Bukkit.getOnlinePlayers()) {
                                if (player.hasPermission("resourcepack.send.update")) {
                                    getLogger().info("Sending updated pack to " + player.getName());
                                    sendResourcePack(player);
                                }
                            }
                        }
                    });
            });
    }

    private void loadHash() {
        hash = readHash();
        if (hash.isEmpty()) {
            getLogger().warning("Hash not found!");
        } else {
            getLogger().info("New hash: '" + hash + "'");
        }
    }

    public String readHash() {
        try {
            Path path = Paths.get("/var/www/static/resourcepacks/Cavetale.zip.sha1").toRealPath();
            String content = Files.readString(path);
            String sha1 = content.split("\\s+", 2)[0];
            return sha1;
        } catch (IOException ioe) {
            ioe.printStackTrace();
            return "";
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        getLogger().info("ResourcePack: " + event.getStatus() + " " + player.getName());
        switch (event.getStatus()) {
        case FAILED_DOWNLOAD: {
            loadedCache.remove(player.getUniqueId());
            Connect.get().broadcastMessage(MESSAGE_REMOVE, "" + player.getUniqueId());
            UUID uuid = player.getUniqueId();
            int failCount = failedAttempts.compute(uuid, (u, i) -> i != null ? i + 1 : 1);
            int maxFailCount = 2;
            getLogger().warning(player.getName() + ": failed download attempt " + failCount + "/" + maxFailCount);
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    Redis.del("ResourcePack." + uuid);
                });
            if (failCount < maxFailCount && player.hasPermission("resourcepack.send.failed")) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                        getLogger().info("Re-sending failed pack to " + player.getName());
                        sendResourcePack(player);
                    }, 20L);
            }
            break;
        }
        case DECLINED: {
            loadedCache.remove(player.getUniqueId());
            Connect.get().broadcastMessage(MESSAGE_REMOVE, "" + player.getUniqueId());
            failedAttempts.remove(player.getUniqueId());
            getLogger().warning("Declined resource pack: " +  player.getName());
            player.sendMessage(join(separator(newline()),
                                    text("Please use our Resource Pack:", RED),
                                    text("\u2022 Open your Multiplayer Server List", RED),
                                    text("\u2022 Add or Edit cavetale.com", RED),
                                    text("\u2022 Set 'Server Resource Packs: Enabled'", RED)));
            break;
        }
        case SUCCESSFULLY_LOADED: {
            loadedCache.add(player.getUniqueId());
            Connect.get().broadcastMessage(MESSAGE_ADD, "" + player.getUniqueId());
            failedAttempts.remove(player.getUniqueId());
            UUID uuid = player.getUniqueId();
            String key = "ResourcePack." + uuid;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    Redis.set(key, hash, 60 * 60 * 24);
                });
            if (doSendHelpMessage) {
                player.sendMessage(join(separator(newline()),
                                        textOfChildren(DefaultFont.CAVETALE,
                                                       text(" Resource Pack Loaded ", GREEN),
                                                       Mytems.SMILE),
                                        textOfChildren(DefaultFont.CAVETALE,
                                                       text(" If anything looks wrong, type ", GRAY),
                                                       text("/rp", GREEN),
                                                       text(" to ", GRAY),
                                                       Mytems.REDO,
                                                       text("reload", GRAY)))
                                   .clickEvent(runCommand("/rp"))
                                   .hoverEvent(showText(join(separator(newline()),
                                                             text("/rp", GREEN),
                                                             text("Reload Resource Pack", DARK_GRAY)))));
            }
            if (player.hasPermission("resourcepack.back") && NetworkServer.current() == NetworkServer.HUB) {
                Back.sendBack(player, backLocation -> { });
            }
            break;
        }
        case ACCEPTED:
            break;
        default:
            break;
        }
    }

    @EventHandler
    void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getChannel()) {
        case "BUNGEE_PLAYER_QUIT": {
            // Wart: All servers will attempt to delete this
            PlayerServerPayload payload = PlayerServerPayload.deserialize(event.getPayload());
            loadedCache.remove(payload.getPlayer().getUuid());
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    Redis.del("ResourcePack." + payload.getPlayer().getUuid());
                });
            break;
        }
        case MESSAGE_ADD:
            loadedCache.add(UUID.fromString(event.getPayload()));
            break;
        case MESSAGE_REMOVE:
            loadedCache.remove(UUID.fromString(event.getPayload()));
            break;
        default:
            break;
        }
    }

    /**
     * Get the cached loaded status of any online player.
     * @param player the player
     * @return true if, to the best of our knowledge, the player has
     *   the server resource pack loaded, False otherwise.
     */
    public static boolean isLoaded(Player player) {
        return instance.loadedCache.contains(player.getUniqueId());
    }

    private void sendResourcePack(Player player) {
        final String nurl = "https://cavetale.com/resourcepack/" + hash + ".zip";
        player.setResourcePack(nurl, hash, false, message);
    }
}
