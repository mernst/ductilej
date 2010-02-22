Object in = ...;
try {
    Object c = RT.invoke("read", in);
    // ...
} catch (IOException e) {
    ...
}
