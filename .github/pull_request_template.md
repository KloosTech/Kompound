## What and why

## New / changed component checklist
(see `docs/COMPONENT_SPEC.md`; delete the section if no component changed)
- [ ] Component justified (§1) and tier chosen
- [ ] Naming/package per §2 (`K` prefix)
- [ ] Styling via `Defaults.style()` and tokens, no hard-coded values (§3)
- [ ] `style: Style` param; state visuals in Style blocks; `clickable` wraps `styleable` (C-016a)
- [ ] Text via `KText`, not M3 `Text` (C-019)
- [ ] API: modifier, slots, state hoisting, stability (§4)
- [ ] All states, keyboard, RTL, font scale (§5, §6)
- [ ] Platform code only via expect/actual with all actuals (§5a)
- [ ] Accessibility semantics + screen-reader check noted (§6)
- [ ] No new dependencies; resources via Compose Resources (§8)
- [ ] `@KompoundDemo` complete and shows all states (§9)
- [ ] KDoc + CHANGELOG (§10)
- [ ] Tests added (override merge, hit area, states, zero recomposition) (§11)
- [ ] Lifecycle status set (§12)
