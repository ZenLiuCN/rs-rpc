package mimic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public class ConcurrentReferenceHashMap<K, V> implements ConcurrentMap<K, V> {
    interface ReferenceFactory<V, C> {
        @NotNull C box(@Nullable Object value);

        @Nullable V unbox(@Nullable Object ref);
    }

    static abstract class ReferencedFactory<V, K extends Reference<V>> implements ReferenceFactory<V, Object> {
        protected final ReferenceQueue<V> queue;

        ReferencedFactory(ReferenceQueue<V> queue) {
            this.queue = queue;
        }

        @Override
        public @NotNull Object box(@Nullable Object value) {
            return value == null ? NULL.Null : innerBox(value);
        }

        protected abstract K innerBox(@NotNull Object value);

        protected abstract V innerUnBox(@NotNull K ref);

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable V unbox(@Nullable Object ref) {
            return ref == null || ref instanceof NULL ? null : innerUnBox((K) ref);
        }
    }

    static class StrongRefFactory<V> implements ReferenceFactory<V, Object> {

        @Override
        public @NotNull Object box(@Nullable Object value) {
            return value == null ? NULL.Null : value;
        }

        @SuppressWarnings("unchecked")
        @Override
        public @Nullable V unbox(@Nullable Object ref) {
            return ref == null || ref instanceof NULL ? null : (V) ref;
        }
    }

    static class SoftRefFactory<V> extends ReferencedFactory<V, SoftReference<V>> {

        SoftRefFactory(ReferenceQueue<V> queue) {
            super(queue);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected SoftReference<V> innerBox(@NotNull Object value) {
            return new EqualSoftReference<V>((V) value, queue);
        }

        @Override
        protected V innerUnBox(@NotNull SoftReference<V> ref) {
            return ref.get();
        }
    }

    static class WeekRefFactory<V> extends ReferencedFactory<V, WeakReference<V>> {

        WeekRefFactory(ReferenceQueue<V> queue) {
            super(queue);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected WeakReference<V> innerBox(@NotNull Object value) {
            return new EqualWeakReference<>((V) value, queue);
        }

        @Override
        protected V innerUnBox(@NotNull WeakReference<V> ref) {
            return ref.get();
        }
    }

    static class EqualSoftReference<T> extends SoftReference<T> {
        final int hashCode;
        final Class<?> type;

        EqualSoftReference(T v) {
            super(v);
            this.hashCode = v.hashCode();
            this.type = v.getClass();
        }

        EqualSoftReference(T v, ReferenceQueue<T> queue) {
            super(v, queue);
            this.hashCode = v.hashCode();
            this.type = v.getClass();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null && get() == null) return true;
            if (obj == null) return false;
            if (obj.getClass() != type) return false;
            return hashCode == obj.hashCode();
        }
    }

    static class EqualWeakReference<T> extends WeakReference<T> {
        final int hashCode;
        final Class<?> type;

        EqualWeakReference(T v) {
            super(v);
            this.hashCode = v.hashCode();
            this.type = v.getClass();
        }

        EqualWeakReference(T v, ReferenceQueue<T> queue) {
            super(v, queue);
            this.hashCode = v.hashCode();
            this.type = v.getClass();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null && get() == null) return true;
            if (obj == null) return false;
            if (obj.getClass() != type) return false;
            return hashCode == obj.hashCode();
        }
    }

    public enum ReferenceType {
        STRONG,
        SOFT,
        WEAK,
        ;
    }

    final ReferenceFactory<K, ?> keyFactory;
    final ReferenceFactory<V, ?> valueFactory;
    final ConcurrentHashMap<Object, Object> container;
    transient private volatile ReferenceQueue<K> kReferenceQueue;
    transient private volatile ReferenceQueue<V> vReferenceQueue;

    @SuppressWarnings("unchecked")
    ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType, ConcurrentHashMap<K, V> container) {
        this.container = (ConcurrentHashMap<Object, Object>) container;
        switch (keyRefType) {
            case STRONG:
                keyFactory = new StrongRefFactory<>();
                break;
            case WEAK: {
                if (kReferenceQueue == null) vReferenceQueue = new ReferenceQueue<>();
                keyFactory = new WeekRefFactory<>(kReferenceQueue);
                break;
            }
            case SOFT: {
                if (kReferenceQueue == null) kReferenceQueue = new ReferenceQueue<>();
                keyFactory = new SoftRefFactory<>(kReferenceQueue);
                break;
            }
            default:
                throw new IllegalArgumentException("not define a valid key reference type");
        }
        switch (valueRefType) {
            case STRONG:
                valueFactory = new StrongRefFactory<>();
                break;
            case WEAK: {
                if (vReferenceQueue == null) vReferenceQueue = new ReferenceQueue<>();
                valueFactory = new WeekRefFactory<>(vReferenceQueue);
                break;
            }
            case SOFT: {
                if (vReferenceQueue == null) vReferenceQueue = new ReferenceQueue<>();
                valueFactory = new SoftRefFactory<>(vReferenceQueue);
                break;
            }
            default:
                throw new IllegalArgumentException("not define a valid value reference type");
        }
    }

    public ConcurrentReferenceHashMap() {
        this(ReferenceType.STRONG, ReferenceType.STRONG, new ConcurrentHashMap<>());
    }


    public ConcurrentReferenceHashMap(int initialCapacity) {
        this(ReferenceType.STRONG, ReferenceType.STRONG, new ConcurrentHashMap<>(initialCapacity));
    }

    public ConcurrentReferenceHashMap(Map<? extends K, ? extends V> m) {
        this(ReferenceType.STRONG, ReferenceType.STRONG, new ConcurrentHashMap<>(m));
    }

    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor) {
        this(ReferenceType.STRONG, ReferenceType.STRONG, new ConcurrentHashMap<>(initialCapacity, loadFactor));
    }

    public ConcurrentReferenceHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(ReferenceType.STRONG, ReferenceType.STRONG, new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel));
    }

    public ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType) {
        this(keyRefType, valueRefType, new ConcurrentHashMap<>());
    }

    public ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType, int initialCapacity) {
        this(keyRefType, valueRefType, new ConcurrentHashMap<>(initialCapacity));
    }

    public ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType, Map<? extends K, ? extends V> m) {
        this(keyRefType, valueRefType, new ConcurrentHashMap<>(m.size()));
        m.forEach((k, v) -> container.put(keyFactory.box(k), valueFactory.box(v)));
    }

    public ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType, int initialCapacity, float loadFactor) {
        this(keyRefType, valueRefType, new ConcurrentHashMap<>(initialCapacity, loadFactor));
    }

    public ConcurrentReferenceHashMap(ReferenceType keyRefType, ReferenceType valueRefType, int initialCapacity, float loadFactor, int concurrencyLevel) {
        this(keyRefType, valueRefType, new ConcurrentHashMap<>(initialCapacity, loadFactor, concurrencyLevel));
    }

    //region Impl
    @Override
    public V putIfAbsent(@NotNull K key, V value) {
        return valueFactory.unbox(container.putIfAbsent(keyFactory.box(key), valueFactory.box(value)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean remove(@NotNull Object key, Object value) {
        return container.remove(keyFactory.box(key), valueFactory.box(value));
    }

    @Override
    public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
        return container.replace(keyFactory.box(key), valueFactory.box(oldValue), valueFactory.box(newValue));
    }

    @Override
    public V replace(@NotNull K key, @NotNull V value) {
        return valueFactory.unbox(container.replace(keyFactory.box(key), valueFactory.box(value)));
    }

    @Override
    public int size() {
        return container.size();
    }

    @Override
    public boolean isEmpty() {
        return container.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsKey(Object key) {
        return container.containsKey(keyFactory.box(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean containsValue(Object value) {
        return container.containsValue(valueFactory.box(value));
    }

    @Override
    public V get(Object key) {
        return valueFactory.unbox(container.get(keyFactory.box(key)));
    }

    @Nullable
    @Override
    public V put(K key, V value) {
        return valueFactory.unbox(container.put(keyFactory.box(key), valueFactory.box(value)));
    }

    @Override
    public V remove(Object key) {
        return valueFactory.unbox(container.remove(keyFactory.box(key)));
    }

    @Override
    public void putAll(@NotNull Map<? extends K, ? extends V> m) {
        m.forEach((k, v) -> container.put(keyFactory.box(k), valueFactory.box(v)));
    }

    @Override
    public void clear() {
        container.clear();
    }

    @NotNull
    @Override
    public Set<K> keySet() {
        return this.new SetView<>(container.keySet(), keyFactory);
    }

    class EntryFactory implements ReferenceFactory<Entry<K, V>, Object> {
        @Override
        public @NotNull Object box(@Nullable Object value) {
            if (!(value instanceof Entry)) throw new IllegalArgumentException("not a entry");
            return new AbstractMap.SimpleEntry<>(keyFactory.box(((Entry<?, ?>) value).getKey()), valueFactory.box(((Entry<?, ?>) value).getValue()));
        }

        @Nullable
        @Override
        public Entry<K, V> unbox(@Nullable Object ref) {
            if (!(ref instanceof Entry)) throw new IllegalArgumentException("not a entry");
            return new AbstractMap.SimpleEntry<>(keyFactory.unbox(((Entry<?, ?>) ref).getKey()), valueFactory.unbox(((Entry<?, ?>) ref).getValue()));
        }
    }

    class ElementIterator<X> implements Iterator<X> {
        final Iterator<Object> inner;
        final ReferenceFactory<X, ?> factory;
        volatile Object current;

        ElementIterator(Iterator<Object> inner, ReferenceFactory<X, ?> factory) {
            this.inner = inner;
            this.factory = factory;
        }

        @Override
        public boolean hasNext() {
            return inner.hasNext();
        }

        @Override
        public X next() {
            current = inner.next();
            return factory.unbox(current);
        }

        @Override
        public void remove() {
            if (keyFactory.equals(factory)) {
                container.remove(current);
            }
        }

        @Override
        public void forEachRemaining(Consumer<? super X> action) {
            inner.forEachRemaining(v -> action.accept(factory.unbox(v)));
        }
    }

    class SetView<X> extends CollectionView<X> implements Set<X> {
        SetView(Set<Object> inner, ReferenceFactory<X, ?> factory) {
            super(inner, factory);
        }

        @Override
        public boolean remove(Object o) {
            return container.remove(factory.box(o)) != null;
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            final ArrayList<Object> list = new ArrayList<>();
            for (Object o : c) {
                list.add(factory.box(c));
            }
            return inner.containsAll(list);
        }


        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            boolean happened = false;
            for (Object o : c) {
                happened = container.remove(factory.box(o)) != null;
            }
            return happened;
        }

        @Override
        public void clear() {
            container.clear();
        }
    }

    class CollectionView<X> implements Collection<X> {
        final Collection<Object> inner;
        final ReferenceFactory<X, ?> factory;

        CollectionView(Collection<Object> inner, ReferenceFactory<X, ?> factory) {
            this.inner = inner;
            this.factory = factory;
        }

        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public boolean isEmpty() {
            return inner.isEmpty();
        }

        @Override
        public boolean contains(Object o) {
            return inner.contains(factory.box(o));
        }

        @NotNull
        @Override
        public Iterator<X> iterator() {
            return new ElementIterator<>(inner.iterator(), factory);
        }

        @NotNull
        @Override
        public Object[] toArray() {
            final Object[] objects = new Object[size()];
            final Object[] ar = inner.toArray();
            for (int i = 0; i < ar.length; i++) {
                objects[i] = factory.unbox(ar[i]);
            }
            return objects;
        }

        @SuppressWarnings("unchecked")
        @NotNull
        @Override
        public <T> T[] toArray(@NotNull T[] a) {
            return (T[]) toArray();
        }

        @Override
        public boolean add(X x) {
            throw new UnsupportedOperationException("");
        }

        @Override
        public boolean remove(Object o) {
            return false;
        }

        @Override
        public boolean containsAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean addAll(@NotNull Collection<? extends X> c) {
            throw new UnsupportedOperationException("");
        }

        @Override
        public boolean removeAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public boolean retainAll(@NotNull Collection<?> c) {
            return false;
        }

        @Override
        public void clear() {

        }
    }

    @NotNull
    @Override
    public Collection<V> values() {
        return this.new CollectionView<V>(container.values(), valueFactory);
    }

    @SuppressWarnings("unchecked")
    @NotNull
    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.new SetView<Entry<K, V>>((Set<Object>) (Object) container.entrySet(), this.new EntryFactory());
    }
    //endregion

    public void purgeUnreferencedEntries() {
        container.forEach((k, v) -> {
            if (keyFactory.unbox(k) == null) container.remove(k);
            if (valueFactory.unbox(v) == null) container.remove(k);
        });
    }
}