# ADR 0007: Structured (JSON) values on ports, fields and templates (proposal)

Status: **implemented** (see "Progress" at the end for where it differs). Source: a feature request from the workflow app team (tested against `0.1.0-alpha07`,
entries 33, 34, 37, 38 and 39 of their notes). Builds on ADR 0004 (model, engine), ADR 0005 (linked subgraphs, triggers; implemented
before this one) and the JSON helpers and `KCombobox` / `KCode(diagnostics)` that came with 0.2.

## Problem

When a node's output is JSON, the next node should see its fields and let the user use any of them in any of its settings, with the
editor helping, and without each app writing the plumbing. Today an app must remember the last JSON of every port, list paths, expand
`{{placeholders}}` per node kind, and build field pickers itself.

The three things the feature needs already belong to the framework: **ports** (where a value arrives), the **engine** (it holds every
value produced) and the **editor** (it draws bodies and wires). The app owns only what a node's settings mean.

## Decisions

### 1. Typed JSON ports (R1)
- `PortType.json(schema: JsonValue? = null)` returns a `JsonPortType` (`id = "json"`, `schema`). The schema is the subset
  `JsonSchema.validate` understands; unknown keywords are kept, not enforced.
- Connections between `json` and `any` (and `json` and `json`) stay allowed. When the source schema cannot satisfy the input schema the
  wire gets a **warning** (a `KNodeGraph` hook `wireWarning: (Edge) -> String?` computed by `PortTypes.check`), never a block.
- `GraphJson` saves the schema next to the port (`"schema"`), round-trips it, and keeps unknown keywords. Ports without a schema save
  exactly as today.

### 2. Where the shape comes from (R2), in this order
1. **Declared from data**: `GraphEngine(schemaFor = { node -> mapOf(PortId("out") to schema) })`. kompound-graph has no node type class (that is
   the app's), so the hook is a function like `signalMode`. It is re-evaluated whenever a node's data changes and is not stored in the
   graph (no undo step, no staleness).
2. **Observed**: the engine keeps the last JSON value of every JSON output port as that port's *sample* (observable map; size
   capped, default 64 KB, larger samples are cut to their shape with example values; never for `secret` ports).
3. **Unknown**: `fieldsOf` returns an empty list; the editor says so and still takes typed paths.

A sample changing is not a relevant change: no invalidation, no re-run, not in undo.

### 3. Persistence of samples
A side object, not the graph file: `interface SampleStore { fun load(): Map<PortRef, JsonValue>; fun save(samples: Map<PortRef, JsonValue>) }`
with an in-memory default; the app saves it where it wants. `GraphEngine(samples = store)` loads on creation and saves (debounced) when a
sample changes. `GraphJson(samples = true)` is an opt-in that writes them into the file for apps that want one file. Reason: graph
files should not grow and carry possibly personal data by default.

### 4. The query API (R3)
```kotlin
class FieldInfo(val path: String, val type: String, val example: JsonValue?)   // path syntax = JsonValue.at
engine.fieldsOf(nodeId, inputPort): List<FieldInfo>      // observable
engine.sampleOf(portRef): JsonValue?
```
It lives on the engine (it holds the graph, the samples and `schemaFor`). It follows the wire (through subgraphs and links), then
applies the order above. Arrays contribute the fields of their first item behind `[*]` (`tasks[*].title`); limits: depth 6, 200
fields. A `SignalMode.Each` stream carries items, so its fields are the item's fields. `secret` examples are masked.

### 5. Templates (R4), in `:kompound` (`tech.kloos.kompound.json`)
- `Template.render(text, scope)`, `Template.names(text)`, `Template.parse(text)` (a list of literal and placeholder parts with
  offsets, used by the editor).
- Syntax `{{path}}` with the `JsonValue.at` path syntax, whitespace allowed, `\{{` is a literal `{{`. Filters after `|`: `json` (a JSON
  string literal body, correctly escaped), `url` (percent-encoded), `default:text`. No expressions, no conditions.
- `TemplateScope` is a list of named sources. In a node: its JSON inputs by port id (`{{in.title}}`), bare `{{title}}` when exactly one
  JSON input is connected; then app scopes (variables, environment) as further `TemplateScope`s.
- A missing field throws `MissingFieldException(node, port, path)`; the engine reports it like any runner failure.
- In a runner: `ctx.render(text, extraScopes)` and `ctx.inputs.json("in")`.

### 6. Editor components (R5), in `:kompound` with a small provider interface so they do not depend on the graph
- `KTemplateField` / `KTemplateArea`: a `KTextField` / `KTextArea` that knows templates. Typing `{{` opens a `KCombobox`-style list of
  fields (type and example value); placeholders are highlighted; unknown ones get a `KCodeDiagnostic` and message before the run.
  `fields: () -> List<FieldInfo>` is passed in; `kompound-graph` offers `KNodeScope.TemplateField(value, onChange, from = "in")` that wires
  it to `engine.fieldsOf`.
- `KFieldPicker(fields, selected, onPick)`: a dropdown of paths for settings that hold a single path.
- `KFieldMapper(fields, parameters: List<ParamSpec>, bindings, onChange)`: rows "parameter <- field | value | template" with the
  resulting example shown.

### 7. Items and arrays (R6)
`PortType.itemOf(inputPort)` is a *reference* resolved by `fieldsOf` while it follows wires: the output has the item shape of the named
input's array. `PortType.arrayOf(T)` is the collecting side. Both are stored as small descriptors in the port (`"schemaFrom": {"item": "in"}`),
not as schemas, so a changed upstream shape flows through.

### 8. Engine and persistence rules (R7)
- Bindings and templates are node `data` (app owned) and invalidate as any data change.
- Samples and derived field lists are not data: no invalidation, not in undo.
- Secret ports never store samples; secret values are masked in examples.
- `GraphJson` round-trips port types and their schema, unknown keywords included.

## Answers to the requester's questions
1. Several JSON inputs: **port id** prefix (`{{in.title}}`); bare `{{title}}` when exactly one JSON input is connected. Labels change, ids do not.
2. Samples: **side object** (`SampleStore`), in-memory default; `GraphJson(samples = true)` as an opt-in.
3. `{{ }}`: kept.

## Non-goals
Full JSON Schema; an expression language in templates; mandatory typing (a port without a type behaves as today; plain text stays text).

## Slices (each useful alone, each a PR)
1. `JsonPortType`, schema in `GraphJson`, samples + `SampleStore`, `fieldsOf`, `sampleOf`.
2. `Template` (+ `parse`), `MissingFieldException`, `ctx.render`, `ctx.inputs.json`.
3. `KTemplateField` / `KTemplateArea`, `KFieldPicker`, `KFieldMapper`, `KNodeScope.TemplateField`.
4. `schemaFor`, `itemOf` / `arrayOf`, wire warnings, and the acceptance test below.

## Acceptance test (kompound-graph, no app code)
1. `Producer` (schema `{title, description}` via `schemaFor`; runner outputs `{"title":"T","description":"D"}`) and `Consumer` (one JSON input, a
   `body: String` setting).
2. `fieldsOf(consumer, "in")` returns `title` and `description` before any run; after a run with the schema removed (sample only); after a
   restart with a `SampleStore`.
3. `ctx.render("{\"a\": \"{{title}}\", \"b\": \"{{description}}\"}")` gives `{"a": "T", "b": "D"}`; with `{{tilte}}` the editor reports the
   unknown field and the run fails with `MissingFieldException(consumer, in, tilte)`.
4. A Compose UI test types `{{` in a `KTemplateField` in the Consumer's body and picks `description`.
5. Changing the Producer's sample does not mark the Consumer stale.

## Open questions
1. Sample size cap: 64 KB per port is a guess; should it be a `GraphEngine` option? Proposal: yes, `maxSampleBytes`.
2. `Template.parse` offsets and `\{{` inside JSON bodies: a body such as `{"a": {{x}}}` has `}}}`; the parser takes the first `}}` after `{{`. Proposal: documented, tested.

## Progress
- **Slice 1 done**: `PortType.json` / `JsonPortType` (schema saved with the port), samples in the engine (`sampleOf`, `sampleSnapshot`, `loadSamples`, cap `maxSampleBytes`),
  `SampleStore` and `InMemorySampleStore`, `fieldsOf` (declared schema first, examples from the sample; `Collect` inputs get `[*]`), `GraphJson(samples = true)`,
  and in `:kompound` `FieldInfo`, `JsonFields`, `JsonSchema.example` and `fromNestedShorthand`. Open question 1 is answered: the cap is `GraphEngine(maxSampleBytes)` (default 64 KB).
  Correction to section 2: kompound-graph has a `KNodeType`, but only as the add-node menu entry; the `schemaFor` hook (slice 4) is still an engine function like `signalMode`.
- **Slice 2 done**: `Template` with `parse` (parts with offsets), `names`, `problems`, `render`; filters `json`, `url`, `default:x`; `MissingFieldException(path, node, port)` lives in
  `:kompound` with optional node and port (the graph fills them in), `TemplateScope`; `ctx.render(text, vararg extra)`, `ctx.templateScope()`, `ctx.inputs.json(port)` as extensions. Open question 2
  (a body like `{"a": {{x}}}`) is answered: the first `}}` after `{{` closes the placeholder, so `{{x}}}` leaves one literal `}`; tested.
- **Slice 3 done**: `KTemplateField` / `KTemplateArea`, `KFieldPicker`, `KFieldMapper` (`FieldBinding`, `ParamSpec`) in `:kompound` with a plain `fields: List<FieldInfo>` provider, and in `:kompound-graph`
  extensions `KNodeScope.TemplateField` / `TemplateArea` / `FieldPicker` that take the engine (`KNodeGraph` has no engine, so it is passed explicitly) plus `GraphEngine.templateFields(node, from)`.
  Bare paths are offered when exactly one input has fields, prefixed ones (`a.title`) otherwise, whichever input `from` selects, because that is what `ctx.render` can resolve. The acceptance UI test (type `{{` in a node's field, pick `description`) is `KTemplateNodeTest`.
- **Slice 4 done**: `GraphEngine(schemaFor)` (an engine function like `signalMode`, not a node-type member), `schemaOf`, `PortType.itemOf(input)` and `arrayOf(item)` (item-of is a reference resolved while following wires,
  saved as `"itemOf"` on the port), `wireWarning(edge)` and `KNodeGraph(edgeWarning)`, `JsonSchema.incompatibilities`. Precedence of shapes is `schemaFor`, the port type's schema, then the sample. The acceptance test is
  `StructuredValuesTest` (parts 1 to 3 and 5) and `KTemplateNodeTest` (part 4). Not done, by design: schemas are never enforced, and nothing blocks a connection.
