# CasualtyKit CoT format, schema v1

This is the contract shared by the ATAK and WinTAK plugins. Both must read and write exactly this.

## Casualty card

- Event type `b-m-p-s-m` (spot marker), so clients without the plugin still show a marker.
- `<contact callsign>` is the battle roster number, or `CAS-xxxx` if none is set.
- `<color argb>` follows evacuation precedence: red urgent, amber priority, green routine.
- `<remarks>` holds the MIST report as text, readable on any client.
- `<__casualtykit v="1">` holds the full DD Form 1380 content. Only the plugin reads it.

| Element | Attributes |
|---|---|
| `__casualtykit` | `v`, `rev`, `updated` (epoch ms), `editor`, `status`, `precedence`, `type`, `firstResponder` |
| `patient` | `roster`, `name`, `unit`, `nationality`, `allergies`, `blood` |
| `injury` | `time` (epoch ms), `moi` (comma list), `moiOther`; children `wound loc kind` with optional `view` (`FRONT` or `BACK`), `x`, `y` |
| `vitals` (repeated) | `time`, optional `pulse sys dia rr spo2 pain avpu src` |
| `tx` (repeated) | `time`, `group` (M A R C H MEDS OTHER), `code`, `detail` |
| `notes` | text |

Conflict rule: the record with the higher `rev` wins; on a tie the later `updated`; on a further tie the
greater `editor` string. Every device applies the same rule, so all devices converge.

### Injury position on the body diagram

`view`, `x` and `y` are optional and were added in plugin v0.2. `x` and `y` are 0..1 within a view box
100 wide by 220 high (see `BodyMap` in `core/`). The front view is drawn facing the casualty, so the
casualty's right side is at low `x`; the back view is mirrored. A reader that does not know these
attributes ignores them, and a wound without them is simply not drawn on the diagram.

## 9-line request

- Event type `b-r-f-h-c` with the stock `_medevac_` detail, attribute names identical to the ATAK
  CASEVAC tool (checked against ATAK-CIV 4.6.0.5 `MedicalDetailHandler`). Stock ATAK and WinTAK open
  it in their own 9-line form.
- `security` and `hlz_marking` are list indexes, as in the stock tool.
- `zMistsMap/zMist` carries one MIST per casualty.
- `<__casualtykit9>` adds what the stock schema lacks: `callsign`, `grid`, `nbc`, the exact A to E
  precedence split, and `casualty uid` links back to the cards.
