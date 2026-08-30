# Censor pass registry

| Pass | State | Primary vector | Baseline | Result / loop guard |
|---|---|---|---|---|
| [16](passes/pass-16.md) | inconclusive | Instrumented baseline and scroll-debt audit | earlier build | Never use aggregate raw/applied parity to excuse per-event amplification. |
| [17](passes/pass-17.md) | accepted | Remove lumped alternating scroll debt | 16 | Kept; do not restore debt reconciliation. Later phase-lock lag remained separate. |
| [18](passes/pass-18.md) | inconclusive | Immediate authoritative motion and duplicate audit | 17 | Faster and less behind, but half-height duplicate reacquisition remained. |
| [19](passes/pass-19.md) | rejected | Capture-phase/grace/label polish follow-up | 18 | User: not a meaningful pass. Do not bundle minor polish and call it a speed pass. |
| [20](passes/pass-20.md) | rejected | Continuously streaming separate quality lane | 18/19 | Re-enter only with strict generation commits and demonstrated fast-lane isolation. |
| [21](passes/pass-21.md) | inconclusive | Unreconstructed smoothing experiment | 20 | No preserved pass record; do not infer a baseline from memory. |
| [22a-d](passes/pass-22.md) | rollback-anchor | Polling, motion, smoothing, and rollback series | 20/21 | 22a is the safe rollback; 22c visibly regressed and must not be revived unchanged. |
| [23](passes/pass-23.md) | candidate | Cancel/preempt stale quality and fence commits | 22a/22d | Frozen upload is unvalidated; later source hardening belongs to Pass24. |
| [24](passes/pass-24.md) | partial-success | Fast-first startup plus a deadline-aware single inference gate | 22a/22d | Retain reduced freezing/disappearance; its asynchronous mid-scroll placement is not release quality. |
| [25](passes/pass-25.md) | candidate | One content-space camera for every render source | 24 | Emulator proves timeline/world ownership; Pixel and Chrome-vs-X gates must separate input latency from camera response. |

## Current anchors

- Safe installed rollback: Pass22a lineage.
- Frozen comparison APK: Pass22d, SHA-256
  `B680B4A35A2409DB6993FA189934885F0C9F408CF01846661282DBAAFC5A6442`.
- Frozen uploaded candidate: Pass23, SHA-256
  `F974FDF148C3C36D48E6490F8562B31B3E69CB76D47CAC37A1F38EECCB842344`.
- Current worktree: Pass25 candidate. It must not overwrite the frozen Pass22d, Pass23, or Pass24
  artifacts.
