package mimic;

import lombok.SneakyThrows;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static mimic.internal.*;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface ReflectUtil {

    static boolean javaBeanGetterPredicate(Method method) {
        return method.getParameterCount() == 0
            && method.getReturnType() != Void.TYPE
            && !CommonMethodName.contains(method.getName())
            && (method.getName().startsWith("is") || method.getName().startsWith("get"));
    }

    static boolean fluentBeanGetterPredicate(Method method) {
        return
            method.getParameterCount() == 0
                && method.getReturnType() != Void.TYPE
                && !CommonMethodName.contains(method.getName());
    }

    static boolean publicMethodPredicate(Method method) {
        return !method.isDefault()
            && !Modifier.isStatic(method.getModifiers())
            && Modifier.isPublic(method.getModifiers())
            ;
    }


    static Method[] getMethods(Class<?> clazz) {
        if (reflectMethodsCache.containsKey(clazz)) {
            final SoftReference<Method[]> ref = reflectMethodsCache.get(clazz);
            if (ref.get() != null) {
                return ref.get();
            }
        }
        final Method[] declaredMethods = clazz.getMethods();
        reflectMethodsCache.put(clazz, new SoftReference<>(declaredMethods));
        return declaredMethods;
    }

    static List<Method> publicMethods(Class<?> clazz, @Nullable Predicate<Method> methodPredicate) {
        final ArrayList<Method> list = new ArrayList<>();
        if (methodPredicate != null) {
            for (Method method : getMethods(clazz)) {
                if (publicMethodPredicate(method) && methodPredicate.test(method))
                    list.add(method);
            }
        } else {
            for (Method method : getMethods(clazz)) {
                if (publicMethodPredicate(method))
                    list.add(method);
            }
        }
        return list;
    }

    static List<Method> getterMethods(Class<?> clazz) {
        if (reflectGetterCache.containsKey(clazz)) {
            final SoftReference<List<Method>> ref = reflectGetterCache.get(clazz);
            if (ref.get() != null) return ref.get();
        }
        final List<Method> list = publicMethods(clazz, getterPredicate.get());
        reflectGetterCache.put(clazz, new SoftReference<>(list));
        return list;
    }

    static <T> List<T> getterMethodsMapping(Class<?> clazz, Function<Method, T> extractor, boolean filterNonNull) {
        final ArrayList<T> ts = new ArrayList<>();
        for (Method m : getterMethods(clazz)) {
            if (!filterNonNull) {
                ts.add(extractor.apply(m));
            } else {
                final T v = extractor.apply(m);
                if (v != null) ts.add(v);
            }
        }
        return ts;
    }

    static String getterNameToFieldName(String name) {
        return name.startsWith("is") ? name.substring(2)
            : name.startsWith("get") ? name.substring(3) : name;
    }

    List<String> CommonMethodName = Arrays.asList(
        "hashCode",
        "clone",
        "toString",
        "equals",
        "wait",
        "notify",
        "notifyAll",
        "getClass",
        "finalize",
        "canEqual"
    );

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object... args) {
        return method.invoke(instance, args);
    }

    static <T extends AccessibleObject> T accessible(T accessible) {
        if (accessible == null) {
            return null;
        }
        if (accessible instanceof Member) {
            Member member = (Member) accessible;
            if (Modifier.isPublic(member.getModifiers()) &&
                Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                return accessible;
            }
        }
        if (!accessible.isAccessible())
            accessible.setAccessible(true);
        return accessible;
    }

    static void setGetterPredicate(Predicate<Method> predicate) {
        getterPredicate.set(predicate);
    }
}
