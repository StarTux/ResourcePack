package com.cavetale.resourcepack;

import java.io.File;
import java.io.IOException;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourcePackPlugin extends JavaPlugin implements Listener {
    private YamlConfiguration playersConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        playersConfig = YamlConfiguration
            .loadConfiguration(new File(getDataFolder(), "players.yml"));
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length == 1
            && args[0].equals("reload")
            && sender.hasPermission("resourcepack.admin")) {
            reloadConfig();
            playersConfig = YamlConfiguration
                .loadConfiguration(new File(getDataFolder(), "players.yml"));
            sender.sendMessage("[ResourcePack] configuration reloaded.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Player expected.");
            return true;
        }
        final Player player = (Player) sender;
        if (args.length == 0) {
            listPacks(player);
            return true;
        }
        switch (args[0]) {
        case "use":
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /rp use <pack>");
            } else {
                packCommand(player, "use", args[1]);
            }
            return true;
        case "always":
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /rp always <pack>");
            } else {
                packCommand(player, "always", args[1]);
            }
            return true;
        case "disable":
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /rp disable <pack>");
            } else {
                packCommand(player, "disable", args[1]);
            }
            return true;
        default:
            if (args.length == 1
                && !args[0].contains(".")
                && getConfig()
                .isConfigurationSection("resourcePacks." + args[0])) {
                packCommand(player, "use", args[0]);
            }
            player.sendMessage(ChatColor.RED + "Unknown command or pack: "
                               + args[0] + ".");
            return true;
        }
    }

    void setPack(final Player player, final String pack) {
        final String url = getConfigString(pack, "url");
        final String hash = getConfigString(pack, "hash");
        if (hash.equals(player.getResourcePackHash())) {
            if (getConfig().getBoolean("debug")) {
                getLogger().info("Player has known hash: " + hash
                                 + ". Skipping.");
            }
            return;
        }
        if (getConfig().getBoolean("debug")) {
            getLogger().info("Sending resource pack to " + player.getName()
                             + ": " + url + " hash=" + hash);
        }
        player.setResourcePack(url, hash);
    }

    void packCommand(final Player player,
                     final String cmd,
                     final String pack) {
        if (!getConfig().isConfigurationSection("resourcePacks." + pack)) {
            player.sendMessage(ChatColor.RED + "Unknown pack: " + pack + ".");
            return;
        }
        String perm = getConfigString(pack, "permission");
        if (!perm.isEmpty() && !player.hasPermission(perm)) {
            player.sendMessage(ChatColor.RED + "Unknown pack: " + pack + ".");
            return;
        }
        String displayName = format(getConfigString(pack, "displayName"));
        switch (cmd) {
        case "use":
            player.sendMessage(ChatColor.GREEN + "Using pack: " + displayName
                               + ChatColor.GREEN + ".");
            setPack(player, pack);
            break;
        case "always":
            playersConfig.set(player.getUniqueId().toString(), pack);
            savePlayersConfig();
            setPack(player, pack);
            listPacks(player);
            player.sendMessage(ChatColor.GREEN + "Using pack automatically: "
                               + displayName + ChatColor.GREEN + ".");
            break;
        case "disable":
            if (playersConfig.isString(player.getUniqueId().toString())) {
                playersConfig.set(player.getUniqueId().toString(), null);
                savePlayersConfig();
            }
            listPacks(player);
            player.sendMessage(ChatColor.RED + "Disabling pack: "
                               + displayName + ChatColor.RED + ".");
            player.sendMessage(ChatColor.RED
                               + "You have to relog for this to take effect.");
            break;
        default: break;
        }
    }

    void savePlayersConfig() {
        try {
            playersConfig.save(new File(getDataFolder(), "players.yml"));
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    String getConfigString(final String pack, final String key) {
        String result = getConfig()
            .getString("resourcePacks." + pack + "." + key);
        if (result != null) return result;
        result = getConfig().getString("default." + key);
        return result != null
            ? result
            : "";
    }

    String format(final String inp) {
        return ChatColor.translateAlternateColorCodes('&', inp);
    }

    String format(final String inp,
                  final String key,
                  final String name,
                  final String url) {
        return ChatColor
            .translateAlternateColorCodes('&', inp)
            .replace("{key}", key)
            .replace("{name}", name)
            .replace("{url}", url);
    }

    void rawify(final ComponentBuilder cb, final String msg) {
        ChatColor color = null;
        boolean italic = false;
        boolean bold = false;
        boolean strikethrough = false;
        boolean underlined = false;
        boolean obfuscated = false;
        for (int i = 0; i < msg.length() - 1; i += 2) {
            if (msg.charAt(i) != ChatColor.COLOR_CHAR) break;
            org.bukkit.ChatColor c = org.bukkit.ChatColor.getByChar(msg.charAt(i + 1));
            if (c == null) break;
            switch (c) {
            case BLACK:
            case DARK_BLUE:
            case DARK_GREEN:
            case DARK_AQUA:
            case DARK_RED:
            case DARK_PURPLE:
            case GOLD:
            case GRAY:
            case DARK_GRAY:
            case BLUE:
            case GREEN:
            case AQUA:
            case RED:
            case LIGHT_PURPLE:
            case YELLOW:
            case WHITE:
                color = ChatColor.getByChar(c.getChar());
                italic = false;
                bold = false;
                strikethrough = false;
                underlined = false;
                obfuscated = false;
                break;
            case RESET:
                color = ChatColor.WHITE;
                italic = false;
                bold = false;
                strikethrough = false;
                underlined = false;
                obfuscated = false;
                break;
            case ITALIC: italic = true; break;
            case BOLD: bold = true; break;
            case STRIKETHROUGH: strikethrough = true; break;
            case UNDERLINE: underlined = true; break;
            case MAGIC: obfuscated = true; break;
            default: break;
            }
        }
        if (color != null) cb.color(color);
        if (italic) cb.italic(true);
        if (bold) cb.bold(true);
        if (strikethrough) cb.strikethrough(true);
        if (underlined) cb.underlined(true);
        if (obfuscated) cb.obfuscated(true);
    }

    void listPacks(final Player player) {
        String header = format(getConfig().getString("header"));
        if (!header.isEmpty()) {
            player.sendMessage(header);
        }
        String pack = playersConfig.getString(player.getUniqueId().toString());
        for (String key : getConfig().getConfigurationSection("resourcePacks")
                 .getKeys(false)) {
            if ("default".equals(key)) continue;
            final String perm = getConfigString(key, "permission");
            if (!perm.isEmpty() && !player.hasPermission(perm)) continue;
            final String name = format(getConfigString(key, "displayName"));
            final String url = getConfigString(key, "url");
            ComponentBuilder cb = new ComponentBuilder("");
            cb.append(format(getConfigString(key, "messages.description"),
                             key, name, url));
            String msg;
            msg = getConfigString(key, "messages.use");
            if (!msg.isEmpty()) {
                String cmd = "/rp use " + key;
                msg = format(msg, key, name, url);
                cb.append(" ").reset().append(msg)
                    .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                          TextComponent.fromLegacyText(cmd)));
                rawify(cb, msg);
            }
            if (!key.equals(pack)) {
                msg = getConfigString(key, "messages.always");
                if (!msg.isEmpty()) {
                    String cmd = "/rp always " + key;
                    msg = format(msg, key, name, url);
                    cb.append(" ").reset().append(msg)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                              TextComponent.fromLegacyText(cmd)));
                    rawify(cb, msg);
                }
            } else {
                msg = getConfigString(key, "messages.disable");
                if (!msg.isEmpty()) {
                    String cmd = "/rp disable " + key;
                    msg = format(msg, key, name, url);
                    cb.append(" ").reset().append(msg)
                        .event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd))
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                              TextComponent.fromLegacyText(cmd)));
                    rawify(cb, msg);
                }
            }
            msg = getConfigString(key, "messages.download");
            if (!msg.isEmpty()) {
                msg = format(msg, key, name, url);
                cb.append(" ").reset().append(msg)
                    .event(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                    .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                          TextComponent.fromLegacyText(url)));
                rawify(cb, msg);
            }
            player.spigot().sendMessage(cb.create());
        }
        String footer = format(getConfig().getString("footer"));
        if (!footer.isEmpty()) {
            player.sendMessage(footer);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        String pack = playersConfig.getString(player.getUniqueId().toString());
        if (pack != null) {
            if (!getConfig().isConfigurationSection("resourcePacks." + pack)) {
                if (getConfig().getBoolean("debug")) {
                    getLogger().warning(player.getName()
                                     + " has unknown resource pack " + pack
                                     + ". Resetting.");
                }
                playersConfig.set(player.getUniqueId().toString(), null);
                savePlayersConfig();
                return;
            }
            String perm = getConfigString(pack, "permission");
            if (!perm.isEmpty() && !player.hasPermission(perm)) {
                playersConfig.set(player.getUniqueId().toString(), null);
                savePlayersConfig();
                if (getConfig().getBoolean("debug")) {
                    getLogger().warning(player.getName()
                                     + " has resource pack " + pack
                                     + " without permission. Resetting.");
                }
                // Do not return
            } else {
                getServer().getScheduler()
                    .runTaskLater(this,
                                  () -> setPack(player, pack),
                                  20L);
                return;
            }
        }
        if (getConfig().getBoolean("showOnJoin")
            && player.hasPermission("resourcepack.resourcepack")) {
            // Add a little delay
            getServer().getScheduler()
                .runTaskLater(this,
                              () -> listPacks(player),
                              100L);
        }
    }

    @EventHandler
    public void onPlayerResourcePackStatus(PlayerResourcePackStatusEvent event) {
        getLogger().info("Status"
                         + " player=" + event.getPlayer().getName()
                         + " status=" + event.getStatus()
                         + " hash=" + event.getHash());
    }
}
