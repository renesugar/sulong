#include <polyglot.h>

int main() {
  void *obj = polyglot_import("foreign");
  if (polyglot_is_number(obj)) {
    return 42;
  } else {
    return 13;
  }
}
