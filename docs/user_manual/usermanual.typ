#import "@preview/polylux:0.4.0": *
#import "formatting.typ": *

#show: userguide.with(
   plugin-name: "CasualtyKit",
   plugin-version: "0.2",
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

The boxes at the top count open casualties per precedence, A to E.

- *At my position* creates a card at the device location.
- *At map centre* creates a card at the centre of the map view.
- Select a casualty to open the card. Each row shows the latest vitals, time since injury and how long a tourniquet has been on.
]

#tak-slide[
  = TCCC card

The card holds identity, evacuation precedence, mechanism and time of injury, injuries, vital signs, treatments grouped by MARCH, and notes.

- *Injuries*: tap the front or back body figure where the casualty is injured, then choose the type. Each injury gets a numbered marker.
- *Treatments*: the quick-add buttons record a common treatment at the current time. A tourniquet shows how long it has been on.

- *Save + Send* stores the card, updates the marker and sends it to the team.
- *Save only* stores the card on this device.
- *MIST report* shows the MIST text for voice or chat.
- *Show on map* pans to the marker.
- *Delete card from this device* removes the card and marker locally; other devices keep theirs.

When two devices edit the same card, the most recent edit is kept on every device.
]

#tak-slide[
  = 9-line MEDEVAC request

Select *9-line MEDEVAC request*, tick the casualties to evacuate, and complete lines 1, 2, 4, 6, 7 and 9. Lines 3, 5 and 8 are filled in from the cards.

*Review and send* shows the full request before it is sent. The request uses the standard CASEVAC format, so devices without the plugin open it in the built-in tool. Selected casualties are marked as evacuation requested.
]
