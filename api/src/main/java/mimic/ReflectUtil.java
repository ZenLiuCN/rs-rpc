package mimic;

import lombok.SneakyThrows;
import org.jooq.lambda.Seq;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface ReflectUtil {

    static boolean javaBeanGetterPredicate(Method method) {
        return method.getParameterCount() == 0
            && method.getReturnType() != Void.TYPE
            && (method.getName().startsWith("is") || method.getName().startsWith("get"));
    }

    static boolean fluentBeanGetterPredicate(Method method) {
        return
            method.getParameterCount() == 0
                && method.getReturnType() != Void.TYPE;
    }

    static boolean publicMethodPredicate(Method method) {
        return !method.isDefault()
            && !Modifier.isStatic(method.getModifiers())
            && Modifier.isPublic(method.getModifiers())
            ;
    }

    AtomicReference<Predicate<Method>> getterPredicate = new AtomicReference<>(ReflectUtil::javaBeanGetterPredicate);

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
        return Seq.of(getDeclareMethods(clazz))
            .filter(ReflectUtil::publicMethodPredicate);
    }

    static Seq<Method> declaredGetterMethods(Class<?> clazz) {
        if (reflectGetterCache.containsKey(clazz)) {
            final SoftReference<List<Method>> ref = reflectGetterCache.get(clazz);
            if (ref.get() != null) return Seq.seq(ref.get());
        }
        final List<Method> list = declaredPublicMethods(clazz)
            .filter(getterPredicate.get()).toList();
        reflectGetterCache.put(clazz, new SoftReference<>(list));
        return Seq.seq(list);
    }

    static String getterNameToFieldName(String name) {
        return name.startsWith("is") ? name.substring(2)
            : name.startsWith("get") ? name.substring(3) : name;
    }

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object... args) {
        return method.invoke(instance, args);
    }
}