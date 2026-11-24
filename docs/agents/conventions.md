# Code Conventions

Most style is enforced automatically (IDE settings + `AspirePlugin.slnx.DotSettings`). The non-obvious rules:

- The Kotlin compiler flag `-Xcontext-parameters` is enabled — `context(...)` declarations are valid.
- Make new Kotlin declarations `internal` by default: classes, interfaces, objects, and top-level functions and
  properties. Don't add new `public` API.
  If a declaration must be `public` because another Gradle module uses it, mark it `@ApiStatus.Internal`. This
  includes the JVM tests in the root project, which can't see `internal` declarations of `rider` and the other modules.
