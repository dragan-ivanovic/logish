# Logish

![](https://github.com/idrag/logish/workflows/build+tests/badge.svg)

MiniKanren with constraints in Java

## Introduction

_Logish_ is a Java 8+ implementation of
[**miniKanren**](http://minikanren.org/), an embedded domain-specific
language for logic programming. The project aims at emulating the key
benefits of fully-fledged
[**Prolog**](https://en.wikipedia.org/wiki/Prolog)-based constraint
logic programming systems in a Java-only environment at an acceptable
performance, and hence the name.

## Features

  - _Logish_ is small and compact: the core system is in a single Java
    source file `Logish.java`, and the finite domain constraint solver
    resides in another file `Fd.java`.
  
  - The usage is simple: for basic use it suffices to import static
    member `Logish.run`, and all static members of `Logish.Goal`.
  
  - _Logish_ internally uses [Vavr](https://www.vavr.io/) for
    immutable functional data structures in Java.  A query result is a
    `Stream` of objects (no need to specify upfront how many solutions
    to look for).
    
  - Constraint solving over finite (integer) domains with linear
    constraints.
    
  - _Logish_ implements attributed variables, allowing multiple
    constraint solvers at the same time. The same mechanism is used by
    `freeze` and the finite domain solver.
    
  
## Basic use

```java
import static org.cellx.Logish.run;
import static org.cellx.Logish.Goal.*;
import io.vavr.collection.Stream;

// ...

 final Stream<Object> result = run(q -> unify(q, "Hello, World!"));
 for (final Object o: result) {
   System.out.println(o);
 }

// ...
```

In this example, method `run()` runs a query over a logical variable
internally named `q`, and returns a stream of values of `q`.
`unify(q, "Hello, world!")` is a logical _goal_ that unifies its
arguments.  In this case, the initially unknown variable `q` receives
value that is a string literal `"Hello, world!"`.
