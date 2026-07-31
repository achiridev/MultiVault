# MultiVault — Agent Guide

## Identity
Java 21 / Spring Boot 4.1.0 / PostgreSQL / Maven. See [docs/](docs/INDEX.md) for full context.

## Principles
- **Code is truth.** If docs contradict code, fix docs. Never vice versa.
- **Zero comments** in code. Naming and structure must be self-explanatory.
- **DB logic lives in DB.** Triggers, constraints, partial indexes are not duplicated in app code.
- **Migrations are additive.** Never modify an applied migration. New change = new `V2__*.sql`.

## Context Loading Protocol (MANDATORY)
1. Read `docs/INDEX.md` — find which docs are relevant
2. Read only those docs + relevant source files (schemas, code)
3. If you need more context, read specific docs — never batch-read all of `docs/`
4. Implement
5. Update only the docs you read (if affected)

## Task Lifecycle

### Before
- `git pull --rebase`
- Branch: `git checkout -b tipo/descripcion` — tipo: `feat/`, `fix/`, `refactor/`, `docs/`, `chore/`
- Load context (see protocol above)
- If task changes architecture, API, or data model → write ADR first

### During
- Prefer small, focused commits over one large change
- Preserve backwards compatibility unless ADR specifies otherwise
- `mvn compile` after each meaningful change — don't accumulate broken code
- Never hardcode or log secrets (passwords, tokens, keys, hashes)
- Every change should be reversible

### After
1. `mvn test` — all tests must pass
2. If tests fail → diagnose the root cause, fix, re-run. Don't delete tests unless they're wrong
3. Update only the docs affected by your change
4. Commit: `git add -p` (review each change), then `git commit -m "tipo: mensaje"`
5. Push

## Documentation Rules
- **Update docs** when: new feature, behavior change, removed feature, new ADR
- **Skip docs** for: refactors without behavior change, bug fixes, internal cleanup
- Docs are additive. Don't delete information unless it's incorrect
- If a doc doesn't exist for a new area, create it following `docs/99-Templates/`

## ADR Rules
- **CREATE an ADR** (`docs/06-Decisiones/ADR-0002.md`) when: new dependency, schema change, auth strategy change, multi-tenant approach change, API deprecation
- **SKIP ADR** for: bug fixes, internal refactors, test additions, config changes, routine additions

## Error Recovery
| Scenario | Action |
|---|---|
| Test fails | Fix the code, not the test (unless the test itself is wrong) |
| Migration conflict | Rollback, create new `V2__*.sql`, re-run |
| Build breaks | Fix smallest scope, `mvn compile`, iterate |
| Stuck >5 min | Document the blocker + what you tried. Ask for guidance |

## Git Conventions
- Branches: `feat/`, `fix/`, `refactor/`, `docs/`, `chore/`
- Commits: conventional — `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- Never commit directly to `main`
- Squash WIP commits before push if needed
