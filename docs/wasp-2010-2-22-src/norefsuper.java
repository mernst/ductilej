class Adder {
    Object sum (Object a, Object b,
                int a$T, int b$T) {
        return RT.binop("+", a, b);
    }
}

class Badder extends Adder {
    Object sum (Object a, Object b,
                int a$T, int b$T) {
        Object s = super.sum(a, b, 0, 0);
        return RT.binop("+", s, 1);
    }
}
