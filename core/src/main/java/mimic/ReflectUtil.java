package mimic;

import lombok.SneakyThrows;
import org.jooq.lambda.Seq;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface ReflectUtil {
    Map<Class<?>, SoftReference<Method[]>> reflectDeclareCache = new ConcurrentHashMap<>();
    Map<Class<?>, SoftReference<List<Method>>> reflectGetterCache = new ConcurrentHashMap<>();
    Map<Class<?>, Class<?>> reflectInterfaceCache = new ConcurrentHashMap<>();

    static Method[] getDeclareMethods(Class<?> clazz) {
        if (reflectDeclareCache.containsKey(clazz)) {
            final SoftReference<Method[]> ref = reflectDeclareCache.get(clazz);
            if (ref.get() != null) {
                return ref.get();
            }
        }
        final Method[] declaredMethods = clazz.getDeclaredMethods();
        reflectDeclareCache.put(clazz, new SoftReference<>(declaredMethods));
        return declaredMethods;
    }

    static Seq<Method> declaredPublicMethods(Class<?> clazz) {
        return Seq.of(getDeclareMethods(clazz)).filter(x -> Modifier.isPublic(x.getModifiers()) && !Modifier.isStatic(x.getModifiers()) && !x.isDefault());
    }

    static Seq<Method> declaredGetterMethods(Class<?> clazz) {
        if (reflectGetterCache.containsKey(clazz)) {
            final SoftReference<List<Method>> ref = reflectGetterCache.get(clazz);
            if (ref.get() != null) return Seq.seq(ref.get());
        }
        final List<Method> list = declaredPublicMethods(clazz).filter(x -> x.getName().startsWith("is") || x.getName().startsWith("get")).toList();
        reflectGetterCache.put(clazz, new SoftReference<>(list));
        return Seq.seq(list);
    }


    static boolean isGeneric(Class<?> clazz) {
        return clazz.getTypeParameters().length != 0;
    }

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object... args) {
        return method.invoke(instance, args);
    }
}
