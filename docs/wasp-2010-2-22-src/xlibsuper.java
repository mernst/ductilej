class MyList<E> extends ArrayList<E> {

    public Object at (Object i) {
        return super.get(RT.cast(int.class, i));
    }

    public void append (Object elem) {
        super.add(
            (E)RT.cast(Object.class, elem));
    }

    public void append (Object elem) {
        super.add(
            RT.<E>typeVarCast(
                Object.class, elem));
    }
}
