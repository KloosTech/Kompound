# ADR 0004: Node graph framework (`kompound-graph`)

Status: accepted; P1 implemented (module `kompound-graph`, Experimental)

Reference for the interaction model: node editors in the React Flow / Blender / Unreal / Figma-plugin family
(pannable and zoomable canvas, draggable nodes with typed input and output ports, wires between ports, group and
reroute nodes). Kompound's aim is the same product shape for Compose Multiplatform, built on Kompound's own
components so any Kompound composable can live inside a node.

## 1. Where it fits

A **separate published module**, `tech.kloos.kompound:kompound-graph` (KMP: Android, iOS, desktop, wasm), that depends on
`:kompound` and nothing else. Reasons: apps that only need buttons and fields must not carry a canvas engine; the graph has its
own release cadence while it matures (`Experimental` → `Beta`); it can ship its own demos (`Graph` category) through the normal
`@KompoundDemo` pipeline (ADR 0003 picks the module up with the demos plugin). Package root `tech.kloos.kompound.graph`.

An optional second module, `kompound-graph-serialization` (kotlinx.serialization JSON), keeps the allowlist in COMPONENT_SPEC C-080
intact for the core. A runtime that *evaluates* graphs (dirty propagation, topological execution) is out of scope for the UI
framework; the model exposes what such an engine needs (typed ports, edges, stable ids) and nothing more.

```
kompound-graph
  model/      pure Kotlin, no Compose: ids, Port, GraphNode, Edge, Graph, ConnectionPolicy, commands
  state/      KGraphState (snapshot state + commands + undo/redo), KViewportState, KSelection
  geometry/   world <-> screen transform, port anchors, edge paths (bezier / straight / step), hit testing
  ui/         KNodeGraph (canvas), KNode (wrapper), KPort, KEdge, KMiniMap, KGraphControls
  defaults/   KNodeGraphDefaults, KNodeDefaults, KPortDefaults, KEdgeDefaults (Style factories)
```

## 2. Layers and responsibilities

### 2.1 Model (immutable, serialisable, testable without UI)
- `NodeId`, `PortId`, `EdgeId`: value classes over `String` (stable across sessions, caller-supplied or generated).
- `PortSpec(id, label, direction: In|Out, type: PortType, capacity: One|Many)`; `PortType` is an open interface with a `compatibleWith(other)`
  rule and a colour key, so apps define `Number`, `Text`, `Flow`... themselves.
- `GraphNode(id, kind: NodeKind, position: Offset, size: Size?, ports: List<PortSpec>, data: Any?)`. `data` is the node's payload; the
  framework never inspects it. `kind` selects the composable.
- `Edge(id, from: PortRef(node, port), to: PortRef(node, port))`; edges always run output → input.
- `Graph(nodes, edges)` immutable snapshot with O(1) lookup indices and cheap structural sharing on update.
- `ConnectionPolicy`: `canConnect(graph, from, to): Result` with a reason (type mismatch, capacity, same node, would create a cycle when
  `allowCycles = false`). Pure function, exhaustively unit-tested, also drives the live "can I drop here?" highlighting.

### 2.2 State
- `KGraphState` is the single source of truth: `graph` (snapshot state), `viewport` (offset + zoom), `selection`, `interaction` (idle,
  panning, draggingNodes, connecting(from, pointer), marquee).
- **All edits are commands** (`MoveNodes`, `AddNode`, `RemoveNodes`, `Connect`, `Disconnect`, `UpdateNodeData`, batched in `Transaction`) applied
  through `state.execute(command)`. Each command knows its inverse, so **undo/redo** is a stack of applied commands; a drag is one
  transaction, not 60 moves. Hosts can observe via `onGraphChange(old, new, command)` to persist or sync.
- Controlled or uncontrolled like the other Kompound components: `rememberKGraphState(initial)` or pass your own state holder; a
  saver (`rememberSaveable`) round-trips graph + viewport.

### 2.3 Geometry
- `ViewportTransform` converts world ↔ screen; zoom clamps (0.1 to 4), zooming keeps the point under the cursor or pinch centre fixed.
- **Port anchors** are measured, not computed: each `KPort` reports its centre (in world space, via `onGloballyPositioned` relative to the
  node, plus node position) into a `PortAnchors` registry. Nodes may therefore contain arbitrary content with ports on any row.
- Edge paths: cubic bezier (default, horizontal tangents scaled by distance), straight, orthogonal step; each exposes `hitTest(point, tolerance)` by
  sampling, used for selecting and deleting edges.

### 2.4 UI
```
KNodeGraph(state, modifier, nodeTypes = registry, ... )
 ├─ GridBackground          (draw phase, follows viewport, snaps visually to grid)
 ├─ EdgeLayer               (one Canvas: reads anchors + viewport in the draw phase, never recomposes while panning)
 ├─ NodeLayer               (each node composed once in world space; positioned with offset { } inside one graphicsLayer carrying zoom/pan)
 ├─ InteractionLayer        (pointer input: pan, pinch/scroll zoom, drag, marquee, wiring, edge picking)
 └─ Overlays                (minimap, zoom controls, "add node" menu using KMenu, connection preview)
```
- **Node wrapper**: `KNode(node, title, modifier, style, actions) { content }`. Header (title, status badge, collapse), body slot with
  `NodeScope` helpers:
  ```kotlin
  KNode(node, title = "Math / Add") {
      Input("a", label = "A") { KNumberField(...) }      // port handle on the left; the lambda is the inline editor shown while unconnected
      Input("b", label = "B") { KSlider(...) }
      Output("sum", label = "Sum")                        // port handle on the right
  }
  ```
  Any Kompound (or other) composable can sit in a node; ports line up with the row they belong to.
- **Node types**: `NodeTypeRegistry { type("math.add") { node -> NodeContent } }` maps `NodeKind` to a composable plus default size and
  ports, so the canvas can also render a palette and create nodes from a menu.
- **Styling** follows ADR 0001: `Style` params everywhere, state blocks for `selected`, `hovered`, `dragging`, `error`; ports have `connected`
  and `compatible` / `incompatible` (while a wire is dragged) states; edges expose colour, width, dash and an animated flow option. Tokens only.
- **Interaction**: wheel/pinch zoom, middle-mouse or space+drag or two-finger pan, drag nodes (multi-select drags together), snap to grid, marquee
  select, shift/cmd toggle, drag from an output to an input (or the reverse) with live validation, drop on empty canvas opens the node menu
  filtered by compatible types, `Delete` removes, `Cmd/Ctrl+Z`/`+Shift+Z` undo/redo, `Cmd/Ctrl+C/V/D` copy/paste/duplicate, `F` fits the view.
  Touch gets long-press to start a marquee and large port hit targets (>= 24dp, 48dp touch area on small screens).

### 2.5 Accessibility and keyboard
Nodes and ports are focusable with a visible focus ring; arrow keys move the focused node by the grid step, Enter on an output starts a connection
that Tab/arrow cycles through compatible inputs, Escape cancels. Semantics: nodes expose `role`, name, position as state description; ports expose type
and connection state; an invisible, ordered **edge list** (from, to) makes the wiring readable without sight (COMPONENT_SPEC C-060 to C-068).

### 2.6 Performance (targets: 500 nodes / 1,000 edges at 60 fps on a mid phone)
- Pan and zoom only change a `graphicsLayer` lambda and the edge Canvas draw lambda: **zero recomposition** per frame (C-073).
- Nodes recompose only for their own data; positions are read in `offset { }` (layout phase).
- Edge layer culls edges outside the viewport and simplifies paths at low zoom; the node layer composes only nodes intersecting the viewport plus a margin
  once the graph exceeds a threshold (virtualised via a spatial index, a uniform grid hash).
- Drag moves the selection through state read in layout; the command is committed on release.

## 3. Quality bar

- Pure model and policy: property-style tests (random graphs, random command sequences: `undo(redo(x)) == x`, no dangling edges, policy invariants).
- Geometry: transform round trips, anchors under zoom, bezier hit tests.
- UI tests with the manual clock: pan/zoom, drag one and many nodes, create an edge through the pointer, rejected connection feedback, delete, undo, keyboard
  wiring; desktop screenshot tests for the default styles in light and dark.
- Demos: a calculator graph (number, math, display nodes with real `KSlider` / `KNumberField` content), a flow-chart with groups, a 500-node stress graph;
  the usual "How to use" sample, compiled in CI.

## 4. Phases

| Phase | Content | Status |
|---|---|---|
| **P1 core** | model, `ConnectionPolicy`, commands + undo/redo, `KGraphState`, viewport, `KNodeGraph` with grid, `KNode` + ports, drag nodes, bezier/straight/step edges, wire dragging with validation, edge and node selection, delete, keyboard basics, calculator demo | done |
| **P2 editing** | marquee/multi-select (**done**), copy/paste/duplicate (**done**), snap + guides, minimap, controls, node palette menu, reroute (dot) nodes, edge styles | in progress |
| **P3 structure** | group/comment nodes, collapse, subgraph nodes, auto layout (layered), JSON serialization module, virtualisation | |
| **P4 runtime (optional)** | evaluation engine module (typed values, dirty propagation), execution tracing overlay | |

## 5. Decisions needed

1. **Module** `kompound-graph` as above (recommended) vs. inside `:kompound`.
2. **Layered world-space composition** (all nodes composed in one transformed layer) vs. a lazy layout that composes only visible nodes from the start. Recommended: layered
   first, add culling in P3 once the API has settled, because the node API (scope, ports as slots) does not depend on it.
3. **Serialization** in a separate optional module so the core keeps its dependency allowlist (recommended).
4. **Scope of v1** = P1 + the keyboard and accessibility basics from 2.5 (recommended) rather than shipping P1 without them.

## Consequences
A new published artifact and a `Graph` demo category; a stricter test bar than the leaf components (state machine and property tests); the node scope API becomes
the main public surface and is marked `@KompoundExperimentalApi` until P2 ships.
