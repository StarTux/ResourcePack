package com.cavetale.resourcepack;

import com.winthier.connect.Redis;
import com.winthier.connect.event.ConnectMessageEvent;
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
import net.kyori.adventure.text.format.NamedTextColor;
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

public final class ResourcePackPlugin extends JavaPlugin implements Listener {
    private static ResourcePackPlugin instance;
    protected String url = "http://static.cavetale.com/resourcepacks/Cavetale.zip";
    protected String hash = "";
    protected Map<UUID, Integer> failedAttempts = new HashMap<>();
    protected Component message = Component.text("Custom blocks and items, and awesome chat!", NamedTextColor.GREEN);
    protected Set<UUID> loadedCache = new HashSet<>();

    enum LoadStatus {
        LOADED,
        LOADED_OUTDATED,
        NOT_LOADED;
    }

    @Override
    public void onEnable() {
        instance = this;
        loadHash();
        Bukkit.getScheduler().runTaskTimer(this, this::loadHashAsync, 0L, 20L * 60L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("[resourcepack:rp] Player expected");
            return true;
        }
        Player player = (Player) sender;
        if (args.length == 1 && args[0].equals("reset") && player.hasPermission("resourcepack.reset")) {
            player.sendMessage(Component.text("Sending void resource pack...", NamedTextColor.YELLOW));
            player.setResourcePack("http://static.cavetale.com/resourcepacks/Void.zip",
                                   "eace0b705db220d5467f28a25381176804e2687b",
                                   false,
                                   Component.text("Empty resource pack", NamedTextColor.YELLOW));
            return true;
        }
        if (args.length != 0) return false;
        player.setResourcePack(url, hash, false, message);
        player.sendMessage(Component.text("Sending resource pack...", NamedTextColor.GREEN));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (hash == null || hash.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!player.isOnline()) return;
                getLoadStatus(player, loadStatus -> {
                        switch (loadStatus) {
                        case NOT_LOADED:
                            loadedCache.remove(player.getUniqueId());
                            if (player.hasPermission("resourcepack.send")) {
                                player.setResourcePack(url, hash, false, message);
                            }
                            break;
                        case LOADED_OUTDATED:
                            loadedCache.remove(player.getUniqueId());
                            if (player.hasPermission("resourcepack.send.switch")) {
                                getLogger().info("Sending updated pack to " + player.getName());
                                player.setResourcePack(url, hash, false, message);
                            }
                            break;
                        case LOADED:
                            loadedCache.add(player.getUniqueId());
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
        loadedCache.remove(uuid);
    }

    public void getLoadStatus(Player player, Consumer<LoadStatus> callback) {
        final UUID uuid = player.getUniqueId();
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
                                    player.setResourcePack(url, hash, false, message);
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
                        player.setResourcePack(url, hash, false, message);
                    }, 20L);
            }
            break;
        }
        case DECLINED: {
            loadedCache.remove(player.getUniqueId());
            failedAttempts.remove(player.getUniqueId());
            getLogger().warning("Declined resource pack: " +  player.getName());
            Component msg = Component.text()
                .append(Component.text("Please use our Resource Pack:", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("\u2022 Open your Multiplayer Server List", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("\u2022 Add or Edit cavetale.com", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("\u2022 Set 'Server Resource Packs: Enabled'", NamedTextColor.RED))
                .build();
            player.sendMessage(msg);
            break;
        }
        case SUCCESSFULLY_LOADED: {
            loadedCache.add(player.getUniqueId());
            failedAttempts.remove(player.getUniqueId());
            UUID uuid = player.getUniqueId();
            String key = "ResourcePack." + uuid;
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    Redis.set(key, hash, 60 * 60 * 24);
                });
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
        // Wart: All servers will attempt to delete this
        if ("BUNGEE_PLAYER_QUIT".equals(event.getMessage().getChannel())) {
            PlayerServerPayload payload = PlayerServerPayload.deserialize(event.getMessage().getPayload());
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                    Redis.del("ResourcePack." + payload.getPlayer().getUuid());
                });
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
}
