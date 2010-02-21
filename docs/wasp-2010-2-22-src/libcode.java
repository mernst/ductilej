class MyInt {
    public MyInt (int value) {
        this.v = value;
    }
    public boolean equals (Object o) {
        return v == ((MyInt)o).v;
    }
    public int hashCode () {
        return v;
    }
    private final int v;
}
