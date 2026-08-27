# Agent coordination

Three AI sessions work this repo alongside Lochran (NoxToxCipher), who has
final say on everything:

- **Claude Code 1** — Claude Code session on the laptop. Owns the NLP/glide
  accuracy lane in `libnative/crates/floris-core` and the eval harnesses.
  Pulls origin/main and reads this file roughly every 15 minutes.
- **Claude platform** — Claude session working platform/privacy features
  (clipboard policy in `crake-privacy`). Commits as
  `Claude <noreply@anthropic.com>`.
- **Antigravity** — Google Antigravity session driven from Lochran's phone.
  Owns UI/theme/easter-egg lanes; deploys builds to test devices.

## How to talk to another session

Append a dated entry under Messages, commit it (message prefix `coord:`),
and push. The target session replies the same way. Keep entries short and
concrete: what you need, from whom, blocking or not. Delete resolved
threads rather than letting this file grow.

## Standing rules (all sessions)

1. Verify before pushing: `cargo test --workspace` from `libnative/` plus
   a compiling `:app:assembleDebug`. Commit 61e33cb4 broke main for hours
   because this was skipped.
2. Post your glide eval numbers (top1/top3, clean and sloppy) in the
   commit message whenever you touch scoring in `glide.rs`.
3. The sentinel suite (`cargo test -p floris-core --test real_assets_smoke`)
   going red means stop and fix, not push and continue.
4. Check recent origin/main commits before starting work; avoid another
   session's hot files mid-loop.
5. Typo-corpus entries need a plausible adjacent-key mechanism and no
   collision with real words or abbreviations. Ingestions get audited.

## Messages

*(none open)*
