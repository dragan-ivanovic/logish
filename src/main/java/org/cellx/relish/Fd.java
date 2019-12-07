package org.cellx.relish;

import io.vavr.*;
import io.vavr.collection.*;
import io.vavr.control.Option;
import org.cellx.relish.Relish.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;

import static org.cellx.relish.Relish.*;

@SuppressWarnings({"unused", "SuspiciousNameCombination"})
public class Fd {

    static final String DOM_DOMAIN = "fd:";

    static abstract class DomainConstraint implements Constraint, Attribute {
        abstract boolean isEmpty();

        abstract DomainConstraint intersect(DomainConstraint other);

        abstract SortedSet<Integer> values();

        abstract Stream<Integer> valueStream();

        abstract boolean accepts(Integer x);

        abstract boolean isBounded();

        abstract boolean isEnumerated();

        abstract int getLowerBound();

        abstract int getUpperBound();

        abstract Option<Integer> optSolution();

        abstract boolean hasSolution();

        abstract int solution();

        abstract Var variable();

        @Override
        public final boolean delegating() {
            return true;
        }

        @Override
        final public Option<Map<Integer, Object>> validate(Var v, Object o, Map<Integer, Object> subst) {
            if (!(o instanceof Integer && accepts((Integer) o))) return Option.none();
            return new Propagator(subst).input(v, (Integer) o).solution();
        }

        @Override
        final public List<Constraint> constraints() {
            return List.of(this);
        }

        @Override
        final public Option<Tuple2<Option<Attribute>, Map<Integer, Object>>> combine(Var v, Attribute other, Map<Integer, Object> subst) {
            final DomainConstraint combined = intersect((DomainConstraint) other);
            return combined.isEmpty() ? Option.none() : Option.of(Tuple.of(Option.of(combined), subst));
        }
    }

    static class IntegerDomain extends DomainConstraint {
        final Var v;

        private IntegerDomain(Var v) {
            this.v = v;
        }

        static IntegerDomain of(Var v) {
            return new IntegerDomain(v);
        }

        @Override
        public String toString() {
            return "Z";
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
        boolean isBounded() {
            return false;
        }

        @Override
        boolean isEnumerated() {
            return false;
        }

        @Override
        int getLowerBound() {
            return Integer.MIN_VALUE;
        }

        @Override
        int getUpperBound() {
            return Integer.MAX_VALUE;
        }

        @Override
        boolean hasSolution() {
            return false;
        }

        @Override
        int solution() {
            return 0;
        }

        @Override
        DomainConstraint intersect(DomainConstraint other) {
            return other;
        }

        @Override
        SortedSet<Integer> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        Stream<Integer> valueStream() {
            return Stream.unfold(0, x -> Option.of(Tuple.of(x >= 0? -x-1: -x, x)));
        }

        @Override
        boolean accepts(Integer x) {
            return true;
        }

        @Override
        Option<Integer> optSolution() {
            return Option.none();
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "int", v);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IntegerDomain)) return false;
            IntegerDomain that = (IntegerDomain) o;
            return v.equals(that.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v);
        }
    }

    static class EnumeratedDomain extends DomainConstraint {
        final Var v;
        final SortedSet<Integer> domain;

        private EnumeratedDomain(Var v, SortedSet<Integer> domain) {
            this.v = v;
            this.domain = domain;
        }

        public static EnumeratedDomain of(Var v, int... values) {
            return new EnumeratedDomain(v, TreeSet.ofAll(values));
        }

        public static EnumeratedDomain of(Var v, SortedSet<Integer> values) {
            return new EnumeratedDomain(v, values);
        }

        @Override
        public String toString() {
            final StringBuilder builder = new StringBuilder("{");
            boolean isFirst = true;
            for (Integer x : values()) {
                if (!isFirst) builder.append(", ");
                builder.append(x);
                isFirst = false;
            }
            return builder.append("}").toString();
        }

        @Override
        Var variable() {
            return v;
        }

        @Override
        boolean isBounded() {
            return true;
        }

        @Override
        boolean isEnumerated() {
            return true;
        }

        @Override
        int getLowerBound() {
            return domain.min().getOrElse(Integer.MAX_VALUE);
        }

        @Override
        int getUpperBound() {
            return domain.max().getOrElse(Integer.MIN_VALUE);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.fromIterable(domain), "dom", v);
        }

        @Override
        public SortedSet<Integer> values() {
            return domain;
        }

        @Override
        Stream<Integer> valueStream() {
            return domain.toStream();
        }

        @Override
        public boolean isEmpty() {
            return domain.isEmpty();
        }

        @Override
        Option<Integer> optSolution() {
            if (domain.size() == 1) return Option.of(domain.head());
            else return Option.none();
        }

        @Override
        boolean hasSolution() {
            return domain.size() == 1;
        }

        @Override
        int solution() {
            return domain.head();
        }

        @Override
        public boolean accepts(Integer x) {
            return domain.contains(x);
        }

        @Override
        public DomainConstraint intersect(DomainConstraint other) {
            if (other instanceof IntegerDomain) {
                return this;
            } else if (other instanceof EnumeratedDomain) {
                return new EnumeratedDomain(v, domain.intersect(((EnumeratedDomain) other).domain));
            } else {
                final RangeDomain rd = (RangeDomain) other;
                return new EnumeratedDomain(v, domain.filter(x -> x >= rd.lb && x <= rd.ub));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof EnumeratedDomain)) return false;
            EnumeratedDomain that = (EnumeratedDomain) o;
            return v.equals(that.v) &&
                    domain.equals(that.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v, domain);
        }
    }

    static class RangeDomain extends DomainConstraint {
        final Var v;
        final int lb, ub;

        private RangeDomain(Var v, int lb, int ub) {
            this.v = v;
            this.lb = lb;
            this.ub = ub;
        }

        static RangeDomain of(Var v, int lb, int ub) {
            return new RangeDomain(v, lb, ub);
        }

        @Override
        public String toString() {
            return "[" + lb + ", " + ub + "]";
        }

        @Override
        Var variable() {
            return v;
        }

        @Override
        boolean isBounded() {
            return true;
        }

        @Override
        boolean isEnumerated() {
            return false;
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
        public SortedSet<Integer> values() {
            return TreeSet.ofAll(Stream.rangeClosed(lb, ub));
        }

        @Override
        Stream<Integer> valueStream() {
            return Stream.rangeClosed(lb, ub);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "range", v, lb, ub);
        }

        @Override
        public boolean isEmpty() {
            return ub < lb;
        }

        @Override
        Option<Integer> optSolution() {
            if (lb == ub) return Option.of(lb);
            else return Option.none();
        }

        @Override
        boolean hasSolution() {
            return lb == ub;
        }

        @Override
        int solution() {
            return lb;
        }

        @Override
        public boolean accepts(Integer x) {
            return x >= lb && x <= ub;
        }

        @Override
        public DomainConstraint intersect(DomainConstraint other) {
            if (other instanceof IntegerDomain) {
                return this;
            } else if (other instanceof EnumeratedDomain) {
                return new EnumeratedDomain(v, ((EnumeratedDomain) other).domain
                        .filter(x -> x >= lb && x <= ub));
            } else {
                final RangeDomain rd = (RangeDomain) other;
                return new RangeDomain(v, Math.max(lb, rd.lb), Math.min(ub, rd.ub));
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RangeDomain)) return false;
            RangeDomain that = (RangeDomain) o;
            return lb == that.lb &&
                    ub == that.ub &&
                    v.equals(that.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v, lb, ub);
        }
    }

    static class DomGoal extends Goal {
        final Var v0;
        final DomainConstraint domain;

        DomGoal(Var v, DomainConstraint domain) {
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

            final DomainConstraint newConstraint;
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
                newConstraint = (DomainConstraint) combination.get()._1.get();
                subst1 = combination.get()._2;
            }

            if (newConstraint.isEmpty()) return Series.empty();

            return Series.of(new Propagator(subst1).input(newConstraint).solution());
        }
    }

    public static Goal dom(Var v, Seq<Integer> elements) {
        return new DomGoal(v, new EnumeratedDomain(v, TreeSet.ofAll(elements)));
    }

    public static Goal domAll(Seq<Integer> elements, Var... vars) {
        return Goal.seq(List.ofAll(Arrays.stream(vars))
                .map(v -> new DomGoal(v, new EnumeratedDomain(v, TreeSet.ofAll(elements)))));
    }

    public static Goal in(Var v, int... elements) {
        return dom(v, List.ofAll(elements));
    }

    public static Goal range(Var v, int lb, int ub) {
        return new DomGoal(v, new RangeDomain(v, lb, ub));
    }

    // -- Arithmetic constraints --

    public static final String ARI_DOMAIN = "fda:";

    static class AriAttribute implements Attribute {
        final List<AriConstraint> constraints;

        public AriAttribute(List<AriConstraint> constraints) {
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

        @SuppressWarnings("RedundantCast")
        @Override
        public List<Constraint> constraints() {
            return constraints.map(c -> (Constraint) c);
        }

        @Override
        public Option<Tuple2<Option<Attribute>, Map<Integer, Object>>> combine(Var v, Attribute other, Map<Integer, Object> subst) {
            final AriAttribute otherAri = (AriAttribute) other;
            return Option.of(Tuple.of(
                    Option.of(new AriAttribute(constraints.appendAll(otherAri.constraints.filter(c -> !constraints.contains(c))))),
                    subst
            ));
        }
    }

    interface AriConstraint extends Constraint {
        Set<Var> vars();

        boolean propagate(Propagator propagator);
    }

    static class Propagator {
        final Map<Integer, Object> subst0;
        Map<Integer, Object> subst;
        Map<Integer, DomainConstraint> varDomains = TreeMap.empty();
        Map<Integer, List<AriConstraint>> varConstraints = TreeMap.empty();
        Map<Integer, Integer> preUnified = TreeMap.empty();
        Set<Integer> instantiatedVars = TreeSet.empty();
        Set<Integer> excitedVars = TreeSet.empty();
        final java.util.Queue<Constraint> queue = new LinkedList<>();
        final java.util.Queue<AriConstraint> propagationQueue = new LinkedList<>();

        Propagator input(Var v, int x) {
            excitedVars = excitedVars.add(v.seq);
            varDomains = varDomains.put(v.seq, EnumeratedDomain.of(v, x));
            return this;
        }

        Propagator input(DomainConstraint dc) {
            int varSeq = walkVar(dc.variable(), subst).seq;
            excitedVars = excitedVars.add(varSeq);
            varDomains = varDomains.put(varSeq, dc);
            return this;
        }

        void excite(Var v) {
            excitedVars = excitedVars.add(v.seq);
        }

        boolean add(DomainConstraint dc) {
            if (dc.isEmpty()) return false;
            queue.add(dc);
            return true;
        }

        Propagator input(AriConstraint ac) {
            if (!propagationQueue.contains(ac)) propagationQueue.add(ac);
            return this;
        }

        void add(AriConstraint ac) {
            queue.add(ac);
        }

        Propagator(Map<Integer, Object> subst) {
            this.subst0 = subst;
            this.subst = subst;
        }

        boolean preUnify(Var v1, Var v2) {
            if (v1.seq < v2.seq) return preUnify(v2, v1);
            else if (v1.seq == v2.seq) return true;
            final DomainConstraint dom1 = getDomain(v1);
            if (dom1.isEmpty()) return false;
            final DomainConstraint dom2 = getDomain(v2);
            if (dom2.isEmpty()) return false;
            if (dom1.hasSolution() && dom2.hasSolution()) {
                return dom1.solution() == dom2.solution();
            } else if (dom1.hasSolution()) {
                return add(EnumeratedDomain.of(v2, dom1.solution()).intersect(dom2));
            } else if (dom2.hasSolution()) {
                return add(EnumeratedDomain.of(v1, dom2.solution()).intersect(dom1));
            } else {
                final DomainConstraint common = getDomain(v1).intersect(getDomain(v2));
                if (common.isEmpty()) return false;
                preUnified = preUnified.put(v1.seq, v2.seq);
                subst = subst.put(v1.seq, v2);
                varDomains = varDomains.remove(v1.seq).put(v2.seq, common);
                final List<AriConstraint> cs2 = getConstraints(v2.seq);
                final List<AriConstraint> cs1 = getConstraints(v1.seq).filter(c -> !cs2.contains(c));
                varConstraints = varConstraints.remove(v1.seq);
                if (!cs1.isEmpty()) {
                    varConstraints = varConstraints.put(v2.seq, cs2.prependAll(cs1));
                }
                if (!dom2.equals(common) || !cs1.isEmpty()) {
                    excitedVars = excitedVars.remove(v1.seq).add(v2.seq);
                }
                return true;
            }
        }

        DomainConstraint getDomain(Var v) {
            final Option<DomainConstraint> optCurrent = varDomains.get(v.seq);
            if (!optCurrent.isEmpty()) return optCurrent.get();
            final Object value = subst.get(v.seq).get();
            if (value instanceof Var) {
                final Option<Attribute> fromSubst = getAttribute(v, subst, DOM_DOMAIN);
                if (fromSubst.isEmpty()) {
                    final DomainConstraint domain = IntegerDomain.of(v);
                    varDomains = varDomains.put(v.seq, domain);
                    return domain;
                } else {
                    return (DomainConstraint) fromSubst.get();
                }
            } else {
                instantiatedVars = instantiatedVars.add(v.seq);
                final DomainConstraint fromValue;
                if (value instanceof Integer) {
                    fromValue = EnumeratedDomain.of(v, (Integer) value);
                } else {
                    fromValue = EnumeratedDomain.of(v);
                }
                varDomains = varDomains.put(v.seq, fromValue);
                return fromValue;
            }
        }

        List<AriConstraint> getConstraints(int varSeq) {
            final Option<List<AriConstraint>> optConstraints = varConstraints.get(varSeq);
            if (!optConstraints.isEmpty()) return optConstraints.get();
            final List<AriConstraint> result = getAttribute(varSeq, subst, ARI_DOMAIN)
                    .map(a -> ((AriAttribute) a).constraints)
                    .getOrElse(List.empty());
            varConstraints = varConstraints.put(varSeq, result);
            return result;
        }

        boolean getToFixpoint() {
            do {
                for (int varSeq : excitedVars) {
                    final List<AriConstraint> cs = getConstraints(varSeq);
                    varConstraints = varConstraints.put(varSeq, List.empty());
                    for (final AriConstraint c : cs) {
//                        if (queue.contains(c)) continue;
                        final Set<Integer> otherVarSeqs = c.vars().map(v -> walkVar(v, subst).seq)
                                .filter(s -> s != varSeq);
                        for (final int otherVarSeq : otherVarSeqs) {
                            varConstraints = varConstraints.put(otherVarSeq,
                                    getConstraints(otherVarSeq).remove(c));
                        }
                    }
                    for (final AriConstraint c : cs) {
                        propagationQueue.add(c);
                    }
                }
                excitedVars = TreeSet.empty();

                while (!propagationQueue.isEmpty()) {
                    if (!propagationQueue.remove().propagate(this)) return false;
                }

                while (!queue.isEmpty()) {
                    final Constraint c = queue.remove();
                    if (c instanceof DomainConstraint) {
                        final DomainConstraint dc = (DomainConstraint) c;
                        if (dc.isEmpty()) return false;
                        final Var v = walkVar(dc.variable(), subst);
                        final DomainConstraint dc0 = getDomain(v);
                        if (!dc0.equals(dc)) {
                            excitedVars = excitedVars.add(v.seq);
                            varDomains = varDomains.put(v.seq, dc);
                        }
                    } else {
                        final AriConstraint ac = (AriConstraint) c;
                        for (int varSeq : ac.vars().map(v -> walkVar(v, subst).seq)) {
                            varConstraints = varConstraints.put(varSeq, getConstraints(varSeq).prepend(ac));
                        }
                    }
                }
            } while (!excitedVars.isEmpty());
            return true;
        }


        Option<Map<Integer, Object>> solution() {

            if (!getToFixpoint()) return Option.none();

            Map<Integer, Object> result = subst0;

            final Map<Integer, DomainConstraint> solved =
                    varDomains.filter(t -> t._2.hasSolution());

            // Remove domain and ari constraint from the solved variables
            for (final int varSeq : solved.keysIterator()) {
                result = removeAttribute(varSeq, removeAttribute(varSeq, result, ARI_DOMAIN), DOM_DOMAIN);
            }
            // ... and for all pre-unified variables
            for (final int varSeq : preUnified.keysIterator()) {
                result = removeAttribute(varSeq, removeAttribute(varSeq, result, ARI_DOMAIN), DOM_DOMAIN);
            }

            // Set domains for unsolved variables
            for (final int varSeq : varDomains.keysIterator()) {
                if (solved.containsKey(varSeq)) continue;
                result = setAttribute(varSeq, result, DOM_DOMAIN, varDomains.get(varSeq).get());
            }

            // Set arithmetic constraints for unsolved variables
            for (final int varSeq : varConstraints.keysIterator()) {
                if (solved.containsKey(varSeq)) continue;
                final List<AriConstraint> cs = varConstraints.get(varSeq).get();
                if (cs.isEmpty()) {
                    result = removeAttribute(varSeq, result, ARI_DOMAIN);
                } else {
                    result = setAttribute(varSeq, result, ARI_DOMAIN, new AriAttribute(cs));
                }
            }

            // Instantiate solved variables by unifying them recursively
            for (final Tuple2<Integer, DomainConstraint> solution : solved) {
                if (instantiatedVars.contains(solution._1)) continue; // variable instantiated earlier
                final Option<Map<Integer, Object>> step = unify(new Var(solution._1), solution._2.solution(), result);
                if (step.isEmpty()) return Option.none();
                result = step.get();
            }
            // ... and also the pre-unified variables
            for (final Tuple2<Integer, Integer> pair : preUnified) {
                final Option<Map<Integer, Object>> step = unify(new Var(pair._1), new Var(pair._2), result);
                if (step.isEmpty()) return Option.none();
                result = step.get();
            }

            return Option.of(result);
        }

        // Helpers

        Option<Tuple2<Var, DomainConstraint>> getVarDomain(Var v) {
            final Var w = walkVar(v, subst);
            final DomainConstraint dc = getDomain(w);
            return dc.isEmpty() ? Option.none() : Option.of(Tuple.of(w, dc));
        }

        boolean feed1(Var u, Function2<Var, DomainConstraint, Boolean> body) {
            return getVarDomain(u).map(tu -> body.apply(tu._1, tu._2)).getOrElse(false);
        }

        boolean feed2(Var u, Var v,
                      Function4<Var, DomainConstraint,
                              Var, DomainConstraint,
                              Boolean> body) {
            return getVarDomain(u).flatMap(tu ->
                    getVarDomain(v).map(tv ->
                            body.apply(tu._1, tu._2, tv._1, tv._2)))
                    .getOrElse(false);
        }

        boolean feed3(Var u, Var v, Var w,
                      Function6<Var, DomainConstraint,
                              Var, DomainConstraint,
                              Var, DomainConstraint,
                              Boolean> body) {
            return getVarDomain(u).flatMap(tu ->
                    getVarDomain(v).flatMap(tv ->
                            getVarDomain(w).map(tw ->
                                    body.apply(tu._1, tu._2, tv._1, tv._2, tw._1, tw._2))))
                    .getOrElse(false);
        }
    }

    /**
     * A constraint of the form: Variable + Constant = Variable
     */
    static class PlusVCV implements AriConstraint {
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
        public Set<Var> vars() {
            return HashSet.of(x, z);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, z, (xv, domX, zv, domZ) -> {
                if (xv.seq == zv.seq) {
                    // Case: X + y = X. Satisfied for any x:dom(X) when y=0
                    return y == 0;
                } else if (domX.hasSolution()) {
                    // z := x + y
                    return propagator.add(
                            EnumeratedDomain.of(zv, domX.solution() + y).intersect(domZ));
                } else if (domZ.hasSolution()) {
                    // x = z - y
                    return propagator.add(
                            EnumeratedDomain.of(xv, domZ.solution() - y).intersect(domX));
                } else if (y == 0) {
                    return propagator.preUnify(xv, zv);
                } else if (domX.isBounded()) {
                    if (domX.isEnumerated()) {
                        // dom(Z)' = { x + y | x:dom(X) } \cap dom(Z)
                        propagator.add(new PlusVCV(xv, y, zv)); // keep the equation
                        return propagator.add(
                                EnumeratedDomain.of(zv, domX.values().map(x -> x + y))
                                        .intersect(domZ));
                    } else {
                        // dom(Z)' = [min(X) + y, max(X) + y] \cap dom(Z)
                        propagator.add(new PlusVCV(xv, y, zv)); // keep the equation
                        return propagator.add(
                                RangeDomain.of(zv, domX.getLowerBound() + y, domX.getUpperBound() + y)
                                        .intersect(domZ));
                    }
                } else if (domZ.isBounded()) {
                    if (domZ.isEnumerated()) {
                        // dom(X)' = { z - y | z: dom(Z) } \cap dom(X)
                        propagator.add(new PlusVCV(xv, y, zv)); // keep the equation
                        return propagator.add(
                                EnumeratedDomain.of(xv, domZ.values().map(z -> z - y))
                                        .intersect(domX));
                    } else {
                        propagator.add(new PlusVCV(xv, y, zv)); // keep the equation
                        // dom(X)' = [min(Z) - y, max(Z) - y] \cap dom(X)
                        return propagator.add(
                                RangeDomain.of(xv, domZ.getLowerBound() - y, domZ.getUpperBound() - y)
                                        .intersect(domX));
                    }
                } else { // nothing can be inferred; reassert the constraint
                    propagator.add(new PlusVCV(xv, y, zv));
                    return true;
                }
            });
        }
    }

    static class PlusVVC implements AriConstraint {
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
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
                if (xv.seq == yv.seq) {
                    // X + X = z; single solution when z is even: x = z/2
                    if ((z & 1) != 0) return false;
                    return propagator.add(EnumeratedDomain.of(xv, z / 2).intersect(domX));
                } else if (domX.hasSolution()) {
                    // y = z - x
                    return propagator.add(EnumeratedDomain.of(yv, z - domX.solution())
                            .intersect(domY));
                } else if (domY.hasSolution()) {
                    // x = z - y
                    return propagator.add(EnumeratedDomain.of(xv, z - domY.solution())
                            .intersect(domX));
                } else if (domX.isBounded()) {
                    if (domX.isEnumerated()) {
                        // dom(Y)' = { z - x | x:dom(X) } \cap dom(Y)
                        propagator.add(new PlusVVC(xv, yv, z)); // keep the equation
                        return propagator.add(
                                EnumeratedDomain.of(yv, domX.values().map(x -> z - x))
                                        .intersect(domY));
                    } else {
                        // dom(Y)' = [z - max(X), z - min(X)] \cap dom(Y)
                        propagator.add(new PlusVVC(xv, yv, z)); // keep the equation
                        return propagator.add(
                                RangeDomain.of(yv, z - domX.getUpperBound(), z - domX.getLowerBound())
                                        .intersect(domY));
                    }
                } else { // both variables unbounded, propagate a copy of self
                    propagator.add(new PlusVVC(xv, yv, z));
                    return true;
                }
            });
        }
    }

    static class PlusVVV implements AriConstraint {
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
        public Set<Var> vars() {
            return HashSet.of(x, y, z);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed3(x, y, z, (xv, domX, yv, domY, zv, domZ) -> {
                // First, try to reduce to VCV or VVC cases
                if (domX.hasSolution()) {
                    return new PlusVCV(yv, domX.solution(), zv).propagate(propagator);
                } else if (domY.hasSolution()) {
                    return new PlusVCV(xv, domY.solution(), zv).propagate(propagator);
                } else if (domZ.hasSolution()) {
                    return new PlusVVC(xv, yv, domZ.solution()).propagate(propagator);
                }

                // Now, check different variable configurations
                if (xv.seq == yv.seq && xv.seq == zv.seq) {
                    // Case: 2X = X; the ony solution is x = 0
                    return propagator.add(EnumeratedDomain.of(xv, 0).intersect(domX));
                } else if (xv.seq == yv.seq) {
                    // Case: 2X = Z
                    propagator.add(new PlusVVV(xv, xv, zv)); // keep the constraint
                    if (domX.isEnumerated()) {
                        SortedSet<Integer> cX = domX.values().filter(x -> domZ.accepts(x + x));
                        return propagator.add(EnumeratedDomain.of(xv, cX)) &&
                                propagator.add(EnumeratedDomain.of(zv, cX.map(x -> x + x))
                                        .intersect(domZ));

                    } else if (domZ.isEnumerated()) {
                        SortedSet<Integer> cZ = domZ.values().filter(z -> (z & 1) == 0 && domX.accepts(z / 2));
                        return propagator.add(EnumeratedDomain.of(zv, cZ)) &&
                                propagator.add(EnumeratedDomain.of(xv, cZ.map(z -> z / 2))
                                        .intersect(domX));
                    } else if (domX.isBounded() && domZ.isBounded()) {
                        final int lbX = domX.getLowerBound(), ubX = domY.getUpperBound(),
                                rX = ubX - lbX + 1,
                                lbZ0 = domZ.getLowerBound(), ubZ0 = domZ.getUpperBound(),
                                lbZ = ((lbZ0 & 1) != 0 ? lbZ0 + 1 : lbZ0),
                                ubZ = ((ubZ0 & 1) != 0 ? ubZ0 - 1 : ubZ0),
                                rZ = ubZ - lbZ + 1;
                        if (2 * rX <= rZ) { // X is more constrained
                            return propagator.add(RangeDomain.of(zv, 2 * lbX, 2 * ubX).intersect(domZ));
                        } else {
                            return propagator.add(RangeDomain.of(zv, lbZ, ubZ).intersect(domZ)) &&
                                    propagator.add(RangeDomain.of(xv, lbZ / 2, ubZ / 2).intersect(domX));
                        }
                    } else if (domX.isBounded()) {
                        return propagator.add(RangeDomain.of(zv, 2 * domX.getLowerBound(), 2 * domX.getUpperBound())
                                .intersect(domZ));
                    } else if (domZ.isBounded()) {
                        final int lbZ0 = domZ.getLowerBound(), ubZ0 = domZ.getUpperBound(),
                                lbZ = ((lbZ0 & 1) != 0 ? lbZ0 + 1 : lbZ0),
                                ubZ = ((ubZ0 & 1) != 0 ? ubZ0 - 1 : ubZ0);
                        return propagator.add(RangeDomain.of(zv, lbZ, ubZ).intersect(domZ)) &&
                                propagator.add(RangeDomain.of(xv, lbZ / 2, ubZ / 2).intersect(domX));
                    } else {
                        return true;
                    }
                } else if (xv.seq == zv.seq) {
                    // Case: X + Y = X
                    return propagator.add(EnumeratedDomain.of(yv, 0).intersect(domY));
                } else if (yv.seq == zv.seq) {
                    // Case: X + Y = Y
                    return propagator.add(EnumeratedDomain.of(xv, 0).intersect(domX));
                } else {
                    // X, Y, and Z are three distinct variables
                    propagator.add(new PlusVVV(xv, yv, zv));
                    if (domX.isEnumerated() && domY.isEnumerated()) {
                        final SortedSet<Tuple2<Integer, Integer>> cXY =
                                TreeSet.ofAll(domX.values().toList().crossProduct(domY.values())
                                        .filter(t -> domZ.accepts(t._1 + t._2)));
                        return propagator.add(EnumeratedDomain.of(zv, cXY.map(t -> t._1 + t._2))) &&
                                propagator.add(EnumeratedDomain.of(xv, cXY.map(Tuple2::_1))) &&
                                propagator.add(EnumeratedDomain.of(yv, cXY.map(Tuple2::_2)));
                    } else if (domX.isEnumerated() && domZ.isEnumerated()) {
                        final SortedSet<Tuple2<Integer, Integer>> cXZ =
                                TreeSet.ofAll(domX.values().toList().crossProduct(domZ.values())
                                        .filter(t -> domY.accepts(t._2 - t._1)));
                        return propagator.add(EnumeratedDomain.of(yv, cXZ.map(t -> t._2 - t._1))) &&
                                propagator.add(EnumeratedDomain.of(xv, cXZ.map(Tuple2::_1))) &&
                                propagator.add(EnumeratedDomain.of(zv, cXZ.map(Tuple2::_2)));
                    } else if (domY.isEnumerated() && domZ.isEnumerated()) {
                        final SortedSet<Tuple2<Integer, Integer>> cYZ =
                                TreeSet.ofAll(domY.values().toList().crossProduct(domZ.values())
                                        .filter(t -> domY.accepts(t._2 - t._1)));
                        return propagator.add(EnumeratedDomain.of(xv, cYZ.map(t -> t._2 - t._1))) &&
                                propagator.add(EnumeratedDomain.of(yv, cYZ.map(Tuple2::_1))) &&
                                propagator.add(EnumeratedDomain.of(zv, cYZ.map(Tuple2::_2)));
                    } else if (domX.isBounded() && domY.isBounded() && domZ.isBounded()) {
                        final int lbX = domX.getLowerBound(), ubX = domX.getUpperBound(), rX = ubX - lbX + 1,
                                lbY = domY.getLowerBound(), ubY = domY.getUpperBound(), rY = ubY - lbY + 1,
                                lbZ = domZ.getLowerBound(), ubZ = domZ.getUpperBound(), rZ = ubZ - lbZ + 1;
                        if (rX <= rZ && rY <= rZ) { // (X, Y, Z) or (Y, X, Z): use X and Y to constrain Z
                            return propagator.add(RangeDomain.of(zv, lbX + lbY, ubX + ubY).intersect(domZ));
                        } else if (rX <= rY) { // (Z, X, Y) or (X, Z, Y): use X and Z to constrain Y
                            return propagator.add(RangeDomain.of(yv, lbZ - ubX, ubZ - lbX).intersect(domY));
                        } else { // (Z, Y, X) or (Y, Z, X): use Z and Y to constrain X
                            return propagator.add(RangeDomain.of(xv, lbZ - ubY, ubZ - lbY).intersect(domX));
                        }
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    static class AriGoal extends Goal {
        final AriConstraint constraint;

        AriGoal(AriConstraint constraint) {
            this.constraint = constraint;
        }

        @Override
        public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
            return Series.of(new Propagator(subst).input(constraint).solution());
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

    static class NeqVC implements AriConstraint {
        final Var x;
        final int y;

        public NeqVC(Var x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v!=n", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(x, (xv, domX) -> {
                if (domX.hasSolution()) {
                    return domX.solution() != y;
                } else if (domX.isEnumerated()) {
                    return propagator.add(EnumeratedDomain.of(xv, domX.values().remove(y)));
                } else if (domX.isBounded()) {
                    final int lbX0 = domX.getLowerBound(), ubX0 = domX.getUpperBound(),
                            lbX = lbX0 == y ? lbX0 + 1 : lbX0, ubX = ubX0 == y ? ubX0 - 1 : ubX0;
                    propagator.add(new NeqVC(xv, y));
                    return propagator.add(RangeDomain.of(xv, lbX, ubX));

                } else {
                    propagator.add(new NeqVC(xv, y));
                    return true;
                }
            });
        }
    }

    static class NeqVV implements AriConstraint {
        final Var x, y;

        public NeqVV(Var x, Var y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v!=v", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
                if (xv.seq == yv.seq) {
                    return false;
                } else if (domX.hasSolution()) {
                    return new NeqVC(yv, domX.solution()).propagate(propagator);
                } else if (domY.hasSolution()) {
                    return new NeqVC(xv, domY.solution()).propagate(propagator);
                } else if (domX.isEnumerated() && domY.isEnumerated() &&
                        domX.values().intersect(domY.values()).isEmpty()) {
                    return true;
                } else if (domX.isBounded() && domY.isBounded() &&
                        (domX.getUpperBound() < domY.getLowerBound() || domY.getUpperBound() < domX.getLowerBound())) {
                    return true;
                } else {
                    propagator.add(new NeqVV(xv, yv));
                    return true;
                }
            });
        }
    }

    public static Goal neqO(Var x, Var y) {
        return new AriGoal(new NeqVV(x, y));
    }

    public static Goal neqO(Var x, int y) {
        return new AriGoal(new NeqVC(x, y));
    }

    public static Goal neqO(int x, Var y) {
        return new AriGoal(new NeqVC(y, x));
    }

    public static Goal neqO(int x, int y) {
        return x != y ? Goal.success() : Goal.failure();
    }

    public static Goal allDifferentO(Var... vs) {
        return Goal.seq(List.ofAll(Arrays.stream(vs)).crossProduct().filter(t -> t._1.seq < t._2.seq)
                .map(t -> neqO(t._1, t._2)).toList());
    }

    static class LeqVC implements AriConstraint {
        final Var x;
        final int y;

        public LeqVC(Var x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v<=n", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(x, (xv, domX) -> {
                if (domX.hasSolution()) {
                    return domX.solution() <= y;
                } else {
                    propagator.add(new LeqVC(xv, y));
                    if (domX.isEnumerated()) {
                        return propagator.add(EnumeratedDomain.of(xv, domX.values().filter(x -> x <= y)));
                    } else if (domX.isBounded()) {
                        final int ubX = domX.getUpperBound();
                        if (ubX <= y) return true;
                        return propagator.add(RangeDomain.of(xv, domX.getLowerBound(), Math.min(ubX, y)));
                    } else {
                        return true;
                    }
                }
            });
        }
    }


    static class LeqCV implements AriConstraint {
        final int x;
        final Var y;

        public LeqCV(int x, Var y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(y);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "n<=v", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(y, (yv, domY) -> {
                if (domY.hasSolution()) {
                    return x <= domY.solution();
                } else {
                    propagator.add(new LeqCV(x, yv));
                    if (domY.isEnumerated()) {
                        return propagator.add(EnumeratedDomain.of(yv, domY.values().filter(y -> x <= y)));
                    } else if (domY.isBounded()) {
                        final int lbY = domY.getLowerBound();
                        if (x <= lbY) return true;
                        return propagator.add(RangeDomain.of(yv, Math.max(lbY, x), domY.getUpperBound()));
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    static class LeqVV implements AriConstraint {
        final Var x, y;

        public LeqVV(Var x, Var y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v<=v", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
                if (xv.seq == yv.seq) {
                    return true;
                } else if (domX.hasSolution()) {
                    return new LeqCV(domX.solution(), yv).propagate(propagator);
                } else if (domY.hasSolution()) {
                    return new LeqVC(xv, domY.solution()).propagate(propagator);
                } else if (domX.isBounded() && domY.isBounded()) {
                    final int lbX = domX.getLowerBound(), ubX = domX.getUpperBound(),
                            lbY = domY.getLowerBound(), ubY = domY.getUpperBound();
                    if (ubX <= lbY) return true;
                    propagator.add(new LeqVV(xv, yv));
                    return propagator.add(RangeDomain.of(xv, lbX, Math.min(ubX, ubY)).intersect(domX)) &&
                            propagator.add(RangeDomain.of(yv, Math.max(lbX, lbY), ubY).intersect(domY));
                } else {
                    propagator.add(new LeqVV(xv, yv));
                    return true;
                }
            });
        }
    }

    public static Goal leqO(Var x, Var y) {
        return new AriGoal(new LeqVV(x, y));
    }

    public static Goal leqO(int x, Var y) {
        return new AriGoal(new LeqCV(x, y));
    }

    public static Goal leqO(Var x, int y) {
        return new AriGoal(new LeqVC(x, y));
    }

    public static Goal leqO(int x, int y) {
        return x <= y ? Goal.success() : Goal.failure();
    }


    static class LtVC implements AriConstraint {
        final Var x;
        final int y;

        public LtVC(Var x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v<n", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(x, (xv, domX) -> {
                if (domX.hasSolution()) {
                    return domX.solution() < y;
                } else {
                    propagator.add(new LtVC(xv, y));
                    if (domX.isEnumerated()) {
                        return propagator.add(EnumeratedDomain.of(xv, domX.values().filter(x -> x < y)));
                    } else if (domX.isBounded()) {
                        final int ubX = domX.getUpperBound();
                        if (ubX < y) return true;
                        return propagator.add(RangeDomain.of(xv, domX.getLowerBound(), Math.min(ubX, y - 1)));
                    } else {
                        return true;
                    }
                }
            });
        }
    }


    static class LtCV implements AriConstraint {
        final int x;
        final Var y;

        public LtCV(int x, Var y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(y);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "n<v", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(y, (yv, domY) -> {
                if (domY.hasSolution()) {
                    return x < domY.solution();
                } else {
                    propagator.add(new LtCV(x, yv));
                    if (domY.isEnumerated()) {
                        return propagator.add(EnumeratedDomain.of(yv, domY.values().filter(y -> x < y)));
                    } else if (domY.isBounded()) {
                        final int lbY = domY.getLowerBound();
                        if (x < lbY) return true;
                        return propagator.add(RangeDomain.of(yv, Math.max(lbY, x + 1), domY.getUpperBound()));
                    } else {
                        return true;
                    }
                }
            });
        }
    }

    static class LtVV implements AriConstraint {
        final Var x, y;

        public LtVV(Var x, Var y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        @Override
        public Cons symbolicRepr() {
            return Cons.th(Cons.NIL, "v<v", x, y);
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
                if (xv.seq == yv.seq) {
                    return false;
                } else if (domX.hasSolution()) {
                    return new LtCV(domX.solution(), yv).propagate(propagator);
                } else if (domY.hasSolution()) {
                    return new LtVC(xv, domY.solution()).propagate(propagator);
                } else if (domX.isBounded() && domY.isBounded()) {
                    final int lbX = domX.getLowerBound(), ubX = domX.getUpperBound(),
                            lbY = domY.getLowerBound(), ubY = domY.getUpperBound();
                    if (ubX < lbY) return true;
                    propagator.add(new LtVV(xv, yv));
                    return propagator.add(RangeDomain.of(xv, lbX, Math.min(ubX, ubY - 1)).intersect(domX)) &&
                            propagator.add(RangeDomain.of(yv, Math.max(lbX + 1, lbY), ubY).intersect(domY));
                } else {
                    propagator.add(new LtVV(xv, yv));
                    return true;
                }
            });
        }
    }

    public static Goal ltO(Var x, Var y) {
        return new AriGoal(new LtVV(x, y));
    }

    public static Goal ltO(int x, Var y) {
        return new AriGoal(new LtCV(x, y));
    }

    public static Goal ltO(Var x, int y) {
        return new AriGoal(new LtVC(x, y));
    }

    public static Goal ltO(int x, int y) {
        return x < y ? Goal.success() : Goal.failure();
    }

    static class TimesCVV implements AriConstraint {
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
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(y, z, (yv, domY, zv, domZ) -> {
                if (yv.seq == zv.seq) {
                    // Case: x*Y=Y
                    if (x == 1) {
                        // Y = Y => always satisfied
                        return true;
                    } else {
                        // x*Y=Y for x!=1 => Y=0
                        return propagator.add(EnumeratedDomain.of(yv, 0).intersect(domY));
                    }
                } else if (domY.hasSolution()) {
                    return propagator.add(EnumeratedDomain.of(zv, x * domY.solution()).intersect(domZ));
                } else if (domZ.hasSolution()) {
                    if (x == 0) {
                        return domZ.solution() == 0;
                    } else {
                        final int z0 = domZ.solution();
                        return z0 % x != 0 &&
                                propagator.add(EnumeratedDomain.of(yv, z0 / x).intersect(domY));
                    }
                } else if (x == 0) {
                    return propagator.add(EnumeratedDomain.of(zv, 0).intersect(domZ));
                } else if (x == 1) {
                    return propagator.preUnify(yv, zv);
                } else {
                    // At this point, we have to keep the constraint
                    propagator.add(new TimesCVV(x, yv, zv));
                    if (domZ.isEnumerated()) {
                        final SortedSet<Integer> cZ = domZ.values().filter(z -> z % x == 0);
                        return propagator.add(EnumeratedDomain.of(yv, cZ.map(z -> z / x)).intersect(domY)) &&
                                propagator.add(EnumeratedDomain.of(zv, cZ));
                    } else if (domY.isEnumerated()) {
                        final SortedSet<Integer> cY = domY.values().filter(y -> domZ.accepts(x * y));
                        return propagator.add(EnumeratedDomain.of(zv, cY.map(y -> x * y)).intersect(domZ)) &&
                                propagator.add(EnumeratedDomain.of(yv, cY));
                    } else if (domY.isBounded() && domZ.isBounded()) {
                        final int lbY = domY.getLowerBound(), ubY = domY.getUpperBound(),
                                lbZ = domZ.getLowerBound(), ubZ = domZ.getUpperBound();
                        if (x > 0) {
                            return propagator.add(RangeDomain.of(yv, lbZ / x, ubZ / x).intersect(domY)) &&
                                    propagator.add(RangeDomain.of(zv, x * lbY, x * ubY).intersect(domZ));
                        } else {
                            return propagator.add(RangeDomain.of(yv, ubZ / x, lbZ / x).intersect(domY)) &&
                                    propagator.add(RangeDomain.of(zv, x * ubY, x * lbY).intersect(domZ));
                        }
                    } else if (domY.isBounded()) {
                        final int lbY = domY.getLowerBound(), ubY = domY.getUpperBound();
                        if (x > 0) {
                            return propagator.add(RangeDomain.of(zv, x * lbY, x * ubY).intersect(domZ));
                        } else {
                            return propagator.add(RangeDomain.of(zv, x * ubY, x * lbY).intersect(domZ));
                        }
                    } else if (domZ.isBounded()) {
                        final int lbZ = domZ.getLowerBound(), ubZ = domZ.getUpperBound();
                        if (x > 0) {
                            return propagator.add(RangeDomain.of(yv, lbZ / x, ubZ / x).intersect(domY)) &&
                                    propagator.add(RangeDomain.of(zv, lbZ, ubZ).intersect(domZ));
                        } else {
                            return propagator.add(RangeDomain.of(yv, ubZ / x, lbZ / x).intersect(domY)) &&
                                    propagator.add(RangeDomain.of(zv, lbZ, ubZ).intersect(domZ));
                        }
                    } else {
                        return true;
                    }
                }
            });
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
            final List<Tuple3<Var, DomainConstraint, Integer>> effectiveVars =
                    vars.map(v -> walk(v, subst))
                            .filter(o -> o instanceof Var)
                            .map(o -> (Var) o).toSet().toList()
                            // Enrich variables with domain
                            .map(v -> Tuple.of(v,
                                    getAttribute(v, subst, DOM_DOMAIN).map(d -> (DomainConstraint) d)))
                            // Eliminate variables without a domain constraint
                            .filter(t -> !t._2.isEmpty())
                            // Extract the domain constraint, and add the number of arithmetic constraints
                            .map(t -> Tuple.of(t._1, t._2.get(),
                                    getAttribute(t._1, subst, ARI_DOMAIN)
                                            .map(o -> ((AriAttribute) o).constraints().length())
                                            .getOrElse(0)));

            final List<Tuple2<Var, DomainConstraint>> boundedVars =
                    effectiveVars.filter(t -> t._2.isBounded())
                            .map(t -> Tuple.of(t._1, t._2,
                                    (t._2.isEnumerated() ? t._2.values().length()
                                            : t._2.getUpperBound() - t._2.getLowerBound() + 1),
                                    t._3))
                            .sorted((t1, t2) -> {
                                if (!t1._3.equals(t2._3)) return t1._3 - t2._3;
                                else return t2._4 - t1._4;
                            })
                            .map(t -> Tuple.of(t._1, t._2));

            final List<Tuple2<Var, DomainConstraint>> unboundedVars =
                    effectiveVars.filter(t -> !t._2.isBounded())
                            .sorted((t1, t2) -> t2._3 - t1._3)
                            .map(t -> Tuple.of(t._1, t._2));

            final List<Tuple2<Var, DomainConstraint>> allVars =
                    boundedVars.appendAll(unboundedVars);

            if (allVars.isEmpty()) return Series.singleton(subst);

            return Goal.seq(allVars.map(t -> new Candidate(t._1, t._2.valueStream())))
                    .apply(subst);
        }
    }

    static class Candidate extends Goal {
        final Var x;
        final Seq<?> sequence;

        Candidate(Var x, Seq<?> sequence) {
            this.x = x;
            this.sequence = sequence;
        }

        @Override
        public Series<Map<Integer, Object>> apply(Map<Integer, Object> subst) {
            final Object v = walk(x, subst);
            if (!(v instanceof Var)) return Series.singleton(subst);
            if (sequence.isEmpty()) return Series.empty();
            return choice(unify(v, sequence.head()),
                    delayed(() -> new Candidate((Var) v, sequence.tail()))).apply(subst);
        }
    }

    public static Goal labeling(Var... vars) {
        return new LabelingGoal(List.ofAll(Arrays.stream(vars)));
    }

    public static Goal labeling(List<Var> vars) {
        return new LabelingGoal(vars);
    }
}
