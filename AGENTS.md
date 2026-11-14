# NeitzelLib

Read README.md and the affected module's README before changes. Use the declared
Java/Maven baseline and the enclosing workspace's checkout/landing policy.

Prefer Lombok for mechanical getters/setters, constructors and logger declarations
when changing Java code. Use focused annotations that preserve the existing API,
defaults, injection annotations, identity and equality. Keep validation, defensive
copies, JavaFX property operations and lifecycle logic explicit; retain records
and concise interface adapters. `@Data`/`@Value` require an intentional full value
contract, including safe `toString` output. Keep Lombok compile-time-only and
preserve every annotation processor in profile-specific configurations.

Run affected module tests, including FXML/reflection and generated metadata when
models change. Inspect Surefire XML: the parent currently ignores test failures.
Do not install changed runtime artifacts over a Designer-pinned baseline without
explicitly qualifying and recording the replacement.
