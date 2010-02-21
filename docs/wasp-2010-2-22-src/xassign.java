// simple assignment
Object foo = 3;

// array element assignment
Object vec = RT.newArray(int.class, 3);
RT.assignAt(vec, 1, 2);

// field assignment
RT.assign(bar, "baz", foo);

// static field assignment
RT.assignStatic(SomeClass.class,
                "baz", foo);
