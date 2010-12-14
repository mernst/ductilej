class MyList<E>
    extends ArrayList<E> {

    public E at (int i) {
        return super.get(i);
    }

    public void append (E elem) {
        super.add(elem);
    }
}
