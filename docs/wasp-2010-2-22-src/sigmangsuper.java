class LibBase {
    LibBase (String arg) {
    }
    LibBase (int arg) {
    }
}

class Foo extends LibBase {
    Foo (String arg) {
        super(arg);
    }
    Foo (int arg) {
        super(arg);
    }
}
