package org.cellx.vavr;

import io.vavr.*;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.collection.TreeMap;
import io.vavr.control.Option;

import java.lang.Iterable;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Relish {

    public static final class Cons implements Iterable<Object> {
        public static final Object NIL = "[]".intern();
        final Object first;
        Object second;

        public Cons(Object first, Object second) {
            this.first = first;
            this.second = second;
        }

        public Object getFirst() {
            return first;
        }

        public Object getSecond() {
            return second;
        }

        void setSecond(Object value) {
            this.second = value;
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            Object current = this;
            while (current instanceof Cons) {
                final Cons cons = (Cons) current;
                if (!first) builder.append(", ");
                builder.append(cons.first);
                current = cons.second;
                first = false;
            }
            if (!NIL.equals(current)) {
                builder.append(" | ");
                builder.append(current);
            }
            builder.append("]");
            return builder.toString();
        }

        @Override
        public boolean equals(Object right) {
            Object left = this;
            while (true) {
                if (left == right) return true;
                if (!(left instanceof Cons)) return Objects.equals(left, right);
                if (!(right instanceof Cons)) return false;
                final Cons leftCons = (Cons) left, rightCons = (Cons) right;
                if (!Objects.equals(leftCons.first, rightCons.first)) return false;
                left = leftCons.second;
                right = rightCons.second;
            }
        }

        @Override
        public int hashCode() {
            int result = 0;
            Object current = this;
            while (current instanceof Cons) {
                final Cons cons = (Cons) current;
                result ^= Objects.hashCode(cons.first);
                current = cons.second;
            }
            return result ^ Objects.hashCode(current);
        }

        public Cons mapTail(Function<Object, ?> function) {
            Cons result = null, last = null;
            Object current = this;
            while (current instanceof Cons) {
                Cons cons = (Cons) current;
                Cons image = new Cons(function.apply(cons.first), null);
                if (last == null) {
                    result = image;
                } else {
                    last.setSecond(image);
                }
                last = image;
                current = cons.second;
            }
            last.setSecond(function.apply(current));
            return result;
        }

        public static Object fromIterable(Iterable<?> iterable) {
            final Iterator<?> iterator = iterable.iterator();
            if (!iterator.hasNext()) return NIL;
            final Cons result = new Cons(iterator.next(), null);
            Cons last = result;
            while (iterator.hasNext()) {
                final Cons fresh = new Cons(iterator.next(), null);
                last.setSecond(fresh);
                last = fresh;
            }
            last.setSecond(NIL);
            return result;
        }

        public static Object list(Object element, Object... elements) {
            return th(NIL, element, elements);
        }

        public static Cons th(Object tail, Object element, Object... elements) {
            final int len = elements.length;
            final Cons result = new Cons(element, null);
            Cons last = result;
            for (int i = 0; i < len; i++) {
                final Cons cons = new Cons(elements[i], null);
                last.setSecond(cons);
                last = cons;
            }
            last.setSecond(tail);
            return result;
        }

        static class PairIterator implements Iterator<Object> {
            Object current;

            PairIterator(Object current) {
                this.current = current;
            }

            @Override
            public boolean hasNext() {
                return current instanceof Cons;
            }

            @Override
            public Object next() {
                final Object result = ((Cons) current).first;
                current = ((Cons) current).second;
                return result;
            }
        }

        @Override
        public Iterator<Object> iterator() {
            return new PairIterator(this);
        }

        public static Iterator<Object> iteratorFor(Object target) {
            if (target instanceof Cons) return ((Cons) target).iterator();
            else return new PairIterator(NIL);
        }
    }

    public final static class Var {
        final int seq;

        Var(int seq) {
            this.seq = seq;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Var)) return false;
            Var var = (Var) o;
            return seq == var.seq;
        }

        @Override
        public int hashCode() {
            return seq;
        }

        @Override
        public String toString() {
            return "_" + seq;
        }

        public boolean occursIn(Object term, Map<Integer, Object> subst) {
            final Object walked = walk(term, subst);
            if (equals(walked)) return true;
            else if (walked instanceof Tuple2) {
                return exists(walked, e -> occursIn(e, subst));
            } else return false;
        }
    }

    public static boolean exists(Object list, Predicate<Object> predicate) {
        while (list instanceof Tuple2) {
            final Tuple2<?, ?> tuple2 = (Tuple2<?, ?>) list;
            if (predicate.test(tuple2._1)) return true;
            list = tuple2._2;
        }
        return false;
    }


    public static Object walk(Object term, Map<Integer, Object> subst) {
        while (term instanceof Var) {
            final Option<Object> mapsTo = subst.get(((Var) term).seq);
            if (mapsTo.isEmpty()) break;
            final Object newTerm = mapsTo.get();
            if (newTerm == term) break;
            term = newTerm;
        }
        return term;
    }

    public static Object walkDeep(Object term0, Map<Integer, Object> subst) {
        final Object term = walk(term0, subst);
        if (term instanceof Cons) {
            return ((Cons) term).mapTail(e -> walkDeep(e, subst));
        } else return term;
    }

    public static abstract class Series<T> implements Iterable<T> {
        public abstract boolean isSuspension();

        public abstract boolean isEmpty();

        public abstract T head();

        public abstract Series<T> tail();

        public abstract Series<T> force();

        public final Series<T> forceStar() {
            Series<T> series = this;
            while (series.isSuspension()) series = series.force();
            return series;
        }

        static class StreamIterator<T> implements Iterator<T> {
            private Series<T> series;

            StreamIterator(Series<T> series) {
                this.series = series;
            }

            @Override
            public boolean hasNext() {
                series = series.forceStar();
                return !series.isEmpty();
            }

            @Override
            public T next() {
                series = series.forceStar();
                final T result = series.head();
                series = series.tail();
                return result;
            }
        }

        @Override
        public Iterator<T> iterator() {
            return new StreamIterator<>(this);
        }

        @SuppressWarnings("unchecked")
        public static <T> Series<T> empty() {
            return (Series<T>) EmptySeries.INSTANCE;
        }

        public static <T> Series<T> cons(T head, Series<T> tail) {
            return new ConsSeries<>(head, tail);
        }

        public static <T> Series<T> suspension(Supplier<Series<T>> supplier) {
            return new SuspendedSeries<>(supplier);
        }

        public static <E> Series<E> appendInf(Series<E> s1, Series<E> s2) {
            if (s1.isSuspension()) {
                return suspension(() -> appendInf(s2, s1.force()));
            } else if (s1.isEmpty()) {
                return s2;
            } else {
                final Series<E> s1Tail = s1.tail();
                return cons(s1.head(), suspension(() -> appendInf(s1Tail, s2)));
            }
        }

        public static <E> Series<E> appendMapInf(Function<E, Series<E>> goal, Series<E> series) {
            if (series.isSuspension()) {
                return suspension(() -> appendMapInf(goal, series.force()));
            } else if (series.isEmpty()) {
                return series;
            } else {
                return appendInf(goal.apply(series.head()), appendMapInf(goal, series.tail()));
            }
        }
    }

    static class EmptySeries<T> extends Series<T> {
        @Override
        public boolean isSuspension() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public T head() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Series<T> tail() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Series<T> force() {
            return this;
        }

        final static EmptySeries<Object> INSTANCE = new EmptySeries<Object>();
    }

    static class ConsSeries<T> extends Series<T> {
        final T head;
        final Series<T> tail;

        public ConsSeries(T head, Series<T> tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public boolean isSuspension() {
            return false;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public T head() {
            return head;
        }

        @Override
        public Series<T> tail() {
            return tail;
        }

        @Override
        public Series<T> force() {
            return this;
        }
    }

    static class SuspendedSeries<T> extends Series<T> {
        final Supplier<Series<T>> suspension;

        public SuspendedSeries(Supplier<Series<T>> suspension) {
            this.suspension = suspension;
        }

        @Override
        public boolean isSuspension() {
            return true;
        }

        @Override
        public boolean isEmpty() {
            throw new IllegalStateException();
        }

        @Override
        public T head() {
            throw new IllegalStateException();
        }

        @Override
        public Series<T> tail() {
            throw new IllegalStateException();
        }

        @Override
        public Series<T> force() {
            return suspension.get();
        }
    }

    public static abstract class Goal implements Function<Map<Integer, Object>, Series<Map<Integer, Object>>> {

        static class Delayed extends Goal {
            final Supplier<Goal> supplier;

            public Delayed(Supplier<Goal> supplier) {
                this.supplier = supplier;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                return supplier.get().apply(subst);
            }
        }

        static class Failure extends Goal {
            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                return Series.empty();
            }

            static final Failure INSTANCE = new Failure();
        }

        static class Success extends Goal {
            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                return Series.cons(subst, Series.empty());
            }

            static final Success INSTANCE = new Success();
        }


        public static Goal failure = Failure.INSTANCE;
        public static Goal success = Success.INSTANCE;

        static class Conj extends Goal {
            final Goal first, second;

            Conj(Goal first, Goal second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                return Series.appendMapInf(second, first.apply(subst));
            }
        }

        static class Disj extends Goal {
            final Goal first, second;

            Disj(Goal first, Goal second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                return Series.appendInf(first.apply(subst), second.apply(subst));
            }
        }

        public static Goal seq(Goal... goal) {
            final int len = goal.length;
            if (len == 0) return Success.INSTANCE;
            else {
                Goal current = goal[len - 1];
                for (int i = len - 2; i >= 0; i--) {
                    current = new Conj(goal[i], current);
                }
                return current;
            }
        }

        public static Goal choice(Goal... goal) {
            final int len = goal.length;
            if (len == 0) return Failure.INSTANCE;
            else {
                Goal current = goal[len - 1];
                for (int i = len - 2; i >= 0; i--) {
                    current = new Disj(goal[i], current);
                }
                return current;
            }
        }

        static Option<Map<Integer, Object>> unify(Object left, Object right, Map<Integer, Object> subst) {
            left = walk(left, subst);
            right = walk(right, subst);
            if (left == right) return Option.of(subst);
            if (left instanceof Var) {
                final Var leftVar = (Var) left;
                if (right instanceof Var) {
                    final Var rightVar = (Var) right;
                    final Var newVar, oldVar;
                    if (leftVar.seq < rightVar.seq) {
                        oldVar = leftVar;
                        newVar = rightVar;
                    } else if (leftVar.seq > rightVar.seq) {
                        oldVar = rightVar;
                        newVar = leftVar;
                    } else {
                        return Option.of(subst);
                    }
                    return Option.of(subst.put(newVar.seq, oldVar));
                } else if (leftVar.occursIn(right, subst)) {
                    return Option.none();
                } else {
                    return Option.of(subst.put(leftVar.seq, right));
                }
            } else if (right instanceof Var) {
                final Var rightVar = (Var) right;
                if (rightVar.occursIn(left, subst)) {
                    return Option.none();
                } else {
                    return Option.of(subst.put(rightVar.seq, left));
                }
            } else if (left instanceof Cons) {
                Map<Integer, Object> current = subst;
                while (left instanceof Cons) {
                    final Cons leftCons = (Cons) left;
                    if (!(right instanceof Cons)) {
                        if (right instanceof Var) break;
                        return Option.none();
                    }
                    final Cons rightCons = (Cons) right;
                    final Option<Map<Integer, Object>> headResult = unify(leftCons.first, rightCons.first, current);
                    if (headResult.isEmpty()) return Option.none();
                    current = headResult.get();
                    left = walk(leftCons.second, current);
                    right = walk(rightCons.second, current);
                    if (left == right) return Option.of(current);
                }
                return unify(left, right, current);
            } else {
                return Objects.equals(left, right) ? Option.of(subst) : Option.none();
            }
        }

        static class Unify extends Goal {
            final Object left, right;

            Unify(Object left, Object right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                final Option<Map<Integer, Object>> result = unify(left, right, subst);
                if (result.isEmpty()) {
                    return Series.empty();
                } else {
                    return Series.cons(result.get(), Series.empty());
                }
            }
        }

        public static Goal unify(Object left, Object right) {
            return new Unify(left, right);
        }

        static class Fresh1 extends Goal {
            final Function1<Var, Goal> body;

            Fresh1(Function1<Var, Goal> body) {
                this.body = body;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                final int nextVar = subst.length();
                final Var v1 = new Var(nextVar);
                return body.apply(v1).apply(subst.put(nextVar, v1));
            }
        }

        static class Fresh2 extends Goal {
            final Function2<Var, Var, Goal> body;

            Fresh2(Function2<Var, Var, Goal> body) {
                this.body = body;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                final int nextVar = subst.length();
                final Var v1 = new Var(nextVar);
                final Var v2 = new Var(nextVar + 1);
                return body.apply(v1, v2).apply(subst.put(nextVar, v1).put(nextVar + 1, v2));
            }
        }

        static class Fresh3 extends Goal {
            final Function3<Var, Var, Var, Goal> body;

            Fresh3(Function3<Var, Var, Var, Goal> body) {
                this.body = body;
            }

            @Override
            public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
                final int nextVar = subst.length();
                final Var v1 = new Var(nextVar);
                final Var v2 = new Var(nextVar + 1);
                final Var v3 = new Var(nextVar + 2);
                return body.apply(v1, v2, v3).apply(subst.put(nextVar, v1)
                        .put(nextVar + 1, v2).put(nextVar + 2, v3));
            }
        }

        public static Goal fresh(Function1<Var, Goal> body) {
            return new Fresh1(body);
        }

        public static Goal fresh(Function2<Var, Var, Goal> body) {
            return new Fresh2(body);
        }

        public static Goal fresh(Function3<Var, Var, Var, Goal> body) {
            return new Fresh3(body);
        }

        public static Goal fresh(Function4<Var, Var, Var, Var, Goal> body) {
            return new Fresh3((v1, v2, v3) -> fresh(v4 -> body.apply(v1, v2, v3, v4)));
        }

        public static Goal fresh(Function5<Var, Var, Var, Var, Var, Goal> body) {
            return new Fresh3((v1, v2, v3) -> fresh((v4, v5) -> body.apply(v1, v2, v3, v4, v5)));
        }

        public static Goal fresh(Function6<Var, Var, Var, Var, Var, Var, Goal> body) {
            return new Fresh3((v1, v2, v3) -> fresh((v4, v5, v6) -> body.apply(v1, v2, v3, v4, v5, v6)));
        }

        public static Goal delayed(Supplier<Goal> supplier) {
            return new Delayed(supplier);
        }

        public static Goal consO(Object x, Object y, Object z) {
            return unify(z, new Cons(x, y));
        }

        public static Stream<Object> run(Function<Var, Goal> body) {
            final Var q = new Var(0);
            final Map<Integer, Object> subst0 = TreeMap.of(0, q);
            return Stream.ofAll(body.apply(q).apply(subst0)).map(subst -> walkDeep(q, subst));
        }

        public static Goal appendO(Object x, Object y, Object z) {
            return choice(
                    seq(unify(x, Cons.NIL), unify(y, z)),
                    fresh((h, a, c) -> seq(unify(x, Cons.th(a, h)), unify(z, Cons.th(c, h)), delayed(() -> appendO(a, y, c))))
            );
        }

        public static Goal memberO(Object x, Object y) {
            return delayed(() -> fresh((t, h) -> seq(
                    unify(y, Cons.th(t, h)),
                    choice(
                            unify(x, h),
                            memberO(x, t)
                    ))
            ));
        }
    }
}
