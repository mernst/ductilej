class Foo {
    static Class<?>[][] Foo$SIGS = {
        { String.class }, { Integer.TYPE }};
    Foo (Object arg, Class<?> arg$T) {
        switch (RT.resolve(Foo$SIGS, arg$T)) {
        case 0: { ...String... }
        case 1: { ...int... }
        }
    }
}

Object sarg = "Hello";
RT.newInstance(
    Foo.class, sarg, String.class);

Object iarg = 5;
RT.newInstance(
    Foo.class, iarg, Integer.TYPE);
