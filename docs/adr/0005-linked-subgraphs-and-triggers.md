# ADR 0005: Linked subgraphs and long-lived triggers (proposal)

Status: **proposed**, nothing here is implemented. Source: items 6 and 10 of the feedback an app team sent after using `0.1.0-alpha02`
(a workflow app built on `kompound-graph`). The other items of that list are done (see CHANGELOG, "Unreleased").
Builds on ADR 0004 (model, subgraphs, engine, traces).

Both features change the model or the engine's execution model, so they are written down first. Each section says what exists today,
what is missing, the proposed shape, and the questions that need an answer before code.

## 1. Linked subgraphs ("this node is workflow X")

### What exists
`Subgraphs.create` wraps nodes of the *same* flat `Graph`; the subgraph's content is a copy. Reuse of a saved graph in another means
pasting a copy. The app that reported this wrote `expandWorkflows`: a `workflow.ref` node kind that is rewritten into a real subgraph
just before `engine.update`, using the public `Subgraphs.inputBoundary(subgraph, port)` / `outputBoundary(...)` ids. It works and is the
shape this ADR proposes to make first class.

### Proposal
- **A link node**: kind `Subgraphs.LinkKind = "subgraph.link"`, `data = SubgraphLink(ref: String, version: String? = null)`. Its ports mirror
  the target document's interface (the input and output boundary nodes of the document's top level). The editor draws it as one node
  (with an "open" action that loads the target), exactly like a collapsed subgraph.
- **A resolver the app owns**: `fun interface GraphResolver { fun resolve(ref: String): ResolvedGraph? }` where `ResolvedGraph(graph, ports)`.
  The library never knows where documents live (files, a database, the network). A `suspend` variant for I/O is a thin wrapper that
  resolves everything first and hands the engine a finished map.
- **Expansion is a pure function**: `LinkedSubgraphs.expand(graph, resolver): ExpandedGraph`. It replaces every link node by a subgraph node
  plus boundary nodes plus the target's nodes, ids prefixed with the link path (`"<link>::<node>"`, nested links recurse, depth limit
  and cycle detection that fail with a named error instead of looping). `ExpandedGraph` also carries `origin: Map<NodeId, List<NodeId>>`
  so traces, status marks and the inspector can say "node `up` inside link `Upload`" instead of showing the generated id (this is what
  `nodeLabel` in `KExecutionList` and `KNodeInspector` is for).
- **Engine**: no change needed beyond using the expanded graph: `engine.update(LinkedSubgraphs.expand(state.graph, resolver).graph)`. A
  convenience `rememberGraphEngine(state, runners, links = resolver)` does that on every edit and when a target document changes
  (`resolver` can expose a change notification).
- **Port drift**: when a target's interface changes, `LinkedSubgraphs.syncPorts(graph, resolver): GraphCommand?` returns one command that
  updates the link nodes' ports (`GraphCommand.UpdateNodePorts`, added for this) and keeps the wires on ports that still exist.
- **Saving**: link nodes serialize as `{kind, data: {ref, version}}`. The target's content is never copied into the file.
- **Pins and test runs** work on the expanded ids; the pin of a node inside a link is stored on the link node (`pin` map keyed by the
  inner path) so it survives re-expansion. (Open question 3.)

### Open questions
1. Where does a document declare its interface: designated boundary nodes at its root, or a separate list? Proposal: boundary nodes at
   the root scope, the same node kinds subgraphs already use, so a document can be used standalone or as a link target.
2. Version pinning: `ref@hash` resolved by the app, or only "latest"? Proposal: optional `version` string passed to the resolver, no
   semantics in the library.
3. Pins inside links: keyed by inner path on the link node (survives re-expansion, but two links to the same document pin separately,
   which is what a user expects).
4. Editing the target from inside a link: out of scope for the library (the app opens the target document); the editor shows the
   link node read-only inside.

### Slices
(a) `LinkKind`, `SubgraphLink`, `LinkedSubgraphs.expand` with origin map and cycle/depth errors, tests; (b) `syncPorts`; (c) the
editor's link node (`KLinkNode`) and "open"; (d) `rememberGraphEngine(links = ...)`; (e) demo.

## 2. Long-lived triggers (schedule, webhook, file watch)

### What exists
A runner that never returns and calls `ctx.emit` in a loop already behaves like a trigger: the node stays `Running`, downstream nodes with
`Latest` or `Each` inputs process each value, and cancelling the node (edit, `stop()`) cancels the loop. Not covered:

1. **Executions**: everything a trigger emits belongs to one never-ending `Execution`. A webhook hit should be its own run in the list.
2. **Lifecycle**: a trigger should listen while the engine is active and be left alone by edits that do not change it. (Edits that
   change its `data` already restart it; a `Declined`/`stop()` already cancels it.)
3. **Idle**: a graph with a live trigger is never `isBusy == false`, so `awaitIdle` hangs. There is no notion of "listening".
4. **Back pressure and memory**: signals are kept in memory until the node is reset; a trigger that fires for days would grow without
   bound. There is no queue policy when events arrive faster than downstream nodes finish.

### Proposal
- **Trigger runners**: `fun interface TriggerRunner { suspend fun listen(ctx: TriggerContext) }` registered like a `NodeRunner`
  (`runners` accepts either). `TriggerContext.fire(outputs: Map<String, Any?>)` starts one **event run**. The node's state is the new
  `NodeRun.Listening` while `listen` is suspended.
- **Event runs**: each `fire` creates an `Execution(trigger = TraceTrigger.Event)` that covers the nodes downstream of that trigger. Signals
  carry the event id, and every downstream node keeps its feeds and cursors per event, so two overlapping events never mix. Nodes that
  are not downstream of the trigger are untouched. The inspector and `KExecutionList` need no change: they already list executions
  (the trigger filter gets an "event" chip).
- **Queue policy per trigger**: `EventPolicy` = `Queue(max)` (default 64, oldest dropped and logged), `Drop` (ignore events while one is
  running), `Latest` (cancel the running event for the new one), plus `maxConcurrent`.
- **Listening and idle**: `engine.isListening` (observable) is true while any trigger is registered and active. `isBusy` keeps meaning "a
  runner is working", so `awaitIdle()` returns between events; `awaitIdle(includeListeners = false)` is the default and the headless
  "run until finished" for graphs without triggers stays unchanged.
- **Bounded history**: event executions follow `TraceOptions.maxExecutions`; per-event feeds are dropped when the event run ends, so memory
  does not grow with the number of events. (This also bounds the plain streaming case: feeds of a finished run are released.)
- **Gate**: `beforeRun` is asked once when a trigger starts listening (`TraceTrigger.Auto`/`Manual`) and per event for the nodes it
  starts, so an app can refuse to arm a trigger on an automatic run.

### Open questions
1. Per-event isolation touches the core of the engine (`Rt`, feeds, cursors), so it is the expensive part. Alternative: keep one set of
   feeds and mark values with the event id, accepting that two events can interleave in a `Latest` node. Proposal: isolate per event;
   it is the only model whose traces make sense to a user.
2. Pins on a trigger: pinning a trigger's output means "run downstream with this event on start". Proposal: allowed, fires one event
   when the engine starts.
3. Subgraphs containing triggers: the trigger belongs to the expanded graph, so a linked subgraph with a trigger arms it once per link.

### Slices
(a) event ids on signals plus per-event runtime state (no trigger API yet; a test fires events from a plain runner); (b) `TriggerRunner`,
`Listening`, `Event` executions; (c) `EventPolicy`; (d) `isListening`, release of per-event feeds; (e) demo: a clock trigger and a
"webhook" simulated with a button.

## 3. Order

Linked subgraphs first (smaller, no engine change, unblocks reuse), then triggers. Both land behind the `Experimental` status of
`kompound-graph`; the public names above are proposals.
