// locals
int foo;
if (4 > 2) {
    foo = 0;
}
System.out.println(foo);

// final fields
class Foo {
    final int value;
    Foo () {
        if (4 > 2) {
            value = 0;
        }
    }
}
