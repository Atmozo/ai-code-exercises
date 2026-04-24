# AI Solution Verification Challenge: Merge Sort Bug

**Language:** JavaScript  
**Algorithm:** Merge Sort  
**Bug type:** Wrong index incremented in leftover loop → infinite loop  
**Tests:** 6/6 passing after fix

---

## The Original Bug

```javascript
// Buggy merge function
function merge(left, right) {
  let result = [];
  let i = 0;
  let j = 0;

  while (i < left.length && j < right.length) {
    if (left[i] < right[j]) {
      result.push(left[i]);
      i++;
    } else {
      result.push(right[j]);
      j++;
    }
  }

  while (i < left.length) {
    result.push(left[i]);
    j++; // BUG: incrementing j instead of i
  }

  while (j < right.length) {
    result.push(right[j]);
    j++;
  }

  return result;
}
```

**What goes wrong:** When the left array has remaining elements after the main loop,
the leftover loop increments `j` instead of `i`. Since `i` never advances, the loop
condition `i < left.length` is always true → infinite loop.

**Trigger condition:** Any input where the right array is exhausted before the left.
Example: `merge([3, 4], [1, 2])` — right finishes in the main loop, left leftover
loop runs, `i` never increments, hangs forever.

**Misleading comment:** The code comment says _"only one of these loops will execute"_
— that is actually correct behaviour for merge sort, not the bug. The real bug is the
wrong counter inside the loop body.

---

## Verification Strategy 1 — Collaborative Solution Verification

**Goal:** Find the bug independently before looking at any fix.

**Process:**

1. Read both leftover loops carefully — they should be symmetric
2. Left loop increments `j`, right loop increments `j` — asymmetry spotted
3. Traced `merge([3,4], [1,2])` manually to confirm infinite loop
4. Confirmed `merge([1,3], [2,4])` returns correct result because main loop
   exhausts both arrays — bug never fires

**Key insight:** The bug only triggers when the left array has remaining elements.
If the right array runs out first, the right leftover loop (`j++`) runs correctly.
If neither has remainders, neither loop runs. Only the left remainder path is broken.

---

## Verification Strategy 2 — Learning Through Alternative Approaches

Three fixes were evaluated:

### Fix 1 — Minimal change

```javascript
while (i < left.length) {
  result.push(left[i]);
  i++; // Fixed: was j++
}
```

One character change. Correct. But still has two manual leftover loops with two
index variables that must stay in sync — the same class of bug can be reintroduced.

### Fix 2 — slice + concat (chosen)

```javascript
return result.concat(left.slice(i)).concat(right.slice(j));
```

Replaces both leftover loops with a single return statement. The leftover bug class
is eliminated because there are no separate loops or index variables to manage.

### Fix 3 — Destructive shift

```javascript
while (left.length && right.length) {
  result.push(left[0] <= right[0] ? left.shift() : right.shift());
}
return [...result, ...left, ...right];
```

Readable but not acceptable. `Array.shift()` is O(n) — it removes the first element
and reindexes the entire array. Calling it `n` times makes merge O(n²), which
degrades the overall sort to O(n² log n). Also mutates input arrays, which would
corrupt the recursive calls in `mergeSort`.

| Approach             | Merge complexity | Overall sort | Mutates input | Leftover bug possible |
| -------------------- | ---------------- | ------------ | ------------- | --------------------- |
| Fix 1 (indices)      | O(n)             | O(n log n)   | No            | Yes                   |
| Fix 2 (slice+concat) | O(n)             | O(n log n)   | No            | No                    |
| Fix 3 (shift)        | O(n²)            | O(n² log n)  | Yes           | No                    |

---

## Verification Strategy 3 — Developing a Critical Eye

Four reviewer-level questions applied to all three fixes:

**Q1: Prove that exactly one slice is non-empty in Fix 2**

The main loop exits when `i === left.length` OR `j === right.length` — it cannot
exit with both conditions false (that would mean the loop continues). Therefore:

- If left exhausted: `left.slice(i) = []`, `right.slice(j)` = remaining elements
- If right exhausted: `right.slice(j) = []`, `left.slice(i)` = remaining elements
- If both exhausted simultaneously: both slices return `[]` — still correct

`concat` of an empty array is a no-op, so both cases are handled safely.

**Q2: `shift()` complexity breaks Fix 3**

`Array.shift()` is O(n) — removing the first element requires reindexing all
remaining elements. Called `n` times during merge, this makes merge O(n²).
Fix 3 is correct but not scalable for large inputs.

**Q3: Stability requires `<=` not `<`**

With `left[i] < right[j]`: when values are equal, the condition is false → right
element is pushed first → right side jumps ahead of equal left elements → unstable.

With `left[i] <= right[j]`: equal values push left first → original relative order
preserved → stable sort.

Fix 1 and Fix 2 used `<` in the original analysis — both need `<=` for stability.
Fix 3 already used `<=` as written.

**Q4: Are the alternatives strictly bug-proof?**

Fix 2 eliminates the index-management leftover bug, but:

- Still relies on correct `i` and `j` from the main loop
- Wrong comparison logic in the main loop would still produce wrong output
- Extra memory allocations (slice creates copies)

Fix 3 eliminates leftover bugs but introduces:

- O(n²) performance degradation
- Input mutation that corrupts recursive calls in mergeSort

Verdict: both eliminate the _specific_ class of bug, but neither makes all bugs
impossible. Fix 2 is the best tradeoff.

---

## Final Verified Solution

```javascript
function mergeSort(arr) {
  if (arr.length <= 1) return arr;
  const mid = Math.floor(arr.length / 2);
  const left = mergeSort(arr.slice(0, mid));
  const right = mergeSort(arr.slice(mid));
  return merge(left, right);
}

function merge(left, right) {
  let result = [];
  let i = 0;
  let j = 0;

  while (i < left.length && j < right.length) {
    // <= preserves stability: equal elements from left are picked first,
    // maintaining their original relative order
    if (left[i] <= right[j]) {
      result.push(left[i++]);
    } else {
      result.push(right[j++]);
    }
  }

  // slice + concat eliminates manual leftover loops and the index
  // management bugs they introduce. Exactly one slice will be non-empty.
  return result.concat(left.slice(i)).concat(right.slice(j));
}

module.exports = { mergeSort, merge };
```

**Test results:** 6/6 passing

```
✓ Empty array should return empty array
✓ Single element array should return same array
✓ Already sorted array should return same array
✓ Reverse sorted array should return sorted array
✓ Array with duplicates should return sorted array
✓ Large array should be sorted correctly
```

---

## Reflection

**How did confidence change after verification?**
Starting from finding the bug manually (Strategy 1) meant the fix was understood
before it was applied — not just accepted. Strategy 3 revealed two issues the
initial fix missed: the stability problem (`<` vs `<=`) and the mutation risk in
Fix 3. Confidence increased because the solution was proven, not assumed.

**Which aspect required the most scrutiny?**
The stability question. The original bug was obvious once traced. The stability
issue was invisible — the code ran correctly for numeric arrays but would silently
produce wrong relative ordering for equal elements in object arrays.

**Which verification technique was most valuable?**
Strategy 3 — the reviewer questions. Finding the bug (S1) and seeing alternatives
(S2) are useful, but the critical questions in S3 were the only step that caught
the `<=` fix and the mutation problem. Both would have survived a normal code review.

---

## Key Learnings

1. **The comment was the real noise.** The misleading comment pointed at correct
   behaviour, not the bug. Always read code, not comments, when debugging.

2. **Minimal fix vs safe fix are different goals.** The one-character fix is
   correct. The `slice+concat` fix is safe. Choose based on context — in a
   learning codebase, minimal; in production, eliminate the bug class.

3. **Stability is invisible until it matters.** `<` vs `<=` produces identical
   results for primitive numbers. For objects sorted by one field, it silently
   reorders equal elements. Always use `<=` in merge sort.

4. **Readability and correctness are not the same as performance.** Fix 3 reads
   better than Fix 2 but is categorically worse for large inputs. Never choose
   an O(n²) implementation of an O(n) operation.

5. **Mutation in recursive algorithms is dangerous.** Fix 3's `shift()` destroys
   input arrays. In the recursive context of `mergeSort`, this would corrupt
   parent calls. Always check whether a fix mutates shared state.

---

## Prompts Used

### Strategy 1 — Collaborative Solution Verification (self-directed)

No AI prompt used for this strategy. The goal was to find the bug independently
before seeing any fix. The three questions answered were:

1. Why is the comment misleading — what is the real bug?
2. Trace `merge([1,3], [2,4])` manually — what does the buggy code return?
3. Which input causes an infinite loop and why?

This strategy establishes ground truth before AI involvement so any AI fix
can be evaluated against your own understanding rather than accepted blindly.

---

### Strategy 2 — Learning Through Alternative Approaches

```
I have a buggy JavaScript merge sort implementation.
Here is the merge function:

function merge(left, right) {
  let result = [];
  let i = 0;
  let j = 0;

  while (i < left.length && j < right.length) {
    if (left[i] < right[j]) {
      result.push(left[i]);
      i++;
    } else {
      result.push(right[j]);
      j++;
    }
  }

  while (i < left.length) {
    result.push(left[i]);
    j++; // Bug: incrementing j instead of i
  }

  while (j < right.length) {
    result.push(right[j]);
    j++;
  }

  return result;
}

The bug causes an infinite loop when the left array has remaining
elements after the main while loop exits.

Could you:
1. Fix the bug with the minimal change needed
2. Show an alternative implementation that makes this class of
   bug impossible
3. Explain what makes the alternative safer
```

**What this prompt produces:**

- Minimal one-character fix
- Alternative implementation eliminating the bug class
- Explanation of tradeoffs between approaches

**Why this prompt works:**
Asking for multiple approaches forces comparison rather than blind acceptance.
Asking for "what makes it safer" surfaces the underlying design reasoning,
not just the syntax change.

---

### Strategy 3 — Developing a Critical Eye (self-directed)

No AI prompt used for this strategy. The goal was to scrutinise all three
fixes with reviewer-level questions before accepting any of them:

1. Prove that exactly one `slice` is non-empty in Fix 2
2. What is the time complexity of `Array.shift()` — does Fix 3 degrade performance?
3. Does `<` vs `<=` affect sort stability — which is correct and why?
4. Do Fix 2 and Fix 3 make bugs strictly impossible, or just less likely?

**Why these questions matter:**

- Q1 verifies Fix 2 is provably correct, not just intuitively correct
- Q2 catches a performance regression that looks like an improvement
- Q3 catches a stability bug invisible in numeric-only tests
- Q4 prevents overconfidence in "safe" alternatives

---

### How to Reuse These Prompts

The Strategy 2 prompt template works for any buggy algorithm:

```
I have a buggy [language] implementation of [algorithm].
Here is the [function/method] with the problem:

[paste code]

The bug causes [describe observed symptom].

Could you:
1. Fix the bug with the minimal change needed
2. Show an alternative implementation that makes this class of
   bug impossible
3. Explain what makes the alternative safer
```

The Strategy 3 questions should always include:

- A correctness proof for the key operation (not just "it looks right")
- A complexity analysis (does the fix introduce a performance regression?)
- An edge case check (empty input, duplicates, already sorted, single element)
- A mutation check (does the fix modify input data unexpectedly?)
