package net.minplay.proxy.pluginmanager;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginDescription;

import java.lang.reflect.*;
import java.net.URL;

public class ReflectionUtils {
    public static Object createPluginClassloader(ProxyServer proxy, PluginDescription desc, URL[] urls) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Class clazz = Class.forName("net.md_5.bungee.api.plugin.PluginClassloader");
        Constructor constroctor = clazz.getConstructor(ProxyServer.class, PluginDescription.class, URL[].class);
        constroctor.setAccessible(true);
        Object obj = constroctor.newInstance(proxy, desc, urls);
        return obj;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValue(Object obj, String fieldname) {
        Class<?> clazz = obj.getClass();
        do {
            try {
                Field field = clazz.getDeclaredField(fieldname);
                field.setAccessible(true);
                return (T) field.get(obj);
            } catch (Throwable t) {
            }
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static void setFieldValue(Object obj, String fieldname, Object value) {
        Class<?> clazz = obj.getClass();
        do {
            try {
                Field field = clazz.getDeclaredField(fieldname);
                field.setAccessible(true);
                field.set(obj, value);
            } catch (Throwable t) {
            }
        } while ((clazz = clazz.getSuperclass()) != null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getStaticFieldValue(Class<?> clazz, String fieldname) {
        do {
            try {
                Field field = clazz.getDeclaredField(fieldname);
                field.setAccessible(true);
                return (T) field.get(null);
            } catch (Throwable t) {
            }
        } while ((clazz = clazz.getSuperclass()) != null);
        return null;
    }

    public static void invokeMethod(Object obj, String methodname, Object... args) {
        Class<?> clazz = obj.getClass();
        do {
            try {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (method.getName().equals(methodname) && method.getParameterTypes().length == args.length) {
                        method.setAccessible(true);
                        method.invoke(obj, args);
                    }
                }
            } catch (Throwable t) {
            }
        } while ((clazz = clazz.getSuperclass()) != null);
    }

}
