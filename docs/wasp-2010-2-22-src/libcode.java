class MyInt {
    final int v;
    MyInt (int value) {
        v = value;
    }

    public boolean equals (Object o) {
        return v == ((MyInt)o).v;
    }

    public int hashCode () {
        return v;
    }
}
