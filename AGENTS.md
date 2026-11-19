# devpilot repository rules

## Architecture

- `devpilot-center` is the Spring Boot control plane. It owns business state and
  never shells out to arbitrary commands.
- `devpilot-gateway` is the only process that executes Agent commands. It must
  keep the command allowlist explicit and must never accept arbitrary paths or
  shell strings from callers.
- Business modules (`project`, `bug`, `task`, `verification`) must not call each
  other's service implementations across module boundaries when a REST/API
  contract is more appropriate. Shared read contracts live in
  `devpilot-common`.
- `devpilot-executor` implements the `AgentExecutor` abstraction. Local and
  remote modes differ only in endpoint/credentials, not business logic.
- `devpilot-repository` owns Git and Git-SVN operations. Persist metadata in the
  center; do not store business state in a workspace.

## Conventions

- Java 17, Spring Boot 3.5.x, Maven multi-module.
- Package root: `com.devpilot`.
- DTOs crossing module boundaries are Java records in `devpilot-common`.
- Database tables use the `dev_` prefix and are defined in
  `devpilot-center/src/main/resources/db/migration`.
- All state changes go through the task state machine and emit an execution
  event. Agents cannot mutate task state directly.
- Never log tokens, credentials, or raw SVN/Git URLs containing passwords.
- Tests use H2 in PostgreSQL compatibility mode; production uses PostgreSQL.
- Do not weaken or delete tests to make a build pass.

## Module ownership during parallel work

| Module | Owner |
|---|---|
| `devpilot-project`, `devpilot-repository` | project-workspace worker |
| `devpilot-bug`, `devpilot-ai` | bug-analysis worker |
| `devpilot-task`, `devpilot-verification` | task-verification worker |
| `devpilot-executor`, `devpilot-gateway` | executor-gateway worker |
| `devpilot-center`, migrations, docs, deployment | lead |

Only edit files inside your owned modules unless the lead explicitly asks you
to touch a shared contract.
