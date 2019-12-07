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

    static abstract class Domain implements Constraint, Attribute {
        abstract boolean isEmpty();

        abstract Domain intersect(Domain other);

        abstract SortedSet<Integer> values();

        abstract Stream<Integer> valueStream();

        abstract boolean accepts(Integer x);

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
            final Domain combined = intersect((Domain) other);
            return combined.isEmpty() ? Option.none() : Option.of(Tuple.of(Option.of(combined), subst));
        }
    }

    static class SomeInteger extends Domain {
        final Var v;

        private SomeInteger(Var v) {
            this.v = v;
        }

        static SomeInteger of(Var v) {
            return new SomeInteger(v);
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
        Domain intersect(Domain other) {
            return other;
        }

        @Override
        SortedSet<Integer> values() {
            throw new UnsupportedOperationException();
        }

        @Override
        Stream<Integer> valueStream() {
            return Stream.unfold(0, x -> Option.of(Tuple.of(x >= 0 ? -x - 1 : -x, x)));
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
            if (!(o instanceof SomeInteger)) return false;
            SomeInteger that = (SomeInteger) o;
            return v.equals(that.v);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v);
        }
    }

    static class Enumerated extends Domain {
        final Var v;
        final SortedSet<Integer> domain;

        private Enumerated(Var v, SortedSet<Integer> domain) {
            this.v = v;
            this.domain = domain;
        }

        public static Enumerated of(Var v, int... values) {
            return new Enumerated(v, TreeSet.ofAll(values));
        }

        public static Enumerated of(Var v, SortedSet<Integer> values) {
            return new Enumerated(v, values);
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
        public Domain intersect(Domain other) {
            if (!other.isEnumerated()) return this;
            else return new Enumerated(v, domain.intersect(((Enumerated) other).domain));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Enumerated)) return false;
            Enumerated that = (Enumerated) o;
            return v.equals(that.v) &&
                    domain.equals(that.domain);
        }

        @Override
        public int hashCode() {
            return Objects.hash(v, domain);
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

            return Series.of(new Propagator(subst1).input(newConstraint).solution());
        }
    }

    public static Goal dom(Var v, Seq<Integer> elements) {
        return new DomGoal(v, new Enumerated(v, TreeSet.ofAll(elements)));
    }

    public static Goal domAll(Seq<Integer> elements, Var... vars) {
        return Goal.seq(List.ofAll(Arrays.stream(vars))
                .map(v -> new DomGoal(v, new Enumerated(v, TreeSet.ofAll(elements)))));
    }

    public static Goal in(Var v, int... elements) {
        return dom(v, List.ofAll(elements));
    }

    public static Goal range(Var v, int lb, int ub) {
        return new DomGoal(v, new Enumerated(v, TreeSet.rangeClosed(lb, ub)));
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
        final Map<Integer, Object> subst;
        Map<Integer, Domain> varDomains = TreeMap.empty();
        Map<Integer, List<AriConstraint>> varConstraints = TreeMap.empty();
        Set<Integer> instantiatedVars = TreeSet.empty();
        Set<Integer> excitedVars = TreeSet.empty();
        final java.util.Queue<Domain> domainQueue = new LinkedList<>();
        final java.util.Queue<AriConstraint> constraintQueue = new LinkedList<>();
        final java.util.Queue<AriConstraint> propagationQueue = new LinkedList<>();

        Propagator input(Var v, int x) {
            excitedVars = excitedVars.add(v.seq);
            varDomains = varDomains.put(v.seq, Enumerated.of(v, x));
            return this;
        }

        Propagator input(Domain dc) {
            int varSeq = walkVar(dc.variable(), subst).seq;
            varDomains = varDomains.put(varSeq, dc);
            return this;
        }

        void excite(Var v) {
            excitedVars = excitedVars.add(v.seq);
        }

        boolean add(Domain d) {
            if (d.isEmpty()) return false;
            domainQueue.add(d);
            return true;
        }

        Propagator input(AriConstraint ac) {
            if (!propagationQueue.contains(ac)) propagationQueue.add(ac);
            return this;
        }

        void add(AriConstraint ac) {
            constraintQueue.add(ac);
        }

        Propagator(Map<Integer, Object> subst) {
            this.subst = subst;
        }


        Domain getDomain(Var v0) {
            final Var v = walkVar(v0, subst);
            final Option<Domain> optCurrent = varDomains.get(v.seq);
            if (!optCurrent.isEmpty()) return optCurrent.get();
            final Object value = subst.get(v.seq).get();
            if (value instanceof Var) {
                final Option<Attribute> fromSubst = getAttribute(v, subst, DOM_DOMAIN);
                if (fromSubst.isEmpty()) {
                    final Domain domain = SomeInteger.of(v);
                    varDomains = varDomains.put(v.seq, domain);
                    return domain;
                } else {
                    return (Domain) fromSubst.get();
                }
            } else {
                instantiatedVars = instantiatedVars.add(v.seq);
                final Domain fromValue;
                if (value instanceof Integer) {
                    fromValue = Enumerated.of(v, (Integer) value);
                } else {
                    fromValue = Enumerated.of(v);
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

        boolean isExcited(int varSeq) {
            return excitedVars.contains(varSeq);
        }

        boolean isExcited(Var v) {
            return excitedVars.contains(walkVar(v, subst).seq);
        }

        static boolean equalValues(Domain d1, Domain d2) {
            if (d1.isEnumerated()) {
                return d2.isEnumerated() && d1.values().equals(d2.values());
            } else return !d2.isEnumerated();
        }

        boolean getToFixpoint() {
            do {
                for (int varSeq : excitedVars) {
                    final List<AriConstraint> cs = getConstraints(varSeq);
                    varConstraints = varConstraints.put(varSeq, List.empty());
                    for (final AriConstraint c : cs) {
                        if (propagationQueue.contains(c)) continue;
                        final Set<Integer> otherVarSeqs = c.vars().map(v -> walkVar(v, subst).seq)
                                .filter(s -> s != varSeq);
                        for (final int otherVarSeq : otherVarSeqs) {
                            varConstraints = varConstraints.put(otherVarSeq,
                                    getConstraints(otherVarSeq).remove(c));
                        }
                        propagationQueue.add(c);
                    }
                }

                while (!propagationQueue.isEmpty()) {
                    if (!propagationQueue.remove().propagate(this)) return false;
                }

                excitedVars = TreeSet.empty();

                while (!constraintQueue.isEmpty()) {
                    final AriConstraint ac = constraintQueue.remove();
                    for (final int varSeq : ac.vars().map(v -> walkVar(v, subst).seq)) {
                        varConstraints = varConstraints.put(varSeq, getConstraints(varSeq).prepend(ac));
                    }
                }

                while (!domainQueue.isEmpty()) {
                    final Domain d = domainQueue.remove();
                    if (d.isEmpty()) return false;
                    final Var v = walkVar(d.variable(), subst);
                    final Domain d0 = getDomain(v);
                    if (!equalValues(d0, d)) {
                        final Domain d1 = d.intersect(d0);
                        if (d1.isEmpty()) return false;
                        varDomains = varDomains.put(v.seq, d1);
                        excitedVars = excitedVars.add(v.seq);
                    }
                }
            } while (!excitedVars.isEmpty());
            return true;
        }


        Option<Map<Integer, Object>> solution() {

            if (!getToFixpoint()) return Option.none();

            Map<Integer, Object> result = subst;

            final Map<Integer, Domain> solved =
                    varDomains.filter(t -> t._2.hasSolution());

            // Remove domain and ari constraint from the solved variables
            for (final int varSeq : solved.keysIterator()) {
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
            for (final Tuple2<Integer, Domain> solution : solved) {
                if (instantiatedVars.contains(solution._1)) continue; // variable instantiated earlier
                final Option<Map<Integer, Object>> step = unify(new Var(solution._1), solution._2.solution(), result);
                if (step.isEmpty()) return Option.none();
                result = step.get();
            }

            return Option.of(result);
        }

        // Helpers

        Option<Tuple2<Var, Domain>> getVarDomain(Var v) {
            final Var w = walkVar(v, subst);
            final Domain dc = getDomain(w);
            return dc.isEmpty() ? Option.none() : Option.of(Tuple.of(w, dc));
        }

        boolean feed1(Var u, Function2<Var, Domain, Boolean> body) {
            return getVarDomain(u).map(tu -> body.apply(tu._1, tu._2)).getOrElse(false);
        }

        boolean feed2(Var u, Var v,
                      Function4<Var, Domain,
                              Var, Domain,
                              Boolean> body) {
            return getVarDomain(u).flatMap(tu ->
                    getVarDomain(v).map(tv ->
                            body.apply(tu._1, tu._2, tv._1, tv._2)))
                    .getOrElse(false);
        }

        boolean feed3(Var u, Var v, Var w,
                      Function6<Var, Domain,
                              Var, Domain,
                              Var, Domain,
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
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, z);
        }

        boolean propagateXZ(Var xv, Domain domX, Var zv, Domain domZ, Propagator propagator) {
            if (domX.isEnumerated()) {
                if (domZ.isEnumerated()) {
                    final List<Tuple2<Integer, Integer>> cXZ =
                            List.ofAll(domX.values().toList().crossProduct(domZ.values().iterator())
                                    .filter(t -> t._1 + y == t._2));
                    return propagator.add(Enumerated.of(xv, TreeSet.ofAll(cXZ.map(Tuple2::_1))).intersect(domX)) &&
                            propagator.add(Enumerated.of(zv, TreeSet.ofAll(cXZ.map(Tuple2::_2))).intersect(domZ));
                } else {
                    return propagator.add(Enumerated.of(zv, domX.values().map(x -> x + y)).intersect(domZ));
                }
            } else if (domZ.isEnumerated()) {
                return propagator.add(Enumerated.of(xv, domZ.values().map(z -> z - y)).intersect(domX));
            } else {
                return true;
            }
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, z, (xv, domX, zv, domZ) -> {
                if (xv.seq == zv.seq) {
                    // Case: X + y = X. Satisfied for any x:dom(X) when y=0
                    return y == 0;
                } else if (domX.hasSolution() && domZ.hasSolution()) {
                    // Verify x + y = z
                    return domX.solution() + y == domZ.solution();
                } else if (domX.hasSolution()) {
                    // z := x + y
                    return propagator.add(Enumerated.of(zv, domX.solution() + y).intersect(domZ));
                } else if (domZ.hasSolution()) {
                    // x = z - y
                    return propagator.add(Enumerated.of(xv, domZ.solution() - y).intersect(domX));
                } else {
                    propagator.add(new PlusVCV(xv, y, zv)); // keep the equation
                    if (!propagator.isExcited(xv) && !propagator.isExcited(zv)) {
                        // Now we need to make the initial check of the constraint
                        return propagateXZ(xv, domX, zv, domZ, propagator);
                    } else if (propagator.isExcited(xv) && propagator.isExcited(zv)) {
                        // Both X and Y are excited: do the full check of the constraint
                        return propagateXZ(xv, domX, zv, domZ, propagator);
                    } else if (propagator.isExcited(xv) && domX.isEnumerated()) {
                        // dom(Z)' = { x + y | x:dom(X) } \cap dom(Z)
                        return propagator.add(Enumerated.of(zv, domX.values().map(x -> x + y)).intersect(domZ));
                    } else if (propagator.isExcited(zv) && domZ.isEnumerated()) {
                        // dom(X)' = { z - y | z: dom(Z) } \cap dom(X)
                        return propagator.add(Enumerated.of(xv, domZ.values().map(z -> z - y)).intersect(domX));
                    } else { // nothing can be inferred; reassert the constraint
                        return true;
                    }
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
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y);
        }

        boolean propagateXY(Var xv, Domain domX, Var yv, Domain domY, Propagator propagator) {
            if (domX.isEnumerated()) {
                if (domY.isEnumerated()) {
                    final List<Tuple2<Integer, Integer>> cXY = List.ofAll(
                            domX.values().toList()
                                    .crossProduct(domY.values().iterator())
                                    .filter(t -> t._1 + t._2 == z));
                    return propagator.add(Enumerated.of(xv, TreeSet.ofAll(cXY.map(Tuple2::_1)))) &&
                            propagator.add(Enumerated.of(yv, TreeSet.ofAll(cXY.map(Tuple2::_2))));
                } else {
                    return propagator.add(Enumerated.of(yv, domX.values().map(x -> z - x)).intersect(domY));
                }
            } else if (domY.isEnumerated()) {
                return propagator.add(Enumerated.of(xv, domY.values().map(y -> z - y)).intersect(domX));
            } else {
                return true;
            }
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed2(x, y, (xv, domX, yv, domY) -> {
                if (xv.seq == yv.seq) {
                    // X + X = z; single solution when z is even: x = z/2
                    if ((z & 1) != 0) return false;
                    return propagator.add(Enumerated.of(xv, z / 2).intersect(domX));
                } else if (domX.hasSolution() && domY.hasSolution()) {
                    // verify x + y = z
                    return domX.solution() + domY.solution() == z;
                } else if (domX.hasSolution()) {
                    // y := z - x
                    return propagator.add(Enumerated.of(yv, z - domX.solution()).intersect(domY));
                } else if (domY.hasSolution()) {
                    // x := z - y
                    return propagator.add(Enumerated.of(xv, z - domY.solution()).intersect(domX));
                } else {
                    propagator.add(new PlusVVC(xv, yv, z));
                    if (!propagator.isExcited(xv) && !propagator.isExcited(yv)) {
                        // The initial restriction of all variables
                        return propagateXY(xv, domX, yv, domY, propagator);
                    } else if (propagator.isExcited(xv) && propagator.isExcited(yv)) {
                        // Both X and Y excited: perform a full check
                        return propagateXY(xv, domX, yv, domY, propagator);
                    } else if (propagator.isExcited(xv) && domX.isEnumerated()) {
                        // dom(Y)' = { z - x : x:dom(X) } \cap dom(Y)
                        return propagator.add(Enumerated.of(yv, domX.values().map(x -> z - x)).intersect(domY));
                    } else if (propagator.isExcited(yv) && domY.isEnumerated()) {
                        return propagator.add(Enumerated.of(xv, domY.values().map(y -> z - y)).intersect(domX));
                    } else {
                        return true;
                    }
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
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public Set<Var> vars() {
            return HashSet.of(x, y, z);
        }

//        boolean propagateXYZ(Var xv, Domain domX, Var yv, Domain domY, Var zv, Domain domZ, Propagator propagator) {
//            if (domX.isEnumerated()) {
//                if (domY.isEnumerated()) {
//                    if (domZ.isEnumerated()) {
//                        final List<Tuple3<Integer, Integer, Integer>> cXYZ = List.ofAll(
//                                domX.values().toList().crossProduct(domY.values()).toList().crossProduct(domZ.values())
//                                        .map(t -> Tuple.of(t._1._1, t._1._2, t._2))
//                                        .filter(t -> t._1 + t._2 == t._3));
//                        return propagator.add(Enumerated.of(xv, TreeSet.ofAll(cXYZ.map(Tuple3::_1)))) &&
//                                propagator.add(Enumerated.of(yv, TreeSet.ofAll(cXYZ.map(Tuple3::_2)))) &&
//                                propagator.add(Enumerated.of(zv, TreeSet.ofAll(cXYZ.map(Tuple3::_3))));
//                    } else {
//                        return propagator.add(Enumerated.of(zv,
//                                TreeSet.ofAll(domX.values().toList().crossProduct(domY.values()).map(t -> t._1 + t._2)))
//                                .intersect(domZ));
//                    }
//                } else if (domZ.isEnumerated()) {
//                    return propagator.add(Enumerated.of(yv,
//                            TreeSet.ofAll(domX.values().toList().crossProduct(domZ.values()).map(t -> t._2 - t._1)))
//                            .intersect(domY));
//                } else {
//                    return true;
//                }
//            }
//            if (domY.isEnumerated()) {
//                if (domZ.isEnumerated()) {
//                    return propagator.add(Enumerated.of(xv,
//                            TreeSet.ofAll(domY.values().toList().crossProduct(domZ.values()).map(t -> t._2 - t._1)))
//                            .intersect(domX));
//                } else {
//                    return true;
//                }
//            } else {
//                return true;
//            }
//        }

        // Weak variant -- never sets the domain as a (subset of a) Cartesian product of two variable domains
        boolean propagateXYZ(Var xv, Domain domX, Var yv, Domain domY, Var zv, Domain domZ, Propagator propagator) {
            if (domX.isEnumerated() & domY.isEnumerated() && domZ.isEnumerated()) {
                final List<Tuple3<Integer, Integer, Integer>> cXYZ = List.ofAll(
                        domX.values().toList().crossProduct(domY.values()).toList().crossProduct(domZ.values())
                                .map(t -> Tuple.of(t._1._1, t._1._2, t._2))
                                .filter(t -> t._1 + t._2 == t._3));
                return propagator.add(Enumerated.of(xv, TreeSet.ofAll(cXYZ.map(Tuple3::_1)))) &&
                        propagator.add(Enumerated.of(yv, TreeSet.ofAll(cXYZ.map(Tuple3::_2)))) &&
                        propagator.add(Enumerated.of(zv, TreeSet.ofAll(cXYZ.map(Tuple3::_3))));
            } else {
                return true;
            }
        }

        boolean propagate2XZ(Var xv, Domain domX, Var zv, Domain domZ, Propagator propagator) {
            if (domX.isEnumerated()) {
                if (domZ.isEnumerated()) {
                    final List<Tuple2<Integer, Integer>> cZX = List.ofAll(
                            domZ.values().filter(z -> (z & 1) == 0).toList().crossProduct(domX.values())
                                    .filter(t -> t._1 == 2 * t._2));
                    return propagator.add(Enumerated.of(zv, TreeSet.ofAll(cZX.map(Tuple2::_1)))) &&
                            propagator.add(Enumerated.of(xv, TreeSet.ofAll(cZX.map(Tuple2::_2))));
                } else {
                    return propagator.add(Enumerated.of(zv, domX.values().map(x -> 2 * x)).intersect(domZ));
                }
            } else {
                return propagate2XZ_Z(xv, domX, zv, domZ, propagator);
            }
        }

        boolean propagate2XZ_Z(Var xv, Domain domX, Var zv, Domain domZ, Propagator propagator) {
            if (domZ.isEnumerated()) {
                final SortedSet<Integer> cZ = domZ.values().filter(z -> (z & 1) == 0);
                return propagator.add(Enumerated.of(zv, cZ)) &&
                        propagator.add(Enumerated.of(xv, cZ.map(z -> z / 2)).intersect(domX));
            } else {
                return true;
            }
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
                    // *** Case: 2X = X *** ; the ony solution is x = 0
                    return propagator.add(Enumerated.of(xv, 0).intersect(domX));
                } else if (xv.seq == yv.seq) {
                    // *** Case: 2X = Z ***
                    propagator.add(new PlusVVV(xv, xv, zv)); // keep the constraint
                    if (!propagator.isExcited(xv) && propagator.isExcited(zv)) {
                        // The initial check of the constraint
                        return propagate2XZ(xv, domX, zv, domZ, propagator);
                    } else if (propagator.isExcited(xv) && propagator.isExcited(zv)) {
                        // Both X and Z are excited: restrict both
                        return propagate2XZ(xv, domX, zv, domZ, propagator);
                    } else if (propagator.isExcited(xv) && domX.isEnumerated()) {
                        return propagator.add(Enumerated.of(zv, domX.values().map(x -> 2 * x)).intersect(domZ));
                    } else if (propagator.isExcited(zv)) {
                        return propagate2XZ_Z(xv, domX, zv, domZ, propagator);
                    } else {
                        return true;
                    }
                } else if (xv.seq == zv.seq) {
                    // *** Case: X + Y = X ***
                    return propagator.add(Enumerated.of(yv, 0).intersect(domY));
                } else if (yv.seq == zv.seq) {
                    // *** Case: X + Y = Y ***
                    return propagator.add(Enumerated.of(xv, 0).intersect(domX));
                } else {
                    // *** Case: X, Y, and Z are three distinct variables ***
                    propagator.add(new PlusVVV(xv, yv, zv));
                    return propagateXYZ(xv, domX, yv, domY, zv, domZ, propagator);
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
        public String toString() {
            return symbolicRepr().toString();
        }

        @Override
        public boolean propagate(Propagator propagator) {
            return propagator.feed1(x, (xv, domX) -> {
                if (domX.hasSolution()) {
                    return domX.solution() != y;
                } else if (domX.isEnumerated()) {
                    return propagator.add(Enumerated.of(xv, domX.values().remove(y)));
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
        public String toString() {
            return symbolicRepr().toString();
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
        public String toString() {
            return symbolicRepr().toString();
        }

        boolean propagateYZ(Var yv, Domain domY, Var zv, Domain domZ, Propagator propagator) {
            if (domY.isEnumerated()) {
                if (domZ.isEnumerated()) {
                    final List<Tuple2<Integer, Integer>> cZY = List.ofAll(
                            domZ.values().filter(z -> z % x == 0).toList().crossProduct(domY.values())
                                    .filter(t -> t._1 - x * t._2 == 0));
                    return propagator.add(Enumerated.of(zv, TreeSet.ofAll(cZY.map(Tuple2::_1)))) &&
                            propagator.add(Enumerated.of(yv, TreeSet.ofAll(cZY.map(Tuple2::_2))));
                } else {
                    return propagator.add(Enumerated.of(zv, domY.values().map(y -> x * y)).intersect(domZ));
                }
            } else {
                return propagateZ(yv, domY, zv, domZ, propagator);
            }
        }

        boolean propagateZ(Var yv, Domain domY, Var zv, Domain domZ, Propagator propagator) {
            if (domZ.isEnumerated()) {
                final SortedSet<Integer> cZ = domZ.values().filter(z -> z % x == 0);
                return propagator.add(Enumerated.of(zv, cZ)) &&
                        propagator.add(Enumerated.of(yv, cZ.map(z -> z / x)).intersect(domY));
            } else {
                return true;
            }
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
                        if (domY.hasSolution()) return domY.solution() == 0;
                        else return propagator.add(Enumerated.of(yv, 0).intersect(domY));
                    }
                    // Below: Y and Z are distinct variables
                } else if (x == 0) {
                    // 0*Y=Z -> Z must be 0, Y is unconstrained
                    if (domZ.hasSolution()) return domZ.solution() == 0;
                    else return propagator.add(Enumerated.of(zv, 0).intersect(domZ));
                } else if (domY.hasSolution() && domZ.hasSolution()) {
                    // Verify solutions for Y and Z
                    return x * domY.solution() == domZ.solution();
                } else if (domY.hasSolution()) {
                    // z := x*y
                    return propagator.add(Enumerated.of(zv, x * domY.solution()).intersect(domZ));
                } else if (domZ.hasSolution()) {
                    final int solZ = domZ.solution();
                    if (solZ % x != 0) return false;
                    return propagator.add(Enumerated.of(yv, solZ / x).intersect(domY));
                } else {
                    // At this point, we have to keep the constraint
                    propagator.add(new TimesCVV(x, yv, zv));
                    if (!propagator.isExcited(yv) && !propagator.isExcited(zv)) {
                        // Initial restriction on both variables
                        return propagateYZ(yv, domY, zv, domZ, propagator);
                    } else if (propagator.isExcited(yv) && domY.isEnumerated()) {
                        return propagator.add(Enumerated.of(zv, domY.values().map(y -> x * y)).intersect(domZ));
                    } else if (propagator.isExcited(zv)) {
                        return propagateZ(yv, domY, zv, domZ, propagator);
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
            final List<Tuple3<Var, Domain, Integer>> effectiveVars =
                    vars.map(v -> walk(v, subst))
                            .filter(o -> o instanceof Var)
                            .map(o -> (Var) o).toSet().toList()
                            // Enrich variables with domain
                            .map(v -> Tuple.of(v,
                                    getAttribute(v, subst, DOM_DOMAIN).map(d -> (Domain) d)))
                            // Eliminate variables without a domain constraint
                            .filter(t -> !t._2.isEmpty())
                            // Extract the domain constraint, and add the number of arithmetic constraints
                            .map(t -> Tuple.of(t._1, t._2.get(),
                                    getAttribute(t._1, subst, ARI_DOMAIN)
                                            .map(o -> ((AriAttribute) o).constraints().length())
                                            .getOrElse(0)));

            final List<Tuple2<Var, Domain>> enumerableVars =
                    effectiveVars.filter(t -> t._2.isEnumerated())
                            .map(t -> Tuple.of(t._1, t._2, t._2.values().size(), t._3))
                            .sorted((t1, t2) -> {
                                if (!t1._3.equals(t2._3)) return t1._3 - t2._3;
                                else return t2._4 - t1._4;
                            })
                            .map(t -> Tuple.of(t._1, t._2));

            final List<Tuple2<Var, Domain>> unboundedVars =
                    effectiveVars.filter(t -> !t._2.isEnumerated())
                            .sorted((t1, t2) -> t2._3 - t1._3)
                            .map(t -> Tuple.of(t._1, t._2));

            final List<Tuple2<Var, Domain>> allVars =
                    enumerableVars.appendAll(unboundedVars);

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
