import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.junit.Test;

import static org.cellx.vavr.Relish.Goal.*;

import org.cellx.vavr.Relish.Cons;

public class Test1 {

    static void takeAndShow(String title, int n, Stream<Object> solutions) {
        System.out.println("## " + title + ": first " + n + " solution(s)");
        List<Object> taken = solutions.take(n).toList();
        taken.zipWithIndex().forEach(tuple2 -> {
            System.out.println("    [" + (tuple2._2 + 1) + "]: " + tuple2._1);
        });
        System.out.println("total " + (taken.length()) + " solution(s)");
        System.out.println();
    }

    @Test
    public void test1() {
        takeAndShow("test1", 10,
                run(q -> fresh((x, y) -> seq(
                        unify(q, Cons.list(x, y)),
                        choice(unify(x, 3), unify(x, 4), success),
                        choice(unify(y, "a"), unify(y, "b"), success)
                )))
        );
    }

    @Test
    public void testAppend1() {
        takeAndShow("append([], [4, 5, 6], Q)", 10,
                run(q -> appendO(Cons.NIL, Cons.list(4, 5, 6), q))
        );
    }

    @Test
    public void testAppend2() {
        takeAndShow("append([], X, Q)", 10,
                run(q -> fresh((x) -> appendO(Cons.NIL, x, q)))
        );
    }

    @Test
    public void testAppend3() {
        takeAndShow("append([], X, Q), X = [4, 5, 6]", 10,
                run(q -> fresh((x) -> seq(appendO(Cons.NIL, x, q), unify(x, Cons.list(4, 5, 6)))))
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
                                        unify(Cons.list(a, c), Cons.list(4, 6)))))
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
                                        unify(Cons.list(a, c), Cons.list(4, 6)))))
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
                                        unify(c, 6))))
        );
    }

    @Test
    public void testAppend7() {
        takeAndShow("append(A, [Q | B], [4, 5, 6, 7])", 10,
                run(q ->
                        fresh((a, b) -> appendO(a, Cons.th(b, q), Cons.list(4, 5, 6, 7)))
                )
        );
    }

    @Test
    public void testAppend8() {
        takeAndShow("append([1], Q, [1, 2, 3, 4])", 10,
                run(q ->
                       appendO(Cons.list(1), q, Cons.list(1, 2, 3, 4))
                )
        );
    }

    @Test
    public void testAppend9() {
        takeAndShow("append(Q, A, [1, 2, 3, 4])", 10,
                run(q -> fresh(a ->
                        appendO(q, a, Cons.list(1, 2, 3, 4))
                ))
        );
    }

    @Test
    public void testAppend10() {
        takeAndShow("append(A, Q, [1, 2, 3, 4])", 10,
                run(q -> fresh(a ->
                        appendO(a, q, Cons.list(1, 2, 3, 4))
                ))
        );
    }

    @Test
    public void testMember1() {
        takeAndShow("member(Q, [1, 2, 3, 4])", 10,
                run(q -> memberO(q, Cons.list(1, 2, 3, 4)))
        );
    }
}
