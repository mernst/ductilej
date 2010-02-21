Object expr = ...;

if (RT.asBoolean(expr)) { ... }

for ( ; RT.asBoolean(expr); ) { .. }

while (RT.asBoolean(expr)) { ... }

do { ... } while (RT.asBoolean(expr));

Object i = RT.asBoolean(expr) ? 1 : 0;

assert RT.asBoolean(expr);
assert RT.asBoolean(expr) : message;

// RT.asBoolean: Object -> boolean
