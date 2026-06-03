# Better Nothing Music Visualizer - TODO list!

## To Do

### UI/UX
* M3E split and round those 2 /\ buttons
* M3E selector capture source
* Replace dropdowns with Material 3 Expressive Split Buttons
* Redesign about page for a proper hierarchical easy M3E design
* Merge Git repo link card with app version card

### Logic/Features
* Latency Auto-Calibration: Sync Wizard using microphone to measure delay
* Voice Assistant Visualization: Specialized mode for voice input detection
* Reduce bit depth haptics amplitude 
* Add one-shot spam haptics engine (from Oliver)
* Refine beat detection engine haptics
* Ask RichTap what is going on
* Remove notification detection

### Device Specific
* **Change np1 preset with wider spectrum range (Aleks)**
* Adapt ui depending on the audio source (Aleks)
* Add an adjustment Hz range preset for Phone (4a)
* Hide glyph tab if device is not a Nothing Phone

### Maintenance/Misc
* Remove the debug disclaimer in readme one we release
* change git link embed
* Update notification (Oliver)

## In Progress
* RichTap haptics (currently not working)

## Done
* Dynamic UI Theming: Use Palette API to match app colors with album art
* Use rotating rounded polygon for the haptic viz visual (boost rotate and change shape when beat detected)
* Change that app theme carrousel thing cuz it sucks
* Global "UI shift" variable (float 0.0 to 1.0, reactive to 50-150 Hz)
* Add Shizuku audio source (Oliver) - Added structure & UI toggle
* Add shitty visualizer audio source WITH DISCLAIMER THAT IT SUCKS
* Alternating strobe mode blinking of the glyphs (10ms)
* Make toggle card white accent color when on
* Make toggle card thinner
* Add author name in generated description of community presets
* Make the "save to community" button more obvious
* Add time count for the vizualizer
* Add time count for the visualizer
* Change title "About and other" to "App info and updates"
* remove latency compensation from microphone input
* Make categories for this Todo list.
* Ask Oliver if i can replace these checkmarks by some easier bullet points?
* Add flashlight visualizer (same UI as Haptics, add it at the bottom of the haptics screen for now)
* Collapse experimental features in settings tab for lighter UI
* Rename OLED black theme to "Default theme"
* Auto dark light theme for Nothing and Material You theme
* Collapse experimental settings in toggleable buttons
* Change "check for updates" text to "Update" when available
* Make the local file button smaller
* Remove latency compensations for microphone
* Add microphone audio source
* **ADDED PHONE (1) COMPATIBILITY WITHOUT DEBUG MODE!**
* Fix status bar padding preventing true edge-to-edge
* Implement haptic visualization
* Add "disable glyph session when no sound" toggle
* Fix package name situation
* Fix haptics implementation
* Remove smoothing from glyph preview
* Quick Settings tile fix
* Merge contributions from rkyzen
* Add "enable glyph debug mode" warning for Phone (1) users
* Fix range slider not blocking tab swipes
* Fetch `zones.config` from GitHub repo
* Fix Phone (2) screen off issues (battery optimization fix)
* Resolve Quick Settings tile functional issues
* *And many things we did before moving the todo list here!*
