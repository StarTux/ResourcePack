package com.cavetale.resourcepack;

import com.winthier.connect.Connect;
import com.winthier.connect.event.ConnectMessageEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
import redis.clients.jedis.Jedis;

public final class ResourcePackPlugin extends JavaPlugin implements Listener {
    String url = "http://static.cavetale.com/resourcepacks/Cavetale.zip";
    String hash = "";
    Map<UUID, Integer> failedAttempts = new HashMap<>();

    enum LoadStatus {
        LOADED,
        LOADED_OUTDATED,
        NOT_LOADED;
    }

    @Override
    public void onEnable() {
        loadHash();
        Bukkit.getScheduler().runTaskTimer(this, this::loadHashAsync, 0L, 20L * 60L);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 0) return false;
        if (!(sender instanceof Player)) {
            sender.sendMessage("[resourcepack:rp] Player expected");
            return true;
        }
        Player player = (Player) sender;
        player.setResourcePack(url, hash);
        player.sendMessage(Component.text("Sending resource pack...", NamedTextColor.GREEN));
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (hash == null || hash.isEmpty()) return;
        switch (getLoadStatus(player)) {
        case NOT_LOADED:
            if (player.hasPermission("resourcepack.send")) {
                player.setResourcePack(url, hash);
            }
            break;
        case LOADED_OUTDATED:
            if (player.hasPermission("resourcepack.send.switch")) {
                getLogger().info("Sending updated pack to " + player.getName());
                player.setResourcePack(url, hash);
            }
            break;
        case LOADED:
        default:
            break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        failedAttempts.remove(event.getPlayer().getUniqueId());
    }

    public LoadStatus getLoadStatus(Player player) {
        UUID uuid = player.getUniqueId();
        String playerHash;
        try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
            playerHash = jedis.get("ResourcePack." + uuid);
        } catch (Exception e) {
            e.printStackTrace();
            return LoadStatus.NOT_LOADED;
        }
        if (playerHash == null) return LoadStatus.NOT_LOADED;
        return Objects.equals(hash, playerHash)
            ? LoadStatus.LOADED
            : LoadStatus.LOADED_OUTDATED;
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
                                    player.setResourcePack(url, hash);
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
        switch (event.getStatus()) {
        case FAILED_DOWNLOAD: {
            UUID uuid = player.getUniqueId();
            int failCount = failedAttempts.compute(uuid, (u, i) -> i != null ? i + 1 : 1);
            int maxFailCount = 2;
            getLogger().warning(player.getName() + ": failed download attempt " + failCount + "/" + maxFailCount);
            try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
                jedis.del("ResourcePack." + uuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (failCount <= maxFailCount && player.hasPermission("resourcepack.send.failed")) {
                Bukkit.getScheduler().runTaskLater(this, () -> {
                        getLogger().info("Re-sending failed pack to " + player.getName());
                        player.setResourcePack(url, hash);
                    }, 20L);
            }
            break;
        }
        case DECLINED: {
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
        case ACCEPTED:
        case SUCCESSFULLY_LOADED: {
            failedAttempts.remove(player.getUniqueId());
            try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
                UUID uuid = player.getUniqueId();
                String key = "ResourcePack." + uuid;
                jedis.set(key, hash);
                jedis.expire(key, 60 * 60 * 24);
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        }
        default:
            break;
        }
    }

    @EventHandler @SuppressWarnings("Unchecked")
    void onConnectMessage(ConnectMessageEvent event) {
        switch (event.getMessage().getChannel()) {
        case "bungee": {
            if (!(event.getMessage().getPayload() instanceof Map)) return;
            Map<String, Object> payload = (Map<String, Object>) event.getMessage().getPayload();
            if (!(payload.get("type") instanceof String)) return;
            String type = (String) payload.get("type");
            if (!(payload.get("player") instanceof Map)) return;
            Map<String, Object> player = (Map<String, Object>) payload.get("player");
            if (!(player.get("uuid") instanceof String)) return;
            UUID uuid;
            try {
                uuid = UUID.fromString((String) player.get("uuid"));
            } catch (IllegalArgumentException iae) {
                return;
            }
            switch (type) {
            case "PlayerDisconnectEvent":
                try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
                    jedis.del("ResourcePack." + uuid);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            default:
                break;
            }
            break;
        }
        default:
            break;
        }
    }
}
