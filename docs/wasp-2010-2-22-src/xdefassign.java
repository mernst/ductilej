// locals
Object foo = null; // added initializer
if (RT.binop(">", 4, 2)) {
    foo = 0;
}
RT.invoke("println", System.out, foo);

// final fields
class Foo {
    Object value; // final removed
    Foo () {
        if (RT.binop(">", 4, 2) {
            value = 0;
        }
    }
}
