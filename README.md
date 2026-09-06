# Bubble Pop Auto Player

Prototype Android accessibility-based auto player tuned from the supplied Bubble Pop Legends recording.

Features:
- Up to 6-hour session watchdog.
- Screenshot-based colored-bubble detection.
- Heuristic matching/target selection.
- Accessibility gesture shooting.
- Level-complete -> Next handling.
- Ad safety: pauses on Install/Download/Play Store CTA text.
- Attempts only Close/Skip/X accessibility nodes.
- Never intentionally clicks Install/Download/Play Store.

Important:
1. This is a prototype. Game physics and UI can change, so aim logic may need tuning on the actual phone.
2. Android 11+ is required.
3. Enable Accessibility for this app, then open the game and enable Auto Play.
4. Keep the game in the foreground. For best 6-hour stability, disable battery optimization for this app and use a charger.
5. Do not enable this automation on apps where automated interaction is prohibited.

Build:
Open the project in Android Studio and build the debug APK.
