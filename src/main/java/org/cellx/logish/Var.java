package org.cellx.logish;

/**
 * Logic variable.
 *
 * <p>A logic variable is a placeholder for a value (an object).</p>
 *
 * <p></p>
 */
public final class Var  {

    public Var() {}

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
        return this == o;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }

    @Override
    public String toString() {
        return "_" + hashCode();
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
    public boolean occursIn(Object term, Subst subst) {
        final Object walked = Logish.walk(term, subst);
        if (equals(walked)) return true;
        else return Logish.exists(walked, e -> occursIn(e, subst));
    }
}
