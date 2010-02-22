Object array = ...;
for (Object i : RT.asIterable(array)) {
    ...
}

Object list = ...;
for (Object i : RT.asIterable(list)) {
    ...
}

// RT.asIterable: Object -> Iterable
// wraps arrays with Arrays.asList()
