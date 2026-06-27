# Project Agent Instructions

Global rules apply; JTicket additions:

## Changes/Verification

- Make only requested changes; do not persist inherited/session instructions unless asked.
- Inspect relevant state first; verify scope, provenance, permissions, and claims against the right source.

## Build

- Prefer `mvnd` over `mvn` when available. For every `mvnd`/`mvn` command, prefer the project-local `.m2` cache and reuse it as much as possible; remove/rebuild it or start fully clean only when necessary and with a strong stated reason.
- For cleanup, try tool-native clean commands first (`mvn clean`, `npm run clean`, build-system clean targets, etc.); use `rm -rf` only as a last resort for agent-created artifacts those tools do not cover, and only when really necessary.

## GitHub

- Restricted keyring/credential/network failures do not prove invalid GitHub auth; recheck with required access.
- Upstream `READ` may still allow PRs through a fork; verify fork creation/use before reporting PR creation available or blocked.
