package org.cellx.logishTest;

import com.pholser.junit.quickcheck.Property;
import com.pholser.junit.quickcheck.generator.InRange;
import com.pholser.junit.quickcheck.runner.JUnitQuickcheck;
import org.cellx.logish.IntMap;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

@RunWith(JUnitQuickcheck.class)
public class IntMapTests {

    final Logger logger = LoggerFactory.getLogger(getClass());

    @Property(trials = 1000)
    public void testInsertion(HashSet<@InRange(min = "0", max = "100") Integer> set) {
        // Insert all values into a map
        final IntMap<Integer> map = IntMap.ofAll(set, i -> IntMap.entry(i, i));
        // Make sure the tree is balanced
        map.checkConsistency();
        // Compare the map and the set
        compareMapKeysAndSet(map, set);
    }

    @Property(trials = 1000)
    public void testDeletion(HashSet<@InRange(min = "0", max = "100") Integer> minusSet) {
        // Create a set of all numbers between 0 and 100
        final Set<Integer> set = new TreeSet<>();
        for (int i=0; i<=100; i++) set.add(i);

        final IntMap<Integer> fullMap = IntMap.ofAll(set, i -> IntMap.entry(i, i));

        set.removeAll(minusSet);

        final IntMap<Integer> reducedMap = fullMap.withoutKeys(minusSet, i -> i);

        reducedMap.checkConsistency();

        compareMapKeysAndSet(reducedMap, set);
    }

    protected  <V> void compareMapKeysAndSet(IntMap<V> map, Set<Integer> set) {
        // The size must be the same
        Assert.assertEquals(set.size(), map.size());
        // All set elements must be in the map
        for (Integer element: set) {
            Assert.assertTrue("set element in map", map.contains(element));
        }
        // All map elements must be in the set
        for (IntMap.Entry<V> entry: map) {
            Assert.assertTrue("map key in set", set.contains(entry.key));
        }
    }

}
