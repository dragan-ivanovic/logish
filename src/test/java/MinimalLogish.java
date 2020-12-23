import static org.cellx.logish.Logish.run;
        import static org.cellx.logish.Logish.StdGoals.*;
        import io.vavr.collection.Stream;

public class MinimalLogish {

    public static void main(String[] argv) {
        final Stream<Object> result = run(q -> unify(q, "World"));
        for (final Object o: result) {
            System.out.println("Hello, " + o + "!");
        }
    }
}