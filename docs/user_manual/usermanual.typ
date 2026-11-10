#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "CasualtyKit",
   plugin-version: "0.1",
   platform: "ATAK",
   platform-version: "5.8.0",
)

#tak-slide[
  = Overview
#toolbox.side-by-side(columns: (.75fr, 9fr))[
#image("plugin_icon.png", width: 70%)
][
CasualtyKit records casualties on a digital TCCC card (DD Form 1380), shares each card with the team as a map marker, and builds a 9-line MEDEVAC request from the selected cards.

This software is unvalidated and is not a medical device. Vital-sign alert limits are placeholders and must be reviewed by a medical lead before use.
]
]

#tak-slide[
  = Casualty list

Select the CasualtyKit toolbar button to open the list. Casualties are sorted by evacuation precedence, then by time of injury. The coloured bar shows precedence: red urgent, amber priority, green routine.

- *New at my position* creates a card at the device location.
- *New at map centre* creates a card at the centre of the map view.
- Select a casualty to open the card.
]

#tak-slide[
  = TCCC card

The card holds identity, evacuation precedence, mechanism and time of injury, injuries, vital signs, treatments grouped by MARCH, and notes.

- *Save + Send* stores the card, updates the marker and sends it to the team.
- *Save only* stores the card on this device.
- *MIST report* shows the MIST text for voice or chat.
- *Show on map* pans to the marker.

When two devices edit the same card, the most recent edit is kept on every device.
]

#tak-slide[
  = 9-line MEDEVAC request

Select *9-line MEDEVAC request*, tick the casualties to evacuate, and complete lines 1, 2, 4, 6, 7 and 9. Lines 3, 5 and 8 are filled in from the cards.

*Review and send* shows the full request before it is sent. The request uses the standard CASEVAC format, so devices without the plugin open it in the built-in tool. Selected casualties are marked as evacuation requested.
]
