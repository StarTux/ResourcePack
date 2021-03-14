package com.cavetale.resourcepack;

import com.winthier.connect.Connect;
import com.winthier.connect.event.ConnectMessageEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

public final class ResourcePackPlugin extends JavaPlugin implements Listener {
    String url = "http://static.cavetale.com/resourcepacks/Cavetale.zip";
    String hash = "";

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
        player.sendMessage(ChatColor.GREEN + "Sending resource pack...");
        return true;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        if (hash == null || hash.isEmpty()) return;
        switch (getLoadStatus(player)) {
        case NOT_LOADED:
            if (player.hasPermission("resourcepack.send")) {
                getLogger().info("Sending pack to " + player.getName() + ": " + url + ", " + hash);
                player.setResourcePack(url, hash);
            }
            break;
        case LOADED_OUTDATED:
            if (player.hasPermission("resourcepack.resend")) {
                getLogger().info("Re-sending pack to " + player.getName() + ": " + url + ", " + hash);
                player.setResourcePack(url, hash);
            }
            break;
        case LOADED:
        default:
            break;
        }
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
                                if (player.hasPermission("resourcepack.resend")) {
                                    getLogger().info("Re-sending pack to " + player.getName() + ": " + url + ", " + hash);
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
        case FAILED_DOWNLOAD:
            getLogger().warning("Failed to download resource pack: " +  player.getName());
            try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
                UUID uuid = player.getUniqueId();
                jedis.del("ResourcePack." + uuid);
            } catch (Exception e) {
                e.printStackTrace();
            }
            break;
        case DECLINED:
            getLogger().warning("Declined resource pack: " +  player.getName());
            player.sendMessage(ChatColor.RED + "Please use our Resource Pack:");
            player.sendMessage(ChatColor.RED + "- Open your Multiplayer server list");
            player.sendMessage(ChatColor.RED + "- Add or Edit cavetale.com");
            player.sendMessage(ChatColor.RED + "- Set 'Server Resource Packs: Enabled'");
            player.sendMessage(ChatColor.RED + "- Or click 'Yes' if prompted");
            break;
        case ACCEPTED:
        case SUCCESSFULLY_LOADED: {
            try (Jedis jedis = Connect.getInstance().getJedisPool().getResource()) {
                UUID uuid = player.getUniqueId();
                jedis.set("ResourcePack." + uuid, hash);
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
