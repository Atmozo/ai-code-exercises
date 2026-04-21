# Error Analysis: StackOverflowError

**File:** `com/example/recursion/FactorialCalculator.java`  
**Language:** Java 17  
**Trigger:** Calling `calculateFactorial(5)` from `main()`

---

## Error Description

A `StackOverflowError` means the JVM ran out of memory on the call stack.

Every time a method is called in Java, the JVM reserves a small block of memory called a **stack frame** to track that call — what method ran, what parameters were passed, and where to return when it finishes. When a method calls itself recursively, each call stacks a new frame on top of the previous one.

Java's call stack has a fixed size limit (typically 512KB–1MB). If a method keeps calling itself without ever stopping, the stack fills up and the JVM throws `StackOverflowError`.

The stack trace tells the story clearly — the same line repeated 1000+ times:

```
Exception in thread "main" java.lang.StackOverflowError
    at com.example.recursion.FactorialCalculator.calculateFactorial(FactorialCalculator.java:15)
    at com.example.recursion.FactorialCalculator.calculateFactorial(FactorialCalculator.java:15)
    at com.example.recursion.FactorialCalculator.calculateFactorial(FactorialCalculator.java:15)
    ... [1000+ more lines]
```

When you see the same line repeated hundreds of times in a stack trace, infinite recursion is always the cause.

---

## Root Cause

The `calculateFactorial` method is missing a **base case** — a stopping condition that returns a value without making another recursive call.

```java
// Broken implementation
public static int calculateFactorial(int num) {
    return num * calculateFactorial(num - 1);  // calls itself forever
}
```

Every recursive method requires two things:

1. **A base case** — a condition that returns directly without recursing
2. **A recursive case** — a call that moves toward the base case

This implementation has only the recursive case. The call chain looks like this:

```
calculateFactorial(5)
  → calculateFactorial(4)
    → calculateFactorial(3)
      → calculateFactorial(2)
        → calculateFactorial(1)
          → calculateFactorial(0)
            → calculateFactorial(-1)
              → calculateFactorial(-2)
                → ... forever ...
                  → StackOverflowError
```

The method counts down through zero into negative numbers indefinitely because there is no condition to stop it.

**Secondary root cause:** The Javadoc correctly describes the mathematical behaviour of factorial but the implementation does not match it. Documentation and code drifted apart — the comment even acknowledges the bug (`// Missing base case`) but it was left unimplemented.

---

## Solution

### Fix 1 — Add the correct base case (minimal fix)

By mathematical definition, `0! = 1`. That is the base case.

```java
public static int calculateFactorial(int num) {
    if (num == 0) return 1;                    // base case — stop here
    return num * calculateFactorial(num - 1);  // recursive case
}
```

### Fix 2 — Add input validation for negative numbers

Fix 1 still recurses infinitely for negative inputs since `-1` never equals `0`. Reject invalid input explicitly:

```java
public static int calculateFactorial(int num) {
    if (num < 0) throw new IllegalArgumentException(
        "Factorial is not defined for negative numbers: " + num);
    if (num == 0) return 1;
    return num * calculateFactorial(num - 1);
}
```

### Fix 3 — Rewrite iteratively (recommended for production)

Even a correct recursive implementation can overflow for very large inputs. An iterative version carries no stack risk. Note the return type change to `long` — `int` silently overflows at `13!`:

```java
public static long calculateFactorial(int num) {
    if (num < 0) throw new IllegalArgumentException(
        "Factorial is not defined for negative numbers: " + num);
    long result = 1;
    for (int i = 2; i <= num; i++) {
        result *= i;
    }
    return result;
}
```

### Verification tests

```java
@Test void factorial_ofZero_returnsOne() {
    assertEquals(1, FactorialCalculator.calculateFactorial(0));
}

@Test void factorial_ofFive_returns120() {
    assertEquals(120, FactorialCalculator.calculateFactorial(5));
}

@Test void factorial_ofNegative_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
        () -> FactorialCalculator.calculateFactorial(-1));
}
```

---

## Learning Points

**1. Every recursive method needs a base case written first**  
The base case should be the very first thing in the method body — visible immediately, impossible to overlook. If you write the recursive call first and the base case after, it becomes easy to ship a method without one.

**2. Verify the recursive contract before implementing**  
Before writing any recursive method, answer three questions:

- What is the base case and when is it reached?
- What does each recursive call pass, and does it move toward the base case?
- What is the return type, and can it hold the largest expected value?

**3. `int` silently overflows for factorial — use `long`**  
`int` overflows at `13!` and returns a wrong positive number with no error. Silent overflow is harder to detect than a crash. Always use `long` for factorial, or `BigInteger` for arbitrarily large values.

**4. Javadoc and implementation can drift apart**  
The Javadoc described correct factorial behaviour but the code didn't implement it. Documentation that describes intent without matching the implementation is misleading — especially to developers who trust the docs without reading the code.

**5. Read the stack trace pattern, not just the error type**  
A `StackOverflowError` with 1000+ identical frames always means infinite recursion — the fix is a missing base case. A `StackOverflowError` with varied frames across different methods suggests legitimately deep recursion on large input — a different problem requiring a different fix (iterative rewrite or increased stack size).

**6. A comment acknowledging a bug is not a fix**  
The code contained `// Missing base case` as a comment. Noting a problem in a comment and leaving the broken code in place creates a false sense of awareness without actually resolving anything. If a known bug cannot be fixed immediately, it belongs in an issue tracker, not a comment.
