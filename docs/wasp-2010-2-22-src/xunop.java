Object foo = 0, bar = 0;

// non-side-effecting op
foo = RT.unop("-", foo);

// prefix increment
foo = (bar = RT.binop("+", bar, 1));

// postfix increment
foo = RT.binop(
    "-",
    (bar = RT.binop("+", bar, 1)), 1);

// same for decrement
