class MyInt {
    Object v;
    MyInt (Object value) {
        v = value;
    }

    public boolean equals (Object o) {
        return RT.cast(Boolean.class,
            RT.binop("==", v,
                RT.select("v", o)));
    }

    public int hashCode () {
        return RT.cast(Integer.class, v);
    }
}
