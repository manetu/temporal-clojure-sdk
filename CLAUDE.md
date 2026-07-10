# Type Hints

`*warn-on-reflection*` is enabled project-wide (`:global-vars` in
`project.clj`), and CI fails the build on any reflection warning in `src`
(see `.circleci/config.yml`). Type hints are therefore mandatory wherever
reflective interop would otherwise occur — but their *placement* follows a
convention:

- **Hint at the binding site**, not the use/call site: function parameter
  vectors, `let` bindings, and `def`/var tags. Prefer:
  ```clojure
  (defn ->byte-string [^bytes value]
    (ByteString/copyFrom value))
  ```
  over:
  ```clojure
  (defn ->byte-string [value]
    (ByteString/copyFrom ^bytes value))
  ```

- **Don't re-hint an already-hinted local** at its use site — the tag
  propagates through the scope, including into `reify`/`fn` closures that
  close over it.

- **Return-type hints for constructor-style fns:** when a `defn`/`def`
  reliably returns one concrete type (factories, `*->` builders, `create`
  fns), tag the return instead of re-hinting the result at every call site:
  ```clojure
  (defn create
    (^TestWorkflowEnvironment []
     (create {}))
    (^TestWorkflowEnvironment [options]
     (TestWorkflowEnvironment/newInstance (test-env-options-> options))))
  ```
  The tag propagates to every caller's binding via the var's arglist
  metadata, so callers need no re-hint. This does *not* help when the value
  comes back through a keyword lookup (`(:worker replayer)` always types as
  `Object`) or a `clojure.core` fn (`cast`, `ex-cause`, `vec`) — only hint
  the return when the interop boundary is our own fn. See
  `temporal.testing.env/create`, `temporal.testing.replayer/create`,
  `temporal.converter.payload`, `temporal.internal.utils/namify`.

- **Anonymous fns:** when a hint is needed, prefer `(fn [^T x] ...)` over
  `#(... ^T % ...)` so the hint lands on a parameter rather than a use-site
  `%`.

- **Branch-narrowed types:** if a local is only known to be a given type
  inside one branch of a conditional, introduce an inner `let` binding for
  the narrowed type in that branch rather than hinting the call:
  ```clojure
  (if (instance? Promise e)
    (let [^Promise p e] (.getFailure p))
    e)
  ```

- **Accepted exception — uniform builder-threading maps:** code that
  threads a single builder through a map of setter fns (e.g.
  `temporal.client.options`) may hint the builder at the use site
  (`^Builder %1`) across every entry. Consistency within the map matters
  more than moving the hint to a per-entry binding.

- **Accepted exception — `extend-protocol`/`extend-type` array params:**
  a primitive-array type hint (e.g. `^bytes`) on a protocol method's
  parameter does not resolve overloads inside the method body — the
  macro's expansion loses the array hint, so a call like
  `(ByteString/copyFrom value)` stays reflective even though the exact
  same hint works on a plain `defn`. Hint at the call site instead in
  these bodies (see `temporal.converter.byte-string`), and leave a
  comment noting why.

## Reflection: what to trust

`lein check` (with project-wide `*warn-on-reflection*`) is the authoritative
reflection signal — it's what CI greps for `"Reflection warning"` and fails
the build on. `lein cloverage` also prints reflection warnings, but many are
**false positives**: cloverage's coverage instrumentation rewrites every
top-level form, which strips inline `^Type` hints and breaks the local type
inference that flows through `doto`/`cond->`/`..`/`let`. The compiler then
re-emits reflection warnings against synthetic locations. Tell-tale signs of
a cloverage artifact: a location like `some_file.clj:1:6432` (column far past
the end of line 1, the `ns` form), or a warning pointing at a `p/then`/
`extend-protocol` form head rather than an actual interop call. Don't chase
these by adding call-site hints `lein check` doesn't require — verify against
`lein check` first.

Also note: `org.clojure/tools.analyzer.jvm` is pinned as a direct dependency
(rather than left to resolve transitively through `core.async`) because
`core.async`'s `go` macro compiles blocks through it, and versions before
1.3.3 have two genuine (upstream, unhinted) reflection sites in their own
source (`Character/isDigit` in `jvm.clj`/`utils.clj`). Since
`*warn-on-reflection*` is bound for the whole compile — including on-demand
compilation of source-only dependency jars — those upstream warnings surface
in `lein check` too and would trip the CI gate. Don't downgrade this pin
without re-running `lein check` clean.
