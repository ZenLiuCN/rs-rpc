package mimic;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-02
 */
public interface Cache<K, V> {
    V put(K k, V v);

    V get(K k);

    V putIfAbsent(K k, V v);

    V computeIfAbsent(K k, Function<K, V> mappingFunction);

    V computeIfPresent(K k, BiFunction<K, V, V> remappingFunction);

    void dispose();

    boolean remove(K k, V v);

    boolean replace(K k, V ov, V nv);

    int size();

    boolean isEmpty();

    boolean containsKey(K k);

    boolean containsValue(V v);

    void purify();

    void clear();

    static <K, V> Cache<K, V> build(@Nullable Duration ttl, boolean softOrWeak) {
        return softOrWeak ? ttl == null ? new SoftRefCache<>() : new TTLSoftRefCache<>(ttl) : ttl == null ? new WeakRefCache<>() : new TTLWeakRefCache<>(ttl);
    }

    final class SoftRef<T> extends SoftReference<T> {
        private final int hashCode;

        SoftRef(T referent) {
            this(referent, null);
        }

        SoftRef(T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            return t != null && t.equals(obj);
        }

        static <T> SoftRef<T> of(T v, ReferenceQueue<? super T> q) {
            return new SoftRef<>(v, q);
        }

        static <T> SoftRef<T> of(T v) {
            return new SoftRef<>(v);
        }
    }

    final class TTLSoftRef<T> extends SoftReference<T> {
        private final int hashCode;

        private final long ttl;

        TTLSoftRef(T referent, int ttl) {
            this(referent, ttl, null);
        }

        TTLSoftRef(T referent, int ttl, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
            this.ttl = System.currentTimeMillis() + ttl;
        }

        @Override
        public T get() {
            if (ttl < System.currentTimeMillis()) return null;
            return super.get();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            return t != null && t.equals(obj);
        }

        static <T> TTLSoftRef<T> of(T v, int ttl, ReferenceQueue<? super T> q) {
            return new TTLSoftRef<>(v, ttl, q);
        }

        static <T> TTLSoftRef<T> of(T v, int ttl) {
            return new TTLSoftRef<>(v, ttl);
        }
    }

    final class WeakRef<T> extends WeakReference<T> {
        private final int hashCode;

        WeakRef(T referent) {
            this(referent, null);
        }

        WeakRef(T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            return t != null && t.equals(obj);
        }

        static <T> WeakRef<T> of(T v, ReferenceQueue<? super T> q) {
            return new WeakRef<>(v, q);
        }

        static <T> WeakRef<T> of(T v) {
            return new WeakRef<>(v);
        }
    }

    final class TTLWeakRef<T> extends WeakReference<T> {
        private final int hashCode;
        private final long ttl;

        TTLWeakRef(T referent, int ttl) {
            this(referent, ttl, null);
        }

        TTLWeakRef(T referent, int ttl, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = System.identityHashCode(referent);
            this.ttl = System.currentTimeMillis() + ttl;
        }

        @Override
        public T get() {
            if (ttl < System.currentTimeMillis()) return null;
            return super.get();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            return t != null && t.equals(obj);
        }

        static <T> TTLWeakRef<T> of(T v, int ttl, ReferenceQueue<? super T> q) {
            return new TTLWeakRef<>(v, ttl, q);
        }

        static <T> TTLWeakRef<T> of(T v, int ttl) {
            return new TTLWeakRef<>(v, ttl);
        }
    }


    static <T> T safeGet(Reference<T> t) {
        return t == null ? null : t.get();
    }

    @Slf4j
    abstract class BaseConcurrentCache<K, V, R extends Reference<V>> implements Cache<K, V> {
        final ConcurrentHashMap<K, R> cache = new ConcurrentHashMap<>();
        static final ReferenceQueue<?> queue = new ReferenceQueue<>();
        static final AtomicReference<List<ConcurrentHashMap<?, ?>>> removable = new AtomicReference<>(new ArrayList<>());


        protected BaseConcurrentCache() {
            removable.get().add(cache);
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize();
        }

        @Override
        public void dispose() {
            removable.get().remove(cache);
        }

        @Override
        public void purify() {
            while (true) {
                try {
                    final Reference<?> r = queue.remove(100);
                    if (r == null) break;
                    for (ConcurrentHashMap<?, ?> map : removable.get()) {
                        if (map.values().removeIf(x -> x == r)) {
                            break;
                        }
                    }
                } catch (InterruptedException ignore) {
                    log.error("InterruptedException in cache remove from {}", this, ignore);
                    break;
                }
            }
        }

        protected ReferenceQueue<?> getQueue() {
            return queue;
        }

        @Override
        public V put(K k, V v) {
            return safeGet(cache.put(k, of(v)));
        }

        protected abstract R of(V v);

        @Override
        public V get(K k) {
            return safeGet(cache.get(k));
        }

        @Override
        public V putIfAbsent(K k, V v) {
            return safeGet(cache.putIfAbsent(k, of(v)));
        }

        @Override
        public V computeIfAbsent(K k, Function<K, V> mappingFunction) {
            return safeGet(cache.computeIfAbsent(k, x -> of(mappingFunction.apply(x))));
        }

        @Override
        public V computeIfPresent(K k, BiFunction<K, V, V> remappingFunction) {
            return safeGet(cache.computeIfPresent(k, (x, y) -> of(remappingFunction.apply(x, safeGet(y)))));
        }

        @Override
        public boolean remove(K k, V v) {
            return cache.remove(k, of(v));
        }

        @Override
        public boolean replace(K k, V ov, V nv) {
            return cache.replace(k, of(ov), of(nv));
        }

        @Override
        public int size() {
            return cache.size();
        }

        @Override
        public boolean isEmpty() {
            return cache.isEmpty();
        }

        @Override
        public boolean containsKey(K k) {
            return cache.containsKey(k);
        }

        @Override
        public boolean containsValue(V v) {
            return cache.containsValue(of(v));
        }

        @Override
        public void clear() {
            cache.clear();
        }

    }

    final class SoftRefCache<K, V> extends BaseConcurrentCache<K, V, SoftRef<V>> {
        @SuppressWarnings("unchecked")
        @Override
        protected SoftRef<V> of(V v) {
            return SoftRef.of(v, (ReferenceQueue<? super V>) getQueue());
        }

    }

    final class WeakRefCache<K, V> extends BaseConcurrentCache<K, V, WeakRef<V>> {
        @SuppressWarnings("unchecked")
        @Override
        protected WeakRef<V> of(V v) {
            return WeakRef.of(v, (ReferenceQueue<? super V>) getQueue());
        }
    }

    final class TTLSoftRefCache<K, V> extends BaseConcurrentCache<K, V, TTLSoftRef<V>> {
        final int ttl;

        TTLSoftRefCache(Duration ttl) {
            this.ttl = (int) ttl.get(ChronoUnit.MILLIS);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected TTLSoftRef<V> of(V v) {
            return TTLSoftRef.of(v, ttl, (ReferenceQueue<? super V>) getQueue());
        }
    }

    final class TTLWeakRefCache<K, V> extends BaseConcurrentCache<K, V, TTLWeakRef<V>> {
        final int ttl;

        TTLWeakRefCache(Duration ttl) {
            this.ttl = (int) ttl.get(ChronoUnit.MILLIS);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected TTLWeakRef<V> of(V v) {
            return TTLWeakRef.of(v, ttl, (ReferenceQueue<? super V>) getQueue());
        }
    }


}
