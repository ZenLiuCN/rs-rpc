package cn.zenliu.java.rs.rpc.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static cn.zenliu.java.rs.rpc.core.ContextRoutes.deRouteMark;
import static cn.zenliu.java.rs.rpc.core.ScopeContextImpl.ROUTE_MARK;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public final class UniqueList {
    final List<String> value;

    UniqueList(List<String> value) {
        this.value = value;
    }

    public static UniqueList of(List<String> source) {
        return new UniqueList(source);
    }

    public int prepare(String v) {
        if (value.contains(v)) {
            return -1;
        }
        value.add(v);
        return value.size() - 1;
    }

    public int getOrAdd(String v) {
        final int i = value.indexOf(v);
        if (i != -1) return i;
        value.add(v);
        return value.size() - 1;
    }

    public int indexOf(String v) {
        return value.indexOf(v);
    }

    public boolean contains(String v) {
        return value.contains(v);
    }

    public boolean add(String v) {
        return value.add(v);
    }

    public void withRouteMark(Consumer<String> consumer, Set<String> exclude) {
        value.forEach(x -> {
            if (!(exclude.contains(deRouteMark(x))))
                consumer.accept(x.endsWith(ROUTE_MARK + "") ? x : (x + ROUTE_MARK));
        });
    }

    public Set<String> toSetWith(UniqueList other) {
        Set<String> set = new HashSet<>(value);
        if (other != null) other.withRouteMark(set::add, set);
        return set;
    }

    public void clear() {
        value.clear();
    }

    public String get(int i) {
        return value.get(i);
    }

    public int size() {
        return value.size();
    }

    @Override
    public String toString() {
        return value.toString();
    }

    public List<String> getValue() {
        return Collections.unmodifiableList(value);
    }
}
