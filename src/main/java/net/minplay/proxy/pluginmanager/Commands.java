package net.minplay.proxy.pluginmanager;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Commands extends Command implements TabExecutor {

    public Commands() {
        super("pluginmanager", null, "pm");
    }

    private static Plugin findPlugin(String pluginname) {
        for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
            if (plugin.getDescription().getName().equalsIgnoreCase(pluginname)) {
                return plugin;
            }
        }
        return null;
    }

    private static File findFile(String pluginname) {
        File folder = ProxyServer.getInstance().getPluginsFolder();
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            Preconditions.checkNotNull(files);

            for (File file : files) {
                try {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        JarFile jar = new JarFile(file);
                        JarEntry pdf = jar.getJarEntry("bungee.yml");
                        if (pdf == null) {
                            pdf = jar.getJarEntry("plugin.yml");
                        }

                        InputStream in = jar.getInputStream(pdf);
                        Representer representer = new Representer();
                        representer.getPropertyUtils().setSkipMissingProperties(true);
                        PluginDescription desc = new Yaml(new Constructor(PluginDescription.class), representer).loadAs(in, PluginDescription.class);
                        desc.setFile(file);

                        if (desc.getName().equalsIgnoreCase(pluginname)) return file;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }
        return new File(folder, pluginname + ".jar");
    }

    private static TextComponent textWithColor(String message, ChatColor color) {
        TextComponent text = new TextComponent(message);
        text.setColor(color);
        return text;
    }

    private static void showHelp(CommandSender p) {
        if (p.hasPermission("pluginmanager.help")) {
            p.sendMessage(parseText("&7&m-----------------&c&l Usage &7&m-----------------"));
            p.sendMessage(new ComponentBuilder("- pm load [plugin]: ").color(ChatColor.YELLOW).append("Load a plugin").color(ChatColor.GRAY).create());
            p.sendMessage(new ComponentBuilder("- pm unload [plugin]: ").color(ChatColor.YELLOW).append("Unload a plugin").color(ChatColor.GRAY).create());
            p.sendMessage(new ComponentBuilder("- pm reload [plugin]: ").color(ChatColor.YELLOW).append("Reload a plugin").color(ChatColor.GRAY).create());
            p.sendMessage(new ComponentBuilder("- pm usage [plugin]: ").color(ChatColor.YELLOW).append("List commands a plugin has registered").color(ChatColor.GRAY).create());
            p.sendMessage(new ComponentBuilder(" pm list: ").color(ChatColor.YELLOW).append("List plugins").color(ChatColor.GRAY).create());
            p.sendMessage(parseText("&7&m------------------------------------------------"));
        } else {
            p.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
        }
    }

    private static BaseComponent[] parseText(String x) {
        return TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', x));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            showHelp(sender);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "unload": {
                if (!sender.hasPermission("pluginmanager.unload")) {
                    sender.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
                    return;
                }
                if (args.length < 2) {
                    showHelp(sender);
                    return;
                }

                Plugin plugin = findPlugin(args[1]);
                if (plugin == null) {
                    sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
                    return;
                }

                if (plugin == BungeePluginManager.getInstance()) {
                    sender.sendMessage(textWithColor("That is not a good idea...", ChatColor.RED));
                    return;
                }

                PluginUtils.unloadPlugin(plugin);
                sender.sendMessage(textWithColor("Plugin unloaded", ChatColor.YELLOW));
                return;
            }
            case "load": {
                if (!sender.hasPermission("pluginmanager.load")) {
                    sender.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
                    return;
                }
                if (args.length < 2) {
                    showHelp(sender);
                    return;
                }

                Plugin plugin = findPlugin(args[1]);
                if (plugin != null) {
                    sender.sendMessage(textWithColor("Plugin is already loaded", ChatColor.RED));
                    return;
                }

                File file = findFile(args[1]);
                if (!file.exists()) {
                    sender.sendMessage(textWithColor("Plugin not found", ChatColor.RED));
                    return;
                }

                if (PluginUtils.loadPlugin(file)) {
                    sender.sendMessage(textWithColor(args[1] + " loaded successfully!", ChatColor.GREEN));
                } else {
                    sender.sendMessage(textWithColor("Failed to load plugin, see console for more info", ChatColor.RED));
                }
                return;
            }
            case "reload": {
                if (!sender.hasPermission("pluginmanager.reload")) {
                    sender.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
                    return;
                }
                if (args.length < 2) {
                    showHelp(sender);
                    return;
                }

                Plugin plugin = findPlugin(args[1]);
                if (plugin == null) {
                    sender.sendMessage(textWithColor("Invalid plugin", ChatColor.RED));
                    return;
                }

                if (plugin == BungeePluginManager.getInstance()) {
                    sender.sendMessage(textWithColor("That is not a good idea...", ChatColor.RED));
                    return;
                }

                File pluginfile = plugin.getFile();
                PluginUtils.unloadPlugin(plugin);

                if (PluginUtils.loadPlugin(pluginfile)) {
                    sender.sendMessage(textWithColor("Plugin reloaded", ChatColor.YELLOW));
                } else {
                    sender.sendMessage(textWithColor("Failed to reload plugin, see console for more info", ChatColor.RED));
                }
                return;
            }
            case "usage": {
                if (!sender.hasPermission("pluginmanager.usage")) {
                    sender.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
                    return;
                }
                if (args.length < 2) {
                    showHelp(sender);
                    return;
                }

                Plugin plugin = findPlugin(args[1]);
                if (plugin == null) {
                    sender.sendMessage(textWithColor("Invalid plugin", ChatColor.RED));
                    return;
                }

                PluginManager pluginmanager = ProxyServer.getInstance().getPluginManager();
                Multimap<Plugin, Command> commandsByPlugin = ReflectionUtils.getFieldValue(pluginmanager, "commandsByPlugin");
                Preconditions.checkNotNull(commandsByPlugin);

                Set<String> cmd = commandsByPlugin.get(plugin).stream().map(Command::getName).collect(Collectors.toCollection(HashSet::new));
                if (cmd.size() > 0) {
                    sender.sendMessage(textWithColor("Commands: /" + String.join(", /", cmd), ChatColor.GREEN));
                } else {
                    sender.sendMessage(textWithColor("That plugin has no commands!", ChatColor.RED));
                }
                return;
            }
            case "list": {
                if (!sender.hasPermission("pluginmanager.list")) {
                    sender.sendMessage(TextComponent.fromLegacyText(ProxyServer.getInstance().getTranslation("no_permission")));
                    return;
                }
                Map<String, Plugin> list = ReflectionUtils.getFieldValue(ProxyServer.getInstance().getPluginManager(), "plugins");
                Preconditions.checkNotNull(list);
                sender.sendMessage(textWithColor("Plugins: " + String.join(", ", list.keySet()), ChatColor.YELLOW));
                return;
            }
            default:
                showHelp(sender);
        }
    }


    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] strings) {
        Set<String> matches = new HashSet<>();
        if (!sender.hasPermission("pluginmanager.unload") && !sender.hasPermission("pluginmanager.reload")) {
            return matches;
        }
        if (strings.length > 1) {
            for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
                String name = plugin.getDescription().getName();
                if (name.toLowerCase().startsWith(strings[1].toLowerCase())) matches.add(name);
            }
        }
        return matches;
    }
}
