package org.cellx.logish;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

/**
 * Lisp/Prolog-like list cell, consisting of an arbitrary head and tail objects.
 *
 * <p>This construct is the key for algorithms that construct data structures
 * using logic variables, in the style of "filling in the holes," where holes correspond
 * to logic variables.  However, this class does not depend on the notion of logic variable
 * or any other constructs.</p>
 *
 * <p>In terms of this class, any Java object can essentially pass for a list, but only the
 * meaning of the following cases is specified:</p>
 *
 * <ul>
 *     <li>A specific object {@link Cons#NIL} denotes an empty list.</li>
 *     <li>An instance of this class denotes a list whose head element is its {@link Cons#car()},
 *     and whose {@link Cons#cdr()} denotes the tail (the remainder) of the list.</li>
 *     <li>Any other object can appear as a list (or a value of {@link Cons#cdr()}), but this class
 *     can say nothing about whether such a list is empty or what its elements or tail could be.</li>
 * </ul>
 *
 * <p>A {@bold proper list} is recursively defined as either {@link Cons#NIL} or a Cons whose
 * {@link Cons#cdr()} is itself a proper list.</p>
 *
 * <p>This data structure is immutable, except when privately built using a {@link Cons.ListBuilder}.
 * However, there is no harm there because the list cannot be accessed by the outside world before
 * being returned from the {@link ListBuilder#build()} method.</p>
 */
@SuppressWarnings("unused")
public final class Cons implements Iterable<Object> {
    /**
     * A special object denoting an empty list.
     */
    public static final Object NIL = "()";
    /**
     * The head component (CAR in Lisp).
     */
    final Object car;
    /**
     * The tail component (CDR in Lisp).
     */
    protected Object cdr;

    /**
     * The main constructor from the given head and tail.
     *
     * @param car List head
     * @param cdr List tail
     */
    public Cons(Object car, Object cdr) {
        this.car = car;
        this.cdr = cdr;
    }

    /**
     * Gets the head component.
     *
     * @return The head list component (the first element of the list).
     */
    public Object car() {
        return car;
    }

    /**
     * Gets the tail component.
     *
     * @return The tail of the list (the remainder of the list).
     */
    public Object cdr() {
        return cdr;
    }

    /**
     * Gets the second element of the list.
     *
     * <p>Does not check if the tail of this Cons is a Cons, therefore may throw a
     * class cast exception.</p>
     *
     * @return The second element of the list.
     */
    public Object cadr() {
        return ((Cons) cdr).car;
    }

    /**
     * Gets the third element of the list.
     *
     * <p>Does not check if the tail of this Cons, and its tail are Conses, therefore may throw a
     * class cast exception.</p>
     *
     * @return The third element of the list.
     */
    public Object caddr() {
        return ((Cons) cdr).cadr();
    }

    /**
     * Gets the fourth element of the list.
     *
     * <p>Does not check if the tail of this Cons, its tail, and so on are Conses,
     * therefore may throw a class cast exception.</p>
     *
     * @return The fourth element of the list.
     */
    public Object cadddr() {
        return ((Cons) cdr).caddr();
    }

    /**
     * Gets the fifth element of the list.
     *
     * <p>Does not check if the tail of this Cons, its tail, and so on are Conses,
     * therefore may throw a class cast exception.</p>
     *
     * @return The fifth element of the list.
     */
    public Object caddddr() {
        return ((Cons) cdr).cadddr();
    }

    /**
     * Gets the remainder of the list after n leading elements.
     *
     * <p>This operation is safe, in the sense that it returns a special value {@code null}
     * if the list does not have n leading elements.</p>
     *
     * @param list List object
     * @param n    The number of leading elements to skip.
     * @return Either a Cons following n leading elements, or {@code null} if the list does
     * not have n leading elements.
     */
    public static Cons nth(Object list, int n) {
        if (!(list instanceof Cons)) return null;
        Cons current = (Cons) list;
        while (n > 0) {
            final Object tail = current.cdr;
            if (!(tail instanceof Cons)) return null;
            current = (Cons) tail;
            n--;
        }
        return current;
    }

    /**
     * Checks if the given object is a proper list.
     *
     * <p>A proper list is either {@link #NIL} or a Cons whose {@link #cdr()} is a proper list.</p>
     *
     * @param list An object to check.
     * @return True if the object is a proper list, false otherwise.
     */
    public static boolean isProper(Object list) {
        while (list instanceof Cons) {
            list = ((Cons) list).cdr;
        }
        return list == NIL;
    }

    /**
     * Checks if this Cons is a proper list.
     *
     * @return True or false.
     * @see #isProper(Object)
     */
    public boolean isProper() {
        return isProper(cdr);
    }

    /**
     * Sets the tail component of this Cons cell.
     *
     * <p>This is an aberration from the principle of data structure immutability, but a very
     * useful one, because it allows fast construction of Cons-based lists in specialized
     * map or filter operations.  Only visible inside the package.</p>
     *
     * @param value The new tail component.
     */
    protected void setCdr(Object value) {
        this.cdr = value;
    }

    /**
     * Creates a new list builder, starting from NIL.
     *
     * @return A new list builder
     * @see ListBuilder
     */
    public static ListBuilder builder() {
        return new ListBuilder();
    }

    /**
     * Creates a new list builder, starting from a copy of the list whose first Cons is this.
     *
     * @return A new list builder
     * @see ListBuilder
     */
    public ListBuilder copyBuilder() {
        return new ListBuilder().appendList(this);
    }

    /**
     * A list builder.
     *
     * <p>Enables fast construction of Cons-based lists, with constant-time append and prepend operations.</p>
     *
     * <p>Created by methods {@link #builder()} or {@link #copyBuilder()}.</p>
     */
    public static class ListBuilder {
        private Object list;
        private Cons last;
        private int prefixLength;

        private void reset() {
            list = NIL;
            last = null;
            prefixLength = 0;
        }

        ListBuilder() {
            reset();
        }

        /**
         * Returns the built list.
         *
         * <p>The builder is reset to the initial state.</p>
         *
         * @return The built list.
         */
        public Object build() {
            final Object result = list;
            reset();
            return result;
        }

        /**
         * Appends an element to the end of the built list.
         *
         * @param element Element to append
         * @return This builder object (fluent style interface).
         */
        @SuppressWarnings("UnusedReturnValue")
        public ListBuilder append(Object element) {
            final Cons newTail;
            if (prefixLength == 0) {
                newTail = new Cons(element, list);
                list = newTail;
            } else {
                newTail = new Cons(element, last.cdr);
                last.setCdr(newTail);
            }
            last = newTail;
            prefixLength++;
            return this;
        }

        public ListBuilder appendList(Object original) {
            while (original instanceof Cons) {
                final Cons consCell = (Cons) original;
                append(consCell.car);
                original = consCell.cdr;
            }
            setTail(original);
            return this;
        }

        public ListBuilder appendAll(Iterable<?> iterable) {
            for (Object element : iterable) {
                append(element);
            }
            return this;
        }

        public ListBuilder prepend(Object element) {
            final Cons newHead = new Cons(element, list);
            if (prefixLength == 0) {
                last = newHead;
            }
            list = newHead;
            prefixLength++;
            return this;
        }

        public ListBuilder prependList(Object source) {
            final Object savedList = list;
            final Cons savedLast = last;
            list = NIL;
            last = null;
            while (source instanceof Cons) {
                final Cons consCell = (Cons) source;
                append(consCell.car);
                source = consCell.cdr;
            }
            setTail(savedList);
            if (savedLast != null) last = savedLast;
            return this;
        }

        /**
         * Sets the final tail of the built list to the specified object.
         *
         * <p>Throws {@link IllegalStateException} if the {@link #build()} method has already
         * been called and the list was not empty.</p>
         *
         * @param tail The new final tail.
         * @return This builder object (fluent style interface).
         */
        @SuppressWarnings("UnusedReturnValue")
        public ListBuilder setTail(Object tail) {
            if (prefixLength == 0) {
                list = tail;
            } else {
                if (last == null) throw new IllegalStateException();
                last.setCdr(tail);
            }
            return this;
        }

        /**
         * Returns the number of elements of the list before the final tail.
         *
         * @return The length of the prefix.
         */
        public int getPrefixLength() {
            return prefixLength;
        }
    }


    public static int prefixLength(Object list) {
        if (list instanceof Cons) return ((Cons) list).prefixLength();
        else return 0;
    }

    public int prefixLength() {
        int len = 1;
        Object tail = cdr;
        while (tail instanceof Cons) {
            len++;
            tail = ((Cons) tail).cdr;
        }
        return len;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder("(");
        boolean first = true;
        Object current = this;
        while (current instanceof Cons) {
            final Cons consCell = (Cons) current;
            if (!first) builder.append(" ");
            builder.append(consCell.car);
            current = consCell.cdr;
            first = false;
        }
        if (!NIL.equals(current)) {
            builder.append(" . ");
            builder.append(current);
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public boolean equals(Object right) {
        Object left = this;
        while (true) {
            if (left == right) return true;
            if (!(left instanceof Cons)) return Objects.equals(left, right);
            if (!(right instanceof Cons)) return false;
            final Cons leftConsCell = (Cons) left, rightConsCell = (Cons) right;
            if (!Objects.equals(leftConsCell.car, rightConsCell.car)) return false;
            left = leftConsCell.cdr;
            right = rightConsCell.cdr;
        }
    }

    @Override
    public int hashCode() {
        int result = 0;
        Object current = this;
        while (current instanceof Cons) {
            final Cons consCell = (Cons) current;
            result ^= Objects.hashCode(consCell.car);
            current = consCell.cdr;
        }
        return result ^ Objects.hashCode(current);
    }

    public static Object mapPrefix(Object list, Function<Object, ?> function) {
        if (list instanceof Cons) return ((Cons) list).mapPrefix(function);
        else return NIL;
    }

    public Cons mapPrefix(Function<Object, ?> function) {
        final ListBuilder builder = builder();
        final Object firstResult = function.apply(car);
        Object current = cdr;
        while (current instanceof Cons) {
            final Cons consCell = (Cons) current;
            builder.append(function.apply(consCell.car));
            current = consCell.cdr;
        }
        return new Cons(firstResult, builder.build());
    }

    public static Object mapTail(Object list, Function<Object, ?> function) {
        if (list instanceof Cons) return ((Cons) list).mapTail(function);
        else return function.apply(list);
    }

    public Cons mapTail(Function<Object, ?> function) {
        final Object firstResult = function.apply(car);
        final ListBuilder builder = builder();
        Object current = cdr;
        while (current instanceof Cons) {
            Cons consCell = (Cons) current;
            builder.append(function.apply(consCell.car));
            current = consCell.cdr;
        }
        builder.setTail(function.apply(current));
        return new Cons(firstResult, builder.build());
    }

    public static Object fromIterable(Iterable<?> iterable) {
        return builder().appendAll(iterable).build();
    }

    public static Object list(Object element, Object... elements) {
        return make(NIL, element, elements);
    }

    public static Cons make(Object tail, Object element, Object... elements) {
        final ListBuilder builder = builder();
        for (Object o : elements) builder.append(o);
        builder.setTail(tail);
        return new Cons(element, builder.build());
    }

    public static Iterator<Object> iteratorFor(Object list) {
        return new ConsIterator(list);
    }

    public static Iterable<Object> iterable(Object list) {
        return () -> iteratorFor(list);
    }

    static class ConsIterator implements Iterator<Object> {
        Object current;

        ConsIterator(Object current) {
            this.current = current;
        }

        @Override
        public boolean hasNext() {
            return current instanceof Cons;
        }

        @Override
        public Object next() {
            final Object result = ((Cons) current).car;
            current = ((Cons) current).cdr;
            return result;
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new ConsIterator(this);
    }

}
