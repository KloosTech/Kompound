# Keyboard navigation matrix

What every Kompound component does with the keyboard, and how that is checked. Desktop and web have a physical keyboard; Android
and iOS have one too (hardware keyboard, switch access, TalkBack and VoiceOver actions), so the same model applies.

## Rules for every component

- **Tab and Shift+Tab** move focus between controls, in reading order. A composite (a tab row, a tree, a table, a menu, a slider group) is
  **one** tab stop; arrow keys move inside it.
- **Space and Enter** activate the focused control: buttons, chips, menu items, list items, rows. Checkboxes, switches and radio
  buttons use **Space**.
- **Escape** closes the topmost overlay (menu, dialog, drawer, palette, suggestion list) and returns focus to where it came from.
- Disabled controls are skipped by Tab and ignore keys.
- Every control can take focus and has an accessible name and role; the catalog test `DemoAccessibilityTest` checks this for every demo
  (see "Automated checks").
- Pointer gestures have a key equivalent where the control is a standard widget (a divider moves with the arrow keys, a slide-to-confirm with Enter). The node graph editor is the exception: moving and wiring nodes needs a pointer.

## Matrix

| Component | Keys | Notes |
|---|---|---|
| `KButton`, `KIconButton`, `KToggleButton`, `KChip` | Space, Enter | Toggles and filter chips announce their state. |
| `KCheckbox`, `KSwitch`, `KRadioButton` | Space | Label is part of the target. |
| `KTextField`, `KTextArea`, `KNumberField`, `KPasswordField` | Typing; IME action (Done/Next) | Error text is announced with the field. |
| `KInlineEdit` | Enter or Space opens; Enter saves; Escape cancels | Multi-line: Enter adds a line, use the check button. |
| `KDropdown`, `KMultiDropdown`, `KMenu`, `KActionMenu` | Enter/Space opens; Up/Down move; Enter picks; Escape closes | Focus moves into the popup and back. |
| `KContextMenuArea` | Menu key or Shift+F10 opens at the focused content; long press on touch | Content must be focusable. |
| `KCombobox` | Type to filter; Down/Up move through options; Enter picks; Escape closes | Focus stays in the field. Down reopens a closed list. |
| `KTagInput` | Enter or `,` ends a tag; Backspace in empty text removes the last; Down/Up/Enter for suggestions; Escape closes them | Each tag chip is a button "Remove …". |
| `KDateField`, `KDateRangeField` | Enter/Space opens the dialog | The dialog is the Material date picker. |
| `KTimePicker` | Up/Down ±1; Page Up/Down ±3 (minutes ±10); Home/End first/last; digits type a value | One tab stop per spinner; the arrow buttons are also focusable. |
| `KColorPicker` | Square: arrows ±2 %, Page Up/Down brightness ±10 %. Sliders: Left/Right ±1 %, Page Up/Down ±10 %, Home/End | Hex field accepts `#RGB`, `#RRGGBB`, `#RRGGBBAA`. |
| `KSlider` | Arrows, Page Up/Down, Home/End | |
| `KSegmentedControl` | Tab to a segment; Space or Enter selects | Each segment is a radio button. |
| `KTabRow` | Left/Right move and select; Home/End | Disabled tabs are skipped. |
| `KNavigationBar`, `KNavigationRail`, `KNavigationDrawer` | Left/Right in the bar, Up/Down in the rail and drawer; Enter/Space selects | `KModalNavigationDrawer`: Escape closes. |
| `KTreeView` | Up/Down; Right opens or goes to the first child; Left closes or goes to the parent; Home/End; Enter/Space selects | WAI-ARIA tree pattern; expand and collapse are also accessibility actions. |
| `KDataTable` | Up/Down between rows; Space toggles selection; Enter clicks the row | Header cells are buttons; Enter/Space sorts. |
| `KSplitPane` | Arrows move the divider; Home/End to the limits | The divider is a slider for screen readers. |
| `KAccordion`, `KExpandable` | Enter/Space toggles | |
| `KDialog`, `KAlertDialog`, `KBottomSheet` | Escape closes | |
| `KCommandPalette` | Ctrl/Cmd+K opens (with `kCommandShortcut`); type to search; Up/Down; Enter runs; Escape closes | Focus is in the search field from the start. |
| `KSlideToConfirm` | Enter or Space confirms | The drag has a key equivalent. |
| `KLineChart`, `KBarChart` | Left/Right move the crosshair; Escape hides it | A summary is announced; the values at the crosshair while it moves. |
| `KForm` | A refused submit moves focus to the first invalid field | The status line is a live region. |
| `KNodeGraph` | Delete or Backspace removes the selection; undo, redo, copy, paste and select-all shortcuts; Escape clears the selection or leaves a subgraph; single letters pick tools | Pointer-first: wiring and moving nodes need a pointer. The canvas is read-only in executions. |

## Automated checks

`catalog/shared/.../DemoAccessibilityTest` renders every demo and walks the merged semantics tree (what a screen reader sees). For every
control that can be clicked, toggled or typed into it checks:

1. it has a name (text or content description),
2. text fields have a name,
3. it is at least 24 × 24 dp (WCAG 2.2 AA target size; for text fields the container counts),
4. clickable nodes have a role,
5. it accepts a focus request, so the keyboard can reach it.

Gaps must be listed in the test's `knownGaps` with a reason; the list may only shrink. Compose's own link nodes inside text are exempt.

Per component, unit tests cover the keys in the matrix (`KTabRowTest`, `KTreeViewTest`, `KComboboxTest`, …). Key events on a headless
desktop test runner are occasionally timing sensitive; those tests wait for idle between presses.

## Not covered yet

- Behaviour with TalkBack and VoiceOver is checked by reading the semantics, not by running the screen readers.
- Contrast: theme presets are tested at 4.5:1 for every on-colour pair; custom themes are the app's responsibility.
- Focus order for custom layouts follows composition order unless the app sets `focusProperties`.
