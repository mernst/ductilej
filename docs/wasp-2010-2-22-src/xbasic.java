Object intval = 1;
Object strval = "Hello";

class Foo {
    Object value;
    Object add (Object a, Object b) {
        return RT.binop("+", a, b);
    }
}

Object foo = RT.newInstance(Foo.class);
Object sum = RT.invoke("add", foo, 2, 3);
Object fval = RT.select("value", foo)
