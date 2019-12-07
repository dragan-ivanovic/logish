package org.cellx.relishTest;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.cellx.relish.Relish;
import org.junit.Test;

import static org.cellx.relish.Relish.Goal.*;
import static org.cellx.relish.Relish.run;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NQueensTest {

    static Relish.Goal nQueens(int n, Object board) {
        return nQueens1(1, n, Relish.Cons.NIL, Relish.Cons.fromIterable(Stream.rangeClosed(1, n)), board);
    }

    static boolean attacks(int r1, int c1, int r2, int c2) {
        return r1 == r2 || c1 == c2 || Math.abs(r1 - r2) == Math.abs(c1 - c2);
    }

    static Relish.Goal nQueens1(int r, int n, Object queens, Object available, Object board) {
        return delayed(() -> {
            if (r > n) return seq(unify(available, Relish.Cons.NIL), unify(queens, board));
            else return fresh((prefix, cv, suffix) ->
                    seq(
                            appendO(prefix, Relish.Cons.th(suffix, cv), available),
                            not(fresh((r2v, c2v) -> seq(
                                    memberO(Relish.Cons.th(c2v, r2v), queens),
                                    test(Integer.class, cv, r2v, c2v, (c, r2, c2) -> attacks(r, c, r2, c2))
                            ))),
                            fresh(rest -> seq(
                                    appendO(prefix, suffix, rest),
                                    nQueens1(r + 1, n, Relish.Cons.th(queens, Relish.Cons.th(cv, r)), rest, board)
                            ))
                    ));
        });
    }

    @Test
    public void testNQueens1() {
        BaseTest.executeQuery("n_queens(4, Q)", 20,
                run(q -> nQueens(4, q)),
                sols -> {
                    // 2 solutions
                    assertEquals(2, sols.length());
                    for (final Object sol : sols) {
                        final List<Tuple2<Integer, Integer>> typedSol =
                                List.ofAll((Relish.Cons) sol).map(e -> {
                                    final Relish.Cons z = (Relish.Cons) e;
                                    int r = (Integer)z.car();
                                    int c = (Integer)z.cdr();
                                    assertTrue(r >= 1 && r <= 4);
                                    assertTrue(c >= 1 && c <= 4);
                                    return Tuple.of(r, c);
                                });
                        for (final Tuple2<Integer, Integer> pos : typedSol) {
                            typedSol.existsUnique(pos::equals);
                            assertTrue(typedSol.forAll(otherPos -> pos.equals(otherPos) ||
                                    !attacks(pos._1, pos._2, otherPos._1, otherPos._2)));
                        }
                    }
                }
        );
    }

    @Test
    public void testNQueens2() {
        BaseTest.executeQuery("n_queens(5, Q)", 20,
                run(q -> nQueens(5, q)),
                sols -> {
                    // 2 solutions
                    assertEquals(10, sols.length());
                    for (final Object sol : sols) {
                        final List<Tuple2<Integer, Integer>> typedSol =
                                List.ofAll((Relish.Cons) sol).map(e -> {
                                    final Relish.Cons z = (Relish.Cons) e;
                                    int r = (Integer)z.car();
                                    int c = (Integer)z.cdr();
                                    assertTrue(r >= 1 && r <= 5);
                                    assertTrue(c >= 1 && c <= 5);
                                    return Tuple.of(r, c);
                                });
                        for (final Tuple2<Integer, Integer> pos : typedSol) {
                            typedSol.existsUnique(pos::equals);
                            assertTrue(typedSol.forAll(otherPos -> pos.equals(otherPos) ||
                                    !attacks(pos._1, pos._2, otherPos._1, otherPos._2)));
                        }
                    }
                }
        );
    }

}
