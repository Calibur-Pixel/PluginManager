package net.minplay.proxy.pluginmanager;

import com.google.common.base.Preconditions;
import com.google.common.collect.Multimap;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.*;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Level;

class PluginUtils {

    @SuppressWarnings({"deprecation", "SuspiciousMethodCalls"})
    static void unloadPlugin(Plugin plugin) {
        PluginManager pluginmanager = ProxyServer.getInstance().getPluginManager();
        ClassLoader pluginclassloader = plugin.getClass().getClassLoader();

        try {
            Preconditions.checkNotNull(pluginclassloader);
            plugin.onDisable();
            for (Handler handler : plugin.getLogger().getHandlers()) {
                handler.close();
            }
        } catch (Throwable t) {
            severe("Exception disabling plugin", t, plugin.getDescription().getName());
        }

        pluginmanager.unregisterListeners(plugin);
        pluginmanager.unregisterCommands(plugin);

        // Cancel tasks
        ProxyServer.getInstance().getScheduler().cancel(plugin);

        // Shutdown internal executor
        plugin.getExecutorService().shutdownNow();

        // Stop all still active threads that belong to a plugin
        Thread.getAllStackTraces().keySet().forEach((thread) -> {
            if (thread.getClass().getClassLoader() == pluginclassloader) {
                try {
                    thread.interrupt();
                    thread.join(2000);
                    if (thread.isAlive()) thread.stop();
                } catch (Throwable t) {
                    severe("Failed to stop thread that belong to plugin", t, plugin.getDescription().getName());
                }
            }
        });

        // TODO: Unregister all Intents
        // event.completeIntent(plugin);

        // Remove commands that were registered by plugin not through normal means
        try {
            Map<String, Command> commandMap = ReflectionUtils.getFieldValue(pluginmanager, "commandMap");
            Preconditions.checkNotNull(commandMap);

            commandMap.forEach((key, val) -> {
                if (val.getClass().getClassLoader() == pluginclassloader) {
                    commandMap.remove(key);
                }
            });
        } catch (Exception t) {
            severe("Failed to cleanup commandMap", t, plugin.getDescription().getName());
        }

        // Cleanup internal listener and command maps from plugin refs
        try {
            Map<String, Plugin> pluginsMap = ReflectionUtils.getFieldValue(pluginmanager, "plugins");
            Preconditions.checkNotNull(pluginsMap);
            pluginsMap.values().remove(plugin);

            Multimap<Plugin, Command> commands = ReflectionUtils.getFieldValue(pluginmanager, "commandsByPlugin");
            Preconditions.checkNotNull(commands);
            commands.removeAll(plugin);

            Multimap<Plugin, Listener> listeners = ReflectionUtils.getFieldValue(pluginmanager, "listenersByPlugin");
            Preconditions.checkNotNull(listeners);
            listeners.removeAll(plugin);
        } catch (Exception t) {
            severe("Failed to cleanup bungee internal maps from plugin refs", t, plugin.getDescription().getName());
        }
        // Close classloader
        if (pluginclassloader instanceof URLClassLoader) {
            try {
                ((URLClassLoader) pluginclassloader).close();
            } catch (Throwable t) {
                severe("Failed to close the classloader for plugin", t, plugin.getDescription().getName());
            }
        }

        // Remove classloader
        Set allLoaders = null;
        try {
            allLoaders = ReflectionUtils.getStaticFieldValue(Class.forName("net.md_5.bungee.api.plugin.PluginClassloader"), "allLoaders");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        Preconditions.checkNotNull(allLoaders);
        allLoaders.remove(pluginclassloader);
    }

    @SuppressWarnings("resource")
    static boolean loadPlugin(File pluginfile) {
        try (JarFile jar = new JarFile(pluginfile)) {
            JarEntry pdf = jar.getJarEntry("bungee.yml");
            if (pdf == null) {
                pdf = jar.getJarEntry("plugin.yml");
            }
            try (InputStream in = jar.getInputStream(pdf)) {
                //load description

                Representer representer = new Representer();
                representer.getPropertyUtils().setSkipMissingProperties(true);
                PluginDescription desc = new Yaml(new Constructor(PluginDescription.class), representer).loadAs(in, PluginDescription.class);
                desc.setFile(pluginfile);


                //check depends
                Map<String, Plugin> pluginsMap = ReflectionUtils.getFieldValue(ProxyServer.getInstance().getPluginManager(), "plugins");
                Preconditions.checkNotNull(pluginsMap);

                for (String dependency : desc.getDepends()) {
                    if (!pluginsMap.keySet().contains(dependency)) {
                        ProxyServer.getInstance().getLogger().log(Level.WARNING, "{0} (required by {1}) is unavailable", new Object[]{dependency, desc.getName()});
                        return false;
                    }
                }

                //load plugin
                URLClassLoader loader = (URLClassLoader) ReflectionUtils.createPluginClassloader(ProxyServer.getInstance(), desc, new URL[]{pluginfile.toURI().toURL()});
                Class<?> mainclazz = loader.loadClass(desc.getMain());
                Plugin plugin = (Plugin) mainclazz.getDeclaredConstructor().newInstance();
                ReflectionUtils.invokeMethod(plugin, "init", ProxyServer.getInstance(), desc);

                pluginsMap.put(desc.getName(), plugin);
                plugin.onLoad();
                plugin.onEnable();
                return true;
            }
        } catch (Throwable t) {
            severe("Failed to load plugin", t, pluginfile.getName());
            return false;
        }
    }

    private static void severe(String message, Throwable t, String pluginname) {
        ProxyServer.getInstance().getLogger().log(Level.SEVERE, message + " " + pluginname, t);
    }

}
