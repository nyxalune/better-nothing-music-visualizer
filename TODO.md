# Better Nothing Music Visualizer - TODO list!

## To Do

* fix the app crash
* remove preset favoriting, track preset usage time
* track audio source playing time, not audio_source_change
* track viz mode playing time (flashlight time, haptics time, glyphs time, these will be added up in the firebase charts ifpossible)
* rework the qs tiles, make one for glyph viz, one for haptic viz, and one for flashlight viz. enable of disable the service automatically based on if we have anything running or not.
* Add a disclaimer popup on first use of the media projection source that we do not record the screen, even if it looks like it.
* and work on the translation tools so we can get hoomans to translate our app.
* Remove richtap haptics and ALL CODE RELATED TO IT, ALSO DEPS
* Improve notification (ALEKS DOES IT)
* Fix the amplitude haptics (Aleks)
* Add 3.0 gamma to the UI shift vizualisation and only have downward smoothing 

* M3E split and round those 2 /\ buttons (ALEKS DOES IT)
* Redesign about page for a proper hierarchical easy M3E design
* Merge Git repo link card with app version card

* Adaptive Smoothing: Real-time decay adjustment based on music tempo (3c) WHY
* Latency Auto-Calibration: Sync Wizard using microphone to measure delay (4a) interesting **OLIVER GO AHEAD**
* Reduce bit depth haptics amplitude 

* Refine beat detection engine haptics
* Remove notification detection

* Adapt ui depending on the audio source (Aleks)
* Add an adjustment Hz range preset for Phone (4a)
* **Hide glyph tab if device is not a Nothing Phone AND DISABLE ALL CODE RELATED TO THE GLYPH UI**

* Check if auto update works 

---

## Done

* Add one-shot spam haptics engine (from Oliver)
* **Change np1 preset with wider spectrum range (Aleks)**
* Remove the debug disclaimer in readme one we release
* Dynamic UI Theming: Use Palette API to match app colors with album art
* Dynamic Gain Normalization: Auto-Gain for quiet audio (Experimental)
* Navigation Bar Overlay: Graphical bar visualizer on top of nav bar
* Dynamic Peak Normalization: Auto-Gain for quiet audio (Experimental)
* Battery Saver Threshold: Dims visualization when low battery (Experimental)
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
