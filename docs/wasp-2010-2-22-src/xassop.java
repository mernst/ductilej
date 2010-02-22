// simple assignment
Object foo = 0;
foo = RT.binop("+", foo, 5);

// array assignment
Object vec = RT.newArrayOf(int.class, 5);
RT.assignOpAt(vec, 0, "+", 5);

// field assignment
RT.assignOp(foo, "bar", "-", 5);

// side-effecting lhs
RT.assignOp(
  RT.invoke(this, "foo"), "bar", "/", 5)
