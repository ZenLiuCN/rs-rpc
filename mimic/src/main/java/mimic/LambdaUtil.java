package mimic;

import org.jooq.lambda.tuple.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface LambdaUtil {
    static Object[] fasterArray(Object[] a, Function<Object, Object> m) {
        for (int i = 0; i < a.length; i++) {
            a[i] = m.apply(a[i]);
        }
        return a;
    }

    static Map<Object, Object> fasterMap(Map<?, ?> src, Function<Object, Object> m) {
        final HashMap<Object, Object> mp = new HashMap<>();
        src.forEach((k, v) -> mp.put(m.apply(k), m.apply(v)));
        return mp;
    }

    static List<?> fasterList(List<?> a, Function<Object, Object> m) {
        List<Object> ar = new ArrayList<>(a.size());
        for (Object o : a) {
            ar.add(m.apply(o));
        }
        return ar;
    }

    @SuppressWarnings("unchecked")
    static Tuple fasterTuple(Tuple a, Function<Object, Object> m) {
        if (a instanceof Tuple0) return a;
        else if (a instanceof Tuple1) return ((Tuple1) a)
            .map1(m);
        else if (a instanceof Tuple2) return ((Tuple2) a)
            .map1(m)
            .map2(m);
        else if (a instanceof Tuple3) return ((Tuple3) a)
            .map1(m)
            .map2(m)
            .map3(m);
        else if (a instanceof Tuple4) return ((Tuple4) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m);
        else if (a instanceof Tuple5) return ((Tuple5) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m);
        else if (a instanceof Tuple6) return ((Tuple6) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m);
        else if (a instanceof Tuple7) return ((Tuple7) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m);
        else if (a instanceof Tuple8) return ((Tuple8) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m);
        else if (a instanceof Tuple9) return ((Tuple9) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m);
        else if (a instanceof Tuple10) return ((Tuple10) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m);
        else if (a instanceof Tuple11) return ((Tuple11) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m);
        if (a instanceof Tuple12) return ((Tuple12) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m)
            .map12(m);
        else if (a instanceof Tuple13) return ((Tuple13) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m)
            .map12(m)
            .map13(m);
        else if (a instanceof Tuple14) return ((Tuple14) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m)
            .map12(m)
            .map13(m)
            .map14(m);
        else if (a instanceof Tuple15) return ((Tuple15) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m)
            .map12(m)
            .map13(m)
            .map14(m)
            .map15(m);
        else if (a instanceof Tuple16) return ((Tuple16) a)
            .map1(m)
            .map2(m)
            .map3(m)
            .map4(m)
            .map5(m)
            .map6(m)
            .map7(m)
            .map8(m)
            .map9(m)
            .map10(m)
            .map11(m)
            .map12(m)
            .map13(m)
            .map14(m)
            .map15(m)
            .map16(m);
        else return a;
    }

    static <T> T findFirst(Collection<T> src, Predicate<T> predicate) {
        for (T t : src) {
            if (predicate.test(t)) return t;
        }
        return null;
    }

    static <T> T findFirstReverse(List<T> src, Predicate<T> predicate) {
        for (int i = src.size() - 1; i >= 0; i--) {
            if (predicate.test(src.get(i))) return src.get(i);
        }
        return null;
    }
}
