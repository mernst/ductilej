class LibBase {
    LibBase (String arg) {
    }
    LibBase (int arg) {
    }
}

class Foo extends LibBase {
    Foo (Object arg, String arg$T) {
        super(RT.cast(String.class, arg));
    }
    Foo (Object arg, int arg$T) {
        super(RT.cast(Integer.TYPE, arg));
    }
}
