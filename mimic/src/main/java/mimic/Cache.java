package mimic;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jooq.lambda.Seq;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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

    @Unmodifiable Set<K> getKeys();

    @Unmodifiable Collection<V> getValues();


    boolean remove(K k, V v);

    boolean replace(K k, V ov, V nv);

    int size();

    boolean isEmpty();

    boolean containsKey(K k);

    boolean containsValue(V v);

    void purify();

    void clear();

    static <K, V> Cache<K, V> build(@Nullable Duration ttl, boolean softOrWeak) {
        return softOrWeak ?
            ttl == null ? new SoftRefCache<>(true) : new TTLSoftRefCache<>(ttl, true)
            : ttl == null ? new WeakRefCache<>(true) : new TTLWeakRefCache<>(ttl, true);
    }

    static <K, V> Cache<K, V> buildNoneAutoPurify(@Nullable Duration ttl, boolean softOrWeak) {
        return softOrWeak ?
            ttl == null ? new SoftRefCache<>(false) : new TTLSoftRefCache<>(ttl, false)
            : ttl == null ? new WeakRefCache<>(false) : new TTLWeakRefCache<>(ttl, false);
    }

    interface WithTTl {
        boolean isAutoPurify();

        boolean isExpired();

        WithTTl putArray(List<WithTTl> array);

        boolean enqueue();
    }

    final class SoftRef<T> extends SoftReference<T> {
        private final int hashCode;

        SoftRef(@NonNull T referent) {
            this(referent, null);
        }

        SoftRef(@NonNull T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = referent.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            if (t == obj) return true;
            if (obj instanceof Reference<?>) {
                return t != null && t.equals(((Reference<?>) obj).get());
            }
            return t != null && t.equals(obj);
        }

        static <T> SoftRef<T> of(T v, ReferenceQueue<? super T> q) {
            return new SoftRef<>(v, q);
        }

        static <T> SoftRef<T> of(T v) {
            return new SoftRef<>(v);
        }
    }

    final class TTLSoftRef<T> extends SoftReference<T> implements WithTTl {
        private final int hashCode;
        @Getter private final boolean autoPurify;

        private final long ttl;

        TTLSoftRef(@NonNull T referent, int ttl, boolean autoPurify) {
            this(referent, ttl, null, autoPurify);
        }

        TTLSoftRef(@NonNull T referent, int ttl, ReferenceQueue<? super T> q, boolean autoPurify) {
            super(referent, q);
            this.hashCode = referent.hashCode();
            this.ttl = System.currentTimeMillis() + ttl;
            this.autoPurify = autoPurify;
        }

        @Override
        public TTLSoftRef<T> putArray(List<WithTTl> array) {
            array.add(this);
            return this;
        }

        @Override
        public T get() {
            if (isExpired()) return null;
            return super.get();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            if (t == obj) return true;
            if (obj instanceof Reference<?>) {
                return t != null && t.equals(((Reference<?>) obj).get());
            }
            return t != null && t.equals(obj);
        }

        static <T> TTLSoftRef<T> of(T v, boolean autoPurify, int ttl, ReferenceQueue<? super T> q) {
            return new TTLSoftRef<>(v, ttl, q, autoPurify);
        }

        static <T> TTLSoftRef<T> of(T v, boolean autoPurify, int ttl) {
            return new TTLSoftRef<>(v, ttl, autoPurify);
        }

        @Override
        public boolean isExpired() {
            return ttl < System.currentTimeMillis() && !super.isEnqueued();
        }

        @Override
        public boolean enqueue() {
            return super.enqueue();
        }
    }

    final class WeakRef<T> extends WeakReference<T> {
        private final int hashCode;

        WeakRef(@NonNull T referent) {
            this(referent, null);
        }

        WeakRef(@NonNull T referent, ReferenceQueue<? super T> q) {
            super(referent, q);
            this.hashCode = referent.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            if (t == obj) return true;
            if (obj instanceof Reference<?>) {
                return t != null && t.equals(((Reference<?>) obj).get());
            }
            return t != null && t.equals(obj);
        }

        static <T> WeakRef<T> of(T v, ReferenceQueue<? super T> q) {
            return new WeakRef<>(v, q);
        }

        static <T> WeakRef<T> of(T v) {
            return new WeakRef<>(v);
        }
    }

    final class TTLWeakRef<T> extends WeakReference<T> implements WithTTl {
        private final int hashCode;
        private final long ttl;
        @Getter private final boolean autoPurify;

        TTLWeakRef(@NonNull T referent, int ttl, boolean autoPurify) {
            this(referent, ttl, null, autoPurify);
        }

        TTLWeakRef(@NonNull T referent, int ttl, ReferenceQueue<? super T> q, boolean autoPurify) {
            super(referent, q);
            this.hashCode = referent.hashCode();
            this.ttl = System.currentTimeMillis() + ttl;
            this.autoPurify = autoPurify;
        }

        @Override
        public TTLWeakRef<T> putArray(List<WithTTl> array) {
            array.add(this);
            return this;
        }

        @Override
        public T get() {
            if (isExpired()) return null;
            return super.get();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            final T t = get();
            if (t == obj) return true;
            if (obj instanceof Reference<?>) {
                return t != null && t.equals(((Reference<?>) obj).get());
            }
            return t != null && t.equals(obj);
        }

        static <T> TTLWeakRef<T> of(T v, boolean autoPurify, int ttl, ReferenceQueue<? super T> q) {
            return new TTLWeakRef<>(v, ttl, q, autoPurify);
        }

        static <T> TTLWeakRef<T> of(T v, boolean autoPurify, int ttl) {
            return new TTLWeakRef<>(v, ttl, autoPurify);
        }

        @Override
        public boolean isExpired() {
            return ttl < System.currentTimeMillis() && !super.isEnqueued();
        }

        @Override
        public boolean enqueue() {
            return super.enqueue();
        }
    }


    static <T> T safeGet(Reference<T> t) {
        return t == null ? null : t.get();
    }

    static void purifyAll() {
        BaseConcurrentCache.purifyAll();
    }

    static void purifyTTL() {
        BaseConcurrentCache.purifyTTL();
    }

    @Slf4j
    abstract class BaseConcurrentCache<K, V, R extends Reference<V>> implements Cache<K, V> {
        final ConcurrentHashMap<K, R> cache = new ConcurrentHashMap<>();
        final ReferenceQueue<?> queue = new ReferenceQueue<>();
        static final List<WeakReference<BaseConcurrentCache<?, ?, ?>>> queuePool = new CopyOnWriteArrayList<>();
        static final List<WithTTl> ttlQueuePool = new CopyOnWriteArrayList<>();

        static void purifyTTL() {
            for (WithTTl ttl : ttlQueuePool) {
                if (ttl.isExpired()) ttl.enqueue();
            }
        }

        static void purifyAll() {

            for (WeakReference<BaseConcurrentCache<?, ?, ?>> r : queuePool) {
                final BaseConcurrentCache<?, ?, ?> cache = r.get();
                if (cache == null) {
                    queuePool.remove(r);
                } else {
                    Reference<?> ref = cache.queue.poll();
                    if (ref == null) continue;
                    do {
                        final Reference<?> reff = ref;
                        cache.cache.values().removeIf(x -> x.hashCode() == reff.hashCode());
                        ref = cache.queue.poll();
                    } while (ref != null);
                }
            }
        }

        final boolean autoPurify;

        protected BaseConcurrentCache(boolean autoPurify) {
            if (autoPurify) queuePool.add(new WeakReference<>(this));
            this.autoPurify = autoPurify;
        }


        @Override
        public void purify() {
            cache.values().removeIf(x -> x.get() == null);
        }

        protected ReferenceQueue<?> getQueue() {
            return autoPurify ? queue : null;
        }

        @Override
        public Set<K> getKeys() {
            return Collections.unmodifiableSet(cache.keySet());
        }

        @Override
        public @Unmodifiable Collection<V> getValues() {
            return Collections
                .unmodifiableCollection(Seq.seq(cache.values())
                    .map(Reference::get)
                    .filter(Objects::nonNull)
                    .toCollection(ArrayList::new));
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
            return cache.get(k) != null && cache.get(k).get() != null;
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
        protected SoftRefCache(boolean autoPurify) {
            super(autoPurify);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected SoftRef<V> of(V v) {
            return SoftRef.of(v, (ReferenceQueue<? super V>) getQueue());
        }

    }

    final class WeakRefCache<K, V> extends BaseConcurrentCache<K, V, WeakRef<V>> {
        protected WeakRefCache(boolean autoPurify) {
            super(autoPurify);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected WeakRef<V> of(V v) {
            return WeakRef.of(v, (ReferenceQueue<? super V>) getQueue());
        }
    }

    final class TTLSoftRefCache<K, V> extends BaseConcurrentCache<K, V, TTLSoftRef<V>> {
        final int ttl;

        TTLSoftRefCache(Duration ttl, boolean autoPurify) {
            super(autoPurify);
            this.ttl = (int) ttl.get(ChronoUnit.MILLIS);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected TTLSoftRef<V> of(V v) {
            return TTLSoftRef.of(v, autoPurify, ttl, (ReferenceQueue<? super V>) getQueue()).putArray(BaseConcurrentCache.ttlQueuePool);
        }
    }

    final class TTLWeakRefCache<K, V> extends BaseConcurrentCache<K, V, TTLWeakRef<V>> {
        final int ttl;

        TTLWeakRefCache(Duration ttl, boolean autoPurify) {
            super(autoPurify);
            this.ttl = (int) ttl.get(ChronoUnit.MILLIS);
        }

        @SuppressWarnings("unchecked")
        @Override
        protected TTLWeakRef<V> of(V v) {
            return TTLWeakRef.of(v, autoPurify, ttl, (ReferenceQueue<? super V>) getQueue()).putArray(BaseConcurrentCache.ttlQueuePool);
        }
    }


}