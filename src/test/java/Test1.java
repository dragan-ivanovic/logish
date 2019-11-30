import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.cellx.vavr.Relish;
import org.cellx.vavr.Relish.Cons;
import org.junit.Assert;
import org.junit.Test;

import java.util.function.Consumer;

import static org.cellx.vavr.Relish.Goal.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class Test1 {

    static List<Object> takeAndShow(String title, int n, Stream<Object> solutions) {
        System.out.println("## " + title + ": up to first " + n + " solution(s)");
        List<Object> taken = solutions.take(n).toList();
        taken.zipWithIndex().forEach(tuple2 -> {
            System.out.println("    " + (tuple2._2 + 1) + ": " + tuple2._1);
        });
        System.out.println("total " + (taken.length()) + " solution(s)");
        System.out.println();
        return taken;
    }

    static void takeAndShow(String title, int n, Stream<Object> solutions, Consumer<List<Object>> check) {
        check.accept(takeAndShow(title, n, solutions));
    }

    @Test
    public void test1() {
        takeAndShow("test1", 10,
                run(q -> fresh((x, y) -> seq(
                        unify(q, Cons.list(x, y)),
                        choice(unify(x, 3), unify(x, 4), success()),
                        choice(unify(y, "a"), unify(y, "b"), success())
                ))),
                sols -> {
                    // Expected 9 solutions: [3, a], [3, b], [3, _], [4, a], [4, b], [4, _], [_, a], [_, b], [_, _]
                    assertEquals(9, sols.length());
                    Assert.assertTrue(sols.forAll(e -> e instanceof Cons));
                    final List<List<Object>> casted = sols.map(e -> List.ofAll((Cons) e));
                    for (List<List<Object>> group : List.of(casted.filter(e -> e.head().equals(3)),
                            casted.filter(e -> e.head().equals(4)), casted.filter(e -> e.head() instanceof Relish.Var))) {
                        assertEquals(3, group.length());
                        Assert.assertTrue(group.exists(e -> e.tail().head().equals("a")));
                        Assert.assertTrue(group.exists(e -> e.tail().head().equals("b")));
                        Assert.assertTrue(group.exists(e -> e.tail().head() instanceof Relish.Var));
                    }
                }
        );
    }

    @Test
    public void testAppend1() {
        takeAndShow("append([], [4, 5, 6], Q)", 10,
                run(q -> appendO(Cons.NIL, Cons.list(4, 5, 6), q)),
                sols -> {
                    // Expected 1 solution: [4, 5, 6]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(4, 5, 6), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend2() {
        takeAndShow("append([], X, Q)", 10,
                run(q -> fresh((x) -> appendO(Cons.NIL, x, q))),
                sols -> {
                    // 1 solution: _
                    assertEquals(1, sols.length());
                    assertTrue(sols.head() instanceof Relish.Var);
                }
        );
    }

    @Test
    public void testAppend3() {
        takeAndShow("append([], X, Q), X = [4, 5, 6]", 10,
                run(q -> fresh((x) -> seq(appendO(Cons.NIL, x, q), unify(x, Cons.list(4, 5, 6))))),
                sols -> {
                    // 1 solution: [4, 5, 6]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(4, 5, 6), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend4() {
        takeAndShow("append([], X, Q), X = [A, 5, C], [A C] = [4, 6]", 10,
                run(q ->
                        fresh((x, a, c) ->
                                seq(
                                        appendO(Cons.NIL, x, q),
                                        unify(x, Cons.list(a, 5, c)),
                                        unify(Cons.list(a, c), Cons.list(4, 6))))),
                sols -> {
                    // 1 solution: [4, 5, 6]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(4, 5, 6), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend5() {
        takeAndShow("append([], Q, X), X = [A, 5, C], [A C] = [4, 6]", 10,
                run(q ->
                        fresh((x, a, c) ->
                                seq(
                                        appendO(Cons.NIL, q, x),
                                        unify(x, Cons.list(a, 5, c)),
                                        unify(Cons.list(a, c), Cons.list(4, 6))))),
                sols -> {
                    // 1 solution: [4, 5, 6]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(4, 5, 6), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend6() {
        takeAndShow("append([], [A | Q], X), X = [4, 5, C], C = 6", 10,
                run(q ->
                        fresh((x, a, c) ->
                                seq(
                                        appendO(Cons.NIL, Cons.th(q, a), x),
                                        unify(x, Cons.list(4, 5, c)),
                                        unify(c, 6)))),
                sols -> {
                    // 1 solution: [5, 6]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(5, 6), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend7() {
        takeAndShow("append(A, [Q | B], [4, 5, 6, 7])", 10,
                run(q ->
                        fresh((a, b) -> appendO(a, Cons.th(b, q), Cons.list(4, 5, 6, 7)))),
                sols -> {
                    // 4 solutions: 4, 5, 6, 7
                    assertEquals(4, sols.length());
                    assertEquals(List.of(4, 5, 6, 7), sols.sorted());
                }
        );
    }

    @Test
    public void testAppend8() {
        takeAndShow("append([1], Q, [1, 2, 3, 4])", 10,
                run(q ->
                        appendO(Cons.list(1), q, Cons.list(1, 2, 3, 4))),
                sols -> {
                    // 1 solution: [2, 3, 4]
                    assertEquals(1, sols.length());
                    assertEquals(List.of(2, 3, 4), List.ofAll((Cons) sols.head()));
                }
        );
    }

    @Test
    public void testAppend9() {
        takeAndShow("append(Q, A, [1, 2, 3, 4])", 10,
                run(q -> fresh(a ->
                        appendO(q, a, Cons.list(1, 2, 3, 4))
                )),
                sols -> {
                    // 5 solutions: [], [1], [1, 2], [1, 2, 3], [1, 2, 3, 4]
                    assertEquals(5, sols.length());
                    assertTrue(sols.exists(Cons.NIL::equals));
                    final List<List<Object>> nonEmpty = sols.filter(e -> e instanceof Cons).map(e -> List.ofAll((Cons) e));
                    assertEquals(4, nonEmpty.length());
                    assertTrue(nonEmpty.existsUnique(e -> List.of(1).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(1, 2).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(1, 2, 3).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(1, 2, 3, 4).equals(e)));
                }
        );
    }

    @Test
    public void testAppend10() {
        takeAndShow("append(A, Q, [1, 2, 3, 4])", 10,
                run(q -> fresh(a ->
                        appendO(a, q, Cons.list(1, 2, 3, 4))
                )),
                sols -> {
                    // 5 solutions: [1, 2, 3, 4], [2, 3, 4], [3, 4], [4], []
                    assertEquals(5, sols.length());
                    assertTrue(sols.exists(Cons.NIL::equals));
                    final List<List<Object>> nonEmpty = sols.filter(e -> e instanceof Cons).map(e -> List.ofAll((Cons) e));
                    assertEquals(4, nonEmpty.length());
                    assertTrue(nonEmpty.existsUnique(e -> List.of(1, 2, 3, 4).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(2, 3, 4).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(3, 4).equals(e)));
                    assertTrue(nonEmpty.existsUnique(e -> List.of(4).equals(e)));
                }
        );
    }

    @Test
    public void testAppend11() {
        takeAndShow("append([1], _a, Q)", 20,
                run(q -> fresh(a ->
                        appendO(Cons.list(1), a, q)
                ))
        );
    }

    static Relish.Goal listOfO(Object x, Object e) {
        return fresh(t -> conda(
                clause(unify(x, Cons.NIL), Relish.Goal::success),
                clause(unify(x, Cons.th(t, e)), () -> listOfO(t, e)))
        );
    }

    @Test
    public void testListOf1() {
        takeAndShow("listOf([1,1,1,1], Q)", 10,
                run(q -> listOfO(Cons.list(1, 1, 1, 1), q)),
                sols -> {
                    // 1 solution: 1
                    assertEquals(1, sols.length());
                    assertEquals(1, sols.head());
                }
        );
    }

    @Test
    public void testListOf2() {
        takeAndShow("listOf(Q, 1)", 10,
                run(q -> listOfO(q, 1)),
                sols -> {
                    // One solution: []
                    assertEquals(1, sols.length());
                    assertEquals(Cons.NIL, sols.head());
                }
        );
    }

    @Test
    public void testMember1() {
        takeAndShow("member(Q, [1, 2, 3, 4])", 10,
                run(q -> memberO(q, Cons.list(1, 2, 3, 4))),
                sols -> {
                    // 4 solutions: 1, 2, 3, 4
                    assertEquals(4, sols.length());
                    assertEquals(List.of(1, 2, 3, 4), sols.sorted());
                }
        );
    }

    @Test
    public void testMember2() {
        takeAndShow("member(1, [1, 2, 3, 4])", 10,
                run(q -> memberO(1, Cons.list(1, 2, 3, 4))),
                sols -> {
                    // 1 solution
                    assertEquals(1, sols.length());
                }
        );
    }

    @Test
    public void testMember3() {
        takeAndShow("member(1, [1, 2, 1, 4])", 10,
                run(q -> memberO(1, Cons.list(1, 2, 1, 4))),
                sols -> {
                    // 2 solutions
                    assertEquals(2, sols.length());
                }
        );
    }

    @Test
    public void testMember4() {
        takeAndShow("member(1, [1, 2, 1, q])", 10,
                run(q -> memberO(1, Cons.list(1, 2, 1, q))),
                sols -> {
                    // 3 solutions: _, _, and 1
                    assertEquals(3, sols.length());
                    assertEquals(2, sols.filter(e -> e instanceof Relish.Var).length());
                    assertEquals(List.of(1), sols.filter(e -> !(e instanceof Relish.Var)));
                }
        );
    }

    @Test
    public void testMember5() {
        takeAndShow("member(5, [1, 2, 1, 4])", 10,
                run(q -> memberO(5, Cons.list(1, 2, 1, 4))),
                sols -> {
                    // No solutions
                    assertEquals(0, sols.length());
                }
        );
    }

    @Test
    public void testMemberCheck1() {
        takeAndShow("memberchk(1, [1, 2, 1, q])", 10,
                run(q -> memberCheckO(1, Cons.list(1, 2, 1, q))),
                sols -> {
                    // 1 solution
                    assertEquals(1, sols.length());
                }
        );
    }

    @Test
    public void testMemberCheck2() {
        takeAndShow("memberchk(5, [1, 2, 1, 4])", 10,
                run(q -> memberCheckO(5, Cons.list(1, 2, 1, 4))),
                sols -> {
                    // No solutions
                    assertEquals(0, sols.length());
                }
        );
    }
}
