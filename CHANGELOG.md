# Changelog

## 0.3 (unreleased)

- Body diagram redrawn as a smooth anatomical figure; regions are tapped by their real outline, and a
  near miss snaps to the closest part.
- New toolbar icon (map pin with medical cross). Alternatives are kept in `docs/icons/`.

## 0.2 (unreleased)

- Body diagram (front and back): tap a body region to record an injury; numbered markers match the list.
- New look: dark card layout, triage board with counts per precedence, larger touch targets, new icon.
- Tourniquet time-on and time-since-injury shown on the list and the card, refreshed automatically.
- Quick-add buttons for common treatments (tourniquet with site, pressure dressing, chest seal, NPA, TXA, hypothermia kit).
- One dialog for a full set of vitals, with AVPU.
- Delete a card from this device.
- CoT: optional `view`, `x`, `y` on `wound` (backward compatible with 0.1).

## 0.1 (unreleased)

- Casualty list, TCCC card editor, MIST report.
- 9-line MEDEVAC request generated from cards, stock CASEVAC CoT format.
- Card sync over CoT with last-writer-wins merge and vitals alerts.
- Core library with unit tests; CoT format specification.
