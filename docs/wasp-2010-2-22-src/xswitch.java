// not detyped
static final int CONSTANT = 1;

Object value = ...;
switch (RT.asInt(value)) {
case CONSTANT:
    ...
}

Object value = ...;
switch (RT.asEnum(MyEnum.class, value)) {
    ...
}

// RT.asEnum: Class<E> -> Object -> E
