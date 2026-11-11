CasualtyKit

_________________________________________________________________
PURPOSE AND CAPABILITIES

CasualtyKit is an ATAK plugin for casualty reporting. It provides a digital TCCC card
(DD Form 1380) with MARCH treatments and a vital-signs log, shows each casualty as a map
marker, shares cards with the team over CoT, and builds a 9-line MEDEVAC request from the
selected cards in the standard ATAK CASEVAC format.

This software is unvalidated and is not a medical device.

_________________________________________________________________
STATUS

In progress. Version 0.4, test build. Not released.

_________________________________________________________________
POINT OF CONTACTS

Kriss (GitHub: vipersq), https://github.com/vipersq

_________________________________________________________________
PORTS REQUIRED

None. The plugin opens no ports and makes no network connections of its own. Casualty cards
and 9-line requests are sent as CoT through the connections already configured in ATAK.

_________________________________________________________________
EQUIPMENT REQUIRED

An Android device running ATAK-CIV.

_________________________________________________________________
EQUIPMENT SUPPORTED

None beyond the Android device.

_________________________________________________________________
COMPILATION

    ./gradlew assembleCivRelease

For a local build, place this folder in <ATAK SDK>/samples/casualtykit and create
local.properties with sdk.dir=<path to Android SDK>. The debug build
(./gradlew assembleCivDebug) loads only in the developer build of ATAK shipped in the SDK.

_________________________________________________________________
DEVELOPER NOTES

- core/ holds the platform-neutral logic and the CoT format (docs/COT-SPEC.md). It has no
  Android dependencies and is compiled into the plugin from source.
- Vital-sign alert limits in VitalsAlerts are placeholders and must be set by a medical lead.
- Cards are saved under atak/tools/casualtykit/ through the ATAK IO provider.
- Deleting a marker on the map does not delete the card.
- No third-party libraries are bundled.
