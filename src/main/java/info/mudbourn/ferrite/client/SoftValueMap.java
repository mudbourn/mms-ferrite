package info.mudbourn.ferrite.client;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// Concurrent map whose values the GC may clear under heap pressure.
public final class SoftValueMap<K, V> extends AbstractMap<K, V> {

    public static volatile boolean ENABLED = true;

    private final ConcurrentHashMap<K, Entry<K, V>> map = new ConcurrentHashMap<>();
    private final ReferenceQueue<V> queue = new ReferenceQueue<>();

    private static final class Entry<K, V> extends SoftReference<V> {

        private final K key;

        private Entry(K key, V value, ReferenceQueue<V> queue) {
            super(value, queue);
            this.key = key;
        }
    }

    @SuppressWarnings("unchecked")
    private void purge() {
        Entry<K, V> dead;
        while ((dead = (Entry<K, V>) queue.poll()) != null) {
            map.remove(dead.key, dead);
        }
    }

    @Override
    public V get(Object key) {
        Entry<K, V> entry = map.get(key);
        return entry == null ? null : entry.get();
    }

    @Override
    public V put(K key, V value) {
        purge();
        Entry<K, V> old = map.put(key, new Entry<>(key, value, queue));
        return old == null ? null : old.get();
    }

    @Override
    public V remove(Object key) {
        Entry<K, V> old = map.remove(key);
        return old == null ? null : old.get();
    }

    @Override
    public V computeIfAbsent(K key, Function<? super K, ? extends V> function) {
        V value = get(key);
        if (value != null) {
            return value;
        }
        V computed = function.apply(key);
        if (computed == null) {
            return null;
        }
        purge();
        Entry<K, V> fresh = new Entry<>(key, computed, queue);
        Entry<K, V> winner = map.compute(key, (k, cur) -> {
            if (cur != null && cur.get() != null) {
                return cur;
            }
            return fresh;
        });
        V result = winner.get();
        return result != null ? result : computed;
    }

    @Override
    public int size() {
        purge();
        return map.size();
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        Map<K, V> live = new HashMap<>();
        map.forEach((k, entry) -> {
            V value = entry.get();
            if (value != null) {
                live.put(k, value);
            }
        });
        return live.entrySet();
    }
}
