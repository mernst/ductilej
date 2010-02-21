class MyInt {
    public MyInt (Object value) {
        this.v = value;
    }
    public boolean equals (Object o) {
        return RT.cast(Boolean.class,
          RT.binop("==", v,
            RT.select("v", o))));
    }
    public int hashCode () {
        return RT.cast(Integer.class, v);
    }
    private final Object v;
}
