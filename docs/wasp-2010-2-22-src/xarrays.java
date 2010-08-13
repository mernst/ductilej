Object length = ...;
Object strings =
  new String[RT.asInt(length)];

Object bar = RT.newArrayOf(
  String.class, "a", "b", "c");

Object foo =
  RT.newArrayOf(int[].class,
    RT.newArrayOf(int.class, 1, 2),
    RT.newArrayOf(int.class, 3, 4));

Object elem = RT.atIndex(bar, 2);
