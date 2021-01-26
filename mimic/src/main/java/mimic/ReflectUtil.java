package mimic;

import lombok.SneakyThrows;
import org.jooq.lambda.Seq;

import java.lang.ref.SoftReference;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
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

    static Seq<Method> publicMethods(Class<?> clazz) {
        return Seq.of(getMethods(clazz))
            .filter(ReflectUtil::publicMethodPredicate);
    }

    static Seq<Method> getterMethods(Class<?> clazz) {
        if (reflectGetterCache.containsKey(clazz)) {
            final SoftReference<List<Method>> ref = reflectGetterCache.get(clazz);
            if (ref.get() != null) return Seq.seq(ref.get());
        }
        final List<Method> list = publicMethods(clazz)
            .filter(getterPredicate.get()).toList();
        reflectGetterCache.put(clazz, new SoftReference<>(list));
        return Seq.seq(list);
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
