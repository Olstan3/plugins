# Three Tick Hunter

RuneLite plugin that keeps box traps laid, checked and reset on a 3-tick rhythm.
Intended for private servers that allow automation. Do not use on official RuneScape.

## Use
1. `./gradlew run` (or run `ThreeTickHunterPluginTest` from your IDE) with your private-server client.
2. Stand next to the trap area with box traps in your inventory and the inventory tab open.
3. Press F9 (configurable) to start. Press it again to stop.

The bot stops itself when the inventory is full, it is out of traps, you log out, or nothing happens
for the stall timeout.

## Not yet compiled
First version written without network access to the RuneLite repo, so it has not been built.
Likely trouble spots if the build complains: `ComponentID.INVENTORY_CONTAINER`, `client.getScene()`,
`LocalPoint.fromWorld(client, ...)` (these names move between RuneLite releases).

# Session Recorder

Second plugin in this repo. Press F10 to start/stop a recording, F11 to drop a marker line.
Files are written to `~/.runelite/session-recorder/session-*.jsonl`: every click (option, target, ids),
nearby object name/action changes (e.g. box trap set -> caught), animations, inventory changes, XP, chat.
Play by hand for a few minutes, stop the recording, then share the file so the hunter plugin can be tuned
to what your server actually does. Same caveat: not compiled yet.
