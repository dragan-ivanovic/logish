package org.cellx.relishTest;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Stream;
import org.cellx.relish.Fd;
import org.cellx.relish.Relish.Cons;
import org.junit.Test;

import java.util.function.Consumer;

import static org.cellx.relish.Fd.*;
import static org.cellx.relish.Relish.Goal.*;
import static org.cellx.relish.Relish.runC;

public class FdTest {

    static List<Object> executeQueryC(String title, int n, Stream<Tuple2<Object, List<Cons>>> solutions) {
        System.out.println("## " + title + ": up to first " + n + " solution(s)");
        List<Tuple2<Object, List<Cons>>> taken = solutions.take(n).toList();
        taken.zipWithIndex().forEach(tuple2 -> {
            final String lead = String.format("    %2d=> ", tuple2._2+1);
            System.out.print(lead);
            System.out.println(tuple2._1._1);
            if (!tuple2._1._2.isEmpty()) {
                final String indent = indentFor(lead) + "| ";
                for (final Cons repr : tuple2._1._2) {
                    System.out.print(indent);
                    System.out.println(repr);
                }
            }
        });
        System.out.println("total " + (taken.length()) + " solution(s)");
        System.out.println();
        return taken.map(Tuple2::_1);
    }

    static String indentFor(String s) {
        final char[] chars = s.toCharArray();
        final int len = chars.length;
        for (int i = 0; i < len; i++) chars[i] = (char) Math.min(chars[i], 32);
        return new String(chars);
    }

    static void executeQueryC(String title, int n, Stream<Tuple2<Object, List<Cons>>> solutions,
                              Consumer<List<Object>> check) {
        check.accept(executeQueryC(title, n, solutions));
    }

    @Test
    public void test1() {
        executeQueryC("true", 10,
                runC(q -> success())
        );
    }

    @Test
    public void test2() {
        executeQueryC("Q in [1, 2, 3]", 10,
                runC(q -> in(q, 1, 2, 3))
        );
    }

    @Test
    public void test3() {
        executeQueryC("range(Q, 1, 10)", 10,
                runC(q -> range(q, 1, 10))
        );
    }

    @Test
    public void test4() {
        executeQueryC("range(X, 1, 10), Q = X", 10,
                runC(q -> fresh(x -> seq(range(x, 1, 10), unify(x, q))))
        );
    }

    @Test
    public void test5() {
        executeQueryC("range(Q, 3, 10), range(Q, 1, 5)", 10,
                runC(q -> seq(range(q, 3, 10), range(q, 1, 5)))
        );
    }

    @Test
    public void test5u() {
        executeQueryC("range(X, 3, 10), range(Q, 1, 5), X=Q", 10,
                runC(q -> fresh(x -> seq(range(x, 3, 10), range(q, 1, 5), unify(x, q))))
        );
    }

    @Test
    public void test5l() {
        executeQueryC("range(X, 3, 10), range(Y, 1, 5), Q = [X, Y]", 10,
                runC(q -> fresh((x, y) -> seq(range(x, 3, 10), range(y, 1, 5),
                        unify(q, Cons.list(x, y)))))
        );
    }

    @Test
    public void test5lu() {
        executeQueryC("range(X, 3, 10), range(Y, 1, 5), [Q, Q] = [X, Y]", 10,
                runC(q -> fresh((x, y) -> seq(range(x, 3, 10), range(y, 1, 5),
                        unify(Cons.list(q, q), Cons.list(x, y)))))
        );
    }

    @Test
    public void test6() {
        executeQueryC("range(Q, 6, 10), range(Q, 1, 5)", 10,
                runC(q -> seq(range(q, 6, 10), range(q, 1, 5)))
        );
    }

    @Test
    public void test7() {
        executeQueryC("range(Q, 5, 10), range(Q, 1, 5)", 10,
                runC(q -> seq(range(q, 5, 10), range(q, 1, 5)))
        );
    }

    @Test
    public void testIn1() {
        executeQueryC("in(Q, 2, 4, 6, 8), in(Q, 0, 1, 2, 3, 4)", 10,
                runC(q -> seq(in(q, 2, 4, 6, 8), in(q, 0, 1, 2, 3, 4)))
        );
    }

    @Test
    public void testIn2() {
        executeQueryC("in(Q, 2, 4, 6, 8), in(Q, 0, 1, 2, 3)", 10,
                runC(q -> seq(in(q, 2, 4, 6, 8), in(q, 0, 1, 2, 3)))
        );
    }

    @Test
    public void testIn3() {
        executeQueryC("in(Q, 2, 4, 6, 8), in(Q, 0, 1, 3)", 10,
                runC(q -> seq(in(q, 2, 4, 6, 8), in(q, 0, 1, 3)))
        );
    }

    @Test
    public void testIn5() {
        executeQueryC("in(Q, [2, 4, 6, 8]), in(Q, [0, 1, 2, 3, 4, 5]), member(Q, [0, 1, 2, 3, 4, 5, 6, 7, 8])", 20,
                runC(q -> seq(in(q, 2, 4, 6, 8), in(q, 0, 1, 2, 3, 4, 5),
                        memberO(q, Cons.list(0, 1, 2, 3, 4, 5, 6, 7, 8))))
        );
    }

    @Test
    public void testIn6() {
        executeQueryC("in(Q, 2, 4, 6, 8), range(Q, 0, 5), element(Q, ${Stream.rangeClosed(0, 5)})", 20,
                runC(q -> seq(in(q, 2, 4, 6, 8), range(q, 0, 5),
                        element(q, Stream.rangeClosed(0, 8))))
        );
    }

    @Test
    public void testPlus0() {
        executeQueryC("X + Y #= Q", 20,
                runC(q -> fresh((x, y) -> plusO(x, y, q)))
        );
    }

    @Test
    public void testPlus1() {
        executeQueryC("X + Y #= Q, X=5", 20,
                runC(q -> fresh((x, y) -> seq(plusO(x, y, q), unify(x, 5))))
        );
    }

    @Test
    public void testPlus2() {
        executeQueryC("X + Y #= Q, X=5, Y=8", 20,
                runC(q -> fresh((x, y) -> seq(plusO(x, y, q), unify(x, 5), unify(y, 8))))
        );
    }

    @Test
    public void testPlus3() {
        executeQueryC("X + Y #= Q, Y::[1, 3, 5]", 20,
                runC(q -> fresh((x, y) -> seq(plusO(x, y, q), in(y, 1, 3, 5))))
        );
    }

    @Test
    public void testPlus4() {
        executeQueryC("X + Y #= Q, Y::[1, 3, 5], X = 9", 20,
                runC(q -> fresh((x, y) -> seq(
                        plusO(x, y, q),
                        in(y, 1, 3, 5),
                        unify(x, 9)
                )))
        );
    }

    @Test
    public void testPlus5a() {
        executeQueryC("X + Y #= Q, Y::[1, 3, 5], X = 9, Y + 1 #= 3", 20,
                runC(q -> fresh((x, y) -> seq(
                        plusO(x, y, q),
                        in(y, 1, 3, 5),
                        unify(x, 9),
                        plusO(y, 1, 3)
                )))
        );
    }

    @Test
    public void testPlus5b() {
        executeQueryC("X + Y #= Q, Y::[1, 3, 5], X = 9, Y + 1 #= 4", 20,
                runC(q -> fresh((x, y) -> seq(
                        plusO(x, y, q),
                        in(y, 1, 3, 5),
                        unify(x, 9),
                        plusO(y, 1, 4)
                )))
        );
    }

    @Test
    public void testPlus6() {
        executeQueryC("X + Y #= Q, Y::[1, 3, 5], X = 9, Y + 1 #= 4", 20,
                runC(q -> fresh((x, y) -> seq(
                        plusO(x, y, q),
                        in(y, 1, 3, 5),
                        unify(x, 9),
                        plusO(y, q, 19)
                )))
        );
    }

    @Test
    public void testSys1() {
        executeQueryC("Q = [X, Y], X + 4 #= Y, X + Y #= 14, X::0..10, Y::0..10", 20,
                runC(q -> fresh((x, y) -> seq(
                        unify(q, Cons.list(x, y)),
                        plusO(x, 4, y),
                        plusO(x, y, 14),
                        domAll(List.rangeClosed(0, 10), x, y)
                )))
        );
    }

    @Test
    public void testSys2() {
        executeQueryC("Q = [X, Y], X + 4 #= Y, X + Y #= 14, X::0..10, Y::0..10", 20,
                runC(q -> fresh((x, y) -> seq(
                        unify(q, Cons.list(x, y)),
                        plusO(x, 4, y),
                        plusO(x, y, 14),
                        domAll(List.rangeClosed(0, 10), x, y),
                        labeling(x, y)
                )))
        );
    }

    //@Test
    public void sendMoreMoney() {
        // s=9, e=5, n=6, d=7, m=1, o=0, r=8, y=2
        executeQueryC("SEND + MORE = MONEY", 10,
                runC(q -> fresh((s, e, n, d) -> fresh((m, o, r, y) -> seq(
                        unify(q, Cons.list(s, e, n, d, m, o, r, y)),
                        domAll(List.rangeClosed(1, 9), s, m),
                        domAll(List.rangeClosed(0, 9), e, n, d, o, r, y),
                        allDifferentO(s, e, n, d, m, o, r, y),
                        fresh((e1, e2, e3) -> seq(
                                linearO(1000, s, 100, e, 10, n, 1, d, e1),
                                linearO(1000, m, 100, o, 10, r, 1, e, e2),
                                linearO(10000, m, 1000, o, 100, n, 10, e, 1, y, e3),
                                plusO(e1, e2, e3),
                                labeling(s, e, n, d, m, o, r, y)
                        ))
                ))))
        );
    }
}

