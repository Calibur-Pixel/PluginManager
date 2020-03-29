package net.minplay.proxy.pluginmanager;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.logging.Level;

public class BungeePluginManager extends Plugin {
    private static BungeePluginManager instance;

    public static BungeePluginManager getInstance() {
        return instance;
    }
    @Override
    public void onEnable() {
        ProxyServer.getInstance().getLogger().log(Level.INFO, "Loading BungeePluginManager Reborn by Margele.");
        instance = this;
        ProxyServer.getInstance().getPluginManager().registerCommand(this, new Commands());
    }
}
