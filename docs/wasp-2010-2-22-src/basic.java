int intval = 1;
String strval = "Hello";

class Foo {
    int value;
    int add (int a, int b) {
        return a + b;
    }
}

Foo foo = new Foo();
int sum = foo.add(2, 3);
int fval = foo.value;
