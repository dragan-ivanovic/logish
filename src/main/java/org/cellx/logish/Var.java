package org.cellx.logish;

/**
 * Logic variable.
 *
 * <p>A logic variable is a placeholder for a value (an object).</p>
 *
 * <p>The meaning of a variable is always relative to a <b>substitution</b>, which is
 * an (immutable) map variables to values.
 * <p>
 * Each distinct variable has a </b>that is initially unknown, but can become
 * known in the course of execution of a query.</p>
 *
 * <p></p>
 */
public final class Var implements Comparable<Var> {
    /**
     * Unique, non-negative index of this variable in a substitution.
     */
    final int index;

    Var(int index) {
        this.index = index;
    }

    /**
     * Returns the index of this variable.
     *
     * @return the unique variable index
     */
    @SuppressWarnings("unused")
    public int index() {
        return index;
    }

    /**
     * Compares this variable to another object.
     *
     * <p>To succeed, the other object needs to be a Variable, and to have the same index.</p>
     *
     * @param o the other object
     * @return Result of the comparison: {@code true} if successful, {@code false} otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Var)) return false;
        return index == ((Var) o).index;
    }

    @Override
    public int hashCode() {
        return index;
    }

    @Override
    public String toString() {
        return "_" + index;
    }

    /**
     * Checks if this variable occurs in the given term under the given substitution.
     *
     * <p>For the check to make sense, the variable needs to be free in the substitution.</p>
     *
     * @param term  the term, the context of which is checked
     * @param subst the substitution
     * @return {@code true} iff occurs.
     */
    public boolean occursIn(Object term, Logish.Subst subst) {
        final Object walked = Logish.walk(term, subst);
        if (equals(walked)) return true;
        else return Logish.exists(walked, e -> occursIn(e, subst));
    }

    @Override
    public int compareTo(Var o) {
        return this.index - o.index;
    }
}
