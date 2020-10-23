package org.cellx.logish;


import io.vavr.Tuple2;
import io.vavr.control.Option;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A fast immutable integer-to-object map.
 *
 * @param <V> the type of values to which the integer keys are mapped
 * @implNote The implementation is based on AVL trees.  Inspired by
 * <a href="https://gist.github.com/howsiwei/9c97bf655b01505f5167c60afe46ed0c">
 * howsiwei/AVLTree.java</a>.
 * @apiNote Does not allow {@code null} values. Mapping a key to
 * {@code null} value is the same as removing the key.
 */
@SuppressWarnings("unused")
abstract public class IntMap<V> implements Iterable<IntMap.Entry<V>> {

    protected final int key;
    protected final V value;
    protected final IntMap<V> left, right;
    protected final int height;
    protected final int size;
    protected final int maxKey;

    protected IntMap(int key, V value, IntMap<V> left, IntMap<V> right,
                     int height, int size, int maxKey) {
        this.key = key;
        this.value = value;
        this.left = left;
        this.right = right;
        this.height = height;
        this.size = size;
        this.maxKey = maxKey;
    }

    /**
     * Creates an empty map.
     *
     * @return An empty map.
     */
    @SuppressWarnings("unchecked")
    public static <V> IntMap<V> empty() {
        return (Nil<V>) Nil.NIL;
    }

    protected static class Triplet<V> {
        final int key;
        final V value;
        final IntMap<V> subTree;

        public Triplet(int key, V value, IntMap<V> subTree) {
            this.key = key;
            this.value = value;
            this.subTree = subTree;
        }
    }

    /**
     * Checks if the map is empty.
     *
     * @return {@code true} if empty (no key-value mappings in this map), {@code false} otherwise.
     */
    public abstract boolean isEmpty();

    /**
     * Gets the size of the map.
     *
     * @return The map size (number of key-value mappings).
     * @implNote Runs in O(1) time.
     */
    public int size() {
        return this.size;
    }

    /**
     * Returns the greatest key in the map.
     *
     * @return The greatest key.
     * @apiNote This method makes sense only if {@link #isEmpty()} return {@code false}.
     * @implNote Runs in O(1) time.
     */
    public int maxKey() {
        return this.maxKey;
    }

    /**
     * Returns the least key in the map.
     *
     * @return The least key.
     * @apiNote This method makes sense only if {@link #isEmpty()} return {@code false}.
     * @implNote Unlike {@link #maxKey} which runs in O(1), this method runs in O(log N) time.
     */
    public abstract int minKey();

    /**
     * Looks up value for a given key.
     *
     * @param key the given key
     * @return Value to which {@code key} is mapped (not {@code null}), or {@code null} if the map does not
     * contain the given key.
     */
    public abstract V getOrNull(int key);

    /**
     * Checks if the map contains a key.
     *
     * @param key the given key
     * @return {@code true} if the map contains the given key, {@code false} otherwise.
     * @implNote Equivalent to {@code getOrNull(key) != null}.
     */
    public boolean contains(int key) {
        return getOrNull(key) != null;
    }

    /**
     * Checks if the map contains a specific key-value mapping.
     *
     * @param key   the given key
     * @param value the given value
     * @return <ul>
     * <li>If {@code value != null}: returns {@code true} iff {@code key} maps to an object
     * that either is identical to, or {@code equals{}}, the given {@code value}.</li>
     * <li>If {@code value == null}: equivalent to {@code !contains(key)}.</li>
     * </ul>
     */
    public boolean contains(int key, V value) {
        return Objects.equals(value,  getOrNull(key));
    }

    /**
     * Gets an optional value of a given key.
     *
     * @param key the given key
     * @return Optional mapping.
     */
    public Option<V> get(int key) {
        final V value = getOrNull(key);
        return value == null? Option.none(): Option.some(value);
    }

    /**
     * Gets an optional value for a given key.
     *
     * @param key the given key
     * @return Optional mapping.
     */
    public Optional<V> getOptional(int key) {
        return Optional.ofNullable(getOrNull(key));
    }

    /**
     * Extends this map with a key-value mapping.
     *
     * @param key   the key
     * @param value the value
     * @return A map that is the same as this map, except that {@code key} is
     * mapped to {@code value}.
     * @apiNote When {@code value==null}, this method is equivalent to {@code without(key)}.
     * @implNote If this map already contains a mapping from the same {@code key} to
     * an object which is either identical or {@code equals()} the given {@code value},
     * the result is this map, otherwise a new, extended map, is constructed.
     * @see #without(int)
     */
    public abstract IntMap<V> with(int key, V value);

    protected abstract Triplet<V> pollFirst();

    /**
     * Excludes a given key from this map.
     *
     * @param key the given key
     * @return A map that is the same as this map, except that it does not contain {@code key}.
     * @implNote If this map already does not contain {@code key}, the result is this map, otherwise a new,
     * restricted, map is constructed.
     */
    public abstract IntMap<V> without(int key);

    /**
     * Excludes a specific key-value binding from this map.
     *
     * @param key   the specific key
     * @param value the specific value
     * @return A map that contains all bindings from this map, except for the specific key-value binding.
     * @apiNote If {@code value == null}, this map is returned. If this map contains a binding from {@code key}
     * to an object that does not correspond to {@code value} in terms of {@link Objects#equals(Object, Object)},
     * the mapping is left untouched.
     */
    public IntMap<V> without(int key, V value) {
        if (value == null) return this;
        final V y = getOrNull(key);
        if (y == null || !Objects.equals(y, value)) return this;
        return without(key);
    }

    protected abstract String toString(String indent);

    /**
     * Returns a printable string representation of the map.
     *
     * @return The string representation.
     */
    public String toString() {
        return "{\n" + this.toString("  ") + "\n}";
    }

    /**
     * Map entry.
     *
     * @param <V> the value type
     */
    public static class Entry<V> {
        /**
         * Key field (immutable).
         */
        public final int key;
        /**
         * Value field (immutable).
         */
        public final V value;

        /**
         * Constructs a new entry.
         *
         * @param key   the key
         * @param value the value
         */
        public Entry(int key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> entry = (Entry<?>) o;
            return key == entry.key &&
                    Objects.equals(value, entry.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value);
        }

        /**
         * Key getter.
         *
         * @return the key
         */
        public int getKey() {
            return key;
        }

        /**
         * Value getter.
         *
         * @return the value
         */
        public V getValue() {
            return value;
        }
    }

    /**
     * Constructs a new entry.
     *
     * @param <V>   the value type
     * @param key   the key
     * @param value the value
     * @return A new entry.
     */
    public static <V> Entry<V> entry(int key, V value) {
        return new Entry<>(key, value);
    }

    /**
     * Returns an iterator over elements of this map.
     *
     * @return An iterator of entries in the map.
     * @see Entry
     */
    public abstract Iterator<Entry<V>> iterator();

    /**
     * Adds new entries to the map.
     *
     * @param source an iterable collection of entries
     * @param <E>    entry value type, must be a subset of {@link V}
     * @return A map that includes all new entries on top of this map.
     */
    public <E extends V> IntMap<V> withAll(Iterable<Entry<E>> source) {
        IntMap<V> result = this;
        for (Entry<E> entry : source) {
            result = result.with(entry.key, entry.value);
        }
        return result;
    }


    /**
     * Returns a map from an iterable collection of entries.
     *
     * @param source the iterable collection of entries
     * @param <V>    entry value type
     * @return A map containing all entries from the collection.
     */
    public static <V> IntMap<V> ofAll(Iterable<Entry<V>> source) {
        final IntMap<V> init = empty();
        return init.withAll(source);
    }

    /**
     * Adds new entries to the map.
     *
     * @param source    an iterable collection of items
     * @param entryFunc a function mapping an item to a map entry
     * @param <U>       item type
     * @return A map that includes all new entries on top of this map.
     * @implSpec Items for which {@code entryFunc} returns {@code null} are skipped.
     */
    public <U> IntMap<V> withAll(Iterable<U> source, Function<U, Entry<V>> entryFunc) {
        IntMap<V> result = this;
        for (U item : source) {
            final Entry<V> entry = entryFunc.apply(item);
            if (entry == null) continue;
            result = result.with(entry.key, entry.value);
        }
        return result;
    }

    /**
     * Constructs a map from a collection of items.
     *
     * @param source    the iterable collection of items.
     * @param entryFunc a function mapping an item to a map entry
     * @param <U>       item type
     * @param <V>       map value type
     * @return New map constructed from items.
     * @implSpec Items for which {@code entryFunc} returns {@code null} are skipped.
     */
    public static <U, V> IntMap<V> ofAll(Iterable<U> source, Function<U, Entry<V>> entryFunc) {
        final IntMap<V> init = IntMap.empty();
        return init.withAll(source, entryFunc);
    }

    /**
     * Excludes specific entries from the map.
     *
     * @param source    an iterable collection of items
     * @param entryFunc a function mapping an item to an entry
     * @param <U>       item type
     * @return A copy of this map without entries corresponding to the collection items.
     * @apiNote This function looks for both the elements and their keys. Keys mapping to objects other than \
     * the elements from the collection are preserved.
     * @implSpec Items on which {@code entryFunc} returns {@code nUll} are skipped.
     * @see #without(int, Object)
     */
    public <U> IntMap<V> withoutAll(Iterable<U> source, Function<U, Entry<V>> entryFunc) {
        IntMap<V> result = this;
        for (U item : source) {
            final Entry<V> entry = entryFunc.apply(item);
            if (entry == null) continue;
            result = result.without(entry.key, entry.value);
        }
        return result;
    }


    /**
     * Removes specific entries from this map.
     *
     * @param source an iterable collection of entries
     * @param <E>    entry type, must be a subset of {@link V}
     * @return A copy of this map from which all entries from the collection are removed.
     * @implNote This method looks at both keys and values of the entries.  Entries in this map that
     * are not {@link Objects#equals(Object, Object)} to an entry in the collection are preserved.
     */
    public <E extends V> IntMap<V> withoutAll(Iterable<Entry<E>> source) {
        IntMap<V> result = this;
        for (Entry<E> entry : source) {
            result = result.without(entry.key);
        }
        return result;
    }

    /**
     * Removes keys from the map.
     *
     * @param source  an iterable collection of items
     * @param keyFunc a function from items to keys
     * @param <U>     item type
     * @return A copy of this map without keys from the collection.
     */
    public <U> IntMap<V> withoutKeys(Iterable<U> source, Function<U, Integer> keyFunc) {
        IntMap<V> result = this;
        for (U item : source) {
            result = result.without(keyFunc.apply(item));
        }
        return result;
    }

    /**
     * Removes specific keys.
     *
     * @param key  the first key
     * @param keys zero or more other keys
     * @return A copy of this map without the specified keys.
     */
    public IntMap<V> withoutKeys(int key, int... keys) {
        IntMap<V> result = without(key);
        for (int i = keys.length - 1; i >= 0; i--) {
            result = result.without(keys[i]);
        }
        return result;
    }

    /**
     * Keeps only entries satisfying an entry predicate.
     *
     * @param predicate the entry predicate
     * @return A map containing only entries satisfying the predicate.
     */
    public IntMap<V> filter(Predicate<Entry<V>> predicate) {
        IntMap<V> result = this;
        for (Entry<V> entry : this) {
            if (!predicate.test(entry)) result = result.without(entry.key);
        }
        return result;
    }

    /**
     * Keeps entries satisfying a key-value predicate.
     *
     * @param predicate the key-value predicate
     * @return A map containing only entries satisfying the predicate.
     */
    public IntMap<V> filter(BiPredicate<Integer, V> predicate) {
        return filter(e -> predicate.test(e.key, e.value));
    }

    /**
     * Keeps entries NOT satisfying an entry predicate.
     *
     * @param predicate the entry predicate
     * @return A map containing only entries from this map _not_ satisfying the predicate.
     */
    public IntMap<V> filterNot(Predicate<Entry<V>> predicate) {
        IntMap<V> result = this;
        for (Entry<V> entry : this) {
            if (predicate.test(entry)) result = result.without(entry.key);
        }
        return result;
    }

    /**
     * Keeps entries NOT satisfying a key-value predicate.
     *
     * @param predicate the key-value predicate
     * @return A map containing only entries from this map _not_ satisfying the predicate.
     */
    public IntMap<V> filterNot(BiPredicate<Integer, V> predicate) {
        return filter(e -> predicate.test(e.key, e.value));
    }

    /**
     * Splits the map according to the predicate.
     *
     * @param predicate the predicate
     * @return A tuple of maps: the first with entries satisfying the predicate, and the second
     * with entries not satisfying it.
     */
    public Tuple2<IntMap<V>, IntMap<V>> splitOn(Predicate<Entry<V>> predicate) {
        IntMap<V> positives = empty();
        IntMap<V> negatives = empty();
        for (Entry<V> entry : this) {
            if (predicate.test(entry)) positives = positives.with(entry.key, entry.value);
            else negatives = negatives.with(entry.key, entry.value);
        }
        return new Tuple2<>(positives, negatives);
    }

    /**
     * Transforms map entries.
     *
     * @param f an entry transformation function
     * @return A map containing the result of applying {@code f} to each entry in this map.
     * @implSpec If {@code f} returns {@code null} on an entry, that entry is not added to the result.
     */
    public IntMap<V> map(Function<Entry<V>, Entry<V>> f) {
        IntMap<V> result = empty();
        for (Entry<V> entry : this) {
            final Entry<V> y = f.apply(entry);
            if (y != null) result = result.with(y.key, y.value);
        }
        return result;
    }

    /**
     * Transforms map values.
     *
     * @param f a function mapping an existing key and value into a new value for the same key
     * @return A map containing the results of the transformation.
     * @implSpec If {@code f} returns {@code null} on a key-value pair, that key is not present
     * in the result.
     */
    public IntMap<V> mapValues(BiFunction<Integer, V, V> f) {
        IntMap<V> result = empty();
        for (Entry<V> entry : this) {
            final V y = f.apply(entry.key, entry.value);
            if (y != null) result = result.with(entry.key, y);
        }
        return result;
    }

    public abstract void checkConsistency();

    private static class Nil<V> extends IntMap<V> {

        private static final Nil<Object> NIL = new Nil<>();

        public Nil() {
            super(-1, null, null, null, 0, 0, -1);
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public int minKey() {
            return 0;
        }

        @Override
        public V getOrNull(int key) {
            return null;
        }

        @Override
        public IntMap<V> with(int key, V value) {
            if (value == null) return this;
            return new Node<>(key, value, this, this);
        }

        @Override
        protected Triplet<V> pollFirst() {
            return new Triplet<>(-1, null, this);
        }

        @Override
        public IntMap<V> without(int key) {
            return null;
        }

        @Override
        protected String toString(String indent) {
            return "";
        }

        private class NilIterator implements Iterator<Entry<V>> {
            @Override
            public boolean hasNext() {
                return false;
            }

            @Override
            public Entry<V> next() {
                throw new IllegalStateException("Call to next() on empty tree iterator.");
            }
        }

        @Override
        public Iterator<Entry<V>> iterator() {
            return new NilIterator();
        }

        @Override
        public void checkConsistency() {
        }
    }

    private static class Node<V> extends IntMap<V> {


        public Node(int key, V value, IntMap<V> left, IntMap<V> right) {
            super(key, value, left, right, Math.max(left.height, right.height) + 1,
                    left.size + right.size + 1, Math.max(key, right.maxKey));
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        private Node<V> balanceLeft(int key, V value, IntMap<V> left, IntMap<V> right) {
            if (left.height <= right.height + 1) {
                return new Node<>(key, value, left, right);
            } else if (left.left.height > right.height) {
                return new Node<>(left.key, left.value, left.left,
                        new Node<>(key, value, left.right, right));
            } else {
                return new Node<>(left.right.key, left.right.value,
                        new Node<>(left.key, left.value, left.left, left.right.left),
                        new Node<>(key, value, left.right.right, right));
            }
        }

        private Node<V> balanceRight(int key, V value, IntMap<V> left, IntMap<V> right) {
            if (left.height + 1 >= right.height) {
                return new Node<>(key, value, left, right);
            } else if (left.height < right.right.height) {
                return new Node<>(right.key, right.value,
                        new Node<>(key, value, left, right.left),
                        right.right);
            } else {
                return new Node<>(right.left.key, right.left.value,
                        new Node<>(key, value, left, right.left.left),
                        new Node<>(right.key, right.value, right.left.right, right.right));
            }
        }

        public IntMap<V> with(int key, V value) {
            if (value == null) return without(key);

            final int c = key - this.key;
            if (c < 0) {
                final IntMap<V> left = this.left.with(key, value);
                if (left != this.left) {
                    return balanceLeft(this.key, this.value, left, this.right);
                } else {
                    return this;
                }
            } else if (c > 0) {
                final IntMap<V> right = this.right.with(key, value);
                if (right != this.right) {
                    return balanceRight(this.key, this.value, this.left, right);
                } else {
                    return this;
                }
            } else if (value == this.value || value.equals(this.value)) {
                return this;
            } else {
                return new Node<>(key, value, this.left, this.right);
            }
        }

        protected Triplet<V> pollFirst() {
            if (this.left instanceof Nil) {
                return new Triplet<>(this.key, this.value, this.right);
            } else {
                final Triplet<V> p = this.left.pollFirst();
                return new Triplet<>(p.key, p.value, balanceRight(this.key, this.value, p.subTree, this.right));
            }
        }

        public IntMap<V> without(int key) {
            int c = key - this.key;
            if (c < 0) {
                final IntMap<V> left = this.left.without(key);
                if (left != this.left) {
                    return balanceRight(this.key, this.value, left, this.right);
                } else {
                    return this;
                }
            } else if (c > 0) {
                final IntMap<V> right = this.right.without(key);
                if (right != this.right) {
                    return balanceLeft(this.key, this.value, this.left, right);
                } else {
                    return this;
                }
            } else if (this.right instanceof Nil) {
                return this.left;
            } else {
                final Triplet<V> p = this.right.pollFirst();
                return balanceLeft(p.key, p.value, this.left, p.subTree);
            }
        }

        protected String toString(String indent) {
            String indent1 = indent + "  ";
            return this.left.toString(indent1)
                    + indent + this.key + ": " + this.value + "\n"
                    + this.right.toString(indent1);
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public int minKey() {
            Node<?> node = this;
            int result = this.key;
            while (node.left instanceof Node) {
                node = (Node<?>) node.left;
                result = node.key;
            }
            return result;
        }

        @Override
        public V getOrNull(int key) {
            if (key == this.key) return this.value;
            Node<V> node = this;
            do {
                final IntMap<V> next = (key < node.key ? node.left : node.right);
                if (!(next instanceof Node)) return null;
                node = (Node<V>) next;
            } while (key != node.key);
            return node.value;
        }

        protected class NodeIterator implements Iterator<Entry<V>> {

            private boolean iteratingLeft = true;
            private Iterator<Entry<V>> subIterator =
                    (left instanceof Node ? left.iterator() : null);
            private Entry<V> next = null;

            @Override
            public boolean hasNext() {
                if (iteratingLeft) {
                    if (subIterator != null && subIterator.hasNext()) {
                        next = subIterator.next();
                        return true;
                    }
                    iteratingLeft = false;
                    next = new Entry<>(key, value);
                    subIterator = (right instanceof Node ? right.iterator() : null);
                    return true;
                } else { // iterating right subtree
                    if (subIterator != null && subIterator.hasNext()) {
                        next = subIterator.next();
                        return true;
                    } else {
                        next = null;
                        return false;
                    }
                }
            }

            @Override
            public Entry<V> next() {
                if (next != null || hasNext()) {
                    final Entry<V> result = next;
                    if (result == null) throw new IllegalStateException();
                    next = null;
                    return result;
                } else throw new NoSuchElementException("right subtree exhausted");
            }
        }

        @Override
        public Iterator<Entry<V>> iterator() {
            return new NodeIterator();
        }

        @Override
        public void checkConsistency() {
            if (Math.abs(left.height - right.height) > 1) {
                throw new IllegalStateException("unbalanced AVL tree");
            }
            left.checkConsistency();
            right.checkConsistency();
        }
    } // class Node

}