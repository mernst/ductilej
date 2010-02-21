Object in = ...;
try {
    try {
        Object c = RT.invoke("read", in);
        // ...
    } catch (WrappedException _w_e) {
        if (_w_e.getCause() instanceof IOException) {
            throw (IOException)_w_e.getCause();
        } else {
            throw _w_e;
        }
    }
} catch (IOException e) {
    ...
}
