package org.cellx.logish;

import io.vavr.*;
import io.vavr.collection.*;
import io.vavr.control.Option;

import java.lang.Iterable;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Logish {


    public final static class Subst {
        final IntMap<Object> map;

        protected Subst(IntMap<Object> map) {
            this.map = map;
        }

        public static Subst empty() {
            return new Subst(IntMap.empty());
        }

        public Object getSome(int key) {
            final Object value = map.getOrNull(key);
            if (value == null) throw new NoSuchElementException();
            return value;
        }

        public Option<Object> getOption(int key) {
            return map.get(key);
        }

        public Object lookup(int key) {
            return map.getOrNull(key);
        }

        public Subst put(int key, Object value) {
            final IntMap<Object> newMap = map.with(key, value);
            if (newMap == map) return this;
            else return new Subst(newMap);
        }

        public Subst remove(int key) {
            final IntMap<Object> newMap = map.without(key);
            if (newMap == map) return this;
            else return new Subst(newMap);
        }

        public int nextVarIndex() {
            return map.isEmpty() ? 0 : map.maxKey() + 1;
        }
    }

    public static SortedSet<Integer> varIndices(Object o) {
        if (o instanceof Var) return TreeSet.of(((Var) o).index);
        if (!(o instanceof Cons)) return TreeSet.empty();
        SortedSet<Integer> current = TreeSet.empty();
        while (o instanceof Cons) {
            current = current.union(varIndices(((Cons) o).car));
            o = ((Cons) o).cdr;
        }
        return current.union(varIndices(o));
    }

    public static boolean exists(Object list, Predicate<Object> predicate) {
        boolean first = true;
        if (list instanceof Cons) {
            do {
                final Cons consCell = (Cons) list;
                if (predicate.test(consCell.car)) return true;
                list = consCell.cdr;
                first = false;
            } while (list instanceof Cons);
        }
        return !first && predicate.test(list);
    }

//    public static boolean isMoreInstantiated(Object o1, Object o2, Subst subst) {
//        final Object v1 = walk(o1, subst);
//        final Object v2 = walk(o2, subst);
//        if (v1 == null) return v2 == null;
//        if (v1 == v2 || v1.equals(v2)) return true;
//        if (v2 instanceof Var) return true;
//        if (v1 instanceof Var) return false;
//        if (v1 instanceof Cons) {
//            if (v2 instanceof Cons) {
//
//            }
//        }
//    }

    public static Object walk(Object term, Subst subst) {
        while (term instanceof Var) {
            final Object o = subst.getSome(((Var) term).index);
            if (o == term) break;
            term = o;
        }
        return term;
    }

    public static Var walkVar(Var v, Subst subst) {
        int prevSeq = v.index;
        Object t = subst.getSome(prevSeq);
        while (t instanceof Var) {
            v = (Var) t;
            if (v.index == prevSeq) break;
            prevSeq = v.index;
            t = subst.getSome(prevSeq);
        }
        return v;
    }

    public static int walkVarIndex(int varIndex, Subst subst) {
        Object t = subst.getSome(varIndex);
        while (t instanceof Var) {
            final int nextVarIndex = ((Var) t).index;
            if (nextVarIndex == varIndex) break;
            varIndex = nextVarIndex;
            t = subst.getSome(varIndex);
        }
        return varIndex;
    }

    public static Object walkDeep(Object term0, Subst subst) {
        final Object term = walk(term0, subst);
        if (term instanceof Cons) {
            return ((Cons) term).mapTail(e -> walkDeep(e, subst));
        } else return term;
    }

    public static abstract class Series<T> implements Iterable<T> {
        public abstract boolean hasFuture();

        public abstract boolean isSuspension();

        public abstract boolean isEmpty();

        public abstract T head();

        public abstract Series<T> tail();

        public abstract CompletableFuture<Series<T>> future();

        public abstract Series<T> force();

        public final Series<T> forceDeep() {
            Series<T> series = this;
            while (series.isSuspension() || series.hasFuture()) series = series.force();
            return series;
        }

        static class StreamIterator<T> implements Iterator<T> {
            private Series<T> series;

            StreamIterator(Series<T> series) {
                this.series = series;
            }

            @Override
            public boolean hasNext() {
                series = series.forceDeep();
                return !series.isEmpty();
            }

            @Override
            public T next() {
                series = series.forceDeep();
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

        public static <T> Series<T> singleton(T element) {
            return new ConsSeries<>(element, empty());
        }

        public static <T> Series<T> of(Option<T> option) {
            return option.isEmpty() ? Series.empty() : Series.singleton(option.get());
        }

        public static <T> Series<T> suspension(Supplier<Series<T>> supplier) {
            return new SuspendedSeries<>(supplier);
        }

        public static <T> Series<T> future(CompletableFuture<Series<T>> futureSeries) {
            return new FutureSeries<>(futureSeries);
        }

        public static <E> Series<E> appendInf(Series<E> s1, Series<E> s2) {
            if (s1.hasFuture()) {
                if (s2.hasFuture()) {
                    return future(s1.future().thenCombineAsync(s2.future(), Series::appendInf));
                } else {
                    return future(s1.future().thenApplyAsync(f1s -> appendInf(f1s, s2)));
                }
            } else if (s2.hasFuture()) {
                return future(s2.future().thenApplyAsync(fs2 -> appendInf(s1, fs2)));
            } else if (s1.isSuspension()) {
                return suspension(() -> appendInf(s2, s1.force()));
            } else if (s1.isEmpty()) {
                return s2;
            } else {
                final Series<E> s1Tail = s1.tail();
                return cons(s1.head(), suspension(() -> appendInf(s1Tail, s2)));
            }
        }

        public static <E> Series<E> appendMapInf(Function<E, Series<E>> goal, Series<E> series) {
            if (series.hasFuture()) {
                return future(series.future().thenApplyAsync(s -> appendMapInf(goal, s)));
            } else if (series.isSuspension()) {
                return suspension(() -> appendMapInf(goal, series.force()));
            } else if (series.isEmpty()) {
                return series;
            } else {
                return appendInf(goal.apply(series.head()), appendMapInf(goal, series.tail()));
            }
        }

        public static <E, T> Series<T> mapFilter(Function<E, T> function, Series<E> series) {
            final Series<E> forced = series.forceDeep();

            if (forced.isEmpty()) {
                return Series.empty();
            } else {
                final T result = function.apply(forced.head());
                if (result == null) {
                    return mapFilter(function, forced.tail());
                } else {
                    return ConsSeries.cons(result, suspension(() -> mapFilter(function, forced.tail())));
                }
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
        public boolean hasFuture() {
            return false;
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
        public CompletableFuture<Series<T>> future() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Series<T> force() {
            return this;
        }

        final static EmptySeries<Object> INSTANCE = new EmptySeries<>();
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
        public boolean hasFuture() {
            return false;
        }

        @Override
        public T head() {
            return head;
        }

        @Override
        public CompletableFuture<Series<T>> future() {
            throw new UnsupportedOperationException();
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
        public boolean hasFuture() {
            return false;
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
        public CompletableFuture<Series<T>> future() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Series<T> force() {
            return suspension.get();
        }
    }

    static class FutureSeries<T> extends Series<T> {
        final CompletableFuture<Series<T>> future;

        public FutureSeries(CompletableFuture<Series<T>> future) {
            this.future = future;
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
        public boolean hasFuture() {
            return true;
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
        public CompletableFuture<Series<T>> future() {
            return future;
        }

        @Override
        public Series<T> force() {
            try {
                return future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public interface Constraint {
        Cons symbolicRepr();
    }

    public interface Attribute {
        Option<Subst> validate(Var v, Object o, Subst subst);

        boolean delegating();

        List<Constraint> constraints();

        Option<Tuple2<Option<Attribute>, Subst>> combine(Var v, Attribute other, Subst subst);
    }

    @SuppressWarnings("unused")
    static Option<Subst> wrapGoal(Goal goal, Subst subst) {
        final Series<Subst> result = goal.apply(subst).forceDeep();
        return result.isEmpty() ? Option.none() : Option.of(result.head());
    }

    public static Option<Attribute> getAttribute(Var v, Subst subst, String domain) {
        return getAttribute(v.index, subst, domain);
    }

    public static Option<Attribute> getAttribute(int varSeq, Subst subst, String domain) {
        final Object untypedMap = subst.lookup(-varSeq - 1);
        if (untypedMap == null) {
            return Option.none();
        } else {
            //noinspection unchecked
            return ((Map<String, Attribute>) untypedMap).get(domain);
        }
    }

    public static Subst setAttribute(Var v, Subst subst,
                                     String domain, Attribute attribute) {
        return setAttribute(v.index, subst, domain, attribute);
    }

    public static Subst setAttribute(int varSeq, Subst subst,
                                     String domain, Attribute attribute) {
        final Object untypedMap = subst.lookup(-varSeq - 1);
        if (untypedMap == null) {
            return subst.put(-varSeq - 1, HashMap.of(domain, attribute));
        } else {
            //noinspection unchecked
            return subst.put(-varSeq - 1, ((Map<String, Attribute>) untypedMap).put(domain, attribute));
        }
    }

    public static Subst removeAttribute(Var v, Subst subst,
                                        String domain) {
        return removeAttribute(v.index, subst, domain);
    }

    public static Subst removeAttribute(int varSeq, Subst subst,
                                        String domain) {
        final Object untypedMap = subst.lookup(-varSeq - 1);
        if (untypedMap == null) {
            return subst;
        } else {
            @SuppressWarnings("unchecked") final Map<String, Attribute> typedMap =
                    (Map<String, Attribute>) untypedMap;
            if (typedMap.containsKey(domain)) {
                return subst.put(-varSeq - 1, typedMap.remove(domain));
            } else {
                return subst;
            }
        }
    }

    static Option<Subst> bind(Var v1, Var v2, Subst subst) {
        Subst bound = subst.put(v1.index, v2);
        final Object nmap1 = subst.lookup(-v1.index - 1),
                nmap2 = subst.lookup(-v2.index - 1);
        if (nmap1 != null) {
            // v1 had some attributes
            bound = bound.remove(-v1.index - 1); // remove them from the map
            if (nmap2 == null) {
                // v1 had no attributes: copy those from v1
                bound = bound.put(-v2.index - 1, nmap1);
            } else {
                // Combine v1's attributes into v2's
                @SuppressWarnings("unchecked") final Map<String, Attribute> map1 =
                        (Map<String, Attribute>) nmap1;
                @SuppressWarnings("unchecked") Map<String, Attribute> map2 =
                        (Map<String, Attribute>) nmap2;
                for (final Tuple2<String, Attribute> e1 : map1) {
                    final String domain = e1._1;
                    final Option<Attribute> a2 = map2.get(domain);
                    if (a2.isEmpty()) {
                        // Copy a1's attribute to a2
                        map2 = map2.put(domain, e1._2);
                    } else {
                        // validate compatibility
                        final Option<Tuple2<Option<Attribute>, Subst>> optCompat =
                                a2.get().combine(v2, e1._2, bound);
                        if (optCompat.isEmpty()) return Option.none();
                        final Tuple2<Option<Attribute>, Subst> compat = optCompat.get();
                        if (compat._1.isEmpty()) {
                            map2 = map2.remove(domain);
                        } else {
                            map2 = map2.put(domain, compat._1.get());
                        }
                        bound = compat._2;
                    }
                }
                bound = bound.put(-v2.index - 1, map2);
            }
        }
        return Option.of(bound);
    }

    static Option<Subst> instantiate(Var v, Object o, Subst subst) {
        final Object nmap = subst.lookup(-v.index - 1);
        if (nmap == null) {
            return Option.of(subst.put(v.index, o));
        } else {
            @SuppressWarnings("unchecked") final Map<String, Attribute> map =
                    (Map<String, Attribute>) nmap;
            final Option<Attribute> optDelegating = map.valuesIterator().find(Attribute::delegating);
            if (!optDelegating.isEmpty()) {
                return optDelegating.get().validate(v, o, subst);
            } else {
                Subst inst = subst.put(v.index, o);
                for (final Attribute a : map.valuesIterator()) {
                    final Option<Subst> result = a.validate(v, o, inst);
                    if (result.isEmpty()) return result;
                    inst = result.get();
                }
                return Option.of(inst);
            }
        }
    }

    static Option<Subst> unify(Object left, Object right, Subst subst) {
        left = walk(left, subst);
        right = walk(right, subst);
        if (left == right) return Option.of(subst);
        if (left instanceof Var) {
            final Var leftVar = (Var) left;
            if (right instanceof Var) {
                final Var rightVar = (Var) right;
                final Var newVar, oldVar;
                if (leftVar.index < rightVar.index) {
                    oldVar = leftVar;
                    newVar = rightVar;
                } else if (leftVar.index > rightVar.index) {
                    oldVar = rightVar;
                    newVar = leftVar;
                } else {
                    return Option.of(subst);
                }
                // newVar binds to oldVar
                return bind(newVar, oldVar, subst);
            } else if (leftVar.occursIn(right, subst)) {
                return Option.none();
            } else {
                return instantiate(leftVar, right, subst);
            }
        } else if (right instanceof Var) {
            final Var rightVar = (Var) right;
            if (rightVar.occursIn(left, subst)) {
                return Option.none();
            } else {
                return instantiate(rightVar, left, subst);
            }
        } else if (left instanceof Cons) {
            Subst current = subst;
            while (left instanceof Cons) {
                final Cons leftConsCell = (Cons) left;
                if (!(right instanceof Cons)) {
                    if (right instanceof Var) break;
                    return Option.none();
                }
                final Cons rightConsCell = (Cons) right;
                final Option<Subst> headResult = unify(leftConsCell.car, rightConsCell.car, current);
                if (headResult.isEmpty()) return Option.none();
                current = headResult.get();
                left = walk(leftConsCell.cdr, current);
                right = walk(rightConsCell.cdr, current);
                if (left == right) return Option.of(current);
            }
            return unify(left, right, current);
        } else {
            return Objects.equals(left, right) ? Option.of(subst) : Option.none();
        }
    }

    public static Stream<Object> run(Function<Var, Goal> body) {
        final Var q = new Var(0);
        final Subst subst0 = Subst.empty().put(q.index, q);
        return Stream.ofAll(body.apply(q).apply(subst0)).map(subst -> walkDeep(q, subst));
    }

    static Tuple2<Map<String, List<Constraint>>, SortedSet<Integer>> augmentConstraints(int varSeq,
                                                                                        Subst subst,
                                                                                        Map<String, List<Constraint>> start) {
        Object attributes = subst.lookup(-varSeq - 1);
        if (attributes == null) return Tuple.of(start, TreeSet.empty());
        Map<String, List<Constraint>> constraints = start;
        SortedSet<Integer> otherVars = TreeSet.empty();
        //noinspection unchecked
        for (final Tuple2<String, Attribute> entry : (Map<String, Attribute>) attributes) {
            final String domain = entry._1;
            final Option<List<Constraint>> seen = start.get(domain);
            List<Constraint> update = seen.isEmpty() ? null : seen.get();
            boolean updated = false;
            for (final Constraint c : entry._2.constraints()) {
                if (seen.isEmpty() || !seen.get().contains(c)) {
                    update = (update == null ? List.of(c) : update.prepend(c));
                    updated = true;
                    otherVars = otherVars.union(varIndices(walkDeep(c.symbolicRepr(), subst)).remove(varSeq));
                }
            }
            if (updated) constraints = constraints.put(domain, update);
        }
        return Tuple.of(constraints, otherVars);
    }

    static Map<String, List<Constraint>> collectConstraints(Object o, Subst subst) {
        Map<String, List<Constraint>> result = TreeMap.empty();
        SortedSet<Integer> varsToGo = varIndices(o);
        SortedSet<Integer> seenVars = TreeSet.empty();
        while (!varsToGo.isEmpty()) {
            SortedSet<Integer> otherVars = TreeSet.empty();
            for (final Integer varSeq : varsToGo) {
                final Tuple2<Map<String, List<Constraint>>, SortedSet<Integer>> step =
                        augmentConstraints(varSeq, subst, result);
                result = step._1;
                otherVars = otherVars.union(step._2);
            }
            seenVars = seenVars.union(varsToGo);
            varsToGo = otherVars.diff(seenVars);
        }
        return result;
    }

    public static Stream<Tuple2<Object, List<Cons>>> runC(Function<Var, Goal> body) {
        final Var q = new Var(0);
        final Subst subst0 = Subst.empty().put(0, q);
        return Stream.ofAll(body.apply(q).apply(subst0)).map(subst -> {
            Object o = walkDeep(q, subst);
            return Tuple.of(o, collectConstraints(o, subst).toList()
                    .map(e -> e._2.map(c -> Cons.make(walkDeep(c.symbolicRepr(), subst), e._1)))
                    .flatMap(Function.identity()));
        });
    }

    @SuppressWarnings({"unused", "SuspiciousNameCombination"})
    public static abstract class Goal implements Function<Subst, Series<Subst>> {

        static class Delayed extends Goal {
            final Supplier<Goal> supplier;

            public Delayed(Supplier<Goal> supplier) {
                this.supplier = supplier;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                return supplier.get().apply(subst);
            }
        }

        static class Failure extends Goal {
            @Override
            public Series<Subst> apply(Subst subst) {
                return Series.empty();
            }

            static final Failure INSTANCE = new Failure();
        }

        static class Success extends Goal {
            @Override
            public Series<Subst> apply(Subst subst) {
                return Series.cons(subst, Series.empty());
            }

            static final Success INSTANCE = new Success();
        }

        public static Goal failure() {
            return Failure.INSTANCE;
        }

        public static Goal success() {
            return Success.INSTANCE;
        }

        static class Conj extends Goal {
            final Goal first, second;

            Conj(Goal first, Goal second) {
                this.first = first;
                this.second = second;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
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
            public Series<Subst> apply(Subst subst) {
                return Series.appendInf(first.apply(subst), second.apply(subst));
            }
        }

        static class ParDisj extends Goal {
            final Executor executor;
            final Goal first, second;

            public ParDisj(Executor executor, Goal first, Goal second) {
                this.executor = executor;
                this.first = first;
                this.second = second;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final CompletableFuture<Series<Subst>> secondFuture =
                        (executor == null ? CompletableFuture.supplyAsync(() -> second.apply(subst))
                                : CompletableFuture.supplyAsync(() -> second.apply(subst), executor));
                return Series.appendInf(first.apply(subst), Series.future(secondFuture));
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

        public static Goal seq(List<Goal> goals) {
            if (goals.isEmpty()) return Success.INSTANCE;
            return goals.reduceRight(Conj::new);
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

        public static Goal par(Executor executor, Goal... goals) {
            final int len = goals.length;
            if (len == 0) return Failure.INSTANCE;
            else {
                Goal current = goals[len - 1];
                for (int i = len - 2; i >= 0; i--) {
                    current = new ParDisj(executor, goals[i], current);
                }
                return current;
            }
        }

        public static Goal par(Goal... goals) {
            return par(null, goals);
        }


        static class Unify extends Goal {
            final Object left, right;

            Unify(Object left, Object right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Option<Subst> result = Logish.unify(left, right, subst);
                if (result.isEmpty()) {
                    return Series.empty();
                } else {
                    return Series.singleton(result.get());
                }
            }
        }

        public static Goal unify(Object left, Object right) {
            return new Unify(left, right);
        }

        static class Equals extends Goal {
            final Object left, right;

            Equals(Object left, Object right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                return Objects.equals(walk(left, subst), walk(right, subst)) ? Series.singleton(subst) : Series.empty();
            }
        }

        public static Goal equals(Object x, Object y) {
            return new Equals(x, y);
        }

        static class Same extends Goal {
            final Object left, right;

            Same(Object left, Object right) {
                this.left = left;
                this.right = right;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                return walk(left, subst) == walk(right, subst) ? Series.singleton(subst) : Series.empty();
            }
        }

        public static Goal same(Object x, Object y) {
            return new Same(x, y);
        }


        static class Not extends Goal {
            final Goal goal;

            Not(Goal goal) {
                this.goal = goal;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Series<Subst> result = goal.apply(subst).forceDeep();
                return result.isEmpty() ? Series.singleton(subst) : Series.empty();
            }
        }

        public static Goal not(Goal goal) {
            return new Not(goal);
        }

        static class Fresh1 extends Goal {
            final Function1<Var, Goal> body;

            Fresh1(Function1<Var, Goal> body) {
                this.body = body;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final int nextVar = subst.nextVarIndex();
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
            public Series<Subst> apply(Subst subst) {
                final int nextVar = subst.nextVarIndex();
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
            public Series<Subst> apply(Subst subst) {
                final int nextVar = subst.nextVarIndex();
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

        public static Goal nilO(Object x) {
            return unify(x, Cons.NIL);
        }

        public static Goal appendO(Object x, Object y, Object z) {
            return delayed(() -> choice(
                    seq(unify(x, Cons.NIL), unify(y, z)),
                    fresh((h, a, c) -> seq(unify(x, Cons.make(a, h)), unify(z, Cons.make(c, h)), appendO(a, y, c)))
            ));
        }

        public static Goal memberO(Object x, Object y) {
            return fresh((t, h) -> seq(
                    unify(y, Cons.make(t, h)),
                    choice(
                            unify(x, h),
                            memberO(x, t)
                    ))
            );
        }

        public static Goal memberCheckO(Object x, Object y) {
            return fresh((t, h) -> seq(
                    unify(y, Cons.make(t, h)),
                    ifte(unify(x, h), Goal::success, () -> memberCheckO(x, t))
            ));
        }

        static class Ifte extends Goal {
            final Goal question;
            final Supplier<Goal> thenBranch;
            final Supplier<Goal> elseBranch;

            Ifte(Goal question, Supplier<Goal> thenBranch, Supplier<Goal> elseBranch) {
                this.question = question;
                this.thenBranch = thenBranch;
                this.elseBranch = elseBranch;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Series<Subst> questionResult = question.apply(subst).forceDeep();
                if (questionResult.isEmpty()) {
                    return elseBranch.get().apply(subst);
                } else {
                    return Series.appendMapInf(thenBranch.get(), questionResult);
                }
            }
        }

        public static Goal ifte(Goal question, Supplier<Goal> thenBranch, Supplier<Goal> elseBranch) {
            return new Ifte(question, thenBranch, elseBranch);
        }

        static class Once extends Goal {
            final Goal goal;

            Once(Goal goal) {
                this.goal = goal;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Series<Subst> result = goal.apply(subst).forceDeep();
                if (result.isEmpty()) return result;
                else return Series.singleton(result.head());
            }
        }

        public static Goal once(Goal goal) {
            return new Once(goal);
        }

        public static class Clause {
            final Goal guard;
            final Supplier<Goal> body;

            public Clause(Goal guard, Supplier<Goal> body) {
                this.guard = guard;
                this.body = body;
            }

            public Goal guard() {
                return guard;
            }

            public Supplier<Goal> body() {
                return body;
            }
        }

        public static Clause clause(Goal goal, Supplier<Goal> body) {
            return new Clause(goal, body);
        }

        public static Goal conda(Clause... clauses) {
            final int len = clauses.length;
            Supplier<Goal> current = Goal::failure;
            for (int i = len - 1; i >= 0; i--) {
                final Goal guard = clauses[i].guard;
                final Supplier<Goal> body = clauses[i].body;
                final Supplier<Goal> last = current;
                current = () -> ifte(guard, body, last);
            }
            return delayed(current);
        }

        public static Goal condu(Clause... clauses) {
            final int len = clauses.length;
            Supplier<Goal> current = Goal::failure;
            for (int i = len - 1; i >= 0; i--) {
                final Goal guard = clauses[i].guard;
                final Supplier<Goal> body = clauses[i].body;
                final Supplier<Goal> last = current;
                current = () -> ifte(once(guard), body, last);
            }
            return delayed(current);
        }

        static class Free extends Goal {
            final Object x;

            Free(Object x) {
                this.x = x;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                return walk(x, subst) instanceof Var ? Series.singleton(subst) : Series.empty();
            }
        }

        public static Goal free(Object x) {
            return new Free(x);
        }

        static class Test0 extends Goal {
            final Function0<Boolean> test;

            Test0(Function0<Boolean> test) {
                this.test = test;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                return test.apply() ? Series.singleton(subst) : Series.empty();
            }
        }

        static class Test1<T> extends Goal {
            final Class<T> clazz;
            final Object x;
            final Function1<T, Boolean> test;

            Test1(Class<T> clazz, Object x, Function1<T, Boolean> test) {
                this.clazz = clazz;
                this.x = x;
                this.test = test;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object deref = walk(x, subst);
                return clazz.isInstance(deref) && test.apply(clazz.cast(deref)) ? Series.singleton(subst) : Series.empty();
            }
        }

        static class Test2<T1, T2> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Function2<T1, T2, Boolean> test;

            Test2(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Function2<T1, T2, Boolean> test) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.test = test;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                return clazzX.isInstance(derefX) && clazzY.isInstance(derefY) &&
                        test.apply(clazzX.cast(derefX), clazzY.cast(derefY)) ? Series.singleton(subst) : Series.empty();
            }
        }

        static class Test3<T1, T2, T3> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Class<T3> clazzZ;
            final Object z;
            final Function3<T1, T2, T3, Boolean> test;

            Test3(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Class<T3> clazzZ, Object z,
                  Function3<T1, T2, T3, Boolean> test) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.clazzZ = clazzZ;
                this.z = z;
                this.test = test;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                final Object derefZ = walk(z, subst);
                return clazzX.isInstance(derefX) && clazzY.isInstance(derefY) && clazzZ.isInstance(derefZ) &&
                        test.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ)) ?
                        Series.singleton(subst) : Series.empty();
            }
        }

        public static Goal test(Function0<Boolean> test) {
            return new Test0(test);
        }

        public static <T> Goal test(Class<T> clazz, Object x, Function1<T, Boolean> test) {
            return new Test1<>(clazz, x, test);
        }

        public static <T> Goal test(Class<T> clazz, Var x) {
            return new Test1<>(clazz, x, v -> true);
        }

        public static <T1, T2> Goal test(Class<T1> clazzX, Var x,
                                         Class<T2> clazzY, Var y,
                                         Function2<T1, T2, Boolean> test) {
            return new Test2<>(clazzX, x, clazzY, y, test);
        }

        public static <T> Goal test(Class<T> clazz, Var x, Var y,
                                    Function2<T, T, Boolean> test) {
            return new Test2<>(clazz, x, clazz, y, test);
        }

        public static <T1, T2, T3> Goal test(Class<T1> clazzX, Var x,
                                             Class<T2> clazzY, Var y,
                                             Class<T3> clazzZ, Var z,
                                             Function3<T1, T2, T3, Boolean> test) {
            return new Test3<>(clazzX, x, clazzY, y, clazzZ, z, test);
        }

        public static <T> Goal test(Class<T> clazz, Var x, Var y, Var z,
                                    Function3<T, T, T, Boolean> test) {
            return new Test3<>(clazz, x, clazz, y, clazz, z, test);
        }

        static class Side0 extends Goal {
            final Function0<Void> action;

            Side0(Function0<Void> action) {
                this.action = action;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                action.apply();
                return Series.singleton(subst);
            }
        }

        static class Side1<T> extends Goal {
            final Class<T> clazz;
            final Object x;
            final Function1<T, Void> action;

            Side1(Class<T> clazz, Object x, Function1<T, Void> action) {
                this.clazz = clazz;
                this.x = x;
                this.action = action;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object deref = walk(x, subst);
                action.apply(clazz.cast(deref));
                return Series.singleton(subst);
            }
        }

        static class Side2<T1, T2> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Function2<T1, T2, Void> action;

            Side2(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Function2<T1, T2, Void> action) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.action = action;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                action.apply(clazzX.cast(derefX), clazzY.cast(derefY));
                return Series.singleton(subst);
            }
        }

        static class Side3<T1, T2, T3> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Class<T3> clazzZ;
            final Object z;
            final Function3<T1, T2, T3, Void> action;

            Side3(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Class<T3> clazzZ, Object z,
                  Function3<T1, T2, T3, Void> test) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.clazzZ = clazzZ;
                this.z = z;
                this.action = test;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                final Object derefZ = walk(z, subst);
                action.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ));
                return Series.singleton(subst);
            }
        }

        public static Goal side(Function0<Void> action) {
            return new Side0(action);
        }

        public static <T> Goal side(Class<T> clazz, Object x, Function1<T, Void> test) {
            return new Side1<>(clazz, x, test);
        }

        public static <T1, T2> Goal side(Class<T1> clazzX, Var x,
                                         Class<T2> clazzY, Var y,
                                         Function2<T1, T2, Void> test) {
            return new Side2<>(clazzX, x, clazzY, y, test);
        }

        public static <T> Goal side(Class<T> clazz, Var x, Var y,
                                    Function2<T, T, Void> test) {
            return new Side2<>(clazz, x, clazz, y, test);
        }

        public static <T1, T2, T3> Goal side(Class<T1> clazzX, Var x,
                                             Class<T2> clazzY, Var y,
                                             Class<T3> clazzZ, Var z,
                                             Function3<T1, T2, T3, Void> test) {
            return new Side3<>(clazzX, x, clazzY, y, clazzZ, z, test);
        }

        public static <T> Goal side(Class<T> clazz, Var x, Var y, Var z,
                                    Function3<T, T, T, Void> test) {
            return new Side3<>(clazz, x, clazz, y, clazz, z, test);
        }

        static class Map0 extends Goal {
            final Function0<Object> f;
            final Object w;

            Map0(Function0<Object> f, Object w) {
                this.f = f;
                this.w = w;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Option<Subst> result = Logish.unify(w, f.apply(), subst);
                return result.isEmpty() ? Series.empty() : Series.singleton(result.get());
            }
        }

        static class Map1<T> extends Goal {
            final Class<T> clazz;
            final Object x;
            final Function1<T, Object> f;
            final Object w;

            Map1(Class<T> clazz, Object x, Function1<T, Object> f, Object w) {
                this.clazz = clazz;
                this.x = x;
                this.f = f;
                this.w = w;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object deref = walk(x, subst);
                if (!clazz.isInstance(deref)) return Series.empty();
                final Option<Subst> result = Logish.unify(w, f.apply(clazz.cast(deref)), subst);
                return result.isEmpty() ? Series.empty() : Series.singleton(result.get());
            }
        }

        static class Map2<T1, T2> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Function2<T1, T2, Object> f;
            final Object w;

            Map2(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Function2<T1, T2, Object> f, Object w) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.f = f;
                this.w = w;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                if (!clazzX.isInstance(derefX) || !clazzY.isInstance(derefY)) return Series.empty();
                final Option<Subst> result =
                        Logish.unify(w, f.apply(clazzX.cast(derefX), clazzY.cast(derefY)), subst);
                return result.isEmpty() ? Series.empty() : Series.singleton(result.get());
            }
        }

        static class Map3<T1, T2, T3> extends Goal {
            final Class<T1> clazzX;
            final Object x;
            final Class<T2> clazzY;
            final Object y;
            final Class<T3> clazzZ;
            final Object z;
            final Function3<T1, T2, T3, Object> f;
            final Object w;

            Map3(Class<T1> clazzX, Object x, Class<T2> clazzY, Object y, Class<T3> clazzZ, Object z,
                 Function3<T1, T2, T3, Object> f, Object w) {
                this.clazzX = clazzX;
                this.x = x;
                this.clazzY = clazzY;
                this.y = y;
                this.clazzZ = clazzZ;
                this.z = z;
                this.f = f;
                this.w = w;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                final Object derefX = walk(x, subst);
                final Object derefY = walk(y, subst);
                final Object derefZ = walk(z, subst);
                if (!clazzX.isInstance(derefX) || !clazzY.isInstance(derefY) ||
                        !clazzZ.isInstance(derefZ)) return Series.empty();
                final Option<Subst> result =
                        Logish.unify(w, f.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ)), subst);
                return result.isEmpty() ? Series.empty() : Series.singleton(result.get());
            }
        }

        public static Goal map(Function0<Object> f, Object w) {
            return new Map0(f, w);
        }

        public static <T> Goal map(Class<T> clazz, Var x, Function1<T, Object> f, Object w) {
            return new Map1<>(clazz, x, f, w);
        }

        public static <T1, T2> Goal map(Class<T1> clazzX, Var x, Class<T2> clazzY, Var y,
                                        Function2<T1, T2, Object> f, Object w) {
            return new Map2<>(clazzX, x, clazzY, y, f, w);
        }

        public static <T1, T2, T3> Goal map(Class<T1> clazzX, Var x, Class<T2> clazzY, Var y,
                                            Class<T3> clazzZ, Var z,
                                            Function3<T1, T2, T3, Object> f, Object w) {
            return new Map3<>(clazzX, x, clazzY, y, clazzZ, z, f, w);
        }

        static class Element extends Goal {
            final Object x;
            final Seq<?> sequence;

            Element(Object x, Seq<?> sequence) {
                this.x = x;
                this.sequence = sequence;
            }

            @Override
            public Series<Subst> apply(Subst subst) {
                if (sequence.isEmpty()) return Series.empty();
                return choice(unify(x, sequence.head()),
                        delayed(() -> new Element(x, sequence.tail()))).apply(subst);
            }
        }

        public static Goal element(Object x, Seq<?> seq) {
            return new Element(x, seq);
        }

    }
}
