package org.cellx.logish;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.control.Option;

public interface Attribute {
    Option<Subst> validate(Var v, Object o, Subst subst);

    boolean delegating();

    List<Constraint> constraints();

    Option<Tuple2<Option<Attribute>, Subst>> combine(Var v, Attribute other, Subst subst);
}
