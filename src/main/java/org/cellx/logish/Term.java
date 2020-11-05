package org.cellx.logish;

/**
 * The constructor interface.
 *
 * @param <T> The resulting element to construct
 */
public interface Term<T> {

    T construct(Logish.Subst subst);

    Logish.Goal unify(Term<T> other);

}
