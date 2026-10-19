package dev.wuason.adapter.plugins;

import dev.wuason.adapter.AdapterComp;
import dev.wuason.adapter.Utils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecutableItemsImpl extends AdapterComp {

    private static final String API_CLASS = "com.ssomar.score.api.executableitems.ExecutableItemsAPI";
    private static volatile Class<?> executableItemsApiClass;
    private static volatile boolean warned;

    public ExecutableItemsImpl(String name, String type) {
        super(name, type);
    }

    @Override
    public ItemStack getAdapterItem(String id) {
        Object executableItem = getExecutableItem(id);
        Object item = executableItem == null ? null : invoke(executableItem, "buildItem", 1, Optional.empty());
        return item instanceof ItemStack itemStack ? itemStack : null;
    }

    @Override
    public String getAdapterId(ItemStack itemStack) {
        Object executableItem = getExecutableItem(itemStack);
        Object id = executableItem == null ? null : invoke(executableItem, "getId");
        return id == null ? null : Utils.convert(getType(), id.toString());
    }

    @Override
    public String getAdapterId(Block block) {
        return null;
    }

    @Override
    public String getAdapterId(Entity entity) {
        return null;
    }

    @Override
    public String getAdvancedAdapterId(ItemStack itemStack) {
        return getAdapterId(itemStack);
    }

    @Override
    public String getAdvancedAdapterId(Block block) {
        return getAdapterId(block);
    }

    @Override
    public String getAdvancedAdapterId(Entity entity) {
        return null;
    }

    @Override
    public boolean existItemAdapter(String id) {
        return getExecutableItem(id) != null;
    }

    @Override
    public Set<String> getAllItems() {
        Object manager = getManager();
        Object ids = manager == null ? null : invoke(manager, "getExecutableItemIdsList");
        if (!(ids instanceof Collection<?> collection)) return Set.of();
        return collection.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(id -> Utils.convert(getType(), id))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Object getExecutableItem(Object idOrItemStack) {
        Object manager = getManager();
        Object result = manager == null ? null : invoke(manager, "getExecutableItem", idOrItemStack);
        return result instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static Object getManager() {
        Class<?> apiClass = resolveApiClass();
        return apiClass == null ? null : invoke(apiClass, "getExecutableItemsManager");
    }

    private static Class<?> resolveApiClass() {
        Class<?> cached = executableItemsApiClass;
        if (cached != null) return cached;

        Throwable lastError = null;
        for (String pluginName : new String[]{"SCore", "ExecutableItems"}) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
            if (plugin == null) continue;
            try {
                Class<?> resolved = Class.forName(API_CLASS, true, plugin.getClass().getClassLoader());
                executableItemsApiClass = resolved;
                return resolved;
            } catch (ClassNotFoundException | LinkageError error) {
                lastError = error;
            }
        }
        if (lastError != null) warn(lastError);
        return null;
    }

    private static Object invoke(Object target, String methodName, Object... arguments) {
        Class<?> targetClass = target instanceof Class<?> type ? type : target.getClass();
        try {
            Method method = Arrays.stream(targetClass.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .filter(candidate -> candidate.getParameterCount() == arguments.length)
                    .filter(candidate -> parametersMatch(candidate.getParameterTypes(), arguments))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchMethodException(targetClass.getName() + "#" + methodName));
            Object receiver = Modifier.isStatic(method.getModifiers()) ? null : target;
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warn(error);
            return null;
        }
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] arguments) {
        for (int index = 0; index < parameterTypes.length; index++) {
            if (arguments[index] == null) continue;
            Class<?> parameterType = wrapPrimitive(parameterTypes[index]);
            if (!parameterType.isAssignableFrom(arguments[index].getClass())) return false;
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return Void.class;
    }

    private static void warn(Throwable error) {
        if (warned) return;
        warned = true;
        Bukkit.getLogger().warning("[Adapter] ExecutableItems API is unavailable: "
                + error.getClass().getSimpleName() + ": " + error.getMessage());
    }
}