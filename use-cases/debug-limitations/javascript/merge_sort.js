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
