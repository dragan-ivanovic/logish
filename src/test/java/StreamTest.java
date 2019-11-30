import io.vavr.collection.Stream;
import org.junit.Test;

public class StreamTest {

    @Test
    public void testConcat() {
        final Stream<Integer> s1 = Stream.from(0, 1);
        final Stream<Integer> s2 = Stream.from(-1, -1);
        System.out.println(Stream.concat(s1, s2).take(50).toList());

    }
}
