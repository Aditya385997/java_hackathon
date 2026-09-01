# Implementation Notes

## Approach

(Fill in during the hackathon: what you built, what you cut, and why.)

## Architecture decisions

| Decision | Alternative considered | Why this one |
| --- | --- | --- |
| | | |

## AI-assisted development

- Tool: Claude Code, with conventions pinned in `CLAUDE.md`.
- Workflow: plan first -> one vertical slice at a time -> review diff -> commit.
- Corrections made to AI output: (list them — this is the part reviewers care about)

## Known gaps / what I'd do with more time

- H2 in-memory for development, Postgres + Flyway for production. Dev gets fast startup and
  clean test isolation; prod gets versioned migrations with Hibernate restricted to `validate`.
  Tradeoff: dev data doesn't persist across restarts, which is acceptable and desirable here.