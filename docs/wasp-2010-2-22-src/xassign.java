// simple assignment
Object foo = 3;

// array element assignment
Object vec = new int[RT.asInt(3)];
RT.assignAt(vec, 1, 42);

// field assignment
RT.assign(bar, "baz", foo);

// static field assignment
RT.assignStatic(SomeClass.class,
                "baz", foo);
