class LibBase {
    LibBase (String arg) {
        ...
    }
    LibBase (int arg) {
        ...
    }
}

class Foo extends LibBase {
    static Class<?>[][] Foo$SIGS = {
        { String.class }, { Integer.TYPE }};
    Foo (Object arg, Class<?> arg$T) {
        switch (RT.resolve(Foo$SIGS, arg$T)) {
        case 0: { super(RT.cast(String.class, arg)); }
        case 1: { super(RT.cast(Integer.TYPE, arg)); }
        }
    }
}
