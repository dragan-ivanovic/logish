package org.cellx.logish;

import io.vavr.control.Option;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

public class IdentityMap<K, V> implements Iterable<Map.Entry<K,V>> {

    class Link {
        final K key;
        final V value;
        final Link next;

        Link(K key, V value, Link next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    private final IntMap<Link> map;

    IdentityMap(IntMap<Link> map) {
        this.map = map;
    }

    public V lookup(K key) {
        Link link = map.getOrNull(System.identityHashCode(key));
        while (link != null) {
            if (link.key == key) return link.value;
            link = link.next;
        }
        return null;
    }

    public Option<V> get(K key) {
        final V value = lookup(key);
        if (value == null) return Option.none();
        else return Option.some(value);
    }

    public boolean contains(K key) {
        return lookup(key) != null;
    }

    public IdentityMap<K, V> with(K key, V value) {
        final int hash = System.identityHashCode(key);
        final Link link = map.getOrNull(hash);
        final IntMap<Link> newMap;

        if (link == null) {
            newMap = map.with(hash, new Link(key, value, null));
        } else {
            newMap = map.with(hash, new Link(key, value, removeLinkWithKey(link, key)));
        }

        if (newMap == map) return this;
        else return new IdentityMap<K, V>(newMap);
    }

    private Link removeLinkWithKey(Link link, K key) {
        if (link == null) {
            return null;
        } else if (link.key == key) {
            return link.next;
        } else {
            final Link newNext = removeLinkWithKey(link.next, key);
            if (newNext == link.next) return link;
            else return new Link(link.key, link.value, newNext);
        }
    }

    public IdentityMap<K, V> without(K key) {
        final int hash = System.identityHashCode(key);
        final Link link = map.getOrNull(hash);

        if (link == null) {
            return this;
        } else {
            final Link normalized = removeLinkWithKey(link, key);
            if (normalized == link) return this;
            else return new IdentityMap<K,V>(map.with(hash, normalized));
        }
    }

    static class EntryImpl<KT, VT> implements Map.Entry<KT, VT> {
        final KT key;
        final VT value;

        public EntryImpl(KT key, VT value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public KT getKey() {
            return key;
        }

        @Override
        public VT getValue() {
            return value;
        }

        @Override
        public VT setValue(VT value) {
            throw new UnsupportedOperationException();
        }
    }

    class EntryIterator implements Iterator<Map.Entry<K, V>> {

        private final Iterator<IntMap.Entry<Link>> subordinateIterator = map.iterator();
        private Link currentLink = null;
        private boolean isHasNextDecided = false;
        private boolean hasNext;
        private Map.Entry<K, V> next;

        @Override
        public boolean hasNext() {
            if (!isHasNextDecided) {
                if (currentLink != null) {
                    hasNext = true;
                    next = new EntryImpl<>(currentLink.key, currentLink.value);
                    currentLink = currentLink.next;
                } else if (subordinateIterator.hasNext()) {
                    hasNext = true;
                    currentLink = subordinateIterator.next().value;
                    next = new EntryImpl<>(currentLink.key, currentLink.value);
                    currentLink = currentLink.next;
                } else {
                    hasNext = false;
                }
                isHasNextDecided = true;
            }
            return hasNext;
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) throw new NoSuchElementException();
            isHasNextDecided = false;
            final Map.Entry<K, V> result = next;
            next = null;
            return result;
        }
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
        return new EntryIterator();
    }
}
