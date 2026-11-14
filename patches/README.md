# Patch manifests

One directory per patch, each holding a `patch.json` manifest and a README. The manifest is
the single source of truth: `scripts/apply_patches.py` applies it to a disassembly tree and
`scripts/verify_apk.py` checks the built dex against it, so the two cannot drift.

## Manifest

| Field | Meaning |
|---|---|
| `name` | directory name and `--only` selector |
| `title` | human-facing name |
| `summary` | one sentence, shown in the build log |
| `compatibility.package` / `.version` | what the hooks were read from |
| `compatibility.evidence` | document that justifies the hooks |
| `hooks[]` | the changes, in application order |

## Hooks

`id` is stable and reads as `Class.method`; it names the marker comment written into the smali
(`# patch:<patch>:<id>`) and the id `--revert` looks for. `why` states what the hook changes
and what breaks without it — a hook without a `why` is a guess.

### `anchor` hooks

Replace one literal instruction sequence. Use when the whole method is being short-circuited:
the injected block ends in a `return`, and the original instructions stay below it, unreachable,
so a reviewer sees exactly what was disabled.

| Field | Meaning |
|---|---|
| `class` | class descriptor, `com/example/Foo` |
| `method` | full `.method` line, including its signature |
| `anchor` | the literal sequence to find; must occur exactly once |
| `original` | copy of the anchor, for readability |
| `replacement` | the injected instructions, ending in a return |
| `marker` | marker comment; defaults to `patch:<patch>:<id>` |
| `returns` | the method's return type, checked against `method` |

### `callsite` hooks

Leave the call in place and replace the result it produces. Use when an API is read from
several places and every read must answer the same way — the call still runs, only its value
is overridden, and the register keeps its width.

| Field | Meaning |
|---|---|
| `class`, `method` | as above; the search is scoped to that method |
| `invoke` | full descriptor of the called method |
| `result` | `move-result-object`, `move-result` or `move-result-wide` |
| `replace_with` | `const-string`, `const/4`, … — must be the result's width |
| `literal` | the value to substitute |
| `expect` | exact number of sites in that method; a mismatch fails the patch |

## Rules

* A hook must be justified by evidence under `analysis/`. "It looked right in jadx" is not
  evidence.
* Hooks are version-bound. When they stop matching, the patch must fail loudly rather than
  apply somewhere else — never loosen an anchor to make a new version pass.
* Every hook is reversible: `scripts/apply_patches.py --revert` restores the original bytes.
