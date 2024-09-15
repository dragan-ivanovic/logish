# Logish

![](https://github.com/dragan-ivanovic/logish/workflows/build+tests/badge.svg)

MiniKanren with finite-domain constraints in Java.

## Introduction

_Logish_ is a Java 8+ implementation of
[**miniKanren**](http://minikanren.org/), an embedded sub-language (domain-specific
language) for relational programming. Logish aims at emulating the key
benefits of fully-fledged [Constraint Logic Programming (CLP)](https://en.wikipedia.org/wiki/Constraint_logic_programming) 
systems based on [**Prolog**](https://en.wikipedia.org/wiki/Prolog) in a Java-only 
environment at an acceptable performance.

## Features

  - Small and compact, single external dependency: _Logish_ 
    internally uses [Java Vavr library](https://www.vavr.io/)
    for immutable functional data structures in Java.
  
  - Simple usage: for basic use it suffices to import static
    method `Logish.run`, and all static members of `Logish.StdGoals`.
  
  - Queries return a `Stream` of objects (no need to specify upfront 
    how many solutions to look for).
    
  - Constraint solving over finite (integer) domains, with linear
    constraints.  The underlying mechanism for constraint solving are attributed 
    variables, allowing coexistence of multiple constraint solvers.
    
  
## Basic use

```java
import static org.cellx.logish.Logish.run;
import static org.cellx.logish.Logish.StdGoals.*;

public class MinimalLogish {

    public static void main(String[] argv) {
        run(q -> unify(q, "World"))
                .forEach(o -> System.out.println("Hello, " + o + "!"));
    }
}
```

In this example, method `run()` executes a query over a logical variable
named `q`, and returns a stream of values for `q` that satisfy a
logical goal to the right of `q ->`.  Here, the goal is `unify(q,
"World")`, which unifies (asserts logical equality between)
the query variable `q` and string literal `"World"`.  

Technically, the argument to `run()` is a function that accepts a logic variable
created by `run()` and returns a goal (to the right of `->` in our example).  What `run()` 
does next is to execute (i.e., try to satisfy) the goal, which may result in zero or more 
solutions.  For each solution, `run()` puts the value of the logic variable in the resulting
stream. This is done lazily: only when the code accessing the stream tries to get the
next element, the next solution of the goal is computed.  Since a goal may succeed infinitely 
many times, the resulting stream may be infinite.

Java expression `run(q -> unify(q, "World")` can be read as a question: \`_Which values of `q`
make goal `unify(q, "World")` true?_' The only solution for _q_ is equal
to the string literal `"World"`, and therefore resulting
stream of values for `q` has exactly one member. The program therefore prints
`"Hello, World!"` ands stops.


## Logic variables and goals

A **logic variable** is an instance of class `Logish.Var`.  Like a
mathematical variable, it serves as a placeholder for a value (an
object).  Unlike Java variables, a logic variable is not a mutable location in
memory that stores the value: after its value has become known, it
never changes.  And it also differs from a `final` variable, because its value 
is initially unknown and may become known only in the process of executing a logic goal. 
In logic programming, and therefore in _Logish_, the
computation if effectively a search for values of the variables that
satisfy the **goal**.

As an example, a query:

```
run(q -> element(q, List.of(1, 2, 3)))
```

returns (a stream of) all values of variable `q` for which goal `element(q,
List.of(1, 2, 3))` succeeds.  Note that `List.of(1, 2, 3)` is a Java
expression that creates an immutable list (an instance of
_io.vavr.collection.List_) whose elements are integers 1, 2, and 3.  So, for
which _q_ is this goal satisfied?  There are three solutions: _q_=1, _q_=2,
and _q_=3, and so the resulting stream has three elements, 1, 2, and 3.

As in mathematics, logic variables remember when they refer to the same value:
if we know that _x_=_y_, then as soon as we learn _y_=3, we
automatically have _x_=3 as well.  In the following
query:

```
run(q -> fresh(x -> seq(unify(q, x), element(x, List.of(1, 2, 3)))))
```

we have two new elements.  The first one is `fresh(x -> G)` that creates a
fresh variable _x_ (with initially unknown value) and passes it to _G_.  Next,
`seq(G1, G2)` is a composite goal that succeeds exactly
when both _G1_ and _G2_ succeed (one after another). Goal `unify(q, x)` has the same
meaning as _q_=_x_ in mathematics, which is different from the meaning of `=` 
(assignment) or `==` (object identity check) in Java.  Since _x_=_q_, this query unsurprisingly
produces exactly the same solutions as the previous one.  

## Unification

Goal `unify(X, Y)` is technically called the **unification** of _X_ and
_Y_.  Unification behaves exactly as Java's _java.util.Objects.equals(X, Y)_, except
for these three cases:

  * When both _X_ and _Y_ are free variables.  In this case, _Logish_ proceeds
    by making sure that any mention of _X_ is synonymous with _Y_ and
    vice-versa.
  * When _X_ is a free variable, and _Y_ is not.  In this case _Logish_
    proceeds by making sure that any mention of _X_ is synonymous with the
    value _Y_. The symmetrical case where _Y_ is a free variable and _X_ is
    not is treated analogously.
  * When both _X_ and _Y_ are instances of a special class _Logish.Cons_.
    This is treated as structural unification, and will be described later in
    the text.
    
It is, of course, perfectly possible to satisfy the goal without instantiating
the variable.  For instance, query:

```
run(q -> success())
```

uses goal `success()` that always succeeds exactly once, which means that it
returns a stream with a single element.  But what is that element like?  It is
a logical variable corresponding to _q_ itself (instance of _Logish.Var_),
whose _toString()_ is `"_0"`.  This reflects the general rule: variables that
are instantiated are replaced by their values, while free variables remain in
the result.  The non-negative integer following the underscore in the string
representation of a free variable (which can be obtained using method
_Logish.Var.seq()_ on the variable) serves to globally distinguish distinct
variables in the result. The symbolic variable names, such as _q_, exist only
at Java compile time, and are not available at run-time.

Each `run()` query gives one variable "for free", which is reported back. As
seen before, if we need more variables, we can use `fresh()`.  But, to make
fresh variables really usable, we need to look at _Logish.Cons_.

## Consing



```
member(q -> fresh((x, y) -> seq(
        element(x, List.of(-1, 0, 1)),
        element(y, List.of(2, 3, 4)),
        
```

