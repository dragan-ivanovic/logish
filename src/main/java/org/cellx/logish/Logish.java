package org.cellx.logish;

import io.vavr.*;
import io.vavr.collection.*;
import io.vavr.control.Option;

import java.lang.Iterable;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Logish {


    public static IdentityMap<Var, Object> variables(Object o) {
        final IdentityMap<Var, Object> emptyMap = IdentityMap.empty();
        if (o instanceof Var) return emptyMap.with((Var) o, Boolean.TRUE);
        if (!(o instanceof Cons)) return emptyMap;
        IdentityMap<Var, Object> current = emptyMap;
        while (o instanceof Cons) {
            current = current.withAll(variables(((Cons) o).car));
            o = ((Cons) o).cdr;
        }
        return current.withAll(variables(o));
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

    public static Object walk(Object term, Subst subst) {
        while (term instanceof Var) {
            final Object o = subst.lookup((Var) term);
            if (o == null) break;
            term = o;
        }
        return term;
    }

    public static Var walkVar(Var v, Subst subst) {
        Object o = subst.lookup(v);
        while (o instanceof Var) {
            v = (Var) o;
            o = subst.lookup(v);
        }
        return v;
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

    @SuppressWarnings("unused")
    static Option<Subst> wrapGoal(Goal goal, Subst subst) {
        final Series<Subst> result = goal.apply(subst).forceDeep();
        return result.isEmpty() ? Option.none() : Option.of(result.head());
    }


    static Option<Subst> bind(Var v1, Var v2, Subst subst) {
        Subst bound = subst.put(v1, v2);
        final Map<String, Attribute> map1 = subst.lookupAttribute(v1);
        Map<String, Attribute> map2 = subst.lookupAttribute(v2);
        if (map1 != null) {
            // v1 had some attributes
            bound = bound.removeAttributeMap(v1); // remove them from the map
            if (map2 == null) {
                // v1 had no attributes: copy those from v1
                bound = bound.setAttributeMap(v2, map1);
            } else {
                // Combine v1's attributes into v2's
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
                bound = bound.setAttributeMap(v2, map2);
            }
        }
        return Option.of(bound);
    }

    static Option<Subst> instantiate(Var v, Object o, Subst subst) {
        final Map<String, Attribute> map = subst.lookupAttribute(v);
        if (map == null) {
            return Option.of(subst.put(v, o));
        } else {
            final Option<Attribute> optDelegating = map.valuesIterator().find(Attribute::delegating);
            if (!optDelegating.isEmpty()) {
                return optDelegating.get().validate(v, o, subst);
            } else {
                Subst inst = subst.put(v, o);
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
                // newVar binds to oldVar
                return bind(leftVar, rightVar, subst);
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
        final Var q = new Var();
        final Subst subst0 = Subst.empty();
        return Stream.ofAll(body.apply(q).apply(subst0)).map(subst -> walkDeep(q, subst));
    }

    static Tuple2<Map<String, List<Constraint>>, IdentityMap<Var, Object>> augmentConstraints(Var v,
                                                                                              Subst subst,
                                                                                              Map<String, List<Constraint>> start) {
        Map<String, Attribute> attributes = subst.lookupAttribute(v);
        if (attributes == null) return Tuple.of(start, IdentityMap.empty());
        Map<String, List<Constraint>> constraints = start;
        IdentityMap<Var, Object> otherVars = IdentityMap.empty();
        for (final Tuple2<String, Attribute> entry : attributes) {
            final String domain = entry._1;
            final Option<List<Constraint>> seen = start.get(domain);
            List<Constraint> update = seen.isEmpty() ? null : seen.get();
            boolean updated = false;
            for (final Constraint c : entry._2.constraints()) {
                if (seen.isEmpty() || !seen.get().contains(c)) {
                    update = (update == null ? List.of(c) : update.prepend(c));
                    updated = true;
                    otherVars = otherVars.withAll(variables(walkDeep(c.symbolicRepr(), subst)).without(v));
                }
            }
            if (updated) constraints = constraints.put(domain, update);
        }
        return Tuple.of(constraints, otherVars);
    }

    static Map<String, List<Constraint>> collectConstraints(Object o, Subst subst) {
        Map<String, List<Constraint>> result = TreeMap.empty();
        IdentityMap<Var, Object> varsToGo = variables(o);
        IdentityMap<Var, Object> seenVars = IdentityMap.empty();
        while (!varsToGo.isEmpty()) {
            IdentityMap<Var, Object> otherVars = IdentityMap.empty();
            for (final java.util.Map.Entry<Var, Object> entry : varsToGo) {
                final Var v = entry.getKey();
                final Tuple2<Map<String, List<Constraint>>, IdentityMap<Var, Object>> step =
                        augmentConstraints(v, subst, result);
                result = step._1;
                otherVars = otherVars.withAll(step._2);
            }
            seenVars = seenVars.withAll(varsToGo);
            varsToGo = otherVars.withoutAllKeys(seenVars);
        }
        return result;
    }

    public static Stream<Tuple2<Object, List<Cons>>> runC(Function<Var, Goal> body) {
        final Var q = new Var();
        final Subst subst0 = Subst.empty();
        return Stream.ofAll(body.apply(q).apply(subst0)).map(subst -> {
            Object o = walkDeep(q, subst);
            return Tuple.of(o, collectConstraints(o, subst).toList()
                    .map(e -> e._2.map(c -> Cons.make(walkDeep(c.symbolicRepr(), subst), e._1)))
                    .flatMap(Function.identity()));
        });
    }

    public interface Goal extends Function<Subst, Series<Subst>> {
//        Series<Subst> apply(Subst subst);
    }

    @SuppressWarnings("unused")
    public static class StdGoals {


        public static Goal delayed(Supplier<Goal> supplier) {
            return subst -> supplier.get().apply(subst);
        }

        public static Goal failure() {
            return subst -> Series.empty();
        }

        public static Goal success() {
            return subst -> Series.singleton(subst);
        }

        static class Conj implements Goal {
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

        static class Disj implements Goal {
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

        public static Goal seq(Goal... goal) {
            final int len = goal.length;
            if (len == 0) return success();
            else {
                Goal current = goal[len - 1];
                for (int i = len - 2; i >= 0; i--) {
                    current = new Conj(goal[i], current);
                }
                return current;
            }
        }

        public static Goal seq(List<Goal> goals) {
            if (goals.isEmpty()) return success();
            return goals.reduceRight(Conj::new);
        }

        public static Goal choice(Goal... goal) {
            final int len = goal.length;
            if (len == 0) return failure();
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
            if (len == 0) return failure();
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

        static class ParDisj implements Goal {
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

        public static Goal unify(Object left, Object right) {
            return subst -> {
                final Option<Subst> result = Logish.unify(left, right, subst);
                if (result.isEmpty()) {
                    return Series.empty();
                } else {
                    return Series.singleton(result.get());
                }
            };
        }

        public static Goal equals(Object left, Object right) {
            return subst -> Objects.equals(walk(left, subst), walk(right, subst)) ? Series.singleton(subst) : Series.empty();
        }

        public static Goal same(Object left, Object right) {
            return subst -> walk(left, subst) == walk(right, subst) ? Series.singleton(subst) : Series.empty();
        }


        public static Goal not(Goal goal) {
            return subst -> {
                final Series<Subst> result = goal.apply(subst).forceDeep();
                return result.isEmpty() ? Series.singleton(subst) : Series.empty();
            };
        }

        public static Goal fresh(Function1<Var, Goal> body) {
            return subst -> body.apply(new Var()).apply(subst);
        }

        public static Goal fresh(Function2<Var, Var, Goal> body) {
            return subst -> body.apply(new Var(), new Var()).apply(subst);
        }

        public static Goal fresh(Function3<Var, Var, Var, Goal> body) {
            return subst -> body.apply(new Var(), new Var(), new Var()).apply(subst);
        }

        public static Goal fresh(Function4<Var, Var, Var, Var, Goal> body) {
            return subst -> body.apply(new Var(), new Var(), new Var(), new Var()).apply(subst);
        }

        public static Goal fresh(Function5<Var, Var, Var, Var, Var, Goal> body) {
            return subst -> body.apply(new Var(), new Var(), new Var(), new Var(), new Var()).apply(subst);
        }

        public static Goal fresh(Function6<Var, Var, Var, Var, Var, Var, Goal> body) {
            return subst -> body.apply(new Var(), new Var(), new Var(), new Var(), new Var(), new Var()).apply(subst);
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
                    ifte(unify(x, h), StdGoals::success, () -> memberCheckO(x, t))
            ));
        }

        public static Goal ifte(Goal question, Supplier<Goal> thenBranch, Supplier<Goal> elseBranch) {
            return subst -> {
                final Series<Subst> questionResult = question.apply(subst).forceDeep();
                if (questionResult.isEmpty()) {
                    return elseBranch.get().apply(subst);
                } else {
                    return Series.appendMapInf(thenBranch.get(), questionResult);
                }
            };
        }

        public static Goal once(Goal goal) {
            return subst -> {
                final Series<Subst> result = goal.apply(subst).forceDeep();
                if (result.isEmpty()) return result;
                else return Series.singleton(result.head());
            };
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
            Supplier<Goal> current = StdGoals::failure;
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
            Supplier<Goal> current = StdGoals::failure;
            for (int i = len - 1; i >= 0; i--) {
                final Goal guard = clauses[i].guard;
                final Supplier<Goal> body = clauses[i].body;
                final Supplier<Goal> last = current;
                current = () -> ifte(once(guard), body, last);
            }
            return delayed(current);
        }

        /**
         * Succeeds if the parameter is a free variable.
         *
         * @param x
         * @return
         */
        public static Goal free(Object x) {
            return subst -> walk(x, subst) instanceof Var ? Series.singleton(subst) : Series.empty();
        }

        public static Goal test(Function0<Boolean> test) {
            return subst -> test.apply() ? Series.singleton(subst) : Series.empty();
        }

        public static <T> Goal test(Class<T> clazz, Object x, Function1<T, Boolean> test) {
            return subst -> {
                final Object deref = walk(x, subst);
                return clazz.isInstance(deref) && test.apply(clazz.cast(deref)) ? Series.singleton(subst) : Series.empty();
            };
        }

        public static <T> Goal test(Class<T> clazz, Var x) {
            return subst -> {
                final Object deref = walk(x, subst);
                return clazz.isInstance(deref) ? Series.singleton(subst) : Series.empty();
            };
        }

        public static <T1, T2> Goal test(Class<T1> clazzX, Var x,
                                         Class<T2> clazzY, Var y,
                                         Function2<T1, T2, Boolean> test) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst);
                return clazzX.isInstance(derefX) && clazzY.isInstance(derefY) &&
                        test.apply(clazzX.cast(derefX), clazzY.cast(derefY)) ?
                        Series.singleton(subst) : Series.empty();
            };
        }

        public static <T> Goal test(Class<T> clazzXY, Var x, Var y,
                                         Function2<T, T, Boolean> test) {
            return test(clazzXY, x, clazzXY, y, test);
        }

        public static <T1, T2, T3> Goal test(Class<T1> clazzX, Var x,
                                             Class<T2> clazzY, Var y,
                                             Class<T3> clazzZ, Var z,
                                             Function3<T1, T2, T3, Boolean> test) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst), derefZ = walk(z, subst);
                return clazzX.isInstance(derefX) && clazzY.isInstance(derefY) &&
                        clazzZ.isInstance(derefZ) &&
                        test.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ)) ?
                        Series.singleton(subst) : Series.empty();
            };
        }

        public static <T> Goal test(Class<T> clazzXYZ, Var x, Var y, Var z,
                                    Function3<T, T, T, Boolean>test) {
            return test(clazzXYZ, x, clazzXYZ, y, clazzXYZ, z, test);
        }

        public static Goal side(Function0<Void> action) {
            return subst -> {
                action.apply();
                return Series.singleton(subst);
            };
        }

        public static <T> Goal side(Class<T> clazz, Object x, Function1<T, Void> action) {
            return subst -> {
                final Object derefX = walk(x, subst);
                if (!clazz.isInstance(derefX)) return Series.empty();
                action.apply(clazz.cast(derefX));
                return Series.singleton(subst);
            };
        }

        public static <T1, T2> Goal side(Class<T1> clazzX, Var x,
                                         Class<T2> clazzY, Var y,
                                         Function2<T1, T2, Void> action) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst);
                if (!clazzX.isInstance(derefX) || !clazzY.isInstance(derefY)) return Series.empty();
                action.apply(clazzX.cast(derefX), clazzY.cast(derefY));
                return Series.singleton(subst);
            };
        }

        public static <T> Goal side(Class<T> clazzXY, Var x, Var y,
                                         Function2<T, T, Void> action) {
            return side(clazzXY, x, clazzXY, y, action);
        }

        public static <T1, T2, T3> Goal side(Class<T1> clazzX, Var x,
                                             Class<T2> clazzY, Var y,
                                             Class<T3> clazzZ, Var z,
                                             Function3<T1, T2, T3, Void> action) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst), derefZ = walk(z, subst);
                if (!clazzX.isInstance(derefX) || !clazzY.isInstance(derefY) || !clazzZ.isInstance(derefZ))
                    return Series.empty();
                action.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ));
                return Series.singleton(subst);
            };
        }

        public static <T> Goal side(Class<T> clazzXYZ, Var x, Var y, Var z,
                                    Function3<T, T, T, Void> action) {
            return side(clazzXYZ, x, clazzXYZ, y, clazzXYZ, z, action);
        }

        public static Goal map(Function0<Object> f, Object w) {
            return unify(w, f.apply());
        }

        public static <T> Goal map(Class<T> clazzX, Var x, Function1<T, Object> f, Object w) {
            return subst -> {
                final Object derefX = walk(x, subst);
                if (!clazzX.isInstance(x)) return Series.empty();
                return unify(w, f.apply(clazzX.cast(derefX))).apply(subst);
            };
        }

        public static <T1, T2> Goal map(Class<T1> clazzX, Var x, Class<T2> clazzY, Var y,
                                        Function2<T1, T2, Object> f, Object w) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst);
                if (!clazzX.isInstance(x) || !clazzY.isInstance(y)) return Series.empty();
                return unify(w, f.apply(clazzX.cast(derefX), clazzY.cast(derefY))).apply(subst);
            };
        }

        public static <T> Goal map(Class<T> clazzXY, Var x, Var y,
                                        Function2<T, T, Object> f, Object w) {
            return map(clazzXY, x, clazzXY, y, f, w);
        }

        public static <T1, T2, T3> Goal map(Class<T1> clazzX, Var x, Class<T2> clazzY, Var y,
                                            Class<T3> clazzZ, Var z,
                                            Function3<T1, T2, T3, Object> f, Object w) {
            return subst -> {
                final Object derefX = walk(x, subst), derefY = walk(y, subst), derefZ = walk(z, subst);
                if (!clazzX.isInstance(x) || !clazzY.isInstance(y) || !clazzZ.isInstance(derefZ)) return Series.empty();
                return unify(w, f.apply(clazzX.cast(derefX), clazzY.cast(derefY), clazzZ.cast(derefZ))).apply(subst);
            };
        }

        public static <T> Goal map(Class<T> clazzXYZ, Var x, Var y, Var z,
                                   Function3<T, T, T, Object> f, Object w) {
            return map(clazzXYZ, x, clazzXYZ, y, clazzXYZ, z, f, w);
        }

        public static Goal element(Object x, Seq<?> sequence) {
            return subst -> {
                if (sequence.isEmpty()) return Series.empty();
                return choice(unify(x, sequence.head()),
                        delayed(() -> element(x, sequence.tail()))).apply(subst);
            };
        }

    }
}
