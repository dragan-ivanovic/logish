package org.cellx.logish;

import io.vavr.*;
import io.vavr.collection.*;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.collection.SortedMap;
import io.vavr.collection.SortedSet;
import io.vavr.collection.TreeMap;
import io.vavr.collection.TreeSet;
import io.vavr.control.Option;
import org.cellx.logish.Logish.*;

import java.util.*;

import static org.cellx.logish.Logish.*;

@SuppressWarnings({"unused", "SuspiciousNameCombination"})
public class Fd {

    static final String DOM_DOMAIN = "fd:";

    static abstract class Domain implements Constraint, Attribute {

        abstract Var variable();

        abstract boolean isDefinite();

        abstract boolean isBounded();

        abstract boolean isEmpty();

        abstract boolean hasSolution();

        abstract int solution();

        abstract Domain intersect(Domain other);

        abstract boolean isSubsetOf(Domain other);

        abstract SortedSet<Integer> get();

        abstract boolean accepts(int x);

        abstract int getLowerBound();

        abstract int getUpperBound();

        @Override
        public final boolean delegating() {
            return true;
        }

        @Override
        final public Option<Map<Integer, Object>> validate(Var v, Object o, Map<Integer, Object> subst) {
            if (!(o instanceof Integer && accepts((Integer) o))) return Option.none();
            return Solver.instantiate(v, (Integer) o, subst);
        }

        @Override
        final public List<Constraint> constraints() {
            return List.of(this);
        }

        @Override
        final public Option<Tuple2<Option<Attribute>, Map<Integer, Object>>> combine(Var v, Attribute other, Map<Integer, Object> subst) {
            final Domain combined = intersect((Domain) other);
            return combined.isEmpty() ? Option.none() : Option.of(Tuple.of(Option.of(combined), subst));
        }

        static Domain of(Var v, int lowerBound, int upperBound) {
            return new Bounded(v, lowerBound, upperBound);
        }

        static Domain of(Var v, SortedSet<Integer> values) {
            return new Subset(v, values);
        }

        static Domain of(Var v) {
            return new AnyInteger(v);
        }

    }

    static class AnyInteger extends Domain {
        final Var v;

        private AnyInteger(Var v) {
            this.v = v;
        }

        static AnyInteger of(Var v) {
            return new AnyInteger(v);
        }

        @Override
        public String toString() {
            return String.valueOf(v) + "\u2208Z";
        }

        @Override
        Var variable() {
            return v;
        }

        @Override
        boolean isEmpty() {
            return false;
        }

        @Override
        boolean isDefinite() {
            return false;
        }

        @Override
        boolean isBounded() {
            return false;
        }

        @Override
        int getLowerBound() {
            throw new UnsupportedOperationException();
        }

        @Override
        int getUpperBound() {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean hasSolution() {
            return false;
        }

        @Override
        int solution() {
            throw new UnsupportedOperationException();
        }

        @Override
        Domain intersect(Domain other) {
            return other;
        }

        @Override
        boolean isSubsetOf(Domain other) {
            return !other.isDefinite() && !other.isBounded();
        }

        @Override
        SortedSet<Integer> get() {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean accepts(int x) {
            return true;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "int", v);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AnyInteger)) return false;
            AnyInteger that = (AnyInteger) o;
            return v.equals(that.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v);
        }
    }

    static class Bounded extends Domain {
        final Var v;
        final int lb, ub;

        public Bounded(Var v, int lb, int ub) {
            this.v = v;
            this.lb = lb;
            this.ub = ub;
        }

        @Override
        boolean isEmpty() {
            return ub < lb;
        }

        @Override
        Domain intersect(Domain other) {
            if (other.isDefinite()) {
                return Domain.of(v, other.get().filter(x -> lb <= x && x <= ub));
            } else if (other.isBounded()) {
                return Domain.of(v, Math.max(lb, other.getLowerBound()), Math.min(ub, other.getUpperBound()));
            } else {
                return this;
            }
        }

        @Override
        boolean isSubsetOf(Domain other) {
            if (isEmpty()) return other.isEmpty();
            if (other.isEmpty()) return false;
            if (other.isDefinite()) {
                final int lb2 = other.getLowerBound(), ub2 = other.getUpperBound();
                if (lb < lb2 || ub > ub2) return false;
                final SortedSet<Integer> set2 = other.get();
                if (set2.size() < ub - lb + 1) return false;
                return set2.containsAll(Stream.rangeClosed(lb, ub));
            } else if (other.isBounded()) {
                final int lb2 = other.getLowerBound(), ub2 = other.getUpperBound();
                return lb >= lb2 && ub <= ub2;
            } else {
                return true;
            }
        }

        @Override
        SortedSet<Integer> get() {
            throw new UnsupportedOperationException();
        }

        @Override
        boolean accepts(int x) {
            return lb <= x && x <= ub;
        }

        @Override
        boolean isDefinite() {
            return false;
        }

        @Override
        boolean isBounded() {
            return true;
        }

        @Override
        int getLowerBound() {
            return lb;
        }

        @Override
        int getUpperBound() {
            return ub;
        }

        @Override
        boolean hasSolution() {
            return lb == ub;
        }

        @Override
        int solution() {
            if (lb != ub) throw new IllegalStateException();
            return lb;
        }

        @Override
        Var variable() {
            return v;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "between", v, lb, ub);
        }

        @Override
        public String toString() {
            return String.valueOf(v) + "\u2208[" + lb + ", " + ub + ']';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Bounded)) return false;
            Bounded bounded = (Bounded) o;
            return lb == bounded.lb &&
                    ub == bounded.ub &&
                    Objects.equals(v, bounded.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v, lb, ub);
        }
    }

    static class Subset extends Domain {
        final Var v;
        final SortedSet<Integer> subset;

        private Subset(Var v, SortedSet<Integer> subset) {
            this.v = v;
            this.subset = subset;
        }

        public static Subset of(Var v, int... values) {
            return new Subset(v, TreeSet.ofAll(values));
        }

        public static Subset of(Var v, SortedSet<Integer> values) {
            return new Subset(v, values);
        }

        static final int MAX_TO_STRING = 32;

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder(String.valueOf(v));
            builder.append("\u2208{");
            final int size = get().size();
            builder.append(size);
            builder.append("]{");
            boolean isFirst = true;
            int count = 0;
            for (Integer x : get()) {
                if (!isFirst) builder.append(", ");
                builder.append(x);
                isFirst = false;
                if (++count == MAX_TO_STRING) {
                    if (count < size) builder.append(", ... (")
                            .append(get().size() - count - 1)
                            .append(" more)");
                    break;
                }
            }
            return builder.append("}").toString();
        }

        @Override
        Var variable() {
            return v;
        }

        @Override
        boolean isDefinite() {
            return true;
        }

        @Override
        boolean isBounded() {
            return !subset.isEmpty();
        }

        @Override
        int getLowerBound() {
            return subset.min().get();
        }

        @Override
        int getUpperBound() {
            return subset.max().get();
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.fromIterable(subset), "dom", v);
        }

        @Override
        public SortedSet<Integer> get() {
            return subset;
        }

        @Override
        public boolean isEmpty() {
            return subset.isEmpty();
        }

        @Override
        boolean hasSolution() {
            return subset.size() == 1;
        }

        @Override
        int solution() {
            return subset.head();
        }

        @Override
        public boolean accepts(int x) {
            return subset.contains(x);
        }

        @Override
        public Domain intersect(Domain other) {
            if (other.isDefinite()) {
                return new Subset(v, subset.intersect(((Subset) other).subset));
            } else if (other.isBounded()) {
                final int lb2 = other.getLowerBound(), ub2 = other.getUpperBound();
                return Domain.of(v, subset.filter(x -> lb2 <= x && x <= ub2));
            } else {
                return this;
            }
        }

        @Override
        boolean isSubsetOf(Domain other) {
            if (other.isDefinite()) {
                return other.get().containsAll(get());
            } else if (other.isBounded()) {
                final int lb2 = other.getLowerBound(), ub2 = other.getUpperBound();
                return !get().exists(x -> x < lb2 || x > ub2);
            } else {
                return true;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Subset)) return false;
            Subset that = (Subset) o;
            return v.equals(that.v) &&
                    subset.equals(that.subset);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v, subset);
        }
    }

    static class DomGoal extends Goal {
        final Var v0;
        final Domain domain;

        DomGoal(Var v, Domain domain) {
            this.v0 = v;
            this.domain = domain;
        }

        @Override
        public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
            // Fail immediately if the constraint is empty
            if (domain.isEmpty()) return Series.empty();

            final Object o = walk(v0, subst);
            if (o instanceof Integer) {
                return domain.accepts((Integer) o) ? Series.singleton(subst) : Series.empty();
            } else if (!(o instanceof Var)) {
                return Series.empty();
            }

            final Domain newConstraint;
            final Var v = (Var) o;
            final Option<Attribute> optOldDomain = getAttribute(v, subst, DOM_DOMAIN);
            final Map<Integer, Object> subst1;
            if (optOldDomain.isEmpty()) {
                newConstraint = domain;
                subst1 = subst;
            } else {
                final Option<Tuple2<Option<Attribute>, Map<Integer, Object>>> combination =
                        optOldDomain.get().combine(v, domain, subst);
                if (combination.isEmpty()) return Series.empty();
                newConstraint = (Domain) combination.get()._1.get();
                subst1 = combination.get()._2;
            }

            if (newConstraint.isEmpty()) {
                return Series.empty();
            }

            return Series.of(Solver.addDomain(newConstraint, subst));
        }
    }

    public static Goal dom(Var v, Seq<Integer> elements) {
        return new DomGoal(v, new Subset(v, TreeSet.ofAll(elements)));
    }

    public static Goal domAll(Seq<Integer> elements, Var... vars) {
        return Goal.seq(List.ofAll(Arrays.stream(vars))
                .map(v -> new DomGoal(v, new Subset(v, TreeSet.ofAll(elements)))));
    }

    public static Goal in(Var v, int... elements) {
        return dom(v, List.ofAll(elements));
    }

    public static Goal range(Var v, int lb, int ub) {
        return new DomGoal(v, new Subset(v, TreeSet.rangeClosed(lb, ub)));
    }

    // -- Arithmetic constraints --

    public static final String ARI_DOMAIN = "fda:";

    static class FdAttribute implements Attribute {
        final List<FdConstraint> constraints;

        public FdAttribute(List<FdConstraint> constraints) {
            this.constraints = constraints;
        }

        @Override
        public boolean delegating() {
            return false;
        }

        @Override
        public Option<Map<Integer, Object>> validate(Var v, Object o, Map<Integer, Object> subst) {
            return Option.of(subst);
        }

        @Override
        public List<Constraint> constraints() {
            return List.narrow(constraints);
        }

        @Override
        public Option<Tuple2<Option<Attribute>, Map<Integer, Object>>> combine(Var v, Attribute other, Map<Integer, Object> subst) {
            final FdAttribute otherAri = (FdAttribute) other;
            return Option.of(Tuple.of(
                    Option.of(new FdAttribute(constraints.appendAll(otherAri.constraints.filter(c -> !constraints.contains(c))))),
                    subst
            ));
        }
    }

    interface FdConstraint extends Constraint {
        /**
         * Gets the variables over which this constraint ranges.
         *
         * @return The set of variables.
         */
        Set<Var> vars();

        void maybeAddToAgenda(Solver solver, int varIndex, Domain dom);

        /**
         * Tries to propagate the constraint.
         *
         * @param solver Solver instance
         * @return false if the problem is unsatisfiable, true otherwise
         */
        boolean propagate(Solver solver);
    }


    static boolean isEmpty(Domain dom) {
        return dom.isEmpty();
    }

    static boolean hasSolution(Domain dom) {
        return dom.hasSolution();
    }

    static int solution(Domain dom) {
        return dom.solution();
    }

    static boolean isDefinite(Domain dom) {
        return dom.isDefinite();
    }

    /**
     * Finite constraint solver.
     */
    static class Solver {

        static <U extends Comparable<U>> SortedSet<U> emptySet() {
            return TreeSet.empty();
        }

        static <U extends Comparable<U>, V> SortedMap<U, V> emptyMap() {
            return TreeMap.empty();
        }

        /**
         * The original substitution under which this solver operates.
         */
        final Map<Integer, Object> subst;

        // ---------- Variable Constraints ----------
        /**
         * Mapping from variable indices to lists of constraints that subscribe to these variables.
         */
        Map<Integer, List<FdConstraint>> varConstraints = emptyMap();

        /**
         * Returns the list of constraints that subscribe to the variable with the given index.
         *
         * @param varIndex The variable index
         * @return The list of constraints subscribing to the changes in the given variable's domain
         */
        List<FdConstraint> getConstraints(int varIndex) {
            final Option<List<FdConstraint>> optConstraints = varConstraints.get(varIndex);
            if (!optConstraints.isEmpty()) return optConstraints.get();
            final List<FdConstraint> result = getAttribute(varIndex, subst, ARI_DOMAIN)
                    .map(a -> ((FdAttribute) a).constraints)
                    .getOrElse(List.empty());
            varConstraints = varConstraints.put(varIndex, result);
            return result;
        }

        /**
         * Registers a new constraint with the variables that listen to it.
         *
         * @param c the new constraint
         * @return true
         */
        boolean registerConstraint(FdConstraint c) {
            for (final int varSeq : c.vars().map(v -> walkVar(v, subst).index)) {
                final List<FdConstraint> cs = getConstraints(varSeq);
                if (!cs.contains(c)) {
                    varConstraints = varConstraints.put(varSeq, cs.prepend(c));
                }
            }
            return true;
        }

        /**
         * Unregisters the constraint with the variables to which it subscribes.
         *
         * @param c the constraint to unregister
         * @return true
         */
        boolean unregisterConstraint(FdConstraint c) {
            for (final int varSeq : c.vars().map(v -> walkVar(v, subst).index)) {
                varConstraints = varConstraints.put(varSeq, getConstraints(varSeq).remove(c));
            }
            return true;
        }

        // ---------- Excited variables ----------

        void excite(int varIndex, Domain dom) {
            for (final FdConstraint c : getConstraints(varIndex)) {
                c.maybeAddToAgenda(this, varIndex, dom);
            }
        }

        void excite(Var v, Domain dom) {
            excite(walkVarIndex(v.index, subst), dom);
        }

        // ---------- Variable Domains ------------
        /**
         * Mapping from (walked) variable indices to domains.
         * <p>Each element is either a finite set of allowed integers (right), or a  finite set of excluded
         * integers (left).</p>
         */
        Map<Integer, Domain> varDomains = emptyMap();
        /**
         * Set of variables that were already instantiated.
         */
        Set<Integer> instantiatedVars = TreeSet.empty();

        /**
         * Gets the domain of the given variable
         *
         * @param varIndex
         * @return the variable domain
         */
        Domain getDomain(int varIndex) {
            final Option<Domain> optCurrent = varDomains.get(varIndex);
            if (!optCurrent.isEmpty()) return optCurrent.get();
            final Object value = subst.get(varIndex).get();
            final Domain result;
            if (value instanceof Var) {
                final Option<Attribute> fromSubst = getAttribute(varIndex, subst, DOM_DOMAIN);
                if (fromSubst.isEmpty()) {
                    result = Domain.of(new Var(varIndex));
                } else {
                    result = (Domain) fromSubst.get();
                }
            } else {
                instantiatedVars = instantiatedVars.add(varIndex);
                if (value instanceof Integer) {
                    result = Subset.of(new Var(varIndex), (Integer) value);
                } else {
                    result = Domain.of(new Var(varIndex));
                }
            }
            varDomains = varDomains.put(varIndex, result);
            return result;
        }


        boolean reduceDomain(int varIndex, int solution) {
            return reduceDomain(varIndex, TreeSet.of(solution));
        }

        boolean reduceDomain(int varIndex, SortedSet<Integer> values) {
            return reduceDomain(varIndex, Domain.of(new Var(varIndex), values));
        }

        boolean reduceDomain(int varIndex, Domain dom) {
            final Domain dom0 = getDomain(varIndex);
            final Domain dom1 = dom0.intersect(dom);
            if (dom1.isEmpty()) return false;
            if (dom0.isSubsetOf(dom1)) return true;
            varDomains = varDomains.put(varIndex, dom1);
            excite(varIndex, dom);
            return true;
        }

        LinkedList<Tuple2<FdConstraint, Integer>> queue = new LinkedList<>();

        Solver(Map<Integer, Object> subst) {
            this.subst = subst;
        }

        boolean isAgendaEmpty() {
            return queue.isEmpty();
        }

        FdConstraint dequeue() {
            return queue.remove()._1;
        }

        /**
         * Enqueues the constraint to the agenda.
         *
         * <p>Constraint with weight (c, w) is added to the agenda in the following way:</p>
         * <ul>
         *     <li>If (c, w') is already in the agenda with w' &lt; w nothing changes.</li>
         *     <li>Else, (c, w) is inserted behind any element (c', w') where w' &le; w</li>
         * </ul>
         *
         * @param c      the constraint
         * @param weight a numerical indicator of the computational weight (smaller is better)
         */
        void enqueue(FdConstraint c, int weight) {
            final ListIterator<Tuple2<FdConstraint, Integer>> iterator = queue.listIterator();
            while (iterator.hasNext()) {
                final Tuple2<FdConstraint, Integer> t = iterator.next();
                if (t._1 == c) {
                    if (t._2 >= weight) iterator.set(Tuple.of(c, weight));
                    return;
                } else if (t._2 > weight) {
                    iterator.previous();
                    iterator.add(Tuple.of(c, weight));
                    do {
                        final Tuple2<FdConstraint, Integer> u = iterator.next();
                        if (u._1 == c) {
                            iterator.remove();
                            return;
                        }
                    } while (iterator.hasNext());
                    break;
                }
            }
            iterator.add(Tuple.of(c, weight));
        }

        boolean enqueueNew(FdConstraint c, int weight) {
            enqueue(c, weight);
            return registerConstraint(c);
        }

        // ---------- Interface ----------

        static Option<Map<Integer, Object>> instantiate(Var v, int x, Map<Integer, Object> subst) {
            final Solver solver = new Solver(subst);
            final Var walked = walkVar(v, subst);
            if (!solver.reduceDomain(walked.index, Subset.of(walked, x)))
                return Option.none();
            return solver.solution();
        }

        static Option<Map<Integer, Object>> addDomain(Domain d, Map<Integer, Object> subst) {
            final Solver solver = new Solver(subst);
            final Var walked = walkVar(d.variable(), subst);
            if (!solver.reduceDomain(walked.index, d))
                return Option.none();
            return solver.solution();
        }

        static Option<Map<Integer, Object>> registerConstraint(FdConstraint c, Map<Integer, Object> subst) {
            final Solver solver = new Solver(subst);
            solver.registerConstraint(c);
            solver.enqueue(c, 0);
            return solver.solution();
        }

        Logish.Series<Map<Integer, Object>> label(Set<Var> vars) {
            if (!getToFixpoint()) return Series.empty();
            final List<Tuple4<Var, SortedSet<Integer>, Integer, Integer>> candidates =
                    vars.map(v -> walkVar(v, subst)).toList().map(v -> Tuple.of(v, getDomain(v.index)))
                            .filter(t -> isDefinite(t._2) && !hasSolution(t._2))
                            .map(t -> Tuple.of(t._1, t._2.get(), t._2.get().size(), getConstraints(t._1.index).length()))
                            .sorted((t1, t2) -> t1._3.equals(t2._3) ? t2._4 - t1._4 : t1._3 - t2._3);
            if (candidates.exists(t -> t._3 == 0)) return Series.empty();
            if (candidates.isEmpty()) return Series.of(solution());
            final Tuple4<Var, SortedSet<Integer>, Integer, Integer> chosen = candidates.head();
            final int half = chosen._3 / 2, chosenVarIndex = chosen._1.index;
            final SortedSet<Integer> firstHalf = chosen._2.take(half),
                    secondHalf = chosen._2.drop(half);
            final Solver branch = duplicate();
            reduceDomain(chosenVarIndex, firstHalf);
            return Series.appendInf(label(vars), Series.suspension(() -> {
                branch.reduceDomain(chosenVarIndex, secondHalf);
                return branch.label(vars);
            }));
        }

        Solver duplicate() {
            final Solver result = new Solver(subst);
            result.varConstraints = varConstraints;
            result.varDomains = varDomains;
            result.instantiatedVars = instantiatedVars;
            return result;
        }


        // ---------- Inner mechanics ----------

        boolean getToFixpoint() {
            while (!queue.isEmpty()) {
                if (!queue.remove()._1.propagate(this)) return false;
            }
            return true;
        }

        Option<Map<Integer, Object>> solution() {

            if (!getToFixpoint()) return Option.none();

            Map<Integer, Object> result = subst;

            final Map<Integer, Integer> solved =
                    varDomains.filter(t -> hasSolution(t._2)).mapValues(d -> d.get().head());

            // Remove domain and ari constraint from the solved variables
            for (final int varSeq : solved.keysIterator()) {
                result = removeAttribute(varSeq, removeAttribute(varSeq, result, ARI_DOMAIN), DOM_DOMAIN);
            }

            // Set domains for unsolved variables
            for (final int varSeq : varDomains.keysIterator()) {
                if (solved.containsKey(varSeq)) continue;
                final Domain d = varDomains.get(varSeq).get();
                result = setAttribute(varSeq, result, DOM_DOMAIN, d);
            }

            // Set arithmetic constraints for unsolved variables
            for (final int varSeq : varConstraints.keysIterator()) {
                if (solved.containsKey(varSeq)) continue;
                final List<FdConstraint> cs = varConstraints.get(varSeq).get();
                if (cs.isEmpty()) {
                    result = removeAttribute(varSeq, result, ARI_DOMAIN);
                } else {
                    result = setAttribute(varSeq, result, ARI_DOMAIN, new FdAttribute(cs));
                }
            }

            // Instantiate solved variables by unifying them recursively
            for (final Tuple2<Integer, Integer> solution : solved) {
                if (instantiatedVars.contains(solution._1)) continue; // variable instantiated earlier
                final Option<Map<Integer, Object>> step = unify(new Var(solution._1), solution._2, result);
                if (step.isEmpty()) return Option.none();
                result = step.get();
            }

            return Option.of(result);
        }
    }

    /**
     * A constraint of the form: Variable + Constant = Variable
     */
    static class PlusVCV implements FdConstraint {
        final Var x;
        final int y;
        final Var z;

        PlusVCV(Var x, int y, Var z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v+n=v", x, y, z);
        }

        @Override
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, z);
        }

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            // If the domain of either x or z has become definite, schedule the propagator to replace
            // itself with more precise PlusVCV_X
            if (isDefinite(dom)) {
                solver.enqueue(this, 2);
            }
        }

        @Override
        public boolean propagate(Solver solver) {
            final Var x = walkVar(this.x, solver.subst), z = walkVar(this.z, solver.subst);

            solver.unregisterConstraint(this);

            if (x.index == z.index) {
                // Case x + y = x
                return y == 0;
            }

            final Domain domX = solver.getDomain(x.index),
                    domZ = solver.getDomain(z.index);

            if (isEmpty(domX) || isEmpty(domZ)) return false;

            if (hasSolution(domX)) {
                if (hasSolution(domZ)) {
                    return solution(domX) + y == solution(domZ);
                } else {
                    return solver.reduceDomain(z.index, solution(domX) + y);
                }
            } else if (hasSolution(domZ)) {
                return solver.reduceDomain(x.index, solution(domZ) - y);
            }

            if (isDefinite(domX)) {
                if (isDefinite(domZ)) {
                    final SortedSet<Integer> zs = domZ.get(),
                            xs2 = domX.get().filter(xx -> zs.contains(xx + y)),
                            zs2 = xs2.map(xx -> xx + y);
                    return solver.reduceDomain(x.index, xs2) && solver.reduceDomain(z.index, zs2) &&
                            solver.registerConstraint(this);
                } else {
                    return solver.reduceDomain(z.index, domX.get().map(xx -> xx + y)) &&
                            solver.registerConstraint(this);
                }
            } else if (isDefinite(domZ)) {
                return solver.reduceDomain(x.index, domZ.get().map(zz -> zz - y)) &&
                        solver.registerConstraint(this);
            }

            return solver.registerConstraint(this);
        }
    }

    static class PlusVVC implements FdConstraint {
        final Var x;
        final Var y;
        final int z;

        PlusVVC(Var x, Var y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v+v=n", x, y, z);
        }

        @Override
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            if (isDefinite(dom)) {
                solver.enqueue(this, 1);
            }
        }

        @Override
        public boolean propagate(Solver solver) {
            final Var x = walkVar(this.x, solver.subst), y = walkVar(this.y, solver.subst);

            solver.unregisterConstraint(this);

            if (x.index == y.index) {
                // Case: 2x = z
                if ((z & 1) != 0) { // z is odd -> no solution
                    return false;
                } else {
                    return solver.reduceDomain(x.index, z / 2);
                }
            }

            // x and y are distinct variables

            final Domain domX = solver.getDomain(x.index),
                    domY = solver.getDomain(y.index);

            if (isEmpty(domX) || isEmpty(domY)) return false;

            if (hasSolution(domX)) {
                if (hasSolution(domY)) {
                    return solution(domX) + solution(domY) == z;
                } else {
                    return solver.reduceDomain(y.index, z - solution(domX));
                }
            } else if (hasSolution(domY)) {
                return solver.reduceDomain(x.index, z - solution(domY));
            }

            if (isDefinite(domX)) {
                if (isDefinite(domY)) {
                    final SortedSet<Integer> ys = domY.get(),
                            xs2 = domX.get().filter(xx -> ys.contains(z - xx)),
                            ys2 = xs2.map(xx -> z - xx);
                    return solver.reduceDomain(x.index, xs2) && solver.reduceDomain(y.index, ys2) &&
                            solver.registerConstraint(this);
                } else {
                    return solver.reduceDomain(y.index, domX.get().map(xx -> z - xx)) &&
                            solver.registerConstraint(this);
                }
            } else if (isDefinite(domY)) {
                return solver.reduceDomain(x.index, domY.get().map(yy -> z - yy)) &&
                        solver.registerConstraint(this);
            }

            return solver.registerConstraint(this);
        }
    }


    static class PlusVVV implements FdConstraint {
        final Var x;
        final Var y;
        final Var z;

        PlusVVV(Var x, Var y, Var z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v+v=v", x, y, z);
        }

        @Override
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y, z);
        }

        final static int CARD_THRESHOLD = 256;

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            if (isDefinite(dom)) {
                solver.enqueue(this, 3);
            }
        }

        @Override
        public boolean propagate(Solver solver) {
            final Var x = walkVar(this.x, solver.subst), y = walkVar(this.y, solver.subst),
                    z = walkVar(this.z, solver.subst);

            solver.unregisterConstraint(this);

            if (x.index == y.index && y.index == z.index) {
                // Case: x + x = x --> x = 0
                return solver.reduceDomain(x.index, 0);
            }

            if (x.index == y.index) {
                // Case: 2x = z
                solver.enqueueNew(new TimesCVV(2, x, z), 2);
                return true;
            }

            if (x.index == z.index) {
                // Case: x + y = x --> y = 0
                return solver.reduceDomain(y.index, 0);
            }

            if (y.index == z.index) {
                // Case: x + y = y --> x = 0
                return solver.reduceDomain(x.index, 0);
            }

            // x, y, z are three distinct variables

            final Domain domX = solver.getDomain(x.index), domY = solver.getDomain(y.index),
                    domZ = solver.getDomain(z.index);

            if (isEmpty(domX) || isEmpty(domY) || isEmpty(domZ)) return false;

            // First, try to reduce to VCV or VVC cases
            if (hasSolution(domX)) {
                return solver.enqueueNew(new PlusVCV(y, solution(domX), z), 1);

            } else if (hasSolution(domY)) {
                return solver.enqueueNew(new PlusVCV(x, solution(domY), z), 1);

            } else if (hasSolution(domZ)) {
                return solver.unregisterConstraint(this);
            }

            if (isDefinite(domX) && isDefinite(domY)) {
                final SortedSet<Integer> setX = domX.get(), setY = domY.get();
                final int nX = setX.size(), nY = setY.size();

                if (nX <= CARD_THRESHOLD && nY <= CARD_THRESHOLD && nX * nY <= CARD_THRESHOLD) {
                    final List<Tuple2<Integer, Integer>> cXY =
                            setX.toList().crossProduct(setY).toList();
                    return solver.enqueueNew(new PlusVVV_XY(x, y, cXY, z), 2);
                }
            }

            if (isDefinite(domX) && isDefinite(domZ)) {
                final SortedSet<Integer> setX = domX.get(), setZ = domZ.get();
                final int nX = setX.size(), nZ = setZ.size();

                if (nX <= CARD_THRESHOLD && nZ <= CARD_THRESHOLD && nX * nZ <= CARD_THRESHOLD) {
                    final List<Tuple2<Integer, Integer>> cXY =
                            setX.toList().crossProduct(setZ).map(t -> Tuple.of(t._1, t._2 - t._1)).toList();
                    return solver.enqueueNew(new PlusVVV_XY(x, y, cXY, z), 2);
                }
            }

            if (isDefinite(domY) && isDefinite(domZ)) {
                final SortedSet<Integer> setY = domY.get(), setZ = domZ.get();
                final int nY = setY.size(), nZ = setZ.size();

                if (nY <= CARD_THRESHOLD && nZ <= CARD_THRESHOLD && nY * nZ <= CARD_THRESHOLD) {
                    final List<Tuple2<Integer, Integer>> cXY =
                            setY.toList().crossProduct(setZ).map(t -> Tuple.of(t._2 - t._1, t._1)).toList();
                    return solver.enqueueNew(new PlusVVV_XY(x, y, cXY, z), 2);
                }
            }

            if (isDefinite(domX) && isDefinite(domY) && isDefinite(domZ)) {
                final SortedSet<Integer> cX = domX.get().filter(xx -> domY.get().exists(yy -> domZ.get().contains(xx + yy))),
                        cY = domY.get().filter(yy -> cX.exists(xx -> domZ.get().contains(xx + yy))),
                        cZ = domZ.get().filter(zz -> cX.exists(xx -> cY.contains(zz - xx)));
                return solver.reduceDomain(x.index, cX) && solver.reduceDomain(y.index, cY) && solver.reduceDomain(z.index, cZ) &&
                        solver.registerConstraint(this);
            }

            return solver.registerConstraint(this);
        }
    }

    static class PlusVVV_XY extends PlusVVV {
        final List<Tuple2<Integer, Integer>> cXY;

        public PlusVVV_XY(Var x, Var y, List<Tuple2<Integer, Integer>> cXY, Var z) {
            super(x, y, z);
            this.cXY = cXY;
        }

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            if (!isDefinite(dom)) return;
            final SortedSet<Integer> set = dom.get();
            if ((walkVar(x, solver.subst).index == varIndex && cXY.exists(t -> !set.contains(t._1))) ||
                    (walkVar(y, solver.subst).index == varIndex && cXY.exists(t -> !set.contains(t._2))) ||
                    (walkVar(z, solver.subst).index == varIndex && cXY.exists(t -> !set.contains(t._1 + t._2)))) {
                solver.enqueue(this, 2);
            }
        }

        @Override
        public boolean propagate(Solver solver) {

            final Var x = walkVar(this.x, solver.subst), y = walkVar(this.y, solver.subst),
                    z = walkVar(this.z, solver.subst);

            solver.unregisterConstraint(this);

            if (x.index == y.index && y.index == z.index) {
                // Case: x + x = x --> x = 0
                return solver.reduceDomain(x.index, 0);
            }

            if (x.index == y.index) {
                // Case: 2x = z
                return solver.enqueueNew(new TimesCVV(2, x, z), 2);
            }

            if (x.index == z.index) {
                // Case: x + y = x --> y = 0
                return solver.reduceDomain(y.index, 0);
            }

            if (y.index == z.index) {
                // Case: x + y = y --> x = 0
                return solver.reduceDomain(x.index, 0);
            }

            // x, y, z are three distinct variables

            final Domain domX = solver.getDomain(x.index), domY = solver.getDomain(y.index),
                    domZ = solver.getDomain(z.index);

            if (isEmpty(domX) || isEmpty(domY) || isEmpty(domZ)) return false;

            if (hasSolution(domX) && hasSolution(domY) && hasSolution(domZ)) {
                final int xx = solution(domX), yy = solution(domY), zz = solution(domZ);
                return xx + yy == zz &&
                        cXY.exists(t -> t._1 == xx) && cXY.exists(t -> t._2 == yy);

            } else if (hasSolution(domX) && hasSolution(domY)) {
                final int xx = solution(domX), yy = solution(domY);
                return cXY.exists(t -> t._1 == xx) && cXY.exists(t -> t._2 == yy) &&
                        solver.reduceDomain(z.index, xx + yy);

            } else if (hasSolution(domX) && hasSolution(domZ)) {
                final int xx = solution(domX), zz = solution(domZ);
                return cXY.exists(t -> t._1 == xx) && cXY.exists(t -> t._1 + t._2 == zz) &&
                        solver.reduceDomain(y.index, zz - xx);

            } else if (hasSolution(domY) && hasSolution(domZ)) {
                final int yy = solution(domY), zz = solution(domZ);
                return cXY.exists(t -> t._2 == yy) && cXY.exists(t -> t._1 + t._2 == zz) &&
                        solver.reduceDomain(x.index, zz - yy);

            } else if (hasSolution(domX)) {
                solver.enqueueNew(new PlusVCV(y, solution(domX), z), 1);
                return true;

            } else if (hasSolution(domY)) {
                solver.enqueueNew(new PlusVCV(x, solution(domY), z), 1);
                return true;

            } else if (hasSolution(domZ)) {
                solver.enqueueNew(new PlusVVC(x, y, solution(domZ)), 1);
                return true;
            }

            if (isDefinite(domX) && isDefinite(domY) && isDefinite(domZ)) {
                final SortedSet<Integer> setX = domX.get(), setY = domY.get(), setZ = domZ.get();
                final List<Tuple2<Integer, Integer>>
                        cXY2 = cXY.filter(t -> setX.contains(t._1) && setY.contains(t._2) && setZ.contains(t._1 + t._2));
                final SortedSet<Integer> cX2 = cXY2.map(t -> t._1).toSortedSet(),
                        cY2 = cXY2.map(t -> t._2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2)
                        && solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domX) && isDefinite(domY)) {
                final SortedSet<Integer> setX = domX.get(), setY = domY.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setX.contains(t._1) && setY.contains(t._2));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domX) && isDefinite(domZ)) {
                final SortedSet<Integer> setX = domX.get(), setZ = domZ.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setX.contains(t._1) && setZ.contains(t._1 + t._2));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domY) && isDefinite(domZ)) {
                final SortedSet<Integer> setY = domY.get(), setZ = domZ.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setY.contains(t._2) && setZ.contains(t._1 + t._2));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domX)) {
                final SortedSet<Integer> setX = domX.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setX.contains(t._1));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domY)) {
                final SortedSet<Integer> setY = domY.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setY.contains(t._2));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));

            } else if (isDefinite(domZ)) {
                final SortedSet<Integer> setZ = domZ.get();
                final List<Tuple2<Integer, Integer>> cXY2 = cXY.filter(t -> setZ.contains(t._1 + t._2));
                final SortedSet<Integer> cX2 = cXY2.map(Tuple2::_1).toSortedSet(),
                        cY2 = cXY2.map(Tuple2::_2).toSortedSet(),
                        cZ2 = cXY2.map(t -> t._1 + t._2).toSortedSet();
                return solver.reduceDomain(x.index, cX2) && solver.reduceDomain(y.index, cY2) &&
                        solver.reduceDomain(z.index, cZ2) &&
                        solver.registerConstraint(new PlusVVV_XY(x, y, cXY2, z));
            }

            return solver.registerConstraint(this);

        }
    }

    static class AriGoal extends Goal {


        final FdConstraint constraint;

        AriGoal(FdConstraint constraint) {
            this.constraint = constraint;
        }

        @Override
        public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
            return Series.of(Solver.registerConstraint(constraint, subst));
        }
    }

    public static Goal plusO(Var x, Var y, Var z) {
        return new AriGoal(new PlusVVV(x, y, z));
    }

    public static Goal plusO(int x, Var y, Var z) {
        return new AriGoal(new PlusVCV(y, x, z));
    }

    public static Goal plusO(Var x, int y, Var z) {
        return new AriGoal(new PlusVCV(x, y, z));
    }

    public static Goal plusO(int x, int y, Var z) {
        return Goal.unify(x + y, z);
    }

    public static Goal plusO(Var x, Var y, int z) {
        return new AriGoal(new PlusVVC(x, y, z));
    }

    public static Goal plusO(int x, Var y, int z) {
        return Goal.unify(z - x, y);
    }

    public static Goal plusO(Var x, int y, int z) {
        return Goal.unify(z - y, x);
    }

    public static Goal plusO(int x, int y, int z) {
        return x + y == z ? Goal.success() : Goal.failure();
    }


    static class AllDifferent implements FdConstraint {
        final Set<Var> xs;
        final SortedSet<Integer> ys;

        public AllDifferent(Set<Var> xs, SortedSet<Integer> ys) {
            this.xs = xs;
            this.ys = ys;
        }

        @Override
        public Set<Var> vars() {
            return xs;
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.fromIterable(xs), "allDifferent", Cons.fromIterable(ys));
        }

        @Override
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            if (isDefinite(dom)) {
                solver.enqueue(this, 0);
            }
        }

        @Override
        public boolean propagate(Solver solver) {

            final List<Tuple2<Var, Domain>> walkedVars =
                    xs.map(v -> walkVar(v, solver.subst)).toList().map(v -> Tuple.of(v, solver.getDomain(v.index)));

            solver.unregisterConstraint(this);

            Set<Var> xs2 = HashSet.empty();
            SortedSet<Integer> ys2 = ys;

            for (final Tuple2<Var, Domain> t : walkedVars) {
                if (!hasSolution(t._2)) continue;
                final int sol = solution(t._2);
                if (ys2.contains(sol)) return false;
                ys2 = ys2.add(sol);
            }

            for (final Tuple2<Var, Domain> t : walkedVars) {
                if (hasSolution(t._2)) continue;
                xs2 = xs2.add(t._1);
                if (!isDefinite(t._2)) continue;
                if (!solver.reduceDomain(t._1.index, t._2.get().diff(ys2))) return false;
            }

            if (!xs2.isEmpty()) solver.registerConstraint(new AllDifferent(xs2, ys2));

            return true;
        }
    }

//    public static Goal neqO(Var x, Var y) {
//        return new AriGoal(new AllDifferent(HashSet.of(x, y), TreeSet.empty()));
//    }
//
//    public static Goal neqO(Var x, int y) {
//        return new AriGoal(new NotInVS(x, y));
//    }
//
//    public static Goal neqO(int x, Var y) {
//        return new AriGoal(new NotInVS(y, x));
//    }
//
//    public static Goal neqO(int x, int y) {
//        return x != y ? Goal.success() : Goal.failure();
//    }

    public static Goal allDifferentO(Var... vs) {
        return new AriGoal(new AllDifferent(HashSet.ofAll(Arrays.stream(vs)), TreeSet.empty()));
    }

    public static Goal allDifferentO(Iterable<Integer> excludedValues, Var... vs) {
        return new AriGoal(new AllDifferent(HashSet.ofAll(Arrays.stream(vs)), TreeSet.ofAll(excludedValues)));
    }

    //    static class LeqVC implements AriConstraint {
//        final Var x;
//        final int y;
//
//        public LeqVC(Var x, int y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(x);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "v<=n", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed1(x, (xv, domX) -> {
//                if (domX.hasSolution()) {
//                    return domX.solution() <= y;
//                } else {
//                    propagator.add(new LeqVC(xv, y));
//                    if (domX.isEnumerated()) {
//                        return propagator.add(Enumerated.of(xv, domX.values().filter(x -> x <= y)));
//                    } else if (domX.isBounded()) {
//                        final int ubX = domX.getUpperBound();
//                        if (ubX <= y) return true;
//                        return propagator.add(RangeDomain.of(xv, domX.getLowerBound(), Math.min(ubX, y)));
//                    } else {
//                        return true;
//                    }
//                }
//            });
//        }
//    }
//
//
//    static class LeqCV implements AriConstraint {
//        final int x;
//        final Var y;
//
//        public LeqCV(int x, Var y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(y);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "n<=v", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed1(y, (yv, domY) -> {
//                if (domY.hasSolution()) {
//                    return x <= domY.solution();
//                } else {
//                    propagator.add(new LeqCV(x, yv));
//                    if (domY.isEnumerated()) {
//                        return propagator.add(Enumerated.of(yv, domY.values().filter(y -> x <= y)));
//                    } else if (domY.isBounded()) {
//                        final int lbY = domY.getLowerBound();
//                        if (x <= lbY) return true;
//                        return propagator.add(RangeDomain.of(yv, Math.max(lbY, x), domY.getUpperBound()));
//                    } else {
//                        return true;
//                    }
//                }
//            });
//        }
//    }
//
//    static class LeqVV implements AriConstraint {
//        final Var x, y;
//
//        public LeqVV(Var x, Var y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(x, y);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "v<=v", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
//                if (xv.seq == yv.seq) {
//                    return true;
//                } else if (domX.hasSolution()) {
//                    return new LeqCV(domX.solution(), yv).propagate(propagator);
//                } else if (domY.hasSolution()) {
//                    return new LeqVC(xv, domY.solution()).propagate(propagator);
//                } else if (domX.isBounded() && domY.isBounded()) {
//                    final int lbX = domX.getLowerBound(), ubX = domX.getUpperBound(),
//                            lbY = domY.getLowerBound(), ubY = domY.getUpperBound();
//                    if (ubX <= lbY) return true;
//                    propagator.add(new LeqVV(xv, yv));
//                    return propagator.add(RangeDomain.of(xv, lbX, Math.min(ubX, ubY)).intersect(domX)) &&
//                            propagator.add(RangeDomain.of(yv, Math.max(lbX, lbY), ubY).intersect(domY));
//                } else {
//                    propagator.add(new LeqVV(xv, yv));
//                    return true;
//                }
//            });
//        }
//    }
//
//    public static Goal leqO(Var x, Var y) {
//        return new AriGoal(new LeqVV(x, y));
//    }
//
//    public static Goal leqO(int x, Var y) {
//        return new AriGoal(new LeqCV(x, y));
//    }
//
//    public static Goal leqO(Var x, int y) {
//        return new AriGoal(new LeqVC(x, y));
//    }
//
//    public static Goal leqO(int x, int y) {
//        return x <= y ? Goal.success() : Goal.failure();
//    }
//
//
//    static class LtVC implements AriConstraint {
//        final Var x;
//        final int y;
//
//        public LtVC(Var x, int y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(x);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "v<n", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed1(x, (xv, domX) -> {
//                if (domX.hasSolution()) {
//                    return domX.solution() < y;
//                } else {
//                    propagator.add(new LtVC(xv, y));
//                    if (domX.isEnumerated()) {
//                        return propagator.add(Enumerated.of(xv, domX.values().filter(x -> x < y)));
//                    } else if (domX.isBounded()) {
//                        final int ubX = domX.getUpperBound();
//                        if (ubX < y) return true;
//                        return propagator.add(RangeDomain.of(xv, domX.getLowerBound(), Math.min(ubX, y - 1)));
//                    } else {
//                        return true;
//                    }
//                }
//            });
//        }
//    }
//
//
//    static class LtCV implements AriConstraint {
//        final int x;
//        final Var y;
//
//        public LtCV(int x, Var y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(y);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "n<v", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed1(y, (yv, domY) -> {
//                if (domY.hasSolution()) {
//                    return x < domY.solution();
//                } else {
//                    propagator.add(new LtCV(x, yv));
//                    if (domY.isEnumerated()) {
//                        return propagator.add(Enumerated.of(yv, domY.values().filter(y -> x < y)));
//                    } else if (domY.isBounded()) {
//                        final int lbY = domY.getLowerBound();
//                        if (x < lbY) return true;
//                        return propagator.add(RangeDomain.of(yv, Math.max(lbY, x + 1), domY.getUpperBound()));
//                    } else {
//                        return true;
//                    }
//                }
//            });
//        }
//    }
//
//    static class LtVV implements AriConstraint {
//        final Var x, y;
//
//        public LtVV(Var x, Var y) {
//            this.x = x;
//            this.y = y;
//        }
//
//        @Override
//        public Set<Var> vars() {
//            return HashSet.of(x, y);
//        }
//
//        @Override
//        public Cons symbolicRepr() {
//            return Cons.th(Cons.NIL, "v<v", x, y);
//        }
//
//        @Override
//        public boolean propagate(Propagator propagator) {
//            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
//                if (xv.seq == yv.seq) {
//                    return false;
//                } else if (domX.hasSolution()) {
//                    return new LtCV(domX.solution(), yv).propagate(propagator);
//                } else if (domY.hasSolution()) {
//                    return new LtVC(xv, domY.solution()).propagate(propagator);
//                } else if (domX.isBounded() && domY.isBounded()) {
//                    final int lbX = domX.getLowerBound(), ubX = domX.getUpperBound(),
//                            lbY = domY.getLowerBound(), ubY = domY.getUpperBound();
//                    if (ubX < lbY) return true;
//                    propagator.add(new LtVV(xv, yv));
//                    return propagator.add(RangeDomain.of(xv, lbX, Math.min(ubX, ubY - 1)).intersect(domX)) &&
//                            propagator.add(RangeDomain.of(yv, Math.max(lbX + 1, lbY), ubY).intersect(domY));
//                } else {
//                    propagator.add(new LtVV(xv, yv));
//                    return true;
//                }
//            });
//        }
//    }
//
//    public static Goal ltO(Var x, Var y) {
//        return new AriGoal(new LtVV(x, y));
//    }
//
//    public static Goal ltO(int x, Var y) {
//        return new AriGoal(new LtCV(x, y));
//    }
//
//    public static Goal ltO(Var x, int y) {
//        return new AriGoal(new LtVC(x, y));
//    }
//
//    public static Goal ltO(int x, int y) {
//        return x < y ? Goal.success() : Goal.failure();
//    }

    static class TimesCVV implements FdConstraint {
        final int x;
        final Var y;
        final Var z;

        public TimesCVV(int x, Var y, Var z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(y, z);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "n*v=v", x, y, z);
        }

        @Override
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public void maybeAddToAgenda(Solver solver, int varIndex, Domain dom) {
            if (isDefinite(dom)) {
                solver.enqueue(this, 0);
            }
        }

        @Override
        public boolean propagate(Solver solver) {
            final Var y = walkVar(this.y, solver.subst), z = walkVar(this.z, solver.subst);

            solver.unregisterConstraint(this);

            if (x == 0) {
                return solver.reduceDomain(z.index, 0);
            }

            // Fom this point on, x != 0

            if (y.index == z.index) {
                // Case: xy = y, x != 0
                if (x == 1) return true;
                return solver.reduceDomain(y.index, 0);
            }

            final Domain domY = solver.getDomain(y.index),
                    domZ = solver.getDomain(z.index);

            if (isEmpty(domY) || isEmpty(domZ)) return false;

            // From this point on:
            if (hasSolution(domY) && hasSolution(domZ)) {
                return x * solution(domY) == solution(domZ);
            } else if (hasSolution(domY)) {
                return solver.reduceDomain(z.index, x * solution(domY));
            } else if (hasSolution(domZ)) {
                final int zz = solution(domZ);
                return zz % x == 0 && solver.reduceDomain(y.index, zz / x);
            }

            if (isDefinite(domY) && isDefinite(domZ)) {
                final SortedSet<Integer> zs2 = domY.get().map(yy -> x * yy).intersect(domZ.get()),
                        ys2 = zs2.map(zz -> zz / x);
                return solver.reduceDomain(y.index, ys2) && solver.reduceDomain(z.index, zs2) &&
                        solver.registerConstraint(this);
            } else if (isDefinite(domY)) {
                return solver.reduceDomain(z.index, domY.get().map(yy -> x * yy)) &&
                        solver.registerConstraint(this);
            } else if (isDefinite(domZ)) {
                final SortedSet<Integer> zs2 = domZ.get().filter(zz -> zz % x == 0);
                return solver.reduceDomain(z.index, zs2) && solver.reduceDomain(y.index, zs2.map(zz -> zz / x)) &&
                        solver.registerConstraint(this);
            }

            return solver.registerConstraint(this);
        }
    }


    public static Goal timesO(int x, Var y, Var z) {
        if (x == 0) return Goal.unify(z, 0);
        else return new AriGoal(new TimesCVV(x, y, z));
    }

    public static Goal timesO(int x, Var y, int z) {
        if (x == 0) return z == 0 ? Goal.success() : Goal.failure();
        else if (z % x != 0) return Goal.failure();
        else return Goal.unify(y, z / x);
    }

    public static Goal timesO(int x, int y, Var z) {
        return Goal.unify(z, x * y);
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, Var r) {
        return Goal.fresh((m1, m2) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int r) {
        return Goal.fresh((m1, m2) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, Var r) {
        return Goal.fresh((m1, m2, m12, m3) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, int r) {
        return Goal.fresh((m1, m2, m12, m3) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, int c4, Var v4, Var r) {
        return Goal.fresh((m1, m2, m12, m3, m13, m4) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, m13),
                timesO(c4, v4, m4),
                plusO(m13, m4, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, int c4, Var v4, int r) {
        return Goal.fresh((m1, m2, m12, m3, m13, m4) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, m13),
                timesO(c4, v4, m4),
                plusO(m13, m4, r)
        ));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, int c4, Var v4, int c5, Var v5, Var r) {
        return Goal.fresh((m1, m2, m12, m3, m13, m4) -> Goal.fresh((m14, m5) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, m13),
                timesO(c4, v4, m4),
                plusO(m13, m4, m14),
                timesO(c5, v5, m5),
                plusO(m14, m5, r)
        )));
    }

    public static Goal linearO(int c1, Var v1, int c2, Var v2, int c3, Var v3, int c4, Var v4, int c5, Var v5, int r) {
        return Goal.fresh((m1, m2, m12, m3, m13, m4) -> Goal.fresh((m14, m5) -> Goal.seq(
                timesO(c1, v1, m1),
                timesO(c2, v2, m2),
                plusO(m1, m2, m12),
                timesO(c3, v3, m3),
                plusO(m12, m3, m13),
                timesO(c4, v4, m4),
                plusO(m13, m4, m14),
                timesO(c5, v5, m5),
                plusO(m14, m5, r)
        )));
    }

    static class LabelingGoal extends Goal {
        final List<Var> vars;

        public LabelingGoal(List<Var> vars) {
            this.vars = vars;
        }

        @Override
        public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
            return new Solver(subst).label(vars.toSet());
        }
    }


    public static Goal labeling(Var... vars) {
        return new LabelingGoal(List.ofAll(Arrays.stream(vars)));
    }

    public static Goal labeling(List<Var> vars) {
        return new LabelingGoal(vars);
    }
}
