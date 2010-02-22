class Adder {
    int sum (int a, int b) {
        return a + b;
    }
}

class Badder extends Adder {
    int sum (int a, int b) {
        int s = super.sum(a, b);
        return s + 1;
    }
}
