# ZERO Client – aktueller Stand

**Zuletzt aktualisiert:** 2026-11-15

- 2026-11-15 (_58): **Rain FX Snow-Modus** (Visuals, Style "Snow" als 4. Option):
  - Langsam fallende Schneeflocken (1,2–2,8 b/s statt 7–11) mit leichtem Winddrift + seitlichem Wackeln;
    gerendert als weiche Billboard-Quads (blasser Schein + heller Kern) statt der Regenstriche.
    Flockengröße: echte Weltgröße bis ~9 b, danach konstante Bildschirmgröße (in der Ferne sichtbar).
  - Aufschlag = sanfter Pulver-Ring + weicher weißer Punkt (0,9 s), OHNE hochspritzende Tröpfchen –
    "Ground Splashes" schaltet das wie bisher ab.
  - Farben Weiß/Kopf + Schneeblau/Schein (`colors()`), Spawnhöhe niedriger (+3…12), Dichte ×1,5 (max 600),
    weil Punkte optisch dünner wirken als Striche. Stilwechsel leert DROPS/SPLASH (andere Geschwindigkeit
    und Array-Länge), damit keine alten Regentropfen als Flocken weiterlaufen.
  - Selbsttest `ZERO_SELFTEST_ONLY=snow` (Nacht, schwarzer Betonboden, Amount 300): Screenshots
    `snow_1/snow_2` + `t_sn0..t_sn3` geprüft – Flocken/Puffs sichtbar, kein Crash, beide Läufe EXIT 0.
    NICHT live getestet: Schnee bei Tag/hellem Himmel (Kontrast), Verhalten auf laufenden Servern mit
    eigenem Wetter; GUI-Dropdown zeigt "Snow" nur als generische Choice-Option (kein eigener Eintrag nötig).
  - Build 3 (Java 25), Jar in `FERTIG\` + `dist\`, `version.json` build 3, Push `d069473`, Discord 204.
    Kopierer in die NoRisk-Instanz läuft als Warteschleife auf javaw-Aus.

- 2026-11-14 (_58): **Aurora-Relook (HUD + GUI + Visuals)** nach 12 Referenzvideos (eigene Umsetzung, kein fremder Code).
  - **Theme**: `Theme.hue()`/`Theme.hsv()`; `AccentModule.hudStyle` jetzt ID `hud_style_v2`, Standard **"Aurora"** (alte
    Configs bekommen es automatisch), `hueFor(name)`, neues `worldTint` (Standard 18 %). `ZeroRender.noTint` = Farben
    exakt (sonst tauscht `Theme.tint` Orange 6–48° auf den Akzent – Hunger/Gold/Ember-Vorschau brauchen das).
  - **Icons**: `zero_icons.ttf` = Lucide 0.460 Subset (76 Glyphen, ISC, `lucide_license.txt`), neue Konstanten in
    `ZeroFont.Icon` (MONITOR, CLOUD, CLOCK, MUSIC, PLAY/PAUSE, FLASK, GAUGE, PALETTE, …).
  - **HUD** `hud/AuroraHud.java`: `surface()` = getöntes Glas (ersetzt im Aurora-Stil `ZeroHud.glass` -> alle alten
    Panels passen mit), Koordinaten-Pille + Info-Pille (Kopf | FPS | Ping | Server | Uhr) oben Mitte (ersetzt
    Watermark/StatusBar/HUD-Info), Musik-Karte + Lyrics-Pille, Potions (Icon, Name, Zeit, Ring), Speed (BPS + Verlauf),
    Keybinds (ersetzt im Aurora-Stil die Modul-Liste), Toggle-Benachrichtigungen (Hook in `Module.setEnabled`),
    Leben/Hunger/Absorption/Rüstung/Sättigung/Luft-Leisten + eigene Hotbar mit gleitender Auswahl, XP-Leiste, Offhand.
    Neue HUD-Module: `keybinds_hud`, `speed_hud`, `health_bars` (+ "Custom Hotbar"), `notifications`.
    `GuiMixin`: `renderPlayerHealth` und `renderHotbarAndDecorations` werden im Aurora-Stil gecancelt
    (`renderSelectedItemName` per @Shadow weiter gezeichnet).
  - **GUI** `gui/AuroraGui.java` (öffnet sich statt ZeroGui, wenn Aurora aktiv; "Classic GUI" unten rechts):
    Icon-Tab-Pillen oben (aktive mit Text + Glow), Suche (einfach lostippen), Modul-Karten (3 Spalten, Schalter,
    Rechtsklick = an/aus), Detail-Karte von rechts (Enabled, Keybind, Slider, Pillen-Auswahl, Schalter, Farb-Swatches;
    Listen -> "Edit" öffnet den klassischen Editor), Themes-Seite mit Vorschau-Kacheln + HUD-Stil/Größe/World Tint/Blur.
  - **Visuals**: Target Marker Stil "Ring" (Standard, Themenfarbe): zwei leuchtende Ringe laufen am Ziel hoch/runter;
    World Tint (Farbstich + Vignette in Themenfarbe).
  - Selftests: `aurora` (HUD, 3 Themes), `agui` (Grid/Detail/Themes/Suche), `ring` (Plattform y=200, Schwein, da
    Testwelt Peaceful). Alle Screenshots geprüft.

- 2026-11-14 (_58): **TPA Auto Confirm** (Misc, `tpa_confirm`) – `ClientPacketListenerMixin` hookt jetzt auch
  `sendCommand` (HEAD, require 0) -> `TpaAutoConfirmModule.onCommand` merkt sich /tpa, /tpahere (+ tpask/etpa-Varianten).
  Geht innerhalb "Wait For Menu" (s) ein Container-Menü auf, wird nach "Click Delay" der beste Slot geklickt:
  grün (lime_/green_*, Smaragd, Slimeball) +2, Name mit confirm/accept/bestätigen/... +3, rot/Barrier/"cancel/deny/
  abbrechen/nein" = nie. Einmal pro Menü. TPA All Macro hat "Auto Confirm Menu" (an) -> ruft `tickConfirm` auch wenn das
  Confirm-Modul aus ist, und wartet bis zu 3 s, bis das Menü zu ist. Selftest `tpaconfirm` (Kiste mit grauen Panes, rotem
  "Cancel", Kopf, grünem "Confirm"): klickt "Confirm".

- 2026-11-14 (_58): **TPA All Macro** (Misc, `tpa_all`) – `TpaAllModule`: schickt `/tpahere` oder `/tpa` (Choice
  "Command") nacheinander an alle Namen aus der Tab-Liste (`getOnlinePlayers`, nur gültige MC-Namen [A-Za-z0-9_]{3,16},
  ohne dich selbst), alphabetisch, Pause "Delay" (s), optional "Repeat", sonst geht es danach aus. "Exclude" =
  Setting.Strings mit eigenem GUI-Editor (`ZeroGui.nameListRows`, Field.NAME: Eingabefeld + Add/Enter, Zeilen mit
  Mülleimer; Enter lässt das Feld offen für den nächsten Namen). Selftest `ZERO_SELFTEST_ONLY=tpa` (Screenshot
  tpa_gui.png; im Einzelspieler korrekt "No players to send to").

- 2026-11-14 (_58): **Bone Meal Bot** (World, `bone_meal_bot`) – Baumfarm im Stehen. `BoneMealBotModule`: Listen
  `spots` (Erdblöcke, Setzling kommt obendrauf) + `chest` (Knochenmehl-Kiste), beide als Setting.Waypoints. Markieren per
  GUI-Buttons "Mark block" (nochmal = entfernen, auf Setzling/Stamm geschaut -> Boden darunter) / "Set chest" / "Start",
  oder Mark Mode (Mittelklick = Block, Shift+Mittelklick = Kiste; Bot pausiert). Ablauf PLAN: 1) Stamm auf Spot -> Axt
  (Netherite, Haltbarkeit > "Keep Axe Above") nehmen, zusammenhängende Logs (BFS, nur erreichbare, unten zuerst) per
  continueDestroyBlock abbauen; 2) Setzling -> Knochenmehl (max 40 Versuche/Setzling, sonst "can't grow"); 3) freier Spot
  -> Setzling (ItemTags.SAPLINGS, canSurvive) pflanzen. Knochenmehl/Setzlinge leer -> Kiste öffnen, QUICK_MOVE
  (`stacks` Stapel Knochenmehl + Setzlinge wenn < 16), sonst Stop mit Meldung. Items außerhalb der Hotbar per SWAP in
  "Hotbar Swap Slot". `BoneMealBotRenderer` (BEFORE_TRANSLUCENT): Spots grün, Kiste orange, Ziel weiß pulsierend
  (StorageEspRenderer.box/edges/col dafür package-private). Selftest `ZERO_SELFTEST_ONLY=bonemeal`: Survival, 4 Gras-Spots
  2 Blöcke weg, Spieler auf 2er-Säule, Kiste mit 128 Knochenmehl -> holt Knochenmehl, 9 Birken in ~40 s gefällt, 0 Logs
  übrig. Wichtig: auf Bodenhöhe sind obere Birken-Logs außer Reichweite (4.5) -> Bot meldet "out of reach";
  Empfehlung: 2 Blöcke höher stehen, Spots 1-2 Blöcke weg.

- 2026-11-12 (_58, Teil 2): Krispy-Look vervollstaendigt + Weihnachts-Skins:
  - **HUD komplett auf lila/grau umgestellt** (Krispy-Palette): alle Panels laufen jetzt ueber den
    gemeinsamen `ZeroHud.glass()`-Helfer (grauer Verlauf 0xD9222429 -> 0xBE17181D, Schatten, Sheen-Band,
    Haarlinie 0x4DFFFFFF -> 0x14FFFFFF), Akzent `0xFFC4B5FD`/`0xFFA78BFA`, Text `0xFFF2F2F5`,
    gedimmt `0xFF9CA0A8`/`0xFF6B6F77`, dunkel auf Akzent `0xFF1C1526`. Target-HUD-Gruen/Orange raus
    (0xFF26282E / 0xFF2A2C33). `AccentModule` Defaults: Preset "Violet", HUD-Style "ZERO Glass".
  - **GUI `ZeroGui.card()`/`glareCard`** neu: r=20, graues Glas (-551344853 / -1072228323), lila
    Sheen-Verlauf (380079098 -> 10980346), weisse Haarlinie, Glare-Farben lila (0xFFC4B5FD / 0xFFE9E4FF);
    Liquid-Glass-Shader `zero_glass.fsh` ueberarbeitet (echter Blur, kein Transparenz-Fake).
  - **Tracer-Fix**: `EspRenderer.line()` nutzt jetzt Screen-Space-Breite (vorher in der Naehe zu dick).
  - **Font Poppins**: `assets/quiqzy/font/poppins.ttf` (+ Lizenz), `ui.json`/`ui_bold.json` zeigen darauf;
    REGULAR/BOLD/LOGO/ICONS bekommen `boldBias` in `ZeroFont`. Poppins gibt es nur als Medium-Schnitt ->
    **BOLD = Pseudo-Bold per SDF**: `stbtt_GetGlyphSDF(..., (byte)(128 + boldBias), ...)` mit `boldBias=18`
    (Kante wandert nach aussen, der 0.5-Schwellwert des `zero_text.fsh` wird frueher erreicht -> dickerer
    Strich). REGULAR/LOGO/ICONS = 0.
  - **Item Skins (Visuals, neu)**: `ItemSkinsModule` (`item_skins`) mit Settings `pickaxe`/`totem`,
    tauscht per `DataComponents.ITEM_MODEL` auf `quiqzy:christmas_pickaxe` / `quiqzy:christmas_totem`
    (Kopie des Stacks, echtes Item bleibt unveraendert, client-side).
    Assets: `items/christmas_*.json`, `models/item/christmas_*.json` (handheld/generated), Texturen
    **selbst gezeichnet** (`tools`-Skript `make_xmas.py`, auf Vanilla-Silhouette): Spitzhacke mit
    holly-gruenem Kopf, braunem Stiel, roten Beeren + weissem Schnee; 16x16 Santa-Totem (gruene Augen,
    goldene Schnalle). NICHT aus dem 02max-Christmas-Pack kopiert.
    Zentraler Hook: neuer `ItemModelResolverMixin` (`@ModifyVariable` auf `appendItemLayers`, argsOnly
    index=2 = ItemStack, HEAD, require 0) -> deckt GUI, erste Person und Totem-Pop in einem ab;
    in `quiqzy.mixins.json` registriert.
  - Selbsttest `ZERO_SELFTEST=t` komplett durchgelaufen (EXIT 0), neuer Step `item_skins@30`
    (Diamant-Spickaxe in Slot 0 + Totem in Offhand): Log `ItemSkins: pickaxe=true totem=true`,
    Screenshots geprueft: `t_item_skins.png` (Skin in Hotbar/Hand/Offhand), `t_ingame_gui.png`
    (graues Glas, lila Sheen, Poppins-Bold in den Ueberschriften), `t_hud_full.png` (grau/lila HUD).
    Build sauber (Java 25), JAR in `FERTIG\`.

- 2026-11-12 (_58): GUI-Relook nach den Krispy-Referenzvideos. Layout-Konstanten eingefuehrt (W=500, H=344, SIDEBAR=126,
  GAP=10, BAR=38, CARD_B, ROW) und alle harten 122/480/312-Koordinaten in ZeroGui ersetzt. Statt einem grossen Block jetzt
  VIER getrennte Glaskarten: Sidebar, Profilkarte unten links (Avatar + Name + fps + "N modules active"), Content-Karte,
  untere Leiste rechts. Suche aus der Kopfzeile in die untere Leiste verschoben (sucht jetzt auf allen Seiten), dazu 3
  Icon-Buttons (Grid/List-Ansicht, Settings, Keybinds). Header: runder Zurueck-Button (Chevron) auf Settings/Configs/Keybinds,
  Drag-Region nur noch ueber der Content-Karte. Glare + Rahmen werden pro Karte gezeichnet (kein Rechteck-Rahmen mehr in den
  Luecken). Sidebar-Abstaende auf 21px verdichtet, damit alles oberhalb der Profilkarte passt. Render-Engine unveraendert
  (glass/glow/gradient/stroke reichen fuer den Look), HUD unveraendert - Pill-Watermark und Modul-Pillen sind schon der
  Default (Accent/CatLean). ZeroGuiScenario-Klicks an das neue Layout angepasst (Chip 420/169, Suchfeld 250/325).
  Selbsttest ZERO_SELFTEST=t komplett durchgelaufen (EXIT 0), Screenshots geprueft: Liste, Settings-Zeilen, Keybinds mit
  Bind (Anchor=R), Suche "stun", HUD-Pillen. Build sauber (Java 25), JAR in FERTIG\. _58 ist ab jetzt die aktuelle Linie.

- 2026-11-12 (_57): Port aus _45 (nur was in _57 fehlte, keine Doppelten): Target Marker (visuals, rotierende Eck-Klammern
  auf dem Ziel, ZeroHud.targetMarker + ModuleManager-Reg + TargetMarkerModule). Sus Chunk: Amethyst-Signal zaehlt jetzt auch
  LARGE/MEDIUM_AMETHYST_BUD, neuer Combined Score (minScore 5, deepslate*3), Strahl/Wuerfel aus dem Renderer entfernt ->
  sauberer Kasten mit doppelter Kante (edges() mit Dicke, glowMid-Saum). NICHT portiert (in _57 schon vorhanden): Base Finder
  onlyDeep/smart/blockEntities, Sound/Paket/Xray-Research (ActivityInspector/SoundChunk/XrayResearch), Tracer-Flacker-Fix (midpoint).
  _57 ist ab jetzt die aktuelle Linie. Build sauber, base-Selftest ok.

- 2026-11-12 (_57): 3D-GUI-Panel bekommt Tiefe. ZeroGui.render: vor drawPanel (im tiltMatrix-Transform) extrudierte Seitenkante -
  10 dunkle, nach unten-rechts versetzte Kopien des Panel-Rechtecks (mit tiltX/Y skaliert) -> das Fenster wirkt wie ein solider,
  schwebender 3D-Block. Gated durch QuiqzyConfig.gui3d (an). Mit t_list.png bestaetigt. Als Naechstes: glossy/spiegelnder Boden.

- 2026-11-12 (_57): Glossy Floor (visuals) - GlossyFloorModule + GlossyFloorRenderer (BEFORE_TRANSLUCENT). Pro Entity
  (Spieler/Mob/Crystal) wird der Boden darunter per clip() gesucht und eine flache, zur Kamera gestreckte Quad-Schliere
  gemalt (nah hell -> fern transparent, per-Vertex-Alpha, Farbe nach Entity-Typ). Kein echter Mirror-Pass. Settings:
  players/mobs/crystals, range, intensity, length, tint. RenderTypes ist in _57 die Vanilla-Klasse
  (net.minecraft.client.renderer.rendertype.RenderTypes). glossy-Selftest -> glossy_1.png, Schlieren sichtbar (subtil).
  3D-GUI + Glossy ausgeliefert. Offen: evtl. Glossy staerker/Sheen, echtes GUI-in-Welt (render-to-texture).

- 2026-11-12 (_57): Echtes 3D-GUI-in-der-Welt (begehbar). GuiWorldModule (visuals): onEnable platziert ein Panel an fester
  Weltposition vor dem Spieler (ax/ay/az + yaw, distance/scale/category Settings). GuiWorldRenderer (BEFORE_TRANSLUCENT):
  PoseStack translate zur Weltpos + Axis.YP.rotationDegrees(-yaw) + scale(s,-s,s), Welt-Quad-Hintergrund (beidseitig) +
  Akzentleiste via debugFilledBox, Modul-Liste als 3D-Text per Font.drawInBatch(Matrix4f, MultiBufferSource, DisplayMode.NORMAL).
  WICHTIG: erst ALLE Quads, dann aller Text - sonst "Not building" (Font beendet den Quad-Puffer). guiworld-Selftest: Panel steht
  in der Welt mit Perspektive, von vorne Liste, von hinten Slab -> drumrumlaufen bestaetigt (guiworld_front/back.png). v1 ist
  NUR Anzeige (Klick per Blick-Raycast kommt noch). Fake-3D (extrudierte Dicke am normalen ClickGUI) auf Userwunsch wieder entfernt.

- 2026-11-12 (_57): GUI-in-Welt 1:1-Ansatz (Render-to-Texture) begonnen. GuiCapture (AbstractTexture-Subklasse, eigene
  GpuTexture mit USAGE_COPY_DST|TEXTURE_BINDING, via TextureManager unter quiqzy:gui_capture registriert): copyTextureToTexture
  kopiert Framebuffer-Rechteck in die Textur. GuiWorldRenderer: texQuad per entityCutoutNoCull(ID) -> BEWIESEN dass eine
  aufgenommene Textur auf einem 3D-Welt-Quad rendert (guiworld_front.png: Quad schwebt mit Perspektive). ABER: Capture laeuft
  im Welt-Render-Pass, da ist die GUI noch nicht gezeichnet -> Bild blass (nur halbfertige Welt). Fuer echtes 1:1 muss der
  Capture NACH dem GUI-Render passieren (eigener Mixin + Timing) - offen. Sauberes Testen blockiert EngineTest (oeffnet ClickGUI
  ueber alles). Daher: 1:1-Aufnahme = Setting "Mirror Real GUI (experimental)" (aus); Standard bleibt die saubere Text-3D-GUI.

- 2026-11-12 (_57): GUI-in-Welt 1:1 FUNKTIONIERT (Snapshot). GuiCaptureMixin (GameRenderer.render TAIL) nimmt den fertigen
  Frame (Welt+echte GUI) auf, solange ClickGUI offen -> GuiCapture. GuiWorldRenderer texQuad zeigt es 1:1 auf dem Welt-Quad.
  UV nur V-gespiegelt (Framebuffer-Y) = aufrecht. GuiWorldModule.onTick platziert das Panel bei jedem GUI-Oeffnen frisch
  (wenn mirror an). UX: "GUI In World"+"Mirror Real GUI" an -> ClickGUI auf (positionieren) -> zu -> echte GUI 1:1 schwebt
  in der Welt, begehbar. GRENZE: live-3D WAEHREND die GUI offen ist geht so nicht (flacher Screen ueberdeckt die Welt; GUI
  unterdruecken = nichts zum Aufnehmen). Dafuer braeuchte es Render-to-Offscreen-Target (internen GuiRenderer in eigenen
  TextureTarget treiben) - offen, tiefster Hook.

- 2026-11-12 (_58 = neue aktuelle Linie, Kopie von _57 + eigener Glossy-Shader): Build-Fix GlossyFloorRenderer
  (RenderType.LARGE_BUFFER_SIZE gibt es in 1.21.11 nicht -> BIG_BUFFER_SIZE). Krispy-Level-Umbau gestartet (User-Referenzen
  in Downloads\zero: Krispy-Client Screenshots/Videos). Befund: Arraylist (Glass-Pills+Slide), Watermark-Kapsel, Radar, Weapon
  Skins etc. sind schon im Krispy-Stil. Luecken: Storage-ESP-Look, Region Map, CSGO Case Opening.
  NEU: Storage ESP (visuals) - StorageEspModule scannt Block-Entities (CHEST/TRAPPED/SHULKER/BARREL/ENDER/HOPPER) alle 10 Ticks
  im Chunk-Radius (getChunkNow), StorageEspRenderer: gefuellte Box + Halo-Kante + Kern-Kante (typfarben: Truhe orange, Shulker
  lila, Fass braun, Ender tuerkis, Hopper grau), duenner Tracer-Faecher ab Handposition (eye+fwd*0.85+right*0.32-0.30y),
  midpoint-Billboard + Saum, eigene NO_DEPTH-Pipeline (through walls). storage-Selftest: storage_fp/side.png = Krispy-Look ok.
  Regel bleibt: nur Look nachbauen, KEIN fremder Code/Assets (auch keine echten CS:GO-Skins).

- 2026-11-12 (_58): ECHTES 3D-Karambit. Quelle: CGTrader-Modell (User-Kauf) Downloads\karambit_src\karambit.glb.
  scratchpad export_mesh.py (trimesh) -> assets/quiqzy/models/mesh/karambit.zmesh (big-endian: "ZMSH", parts, je Teil
  texname + tris*3*(xyz uv nxnynz)) + textures/mesh/karambit_0..3.png (1024). Ausrichtung rot40+flipX, in 0..1-Wuerfel.
  MeshSpecialRenderer (NoDataSpecialModelRenderer, Typ "quiqzy:mesh", Codec {mesh}) laedt .zmesh beim Bake, zeichnet per
  submitCustomGeometry + entityCutoutNoCull(textur), Dreiecke als degenerierte Quads. Registrierung ueber
  SpecialModelRenderersAccessor (ID_MAPPER.put) in QuiqzyClient. items/weapon_karambit.json = minecraft:special mit
  base quiqzy:item/weapon_karambit (handheld + display: 1st scale .5 transl 1.13/4.6/.6). Alter Kloetzchen-Karambit
  im scratchpad gesichert. Neues Werkzeug fuer weitere Messer: dieselbe Pipeline (glb -> zmesh -> special).
  Auch neu: Region Map (HUD, Chunk-Raster), Open Case (CaseOpeningScreen, Karambit-Karte zeigt case_karambit.png).
  OFFEN: Weapon Skins in 3rd Person (bisher nur Ego-Sicht); andere Messer noch Kloetzchen.

- 2026-11-12 (_58): Weapon Skins jetzt auch in 3rd Person (eigener Spieler). ItemModelResolverMixin: @Inject HEAD
  updateForLiving, bei THIRD_PERSON_RIGHT/LEFT_HAND + entity==mc.player + WEAPON_SKINS.applies -> rekursiv mit skin(stack)
  (Reentrancy-Guard zero$inSkin), cancel. Karambit 3rd-Person display = vanilla (rot 0/-90/55, transl 0/4/.5, scale .9).
  karambit-Selftest hat jetzt Seitenansicht (karambit_side.png).

- 2026-11-12 (_58): Dropdowns in der ClickGUI. ZeroGui: Choice-Settings, die nicht als Chips passen (und immer
  Weapon Skins.Model + Item Skins.Pickaxe), oeffnen bei Klick eine Liste (drawDropdown, modal; Region ueber alles = schliessen,
  Esc schliesst, Rechtsklick blaettert zurueck; klappt nach oben auf, wenn unten kein Platz). Item Skins: Bool "Christmas
  Pickaxe" -> Choice "Pickaxe" (id pickaxe_skin: Off/Christmas; weitere Namen -> Modell quiqzy:pickaxe_<name>). Test-Szenario
  "dropdown" (dd_open/dd_weapon/dd_pickaxe.png) ok. Pickaxe-3D-Modelle warten auf Dateien vom User (.glb bevorzugt).

- 2026-11-12 (_58): Pickaxe-Skin "Climbing Axe" (echtes 3D). Quelle: Downloads\pickaxe_srcxe.obj (+ selbst geschriebene
  .mtl -> tex/Textures/M_Climbing_Axe_Base_Color.png), 5746 Dreiecke. Skripte (scratchpad export_mesh.py/render_knife.py)
  jetzt allgemein: duennste Achse automatisch = Tiefe, OBJ-SimpleMaterial-Textur, Export-Args rot/flip (Axe: -45, flip 0).
  Assets: models/mesh/climbing_axe.zmesh, textures/mesh/climbing_axe_0.png, items/pickaxe_climbing_axe.json (special mesh),
  models/item/pickaxe_climbing_axe.json (handheld, 1st scale .62). Item Skins Pickaxe-Dropdown: Off/Christmas/Climbing Axe.
  Selftest "axe" ok (1st, 3rd, Hotbar-Icon zeigen 3D).
  NEUES MODELL HINZUFUEGEN: glb/obj -> export_mesh.py <datei> <assets> <name> 1024 <rot> <flip> -> items/<x>.json
  (minecraft:special + quiqzy:mesh) -> Option im Dropdown.

- 2026-11-12 (_58): FIX Karambit ohne Textur im echten Spiel: Log "Unable to bake item model quiqzy:weapon_karambit"
  ConcurrentModificationException in MeshSpecialRenderer.load (HashMap.computeIfAbsent) - Item-Modelle werden parallel
  gebacken (Karambit + Climbing Axe gleichzeitig). Jetzt ConcurrentHashMap + get/read/put ohne computeIfAbsent, leere
  Ergebnisse werden nicht gecacht. Tipp: User-Log liegt in D:\minecraft norisk\profiles.21.4 liamo\logs\latest.log.

- 2026-11-12 (_58): Pickaxe-Skins auf Userwunsch ENTFERNT (Climbing Axe + Christmas Pickaxe, Assets + Dropdown + Szenarien).
  Item Skins = nur noch Christmas Totem. Mesh-Pipeline (MeshSpecialRenderer/.zmesh) bleibt fuer Karambit + kuenftige Modelle.

- 2026-11-12 (_58): Witch-Totem (eigenes Halloween-Pixel-Art im Krispy-Stil, NICHT deren Textur). scratchpad witch_totem.py
  zeichnet 36x36 per Formen (Kuerbis-Ellipse mit Rippen/Glanz, leuchtendes Gesicht, Hexenhut mit Knick + goldener Schnalle,
  Robe, Totem-Arme) + Auto-Outline -> textures/item/totem_witch.png, items/models totem_witch.json (item/generated).
  Item Skins: Bool "Christmas Totem" -> Choice "Totem" (id totem_skin: Off/Christmas/Witch, Default Witch), Dropdown.
  Selftest "totem": Hand, Hotbar, Totem-Pop zeigen Hexen-Totem.

- 2026-11-12 (_58): Witch-Totem auf Userwunsch komplett ENTFERNT (2D + 3D-Mesh, Assets, Szenario). Item Skins = wieder Bool
  "Christmas Totem". MeshSpecialRenderer kann jetzt Teile mit "_glow" im Texturnamen emissive rendern
  (entityTranslucentEmissive, Alpha = Helligkeit) - bleibt fuer kuenftige Modelle.
  WICHTIG: Jar NIE in den Mods-Ordner kopieren, waehrend Minecraft laeuft (javaw.exe) -> Crash beim Klassenladen
  (EOFException SpawnerTracker$SpawnerEntry). Erst schliessen lassen, dann kopieren.

- 2026-11-13 (_58): Tote Buttons. Accent-Farbe wirkte im HUD kaum: Theme.tint wird nur in ZeroRender-Gradienten/Arcs
  angewendet, HUD nutzte feste ORANGE/ORANGE2 (lila). Jetzt acc()/acc2() = Theme.tint(BASE) -> HUD-Akzent folgt Accent.
  Accent "HUD Style": Option "Classic" entfernt (war identisch mit ZERO Glass). Base Radar "Dig Trails" entfernt (ungenutzt).
  Scanner scratchpad deadsettings.py (Settings ohne Lesezugriff) -> jetzt 0 Treffer. Witch-Totem-Entfernung ebenfalls in dieser Jar.

## Ziel
Eigene Fabric-Mod "ZERO Client" für Minecraft 1.21.11 (Package `dev.quiqzy.client`).
GUI mit eigener Render-Engine (Right Shift), Module in Combat / Player / Visuals / World / Movement / Misc.
Neue Features werden oft nach Video-Vorlagen (Screen-Recordings) nachgebaut.

## Wo liegt was
- **Aktueller Quellcode:** `C:\Users\jonas\Downloads\zero-client_58\zero-client_58` (DIESER Ordner)
- **Fertige JAR:** `FERTIG\zero-client-1.0.0.jar` (in diesem Ordner)
- **Screen-Recordings (Vorlagen):** `C:\Users\jonas\AppData\Local\Packages\Microsoft.ScreenSketch_8wekyb3d8bbwe\TempState\Recordings\`
  - Diese Runde genutzt: `C:\Users\jonas\Downloads\zero` (Krispy-Referenzvideos + Bilder); Frames in
    `C:\Users\jonas\AppData\Local\Temp\opencode\zeroframes\`
  - Frames ausziehen mit ffmpeg aus: `C:\Users\jonas\AppData\Local\Programs\Python\Python311\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe`
- **Minecraft mods-Ordner:** `C:\Users\jonas\AppData\Roaming\.minecraft\mods`
- Alte Ordner `zero-client_1` … `_57` und alle Zips = VERALTET, nicht als Basis nehmen.

## Bauen
- Braucht **Java 25**: `JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`
- `BAUEN.bat` doppelklicken (setzt Java 25 selbst) oder: `gradlew build`
- Ergebnis: `build\libs\zero-client-1.0.0.jar` → nach `FERTIG\` kopieren. Nie die `-dev.jar` nehmen.

## Wichtig zum Code
- Render-Engine: `src/main/java/dev/quiqzy/client/gui/engine/` + `src/main/resources/assets/quiqzy/shaders/`
- Haupt-GUI: `gui/ZeroGui.java` (kann Settings: Bool, Int, Choice, Color, Blocks, Items, Waypoints – **keine Kommazahlen**)
- Settings-Typen: `module/Setting.java`, Module: `module/ModuleManager.java`
- GUI/Engine/Config/HtmlGui sind dekompiliert (keine Kommentare) – funktioniert normal.

## Erledigt
- 2026-11-07: Stun Slam bekam Settings wie im Prestige-Video: Bind Mode (Toggle/Hold),
  Horizontal Target Dist, Min Fall Distance, Stun Slam an/aus, Stun Slam Mode (Chance/Hold Key),
  Chance, Scale With Fall, Stun Min Fall, Breach Check (bevorzugt Breach-Mace).
- 2026-11-07: Quellcode mit Render-Engine aus der JAR wiederhergestellt → zero-client_42.

- 2026-11-07 (_43): Grid-Ansicht: geöffnete Kachel wächst jetzt weich auf volle Breite (Karte bleibt erhalten),
  andere Kacheln gleiten mit. Orange Puls-Glow (wie beim Keybind setzen) zusätzlich auf: Keybind-Chip (Hover),
  Slider-Knopf (Hover/Ziehen), gewählte Choice-Option, Dropdown-Choice (Hover), Hex-Farbfeld (beim Tippen).

- 2026-11-07 (_43, Teil 2):
  - Sounds: eigener Java-Synth (`util/UiSounds.java`, javax.sound) statt HTML-HUD-Browser – vorher war oft nichts zu hören.
  - HUD komplett neu im Engine-Stil: `hud/ZeroHud.java` (Info, Waypoints, Sus Chunk, Keystrokes, Rüstung,
    Waypoint-Marker im Bild). Alte `QuiqzyHud`/`InfoHud`/HtmlHud werden nicht mehr gezeichnet.
  - Block ESP: Preset-Chips (gewähltes orange mit Verlauf + Puls), Picker-Reihenfolge bleibt beim Klicken stehen,
    Rechtsklick auf Block im Picker = eigene Farbe (Palette + Farbverlauf + Reset, gespeichert als `block_colors`),
    sofortige Updates bei Blockänderungen über `mixin/ClientLevelMixin` (sendBlockUpdated), Rescan alle 0,5 s.
  - Sus Chunk: Anzeichen Amethyst / Rotated Deepslate / Grown Kelp (je an/aus + Grenzwert), orange Darstellung,
    Sound bei neuem Fund, Grund im HUD (z. B. "6 amethyst · 3 deepslate").
  - Waypoints: schönere Zeilen im GUI (Farbpunkt, Entfernung), Marker im Bild, HUD mit Richtungspfeilen.
  - Neuer Setting-Typ `Setting.ColorMap` (unsichtbar im GUI, Config speichert als {id: "#RRGGBB"}).

- 2026-11-07 (_44): Block-Farbe per Rechtsklick ist jetzt ein ganzes Fenster: großes Farbfeld (Sättigung/Helligkeit,
  ziehbar), Hue-Balken (ziehbar), Vorschau + Hex, Schnellfarben, Reset, orange "Done"-Knopf. Speichert beim Loslassen.
  Code: `drawColorPopover` / `updateColorDrag` in `gui/ZeroGui.java`.

- 2026-11-07 (_44): Neues Modul **Avatar** (Visuals): verwandelt dich in die eigene Figur "Rook"
  (Hoodie, Cargo-Hose, Cap rückwärts, Sonnenbrille, Kette). Outfits Orange/Midnight/Ice, Hauttöne Light/Medium/Dark,
  "Apply to Others". Umsetzung: Mixin auf `AbstractClientPlayer.getSkin` (`mixin/AbstractClientPlayerMixin`),
  Texturen `assets/quiqzy/textures/avatar/*.png` erzeugt mit `tools/make_avatar.py` (Design dort anpassen + neu ausführen).
  Hinweis: Keine Nachbauten geschützter Figuren (z. B. aus GTA) – nur eigene Designs.

- 2026-11-07 (_45): Neues Modul **Hit Effects** (Visuals), inspiriert von Treffer-Effekt-Videos, eigener Look in Client-Farben:
  Ripple-Ring, Spark Splash, Crit Stars (nur echte Crits), Kill Beam (Lichtsäule wenn Ziel stirbt), Screen Glow (Rand-Leuchten),
  Hit Sounds (eigener Synth: hit/crit/kill). Farben Ember/Gold/Sunset, Intensity 30–200 %.
  Code: `module/visuals/HitEffectsModule`, Rendering `hud/HitFx` (Welt-Positionen -> Bildschirm projiziert, ZeroRender),
  Mixin `MultiPlayerGameModeMixin` (attack HEAD).

- 2026-11-07 (_45, Teil 2), alles eigene Designs in Client-Farben (Videos nur Inspiration):
  - **Weapon Skins** (Visuals): Schwerter (optional Äxte) als eigene 3D-Modelle Katana/Greatsword/Scythe/Karambit/Butterfly,
    orange Teile leuchten. Tricks (Dreh/Flip) beim Waffenwechsel + Taste R ("Weapon Trick"). Modelle: tools/make_weapons.py.
    Umsetzung: ITEM_MODEL-Komponente auf Kopie des Stacks (ItemInHandRendererMixin), Trick = PoseStack-Drehung in renderItem.
  - **Embers** (Visuals): schwebende Funken um dich herum (HitFx EMBER).
  - **Low Fire** (Visuals): Feuer-Overlay tiefer (ScreenEffectRendererMixin.renderFire).
  - **Target HUD** (Misc): Panel neben dem Gegner: Kopf, Name, HP, Waffe, Entfernung, HP-Balken mit Schadens-Nachzieher,
    Rüstung, Combo, Treffer gegeben-kassiert, ahead/behind, Schild-Cooldown (5 s nach Axt-Schildbruch), Real Position Ghost.
  - **Hit Effects** erweitert: Hit Color (Treffer-Aufblinken in Client-Farbe statt rot, OverlayTextureMixin),
    Shield Breaker (Splitter beim Schildbruch), Afterglow (Getöteter löst sich in Licht auf).
  - Nicht gemacht: animierter Energie-Himmel aus dem Video.

## Selbsttest (Claude kann das Spiel selbst starten und prüfen)
- Befehl (Git Bash, im Projektordner): JAVA_HOME auf Java 25, dann
  ZERO_SELFTEST=t ./gradlew runClient   (alles)
  ZERO_SELFTEST=t ZERO_SELFTEST_ONLY=ingame ./gradlew runClient   (nur im Spiel: HUD, Waffen, Treffer, Kill, Feuer)
  ZERO_SELFTEST=t ZERO_SELFTEST_ONLY=weapons ./gradlew runClient  (nur Waffenmodelle)
- Screenshots: run/screenshots/t_*.png, Log im Gradle-Output ([Selbsttest]-Zeilen). Spiel schließt sich selbst.
- Szenarien: gui/engine/ZeroGuiScenario.java (GUI) und gui/engine/InGameScenario.java (Spiel). Label "name@N" = N Ticks bis Screenshot.
- Während des Tests nicht ins Fenster klicken (verfälscht Treffer/Hand).
- 2026-11-07: Selbsttest bestanden – kein Crash, alle Mixins greifen, Effekte/Target HUD/Waffen/Low Fire/Farbfenster sichtbar.
  Waffen-Display wurde danach auf ~55 % verkleinert (vorher viel zu groß).

- 2026-11-07 (_45, Teil 3): Target HUD wackelt nicht mehr: Standard-Position fest rechts neben dem Fadenkreuz
  ("Next to Crosshair"), außerdem "Below Crosshair" und ruhiges "Follow Target" (Totzone 14 px).
  Hit Effects: normaler Hit dezent (kleiner Ring, 5 Funken, kaum Glow), Crit heftig (3 Schockwellen, 30 Funken,
  Lichtstreifen, Sterne, aufspringendes "CRIT", Bildschirm-Blitz). Per Selbsttest geprüft.
  Hinweis: Im Dev-Selbsttest kann Rinku (Chromium) mit INITIALIZATION_FAILED loggen – harmlos, nicht unser Code.

- 2026-11-07 (_45, Teil 4) – Liste vom Nutzer abgearbeitet + eigene Ideen (alles per Selbsttest geprüft):
  - Hit Effects ruhiger: kein großer Ring mehr, weicher Leuchtpunkt; normal 4 Funken, Crit 10 Funken + 5 Sterne.
  - HUD: Watermark, Module List (ArrayList, links/rechts), Potion HUD (Icons, Restzeit-Balken), Armor HUD (Haltbarkeit %),
    Item Counter (Totems/Perlen/Kristalle/Äpfel/Obsi/XP), Session Stats (Spielzeit/Kills/Tode/KD/Blöcke), CPS in Keystrokes.
  - Visuals: No Hurt Cam, Custom FOV, No Fog (FogRendererMixin, @ModifyArgs updateBuffer), Nametags (Name/HP/Distanz/Gear,
    Items am Boden; Vanilla-Namen aus), 2D ESP (Ecken/Box + Health Bar), Crosshair (Kreuz/Punkt/Kreis, weitet sich bei Treffer,
    Cooldown-Balken), Animations (Swing Speed, Low Shield, Low Offhand, Item Size).
  - Player: Toggle Sprint, Auto Respawn. World: Death Waypoint. Misc: Low HP Warning (roter Rand), Start Menu (ZERO-Logo im Hauptmenü).
  - Rand-Leuchten (Low HP / Screen Glow) jetzt aus gestapelten Streifen (HitFx.edges) – Verläufe/Glows am Rand waren unsichtbar.
  - Bewusst NICHT gebaut (unfairer Vorteil gg. andere Spieler / irreführend): Combat Aura, Triggerbot, AutoClicker, Reach,
    Hitboxes, Velocity, Criticals, Auto Crystals, NoFall, Scaffold, FastPlace, Fake Stats/Leaderboard.

- 2026-11-07 (_45, Teil 5) – Video "Setup" (eigene Umsetzung) + Freecam:
  - Accent Colour (Misc): ganzer Client in anderer Akzentfarbe – util/Theme.tint() dreht Orange-Töne, eingehängt in
    ZeroRender.shape() und ZeroRender.layout() (Text). Presets + Custom.
  - Status Bar (RAM/Uhrzeit/Server/FPS/Ping), Radar (Kompass + Spielerköpfe), Creamy Key Sounds (Keystrokes),
    Embers: Style Embers/Petals/Both + Stardust Trail.
  - Freecam (Visuals): NUR Kamera fliegt (WASD/Space/Shift), Spieler bleibt und handelt von sich aus (Gegenteil Meteor),
    Mausrad = Tempo. Mixins: FreecamMixins$CameraMixin/TurnMixin/ScrollMixin, Hand ausgeblendet.
  - Low Fire weiter abgesenkt. Selbsttest-Modi: ZERO_SELFTEST_ONLY=freecam / petals.
  - Arbeitsordner bleibt zero-client_45 (Platte war voll; alte Versionen _1.._44 gelöscht).
  - Nicht gemacht: animierter Energie-Himmel, Glasboden, HUD-Editor (Drag&Drop), Spotify-Player.

- 2026-11-07 (_45, Teil 6): Freecam startet im Kopf (Körper erst sichtbar ab 1,2 Blöcken Abstand, outsideBody()),
  Hände sichtbar (Option "Show Hands"), Tempo-Anzeige optional ("Speed Display"), klein unter dem Fadenkreuz, blendet aus.
  HUD lebendiger (Accent → "Animated HUD" + "Animation Speed"): wandernder Rand-Schimmer, gleitendes Glanzlicht oben,
  atmender Farbschleier, fließende Akzentfarben; jedes Panel eigene Phase (ZeroHud.panel/flow).

- 2026-11-07 (_45, Teil 7): Rinku/HTML-GUI komplett entfernt (build.gradle, fabric.mod.json, gui/html, assets/html) –
  JAR von ~1,4 MB auf ~0,6 MB. HUD-Rework: HUD Size (Accent, Standard 80 %), jedes Element eigener Stil (Info freistehend,
  Waypoints als Pillen, Tränke als runde Abzeichen mit Punktring, Session als Kacheln, Watermark nur Logo, kleinere Modulliste),
  Animation dezenter (Standard 60 %). HUD-Editor (hud/HudEditorScreen): Drag&Drop, Rechtsklick = Reset, Esc = fertig;
  Öffnen über GUI-Settings "Edit HUD" oder Taste "HUD Editor". Offsets in Accent.hudOffsets. Vanilla-Effekt-Icons aus bei Potion HUD.

- 2026-11-07 (_45, Teil 8) – "alles verbessern":
  - Alte GUIs (ClickGuiScreen, QuiqzyScreen, LiquidGui), alte HUDs (InfoHud, QuiqzyHud), GUI-Umschalt-Taste und WalksyLib entfernt
    (kein extra Mod mehr nötig). JAR ~0,57 MB.
  - Neue Kategorie HUD (Module.Category.HUD) mit allen Anzeige-Modulen; Misc hat nur noch Accent, Chat, Sounds, Start Menu.
  - Performance: Nametags/2D ESP/Radar nutzen Entity-Cache (ZeroHud.nearby, 20x/s, 256 Blöcke).
  - Großes Farbfenster jetzt für JEDE Farbeinstellung (Klick auf den großen Farbkreis), nicht nur Block ESP.
  - HUD-Editor: Andocken an Rändern/Mitte/anderen Elementen mit orangen Hilfslinien.
  - Config-Profile gab es schon (GUI-Seite "Configs").
  - Selbsttest-Modi: ZERO_SELFTEST_ONLY=hud / improve.

- 2026-11-07 (_45, Teil 9):
  - Text Replace (Misc): ersetzt Text im Scoreboard nur lokal (Server/andere sehen echte Werte), Farben bleiben.
    Regeln per Chat: /zreplace add <alt> => <neu> | list | remove <nr> | clear (util/ReplaceCommand, Fabric client commands).
    Option "Hide My Name" (Streamer-Modus). Mixin ScoreboardTextMixin (drawString + Font.width in displayScoreboardSidebar).
    Neuer Setting-Typ Setting.Strings (unsichtbar im GUI). Selbsttest: ZERO_SELFTEST_ONLY=replace.
  - Status Bar neu: einzelne schwebende Kapseln mit eigenen Icons (Speicher-Ring, Uhr, pulsierender Server-Punkt, FPS-Balken).
  - Keystrokes neu: große Buchstaben ohne Kästen, gedrückte Taste wird zur Akzent-Kachel, LMB/RMB mit CPS darunter,
    Leertaste als schmale Leiste. Radar: feiner wandernder Ring, Sichtkegel, Nord-Markierung, Spielerköpfe mit Akzentring.

- 2026-11-07 (_45, Teil 10): Text Replace hat jetzt im Modul eine LIVE-VORSCHAU des echten Server-Scoreboards
  (ZeroGui.scoreboardEditor): Zeile anklicken -> Textfeld (Field.REPLACE), Enter speichert, Rechtsklick = Original.
  &-Farbcodes werden zu §. Regeln pro ganzer Zeile (TextReplaceModule.setRule/ruleFor/serverLines). /zreplace geht weiterhin.
  Radar-Fix: Sichtkegel-Kasten und oranger Ring-Strich entfernt, eigener Pfeil mit dunklem Punkt in der Mitte.

- 2026-11-07 (_45, Teil 11): Neues Modul Visual FX (Visuals): Target Ring (wandernder Punktring um den Gegner),
  Target Outline (Vanilla-Glow-Umriss in Akzentfarbe, GlowMixins), Jump Circles, Kill Screen ("ELIMINATED" + Grau),
  Halo (goldener Ring über dem Kopf, in F5). Wichtiger Fix: Welt->Bildschirm-Projektion (HitFx.render, ZeroHud.markers)
  nutzt jetzt die ECHTE Kamera (gameRenderer.getMainCamera) – vorher falsch in F5/Freecam. Selbsttest: ZERO_SELFTEST_ONLY=vfx.

- 2026-11-07 (_45, Teil 12) – Render-Engine verbessert:
  - Neues Vertex-Element "Extra" (vec4) im SHAPE_FORMAT (ZeroPipelines.EXTRA), zero_shape.vsh/fsh erweitert.
  - Neue Grundformen: ZeroRender.arc(cx,cy,radius,width,startDeg,sweepDeg,from,to) (Bogen/Ring, runde Enden,
    Verlauf entlang des Bogens), ZeroRender.ring(...), ZeroRender.radial(...) (radialer Verlauf).
  - Glow fällt jetzt gauss-ähnlich ab (weicher, lichtartiger) statt linear.
  - Eingesetzt: Trank-Restzeit-Ringe, Speicher-Ring (Status Bar), Crosshair-Cooldown als Bogen ums Fadenkreuz.

- 2026-11-07 (_45, Teil 13): Neues Modul Base Finder (World): bewertet geladene Chunks nach Spieler-Spuren
  (Shulker 15, Beacon 25, Ench. Table 8, Ender Chest 6, Anvil 5, Redstone 3, Schilder/Banner 3, Truhen/Fässer 2,
  Glas/Wolle/Beton/Obsidian je wenig), Dörfer (Glocke) x0.35. HUD-Liste (Richtung, Distanz, Score-Bogen, Grund),
  Leuchtsäule + Ring in der Welt, Sound, optional Auto-Waypoint. Selbsttest ZERO_SELFTEST_ONLY=base (baut Test-Base).

- 2026-11-07 (_45, Teil 14): Promo-Edit "ZERO_BaseFinder_Edit.mp4" (im Projektordner). Footage per Selbsttest
  ZERO_SELFTEST_ONLY=edit ZERO_SELFTEST_SIZE=1280x720 (baut Test-Base, Freecam-Kamerafahrt, Screenshots edit_a/b/c_*.png),
  Schnitt mit tools/make_edit.py <Projekt> <Arbeitsordner> <ffmpeg.exe> (Titel, Zoom-Blur, Glow, eigener Synth-Sound).

- 2026-11-07 (_45, Teil 15): Zweites Edit "ZERO_Bases_Edit_NoMusic.mp4" (33,4 s, OHNE Musik, Schnitte auf Beat-Raster
  92,3 BPM / Drop 11,61 s / Outro 30,37 s; Schnittliste ZERO_Bases_Edit_Schnitte.txt). Footage: ZERO_SELFTEST_ONLY=edit2
  (baut 41x41 Mega-Base mit Türmen/Beacons + unterirdische 27x27 Base mit Schacht, 8 Kamerafahrten e2_*.png).
  Schnitt: tools/make_edit2.py (Buchstaben-Pop-Titel, ZERO-Logo, Punch-Zoom pro Beat, Zoom-Blur).
  Fremde Musik wird NICHT eingebaut – Nutzer legt den Song selbst in CapCut ab 0:00 darunter.

- 2026-11-07 (_45, Teil 16) – Effekte aus Showcase-Videos (eigene Umsetzung):
  - Glass (Visuals): Spieler durchscheinend (state.isInvisible + !isInvisibleToPlayer), weißer Umriss, Rüstung weg
    (GlassMixin + GlassAvatarMixin auf AvatarRenderer.extractRenderState RETURN, da Ausrüstung dort zuletzt gesetzt wird).
  - X-Ray (Visuals): animierte Skelette im HUD (ZeroHud.xray). Cyberspace (Visuals): Himmels-Gitter + Sternschnuppen.
  - Visual FX: Shockwave (Block-Umrisse beim Landen) + Hit Splash (Tinten-Spritzer am Boden). Welt-Renderer render/FxRenderer
    (debugFilledBox, mit Tiefentest). Rain FX (Visuals): Embers/Water/Neon-Regen mit Boden-Spritzern.
  - Weapon Skins: neue eigene Messer Bayonet/Flip/Gut/Bowie (tools/make_weapons.py) + Swing Trail (Bogen-Spur beim Schlagen).
  - Nicht gemacht: Chroma (Welt grau außer um dich – bräuchte Post-Shader), Planet am Himmel. CS:GO-Skins nicht kopiert.

- 2026-11-08 (_45, Teil 17) – Combat-Runde + Spawner Bot:
  - Neu (Combat): Kill Aura, Trigger Bot, Aim Assist, Auto Clicker, Velocity (VelocityMixin auf ClientPacketListener,
    skaliert Rückstoß nach dem Setzen), Hitboxes (HitboxesMixin auf Entity.getPickRadius), Criticals (Auto-Jump),
    W-Tap, Auto Weapon (Hook in MultiPlayerGameModeMixin.attack HEAD), Auto Gap, Auto Pot, Auto Armor, Crystal Aura.
    Auto Totem von Player nach Combat verschoben. Gemeinsame Ziel-/Blick-Hilfen: util/Targets.
  - Spawner Bot (World): markierte Spawner (Setting.Waypoints, GUI-Knöpfe "Mark spawner" / "Run now" in waypointRows)
    alle X min öffnen -> Knopf-Item (Standard Dropper) klicken -> schließen; dann /order <item> -> oberstes Item klicken ->
    alle Items per Shift-Klick ins Menü. Zustandsautomat in SpawnerBotModule. Spawner müssen in Reichweite sein.
  - Getestet (Einzelspieler, ZERO_SELFTEST_ONLY=combat): Kill Aura trifft, Auto Armor zieht Helm an, Bot-Ablauf läuft
    durch und bricht sauber ab (kein Spawner-Menü / kein /order im Einzelspieler). Auf DonutSMP-Server NOCH NICHT getestet.

- 2026-11-08 (_45, Teil 18) – Spawner Bot nach Server-Test (ArticSMP) robuster:
  - Spawner-Menü: bis zu 3 Öffnungsversuche, wartet bis der Knopf (Dropper) im Menü geladen ist.
  - CLOSE_ALL: Esc so oft (max. 6), bis ~10 Ticks lang kein Menü mehr aufgeht (Server öffnet nach Abliefern das
    Sell/Orders-Menü erneut -> vorher hing der Bot darin). Esc liefert die Order ab.
  - Abliefern: alle passenden Stapel auf einmal per Shift-Klick (wie Shift-Doppelklick).
  - Neu: "Collect Ground Items" + Radius – wartet 1,5 s, läuft zu gedroppten Items (Dropper = "Dropped current page loot"),
    springt bei Hängenbleiben, überspringt unerreichbare Items nach 2 s, läuft zurück zum Startplatz (schleicht am Ende). Test ZERO_SELFTEST_ONLY=bones: 48/48 Bones, 0,2 b vom Start.

- 2026-11-08 (_45, Teil 19) – Spawner Bot Abliefern-Fix (Medal-Clip ArticSMP: Bones blieben im Inventar):
  Ursache: alle 36 Shift-Klicks im selben Tick, bevor der Server das Liefer-Menü geschickt hatte -> verworfen.
  DEPOSIT wartet jetzt auf containerMenu.getStateId() != 0, verschiebt 1 Stapel pro 2 Ticks, prüft den Rest;
  geht ein Stapel 3x nicht weg -> Aufnehmen + in freien Menü-Slot legen. Stoppt bei vollem Menü. Test ZERO_SELFTEST_ONLY=deposit
  (Truhe als Ersatz): 1344/1344 Bones abgeliefert, Menü geschlossen, Status zurück auf IDLE.

- 2026-11-08 (_45, Teil 20):
  - Tracers 3D (Ich-Perspektive: aus dem Fadenkreuz): Startpunkt sonst echte Punkte am Spieler ("start_3d": Body/Eyes/Feet, Standard Body; Crosshair/Bottom
    nutzen die ECHTE Kamera -> richtig in Freecam). Tracer-Schalter in Entity ESP, 2D ESP und Block ESP (EspRenderer).
  - Freecam: smartCull aus solange aktiv (keine verschwindenden Chunks in/hinter Blöcken), "Look Sensitivity" (100 % =
    Minecraft-Sensi, gleiche Formel wie Entity.turn x0,15). Selbsttests: ZERO_SELFTEST_ONLY=tracer / fcull.

- 2026-11-08 (_45, Teil 21): Spawner Bot "Auto Mark Spawners" (Standard an) + "Mark Radius" (5 b): alle 2 s Würfel
  um den Spieler scannen, neue Spawner markieren; abgebaute (geladen, < 48 b, kein Spawner mehr) aus der Liste nehmen.
  GUI-Knopf "Mark all". Test ZERO_SELFTEST_ONLY=automark: Spawner in 5 b gefunden, der in 12 b nicht.

- 2026-11-08 (_45, Teil 22): Spotify HUD (HUD-Kategorie, verschiebbar "spotify"): util/MediaInfo startet
  assets/quiqzy/media/media_watch.ps1 (Windows GlobalSystemMediaTransportControls über PowerShell 5.1, kein Login),
  liest Titel/Künstler/Position/Dauer/Status/Cover alle 0,5 s; Steuerung media_control.ps1 (toggle/next/prev, noch ohne Knöpfe).
  Lyrics synchron von lrclib.net (/api/get, Fallback /api/search), Position "Above Hotbar"/Top/Under Widget, Größe, Offset.
  Optionen: Album Cover, Hide When Paused, Spotify Only. Selbsttest ZERO_SELFTEST_ONLY=spotify (MediaInfo.injectTest).
  Mit echtem Spotify NOCH NICHT getestet (lief nicht); Cover nur getestet: Chrome lieferte keins.

- 2026-11-08 (_45, Teil 23): Spotify im Anubis-Stil: Lyrics Karaoke (ZeroHud.lyrics) – Wörter gleiten gestaffelt rein,
  gesungene Wörter werden grün (Wortzeit nach Länge auf 90 % der Zeilendauer verteilt), aktives Wort mit Glow,
  Umbruch auf mehrere Zeilen, nächste Zeile grau darunter, "• • •" vor dem ersten Einsatz. Widget neu: größeres Cover,
  Schein in Cover-Durchschnittsfarbe, Equalizer, Fortschritt mit Knopf, "PAUSED".

- 2026-11-08 (_45, Teil 24) – Crystal PvP: Crystal Optimizer (Standard an; MultiPlayerGameModeMixin attack RETURN ->
  ClientLevel.removeEntity), Surround (util/Place, Obsidian 4 Seiten + Boden, Center, Rotate, Disable When Moving),
  Auto Double Hand (Totem in Haupthand bei HP/Kristall in Nähe, füllt Hotbar-Totem-Slot nach), Key Pearl (Keybind = Perle
  werfen + zurück), Hole ESP (Bedrock grün / Obsidian gelb, FxRenderer.holes). GUI-Filter "Crystal" erweitert.
  Test ZERO_SELFTEST_ONLY=cpvp: alles bestanden (Kristall sofort weg, 4/4 Obsidian, Totem in Hand, 2 Löcher erkannt).

- 2026-11-08 (_45, Teil 25) – Anchor PvP: Safe Anchor (Anchor-Modul "Safe Anchor", Standard an; util/AnchorUtil.placeShield:
  Glowstone zwischen dich und den Anchor, bei Anchor direkt neben dir auf den Anchor drauf), Anchor Aura (neben Gegner
  setzen -> laden -> Schild -> sprengen; Mobs-Schalter zählt auch Tiere), Hit Crystal (Rechtsklick mit Obsidian:
  Obsidian, Kristall, schlagen). Test ZERO_SELFTEST_ONLY=anchor bestanden. Hinweis Test: ingame_setup legt ein Schwert
  nur clientseitig in den aktiven Slot -> im Test Items auch clientseitig setzen.

- 2026-11-08 (_45, Teil 26): GUI 3D-Kippen (QuiqzyConfig.gui3d, Settings "3D tilt"): ZeroGui.tiltMatrix (Verkürzung +
  Scherung + Versatz zur Maus, affin, da GuiGraphics nur 2D-Posen kann), Maus per inverser Matrix (toPanel) zurückgerechnet
  in render/mouseClicked/mouseReleased; Schatten gegen die Neigung, Lichtreflex (drawGlare) folgt der Maus. Pausiert beim
  Ziehen von Fenster/Slider/Farbwähler. Test ZERO_SELFTEST_ONLY=tilt.

- 2026-11-08 (_45, Teil 27c): Triple Tap final nach Clip + Nutzer: Sprint-Hit mit Totem in der Hand -> Obsidian + Launch-
  Kristall -> Air Hit mit AXT (wartet hurtTime==0) -> 2 Kristalle auf dasselbe Obsidian (je hurtTime==0). Settings:
  "Sprint Hit (Totem)", "Air Axe Hit", "Wait For Damage Tick". Test: Reihenfolge stimmt (3 Kristalle, 1 Axtschlag).
- 2026-11-08 (_45, Teil 27b, ersetzt): Triple Tap umgebaut auf die Reihenfolge aus dem Video: Launch-Kristall -> Air Sword Hit ->
  2 Pop-Kristalle; jeder Schritt wartet auf target.hurtTime == 0 ("Wait For Damage Tick"), Timeout 10 s.
- 2026-11-08 (_45, Teil 27, alt): Triple Tap (Combat, Crystal-Filter): Keybind -> Schwertschlag, Obsidian neben Ziel
  (vorhandenes Obsidian/Bedrock bevorzugt), Kristall setzen+schlagen (Launch), dann "Extra Crystals" (2) mal wiederholen
  (Double Pop); wartet auf Server-Bestätigung von Obsidian/Kristall, Timeout 6 s, schaltet sich danach aus.
  Test ZERO_SELFTEST_ONLY=triple: 3 Kristalle, 1 Obsidian, Ziel 40 -> 1 HP.

- 2026-11-08 (_45, Teil 28c): Loader-Crash gefixt (Hover-Index -> mouse.Contains, vorher IndexOutOfRange -> halbe Malerei/"Striche").
  Look voller: Hero-Vorschaubild (tools/loader/preview.png aus t_ingame_setup) hinter großem ZERO-Banner, CLIENT + Tagline,
  Chips (ONE CLICK / FABRIC 1.21.11), Fenster 620x574. Build jetzt mit -resource:preview.png,preview.png.
- 2026-11-08 (_45, Teil 28b, alt): Loader-Style komplett neu (tools/loader/ZeroLoader.cs), ZERO-Look: dunkel, Ember-Glow,
  schwebende Funken, Glas-Karten-Liste mit LOADED-Badges + Akzentbalken, großer Load-Knopf mit Glow + Sheen, Ghost-Buttons,
  randloses Fenster. WICHTIG: System-csc ist C#5 (kein ?., kein $"-String, keine expression-bodied members).
  Inject-Machbarkeitstest (tools/inject, gelöscht) funktionierte (Tick-/HUD-Payload live), aber Mixins/Shader brauchen
  Neustart -> keine volle Inject-Version gebaut.
- 2026-11-08 (_45, Teil 28, alt): "ZERO Loader.exe" (FERTIG/, Quelle tools/loader/ZeroLoader.cs, C# WinForms, gebaut mit
  .NET-Framework-csc: csc -target:winexe -resource:<jar>,zero.jar -r:System.Management.dll ...). Findet Profile
  (Vanilla, NoRisk D:\minecraft norisk\profiles + AppData, Modrinth, Prism, Feather .dawn, eigene via "Other…"),
  Load = eingebettete JAR in mods kopieren (alte zero-client*.jar weg), Unload, Folder; erkennt laufendes Spiel (WMI).
  KEIN Injizieren in laufende Prozesse. Bei jedem neuen Build die EXE mit der neuen JAR neu kompilieren.

- 2026-11-08 (_45, Teil 29): Cart Aura (Combat, TNT-Minecart-PvP): Schiene neben Ziel -> TNT-Minecart drauf -> zünden
  (Schlag oder Feuerzeug). "Auto Ignite Nearby" zündet gesetzte Carts in Reichweite, "Safe (Obsidian)" setzt Block dazwischen.
  Braucht Schiene + TNT_MINECART in der Hotbar. cartAt-Box auf Rail-Höhe (nicht .above()). Zündung = gameMode.attack ->
  server hurtServer -> primeFuse. Test ZERO_SELFTEST_ONLY=cart (Gameregel tntExplodes muss an sein): Schwein 20->10.
  Hinweis: InjectEntry wieder entfernt (Nutzer: "scheiß auf inject"), ZeroRender hat jetzt Reflection-Fallback für
  zero$beginElement (beginElement per Signatur gesucht) - normaler Build nimmt weiter den Mixin-Pfad.

- 2026-11-08 (_45, Teil 30): Flame Cart (Combat): erkennt eigene (Flammen-)Pfeile, simuliert die Flugbahn (Schwerkraft 0,05,
  Luftwiderstand 0,99, level.clip) bis zum Einschlag und setzt dort Schiene + TNT-Minecart OHNE Blickdrehung
  (clickOn: Schiene auf Block darunter, Cart auf die Schiene; 4-Tick-Gap für Sync). Flammenpfeil zündet den Cart.
  Settings: Max Distance, Any Arrow, Hit Ignite Fallback. Weit-Platzieren geht nur auf Servern ohne Reichweiten-Check.
  Cart Refill (util/Refill.keep per SWAP-Klick ohne Inv zu öffnen) in Cart Aura + Flame Cart: hält Cart-/Schienen-Slot gefüllt.
  Test ZERO_SELFTEST_ONLY=flamecart: rails=1 carts=1 am Pfeil-Einschlag.

- 2026-11-08 (_45, Teil 30b): Flame Cart nach Server-Clip: Platzierung schneller (gap 1 statt 4), Rail/Cart-Slot wird
  WIRKLICH in die Main Hand genommen und erst nach ~3 Ticks zurückgewechselt (savedSlot/holdRevert), "Hit Ignite Fallback"
  Standard jetzt AUS (nur der Flammenpfeil zündet). Test weiter rails=1 carts=1.

- 2026-11-08 (_45, Teil 30c): Flame Cart: Platzierung jetzt im SELBEN Tick (Schiene+Cart, Server ordnet Pakete),
  "Totem Before Boom" (totem_swap/totem_range) in Flame Cart + Cart Aura: bei scharfem TNT-Minecart in Reichweite
  Totem in die Hand. WICHTIG: isPrimed()/getFuse() kommt nur beim Client an, wenn tntExplodes an ist UND der Cart
  schon in der Welt ist (erst addFreshEntity, dann primeFuse). Test: slot wechselt auf Totem, fuse zählt runter.

- 2026-11-08 (_45, Teil 31): Kit-Module (Combat): Auto Jump Reset (springt bei hurtTime-Anstieg / optional beim eigenen
  Schlag via MultiPlayerGameModeMixin), Shield Breaker (auf blockenden Gegner kurz Axt -> Schlag -> zurück), Auto Shield
  (hebt Offhand-Schild bei Kristall/Cart/Pfeil in Reichweite, keyUse). NICHT in-game getestet (nur kompiliert, Usage sparen).

- 2026-11-08 (_45, Teil 32): Mehr Module: Auto Pearl (Perle bei niedrigem HP), Auto Clutch (Wassereimer beim Fallen),
  Auto Drain (schnelle Axt-Schläge aufs Schild), Fast Bow (abfeuern bei voller Spannung + neu spannen). Nur kompiliert,
  nicht in-game getestet (Usage sparen). Auto Respawn gab es schon (Datei war überschrieben -> wiederhergestellt).

- 2026-11-08 (_45, Teil 33): Trajectories (Visuals, FxRenderer.trajectories, Billboard-Punkte + Landering, Physik je Item:
  Bogen/Armbrust/Dreizack 0.05 Gravity, Perle/Snowball/Egg/Pots 0.03) und Auto Sprint (Movement). Trajectory-Punkte
  im Test sichtbar (kurzer Bogen auf nahe Wand; senkrechte Strahlen im Bild = Sus Chunk, nicht Trajectory).

- 2026-11-09 (_45, Teil 34): Base Finder gegen Anti-Xray (DonutSMP zeigt nur bis Deepslate): Mixin ChunkBlockEntitySpyMixin
  (@ModifyArg auf ClientPacketListener.updateLevelChunk -> replaceWithPacketData Consumer, index 4) greift jede Block-Entity
  aus dem Chunk-Paket ab (onServerBlockEntity), auch versteckte unter Deepslate -> packetBEs je Chunk. scan() zählt diese
  mit (bePoints), kein Doppelzählen mit sichtbaren Blöcken. Settings: "Scan Block Entities", "Only Below Deepslate" (an),
  "Deepslate Y" (0), "Deep Boost" (5x). onlyDeep überspringt Sektionen/Blöcke/BEs über deepY. Regressionstest (base) ok vor onlyDeep.

- 2026-11-09 (_45, Teil 35): Base Finder "Only Below Deepslate" (an): onlyDeep überspringt Sektionen/Blöcke/BEs ab deepY (0).
  Smart Filter (an) gegen Rauschen: Chunk zählt nur mit Spieler-Signal (shulkers/beacons/anchors/anvils/ench.tables/brewing/redstone)
  ODER >= minContainers (4) Containern (chests+barrels+shulkers+ender+hoppers), sonst verworfen (einzelne natürliche Truhe = Rauschen).

- 2026-11-09 (_46): Neues Modul **Sound Chunks** (World): liest auf welchem Weg Maschinen-Sounds zum Client kommen und markiert
  die Chunks (orangener Chunk-Kasten + Beam + Maschinen-Boxen, wie Sus Chunks). Sound-Zugänge in 1.21.11:
  - Ofen/Smoker/Hochofen/Campfire: **kein Server-Paket** – Server spielt in animateTick CLIENTSEITIG via
    ClientLevel.playLocalSound → Mixin ClientLevelSoundSpyMixin (require=1; Level-Mixin reicht nicht, ClientLevel überschreibt
    ohne super). Erkennung per Sound-ID (block.smoker.smoke, block.furnace.fire_crackle, ...).
  - Crafter/Brewing/Dispenser: ClientboundLevelEventPacket (LevelEvent.SOUND_CRAFTER_CRAFT/FAIL, SOUND_DISPENSER_DISPENSE/
    PROJECTILE_LAUNCH, SOUND_BREWING_STAND_BREW) → Mixin SoundEventSpyMixin.handleLevelEvent.
  - Positionierte Server-Sounds: ClientboundSoundPacket → SoundEventSpyMixin.handleSoundEvent.
  - Dateien: module/world/SoundChunkModule.java, render/SoundChunkRenderer.java, hud "Sound Chunks" in ZeroHud.sounds(),
    Mixins SoundEventSpyMixin + ClientLevelSoundSpyMixin (+json). Selftest "sounds": Smoker+Ofen vor Spieler werden per
    Reflection gezündet (litTimeRemaining), Log "sound marked chunks/sus/kinds" → Verifiziert: 1 sus Chunk, smoker x16,
    HUD + Beam sichtbar (Screenshot t_snd1.png). Settings: smoker/crafter an, furnace/blast_furnace/campfire/dispenser aus,
    min_sounds 1, forget 10 min.

- 2026-11-09 (_48):
  - **Spawner Nametags** (Visuals / Nametags): Nametags über gefundenen Spawnern mit Mob-Icon (Schädel bei Skelett/Wither/Zombie/Creeper/Piglin/Drache, Drop-Items wie Blaze Rod/Spider Eye/Perle/Magma/Slime/Breeze, Spawn-Egg für alle weiteren Mobs), Typ ("Skeleton Spawner" etc.), Entfernung ("18m") und Koordinaten. Schalter "Spawners" auch im Nametags-Modul.
  - **Spawner Noti** (World): Popup-Benachrichtigung oben im Bildschirm bei neu entdeckten Spawnern ("SPAWNER GEFUNDEN! [Icon] Skeleton Spawner • X Y Z (Distanz)") mit Ton (UiSounds.found()).
  - **Beacon Beam**: Leuchtender Strahl direkt über dem Spawner bis zum Build-Limit (mc.level.getMaxY()), optional durch Wände sichtbar (SpawnerRenderer).
  - **Spawner Tracker**: erkennt Spawner aus Anti-Xray-Chunk-Paketen (ChunkBlockEntitySpyMixin), geladenen Chunks und Block-Updates (ClientLevelMixin).
  - Bugfix BaseRadarModule (Kompilierfehler Namenskonflikt clusters behoben).

- 2026-11-09 (_49):
  - **Individuelle Standardfarben in Block ESP**: Jeder Block hat beim Auswählen eine passende, individuelle Standardfarbe (z. B. Ender Chest violett #9B51E0, normale Chest warmes Orange #FFA733, Trapped Chest rotorange, Shulker Boxes nach Farbtyp, Erze/Metalle in Diamantcyan, Smaragdgrün, Goldgelb, Kupfer etc.). Eigene Farbanpassung per Rechtsklick bleibt erhalten und setzt sich beim Reset auf die individuelle Standardfarbe zurück.

- 2026-11-09 (_50):
  - **Freecam Keep Movement**: Neues Setting "Keep Movement" (Standard an) in Freecam. Wenn man läuft, sprintet, springt oder sneakt und Freecam aktiviert, läuft/sprintet/springt/sneakt der eigene Spieler-Körper nahtlos weiter, während die Kamera frei fliegt. Beim Ausschalten der Freecam wird die Bewegung wieder freigegeben.

- 2026-11-09 (_51):
  - **HUD & Visuals Rework (Apple Frosted Glass & Kompakt)**:
    - Alle HUD-Elemente und Panels auf kompaktes Apple-Glas-Design überarbeitet: transluzenter Frosted-Glass-Backdrop mit dezentem oberen Lichtreflex und feinen 0.5px-Rändern.
    - Notifies & Popups kompakter als schwebende Floating Pills (Dynamic Island Style, einzeilig, dezent und nicht mehr im Sichtfeld störend).
    - Nametags (Spieler, Mobs, Items, Spawner) verschlankt: ~25 % kompaktere Höhe, kleinere Schrift (7.0pt), subtileres Glas-Backplate.
    - Chunk- & Spawner-Markierungen verfeinert: Schlankere Beams (0.18b statt 0.35b) und feinere Kanten (0.025b), weichere Transparenz gegen Blendung.

- 2026-11-09 (_52):
  - **CatLean-inspirierte Nametags & HUD-Details**:
    - Nametags nach Tiefe/Distanz sortiert (nähere Spieler verdecken weiter entfernte, wie in CatLean).
    - Ping-Anzeige mit Farbcodierung (grün < 50ms, gelb < 120ms, rot >= 120ms), ein-/ausschaltbar im Nametags-Modul.
    - Ausrüstungs-Vorschau mit Minecraft-Item-Dekorationen (Haltbarkeitsbalken & Stack-Größe) über dem Nametag.
    - Aktive Trankeffekte (Potion Effects) als kompakte Glas-Badges unter dem Spielernamen (Name, Stufe, Dauer), ein-/ausschaltbar.
    - Vollständig abgestimmt auf den ZERO Client Frosted Glass- & Orange-Akzentstil.

- 2026-11-09 (_53):
  - **Volles CatLean Nametag-System & CatLean GUI/HUD-Stil**:
    - **Neuer GUI/Client-Style "CatLean"**: In `AccentModule` neuer Preset & HUD-Style "CatLean" (Signatur Electric Lean Violett #A855F7 & Neon Magenta #D946EF mit tiefem Obsidian-Glas).
    - **Vollständiges CatLean Nametag**:
      - Durchgehender Health-Balken direkt am unteren Nametag-Rand mit dynamischer Farbgebung.
      - Ausrüstung mit Verzauberungs-Badges über den Items (P4, U3, M, S5, Sharpness etc.) und Haltbarkeitsbalken.
      - Gamemode-Tag ([S], [C], [A], [SP]), Ping, Herz-Symbol (❤) und Pop-Counter.
      - Potion-Badges mit Farbpunkten passend zum Trankeffekt.
    - **Alle HUD-Elemente im CatLean-Stil**:
      - **Watermark**: Sleek Kapsel `[ • ZERO | v1.0.0 | User | FPS | Ping ]` mit pulsierendem Violett-Punkt und Gradient-Rand.
      - **ModuleList / ArrayList**: Modus-Suffixe in eckigen Klammern `[Mode]` (z. B. `KillAura [Smooth]`), fließender Neon-Farbverlauf und 1.5px Neon-Balken.
      - **TargetHUD**: Obsidian-Karte mit lila Glow, Kopf-Avatar, Distanz-Tag, sanfter Lebensbalken mit Schadens-Trail.
      - **Info HUD**: Kompakte Kapseln mit lilafarbenen Labels und weißen Werten.

- 2026-11-09 (_54):
  - **CatLean Grau-Design & 1:1 identische Spawner- und Player-Nametags**:
    - **Reines CatLean Dunkelgrau-Design**: `Theme.tint` wandelt warme/orange Clientfarben und bräunliche Hintergründe direkt in minimalistisches, cleanes Charcoal/Dunkelgrau (`#141418`), Slate-Ränder (`#3F3F46`) und Silber-Weiß (`#D4D4D8`/`#FFFFFF`) um.
    - **1:1 identische Nametags für Spawner & Player**:
      - Exakt selbe Abmessungen (13.5pt Höhe, 4.5pt Radius), dunkles Grau-Backplate (`0xD8141418`) und dezenter grauer 0.6px-Rand (`0x553F3F46`).
      - Spawner-Nametag hat nun dasselbe Pill-Layout mit Icon, fettem weißen Mob-Namen, Distanz in cleanem Grau (`#A1A1AA`), Koordinaten und dezenter Bodenlinie wie beim Player-Nametag.
      - Player-Nametag hat ebenfalls das cleane CatLean Dunkelgrau-Design (weißer Name, Ping, Gamemode, Health-Bar, Enchants).

- 2026-11-11 (_56): **Base Finder Research** (neues World-Modul, Forschungs-Modul mit Confidence-Scores):
  - Abgrenzung: klassisches **Base Finder** bleibt unverändert (Score-Scan); das hier ist die
    Forschungsvariante mit sauberen Confidence-Werten statt Fund-Behauptungen.
  - **1) Position & Chunk-Tracking**: neuer Mixin `ClientPacketListenerMixin`
    (`handleMovePlayer` HEAD, require 0) – `ClientboundPlayerPositionPacket` =
    server-bestätigte Position + Zähler für Korrekturen/Rubberbands. Bei jedem Chunk-Load wird
    Spieler-Y + Menge an Untergrund-Sections protokolliert → geschätzter **Data-Y-Schwellwert**
    (ab welchem Y der Server Untergrund-Daten mitschickt; Anzeige nur bei konsistenter Stichprobe).
    Rescan-Vergleiche zählen "Data-Gain"-Events (Untergrund-Sections nehmen zu).
  - **2) Chunk-Update-Analyse**: Load/Unload-Tracking + Block-Updates im Untergrund (y<0) über den
    erweiterten `ClientLevelMixin`-Hook.
  - **3) Untergrund-Struktur-Erkennung** (nur Sections unter Oberfläche, Palette-Vorabcheck):
    - Rechteckige Räume: größtes Luft-Rechteck pro Ebene (Histogramm-Algorithmus), Decke/Boden-
      Soliditäts-Probe (3x3) → Conf 0.30+0.45·above+0.25·below, <0.45 verworfen (vermutlich natürlich)
    - Tunnel: horizontale Luft-Korridore ≥ Mindestlänge, oben+unten fest, Conf nach Länge
    - Indikator-Blöcke mit Gewichten (Kiste 18, Bett 20, Öfen/Werkbank 15, Leitern 10, Fackeln 4 ...)
    - **Mineshaft-Unterscheidung**: ≥8 Zäune + ≥6 Schienen → Confidence auf 30 % gedeckelt
  - **4) Research Dashboard** (ZeroHud, grün): Client-Y + server-Position (frisch/alt) + Correction-
    Zähler, geladene Chunks + Section-Statistik (unter/total), Struktur-Anzahl + Top-Confidence,
    Data-Y-Schätzung + Gain-Events.
  - **Renderer** (`BaseFinderResearchRenderer`, eigene Through-Walls-Pipeline
    `pipeline/base_research_through_walls`): Chunks über Min. Confidence, Farbe nach Confidence
    (rot→grün), Struktur-Boxen: Raum türkis / Tunnel orange / Indikator-Cluster gelb.
  - Settings: Scan Radius, Scan Budget, Track Position/Chunks, Rectangular Rooms, Tunnels,
    Indicator Blocks, Min. Room Size, Min. Tunnel Length, Min. Confidence, Overlay, Through Blocks,
    Overlay Style, Overlay Opacity, Mark Color (0x8CF56B), Structure Boxes, Research Dashboard.
  - Neue Dateien: `module/world/BaseFinderResearchModule.java`, `render/BaseFinderResearchRenderer.java`,
    `mixin/ClientPacketListenerMixin.java` (+ mixins.json-Eintrag).
  - Geändert: `ModuleManager`, `QuiqzyClient`, `ClientLevelMixin` (+1 Zeile), `ZeroHud` (+Dashboard).

- 2026-11-11 (_56): **Activity Inspector** (neues World-Modul, Anti-Xray-Bypass über Spieler-Aktivität):
  - Idee: Anti-Xray versteckt Erze, aber **Luft wird nie ersetzt** – gegrabene Schächte sind echte
    Client-Daten und damit nicht versteckbar.
  - Analyse pro Chunk: 16×16 Spalten von der Oberfläche abwärts (Heightmap-Sprung, kein Sky-Walk),
    vertikale Luft-Runs zwischen festen Blöcken = "Loch nach unten" (jede Größe zählt, ab Mindesttiefe).
  - Klassifikation: Luft-Nachbarn an Mittel- + Unterzelle (8 Samples): ≤ 4 = **enger Schacht**
    (1x1 = 0 Nachbarn, 3x1/2x2 = 4), ≥ 5 = offenes Loch (Höhle/Ravin).
  - **Chunk wird markiert, wenn: mindestens 1 Loch UND insgesamt nur wenige Löcher ≤ Max. Holes**
    (Löcher egal welcher Größe zählen: 1x1/3x1-Schächte wie offene Löcher; kein natürliches
    Höhlensystem → verdächtig). Setting **Only 1x1/3x1 Shafts** (Standard aus) schärft auf enge
    Schächte; enge Schächte werden weiterhin separat gezählt und als Würfel markiert.
  - **Markierung wie Xray Research** (`ActivityInspectorRenderer`, eigene Through-Walls-Pipeline
    `pipeline/activity_inspector_through_walls`): Chunk-Box Fill + pulsierende Kanten über dem
    Tiefenbereich der Schächte + kleine Würfel an jeder Schacht-Oberkante.
  - Settings: Scan Radius, Scan Budget (Standard 2/t), Min. Shaft Depth (4), Max. Holes (6),
    Chunk Overlay, Through Blocks, Overlay Style, Overlay Opacity, Mark Color (0x4FC3F7, Cyan –
    unterscheidet sich vom Xray-Research-Lila), Shaft Boxes.
  - Neue Dateien: `module/world/ActivityInspectorModule.java`, `render/ActivityInspectorRenderer.java`.
  - Geändert: `ModuleManager` (+Feld), `QuiqzyClient` (+2 Chunk-Events, +Renderer-Registrierung).
  - Performance: Budget-Scan pro Tick, Rescan alle 20 s, Radius-Pruning; keine HUD-/Mixin-Änderungen.

- 2026-11-11 (_56): Xray Research Fix für Anti-Xray-Server (DonutSMP):
  - **Rare Ore Boxes + Reveal Boxes** werden jetzt in **allen** analysierten Chunks gezeichnet,
    nicht nur in Chunks über dem Highlight-Schwellwert (auf Anti-Xray-Servern kommen kaum
    Chunk-Dichten zustande, aber einzelne freiliegende Erze/Reveals trotzdem).
  - **Highlight Threshold** Standard 24 → 8 Erze.
  - Hinweis: Paper-Anti-Xray schickt nicht freiliegende Erze gar nicht erst an den Client –
    die Modul-Philosophie bleibt: nur tatsächliche Client-Daten anzeigen, keine erfundenen.

- 2026-11-11 (_56): **Xray Research** (neues World-Modul, selbstständig, keine bestehenden Module angetastet):
  - **Chunk Data Analyzer**: Chunk Load/Unload-Tracking (Fabric `ClientChunkEvents`), Section-Scans mit
    Palette-Vorabcheck (`maybeHas`, überspringt fast alles), Scan-Budget pro Tick (1–8, Standard 3),
    Rescan alle 20 s, Block-State-Update-Tracking über `ClientLevelMixin.sendBlockUpdated`
    (zusätzlicher Hook-Zeil; Spawner/Block-ESP unverändert) inkl. **Reveals** (Stein → Erz beobachtet).
  - **Block Analysis**: 11 Erz-Typen (Coal/Iron/Copper/Gold/Redstone/Lapis/Diamond/Emerald/Quartz/
    Nether Gold/Ancient Debris) pro Chunk gezählt + Rare-Positionen (Diamond/Emerald/Debris, max 64)
    + laufende Umgebungs-Dichte-Statistik (EMA) im eigenen Daemon-Thread (`ZERO Xray Research`),
    unveränderliche Snapshots via `ConcurrentHashMap` publiziert.
  - **Actual vs. Predicted**: gescannte Chunks = *tatsächliche Client-Daten* (100 % Confidence);
    geladene, nicht gescannte Chunks bekommen eine *statistische Vorhersage* (Vanilla-Dichte-Tabelle
    gemischt mit beobachteter Umgebungsdichte) und eine Confidence aus der Sample-Basis (25–95 %).
  - **Research Overlay** (`XrayResearchRenderer`, `WorldRenderEvents.BEFORE_TRANSLUCENT`, eigene
    Through-Walls-Pipeline `pipeline/xray_research_through_walls`): analysierte Chunks als Fill/Edges
    (Style Choice "Fill + Edge"/"Edges"/"Fill"), Farbe nach **Confidence** (rot→grün) oder Akzentfarbe,
    Vorhersagen gedämpft mit Puls, optionale Rare-Ore- und Reveal-Boxen, Overlay-Opacity 10–100 %.
  - **HUD-Panel** in `ZeroHud` (Xray-Research-Purpur `#A78BFA`): Top-3-Chunks mit Distanz, Top-2-Erzen
    und Confidence-% + 1 Vorhersage-Zeile ("predicted ~N ores") + Footer "Actual N · Predictions M · upd/s".
  - Settings: Scan Radius, Scan Budget, Chunk Overlay, Through Blocks, Overlay Style, Overlay Opacity,
    Highlight Threshold (min. Erze), Color By, Accent Color, Rare Ore Boxes, Show Predictions,
    Reveal Boxes, Track Block Updates, Info Panel.
  - Neue Dateien: `module/world/XrayResearchModule.java`, `render/XrayResearchRenderer.java`.
  - Geänderte Dateien: `ModuleManager` (Feld), `QuiqzyClient` (2 Chunk-Events + Renderer-Registrierung),
    `ClientLevelMixin` (1 Hook-Zeile), `ZeroHud` (Imports + Panel-Hook + `xr()`-Panel).

- 2026-11-11 (_56): Hand Shader > Item Glow (wie Gooba) – **Shell-Ansatz, sitzt auf dem Item**:
  - Erster Versuch (Vanilla-Outline-Pfad, `outlineColor` bei `ItemStackRenderState.submit`) war
    versetzt (Vanilla `ItemFeatureRenderer` zeichnet den Outline-Pass mit anderer Pose-Kette) → verworfen.
  - Neu: eigene Glow-"Huelle" in `HandGlowRenderer` – die `BakedQuad`s jeder Item-Ebene werden vom
    eigenen Zentrum aus aufgeblasen (Glow Size, %, 2–50, Standard 10) und als halbtransparente
    Position-Color-Geometrie mit **exakt derselben Pose** wie das Item gesubmitet
    (`submitCustomGeometry`, eigener RenderType/Pipeline aus `DEBUG_FILLED_SNIPPET`, kein
    Depth-Write, NO_DEPTH_TEST) – der Glow sitzt dadurch immer auf dem Item, für beide Hände und
    alle Items mit normalen Modellen. Redirect auf `ItemStackRenderState.submit` in
    `ItemInHandRenderer.renderItem` bleibt der Hook.
  - Settings: **Item Glow** (Bool), **Glow Color** (0xA78BFA), **Glow Opacity** (20–100 %, 80),
    **Glow Size** (2–50 %, 10).
  - Neue Mixins: `ItemStackRenderStateAccessor` (displayContext, layers, activeLayerCount) und
    `LayerRenderStateAccessor` (quads, transform) – in `quiqzy.mixins.json` registriert.
  - Kein Glow bei Special-Modell-Items (Schild, Elytra, Dreizack) – die haben keine BakedQuads.
- 2026-11-10 (_56): Hand Shader Swing-Styles (Gooba) + Spawner-Beam neu:
  - **Hand Shader > Swing** (Setting.Choice "Off/First..Sixth"): ersetzt die Vanilla-Swing-Animation
    der Hand. Dazu **Intensity** (%, Standard 100) und **Swing Speed** (%, Standard 100). Mixin-Redirects
    in `ItemInHandRendererMixin` (`swingArm` + `applyItemArmTransform` ordinal 4, keine MixinExtras)
    Formeln 1:1 aus der Gooba-Referenz-JAR (Ease: easeOutSine / linear / easeOutBack), Crossbows vanilla.
    (Wurde zwischendurch mal entfernt, danach auf Wunsch wieder drin.)
  - **Spawner Noti > Beam** (Vollausbau, alles einstellbar):
    - Beam Tooltip: **Beam Top** = "Build Limit" / "Player +64" / "Fixed" (+ **Fixed Beam Height** in Blöcken)
    - **Beam Width** (%, 100 % = exakt 1×1 Block, bis 300 %), **Beam Opacity** (5–100 %)
    - **Pulse Animation** (Bool) + **Pulse Speed** (10–300 %, Periode ~2,65 s bei 100 %) + **Pulse Strength** (0–100 %)
    - **Core Beam** (heller innerer Strahl wie beim echten Beacon) mit **Core Color**, **Core Width** (0–50 %) und **Core Opacity** (5–100 %)
    - Strahl startet an der Spawner-Blockoberkante; Box/Through Blocks unverändert.

## Offen / Nächste Schritte
- Item Skins im echten Spiel prüfen (nur Selbsttest): Spitzhacke/Totem-Optik in der Hand, Totem-Pop,
  ggf. weitere Items (Schwert, Äxte, Rüstung) als Christmas-Variante ergänzen.
- 2026-11-10 im Spiel testen: Hand Swing (6 Styles, Intensity/Speed) und neuen Spawner-Beam ansehen
  (Box/Walls-Rendering unverändert).
- Alles von heute im Spiel testen (noch nicht getestet): Grid-Animation, Glows, Sounds, neues HUD, Waypoint-Marker,
  Block-Farben per Rechtsklick, Sus-Chunk-Anzeichen.
- Stun Slam im Spiel testen (noch nicht getestet).
- Breach Check: genaue Bedeutung im Video war abgeschnitten – ggf. anpassen.
- Sound Chunks: Crafter/Brewing/Dispenser-Weg (Level-Events) ist implementiert, aber im Selbsttest noch nicht live verifiziert
  (nur Smoker-Weg getestet). Auf einem Server mit Crafter/Dispenser prüfen.

## 2026-11-13 Instant Launch (Spear) Fixes
- Launch-Taste + Spear-Hotbar-Taste gleich (z.B. R): Swap Back nutzt jetzt den Slot von Tick-Beginn (vor Vanilla-Hotbar-Wechsel) -> geht korrekt zurueck.
- Modul-Keybind + Minecraft-Keybind auf gleicher Taste: max. 1 Launch pro Tick.
- Modul-Keybind launcht immer (nicht nur wenn Modul an); Swap Back laeuft in beginTick (auch Modul aus).
- KeyboardHandlerMixin (keyPress HEAD) -> ModuleManager.PRESSED: kurze Tipper (<1 Tick) gehen nicht mehr verloren (gilt fuer ALLE Modul-Keybinds).
- Launch Item leer -> jeder Speer (ItemTags.SPEARS).
- Hinweis: INSPECT (Weapon Skins) ist default auf R.

## 2026-11-14 Glass-Update (Sounds, Glass-Theme, Visuals-Fixes)
- **Sounds** (util/UiSounds.java) komplett neu synthetisiert: Style-Setting jetzt wirklich aktiv (`style_v2`: Glass/Soft/Pling/Click),
  Glas-Teiltöne, Tap, Whoosh, Stereo-Hall (Freeverb-artig), Normalisierung. Keys (Creamy Keys) eigener Sound.
- **Glass-Theme** (Accent `preset_v2`, Standard "Glass"): alles farblos (Theme.hsv s=0, Theme.tint -> toGrey).
  HUD-Panels = `ZeroRender.frost` (neuer Shader zero_frost: nur Blur + graue Tönung, KEIN Rand/Glanz – Wunsch des Users).
  HUD-Blur pro Frame (`AuroraHud.captureBlur`, Setting "HUD Glass Blur"). Farbfelder im GUI unter noTint.
- **HUD-Widgets** neu (AuroraHud): Kapsel-Info, Keybinds, Effects, Speed, Notifications (Insel), Leben/Hunger-Kapseln, Dock-Hotbar.
- **GUI**: Linksklick = Modul an/aus, Rechtsklick = Settings. Glass-Fenster, Glass-Preset-Karte, 6 Spalten Themes.
- **Glossy Floor gefixt**: (1) Kamera-Offset fehlte, (2) Quads wurden per Backface-Culling verworfen (withCull(false)),
  (3) Shader = echtes SSR mit kopiertem Tiefenpuffer + Binärsuche.
- **Hand Glow neu**: render/HandGlowPass – Screen-Space aus Tiefenpuffer nach dem Hand-Draw
  (Hook: GameRenderer.renderLevel nach BufferSource.endBatch ordinal 0; in 1.21.11 wird die Hand erst dort gezeichnet).
  Alter "aufgeblasene Quads"-Glow entfernt (ergab Rechtecke). Settings: glow_size_v2, glow_inner, glow_color_v2, glow_theme_v2.
- **Neu: Glow Wings** (GlowWingsModule + GlowWingsRenderer): Angel/Butterfly/Blade, Knochen + Federn, 3 Glow-Schichten.
- **Neu: Clean F5** (CleanF5Module, Mixins in FreecamMixins.CameraMixin): getMaxZoom + Redirect setRotation in setup.
- Selbsttest: `ZERO_SELFTEST_ONLY=glassfx` (gx_hand/gx_f5/gx_gui/gx_detail/gx_themes).
- Offen: alles nur im Selbsttest geprüft, nicht mit Sodium/NoRisk live.
- 2026-11-14 später: Hand Glow auf Maske + Dual-Kawase-Blur umgebaut (zero_glow_mask.fsh, HandGlowPass.ensure/blit) – vorher Ring-Abtastung -> Streifen.
  Leben/Hunger-Leisten wieder exakt wie vor dem Glass-Update (User-Wunsch), Hotbar = graues Frost-Glas + graue Auswahl.
- GUI im Glass-Theme ohne Kanten (ZeroRender.noStroke waehrend AuroraGui.render, Fenster = frost).
- Sounds: neuer Standard-Style "Minimal" (style_v3) – kurze weiche Tock/Tick-Klaenge ohne Hall; User fand Glass-Glocken schlecht.
- 2026-11-14 abends: **Custom Sky** (CustomSkyModule, render/SkyRenderer, zero_sky.fsh + zero_snow.fsh): 9 prozedurale Himmel
  (Planets/Snowy/Moon/Aurora/Sunset/Nebula/Galaxy/Storm/Theme), Vollbild mit gl_FragDepth=1 + LEQUAL nach dem Terrain,
  Nebelfarbe = Horizont (FogRendererMixin). Snowy = fallender Schnee-Overlay.
- **Entity ESP**: neuer Standard-Style "Shader" (style_v3): Modelle zusätzlich flach in eigenes Target (OutputTarget "zero_esp_mask"),
  BEFORE_ENTITIES leeren, AFTER_ENTITIES Füllung + Umriss (zero_esp_composite). Style "Chams" = Skin eingefärbt (zero_chams).
  Hook: ChamsMixins (Redirect submitModel in LivingEntityRenderer.submit). Rüstung/Items in der Hand nicht in der Silhouette.
- Planeten realistisch neu (gasGiant mit Ringprofil/Cassini/AA, earthPlanet, Mond mit AA, Eisriese in Nebula).
- **Alte GUI entfernt**: Right Shift öffnet immer AuroraGui (vorher an HUD-Style gekoppelt -> Bug: HUD Style umstellen öffnete alte GUI).
  "Classic GUI"-Knopf weg. Listen (Blocks/Items/Strings/Waypoints) haben eigene Editoren in AuroraGui (listPage, editing).
  Modul-Settings = eigene Seite (detailPage, 2 Spalten) statt Overlay-Panel. Fix: Karten wurden beim Scrollen unsichtbar (stagger).
- **Shader-ESP neu**: läuft über Minecrafts Glow/Outline-Puffer (GlowMixins: shouldEntityAppearGlowing + getTeamColor = ESP-Farbe),
  Vanilla-Post-Chain entity_outline übersprungen (EspOutlineMixin: Redirect PostChain.addToFrame ordinal 0 in LevelRenderer.renderLevel),
  doEntityOutline ersetzt durch EspShaderRenderer.composite (Füllung + Umriss + Glow). Enthält Rüstung/Kopf/Items. Setting "Yourself".
- Tracers F5/Freecam: Linien-Seitenrichtung pro Endpunkt (vorher aus der Mitte -> verdrehte Bänder).
- Clean F5: "Stable Height (Jumps)" – Kamera bleibt beim Springen auf Boden-Höhe (CleanF5Module.heightOffset, FreecamMixins setup RETURN). Aura-Modul wieder entfernt (User wollte das nicht).
- 2026-11-14: Silent-Rotations-Versuch wieder entfernt (Anti-Cheat-Bypass abgelehnt) – Module auf altes Verhalten zurückgesetzt, util/Rotations.java gelöscht.
- **Glossy Floor gefixt**: Scan nutzt jetzt Heightmap (MOTION_BLOCKING_NO_LEAVES) statt Fenster um Spielerhöhe -> durchgehender spiegelnder Boden auf hügeligem Terrain (vorher einzelne schwebende Quads). step = range/24.
- **Rain FX**: Fallgeschwindigkeit 14-22 -> 7-11 b/s (war zu schnell); Spritzer deutlich sichtbarer (Doppelring, Aufschlag-Blitz, 6 hochspringende Tröpfchen, Lebensdauer 0.45->0.6s). Streifen kürzer (0.9->0.55).
- **Glow Wings Ruckeln gefixt**: Animation nutzt jetzt System.nanoTime() statt currentTimeMillis() (auf Windows ~15ms grob -> ruckelte). Läuft pro Frame flüssig.
- **Glass Inventory** (neues Modul, misc, default an): Inventar/Kisten im grauen Glas-Look. GlassInventoryMixin @Redirect auf renderBg-Aufruf in renderContents (renderBg ist abstrakt -> kein direkter @Inject möglich). Spieler-Vorschau im InventoryScreen neu gezeichnet (renderEntityInInventoryFollowsMouse, leftPos+26/topPos+8/+75/+78, scale 30). Slot-Zellen als runde dunkle Kästchen. Titel ausgeblendet (Toggle "Keep Titles"). CreativeModeInventoryScreen ausgenommen. Nur Selbsttest-OOM (Spiel lief parallel) - Mixin wendet sauber an (0 Fehler), visuell im Spiel noch nicht bestätigt.
- **Glass Inventory gefixt**: Redirect war auf renderContents, aber renderBg wird in renderBackground aufgerufen -> jetzt dort. Blur immer an (nicht mehr an guiBlur gekoppelt). Hover-Highlight (renderSlotHighlightBack/Front) ersetzt durch rundes graues Highlight. Slot-Zellen rund.
- **HUD im Chat verschieben** (AccentModule.chatEdit, default an): HudDrag (geteilte Drag/Snap-Logik aus HudEditorScreen extrahiert) + ChatHudMixin (mouseClicked + render-Polling, da ChatScreen kein mouseDragged/Released hat). Umrisse + Hinweis im Chat.
- **Live Preview** (AccentModule.livePreview, default an): Settings eines Visuals-Moduls offen -> AuroraGui schaltet Kamera auf THIRD_PERSON_BACK (gemerkt/wiederhergestellt in updatePreview/removed), Fenster rückt nach rechts (an("winx")), Hintergrund kaum abgedunkelt. Welt rendert live hinter dem Screen (isPauseScreen=false) -> Wings/Hands/Trail live am Charakter sichtbar beim Einstellen. Angelehnt an fixxedvisuals-Video.
- **Config Cloud**: server/zero_config_server.py (stdlib, keine Deps) + Dockerfile + DEPLOY.md. User hat ihn auf Render deployt: https://zero-config-server.onrender.com (getestet: upload/list/fetch ok).
  Mod-Seite: config/ConfigCloud.java (HttpClient, async, Callbacks via mc.execute), QuiqzyConfig neue Helfer (currentJson/namedJson/saveNamedFromJson/applyJsonPublic).
  AuroraGui neuer Tab TAB_CONFIGS (-2): links "My Configs" (listNamed + Save/load/Upload/Delete), rechts "Public Configs" (Liste vom Server, Import per Code, Get lädt+wendet an). Eingabefelder cfgName/cfgCode (cfgFocus), Scroll cScroll. Server-URL hardcoded in ConfigCloud.SERVER.
  Nur Build geprüft, visuell noch nicht (Spiel lief parallel).
- **WICHTIGER FIX (Crash bei Freunden / AMD-GPUs)**: zero_sky.fsh benutzte noise2/noise3 – das sind RESERVIERTE GLSL-Built-in-Namen (noise1..4). NVIDIA ignoriert sie, AMD nicht -> "overloaded functions must have the same return type" -> Pipeline quiqzy:pipeline/custom_sky (required) failt -> ganzer Client-Start crasht (Folge-Crash im TitleScreen "atlas not initialized"). Umbenannt in snoise2/snoise3. Freunde brauchen die neue Jar.
- **Config Cloud: Löschen**. Server: Upload gibt token zurück, DELETE /api/configs/<id>?token= (nur Besitzer), fetch ohne token. Gepusht -> Render redeploy. Mod: ConfigCloud merkt Tokens eigener Uploads (quiqzy-cloud.json), owns()/delete(), Papierkorb bei eigenen Public-Configs. Lokaler Delete (My Configs) war schon da.
- **Triple Tap Launch-Versuch**: findBase waehlt jetzt die horizontal naechste Stelle zum Ziel (score = Horizontaldist zum Kristall + 0.15*Reichweite - obsidianBonus) statt der am besten erreichbaren. PLACE: wenn Ziel >2.3 Bloecke vom base wegrutscht und noch kein Launch-Kristall steht -> zurueck zu OBBY (folgt dem Gegner). Grund laut Clip: Gegner wird weggeschlagen und Combo verfehlt die alte Position. NICHT live getestet (Crystal-Knockback server-seitig).
- **Live Preview v2**: kein Kamera-Wechsel mehr. Stattdessen 3D-Spielermodell links im Menü (renderPreviewModel -> InventoryScreen.renderEntityInInventoryFollowsMouse, Screen-Pixel nach dem virtuellen Scale, folgt Cursor). Fenster schmal/rechtsbündig, Settings einspaltig, dim 0.45. Hinweis: Wings/Welt-Effekte zeigen sich am GUI-Modell NICHT (Welt-Space).
- **Live Preview v3 (Orbit-Kamera)**: GUI-Modell verworfen (GuiGraphics ist 2D -> keine 3D-Flügel). Jetzt echter Charakter in 3rd-Person (echte Flügel/Effekte live), Kamera kreist per Maus um ihn (render/PreviewCam + FreecamMixins CameraMixin setup RETURN: setPosition/setRotation orbit; Prio vor CleanF5). AuroraGui updatePreview setzt Kamera+PreviewCam, yaw/pitch aus Maus. Fenster schmal/rechts, dim 0.18. Nicht live getestet.
- **Live Preview v4 (Orbit zurückgenommen)**: Orbit-Kamera (v3) ließ die ganze Welt mitschwenken -> schwindelerregend/unbrauchbar (User-Clip). Zurück auf stabiles GUI-Modell (renderPreviewModel, folgt Cursor), PreviewCam.active=false (Orbit-Block in FreecamMixins bleibt, aber tot). dim 0.42. Flügel zeigen sich am GUI-Modell NICHT (Welt-Space) -> echte Wings-am-Modell bräuchte Umbau zu Entity-RenderLayer (offen).
- **Card Aura + Kill Effects + Preview-Fix** (2026-11-15): CardFxRenderer (render/, eigene Billboard-Quad-Geometrie via debugFilledBox, Kamera-Achsen upVector/leftVector/forwardVector, Suit-Symbole Pik/Herz/Karo/Kreuz). CARD_AURA (Karten kreisen) + KILL_EFFECTS (Burst beim Kill, Tracking wie HitEffects, Hook in MultiPlayerGameModeMixin). Preview: Skin kleiner (scale 38-90), mouseY fix -> Körper neigt nicht, dreht nur horizontal.
- **Batch 2026-11-15b**: Snow jetzt bildschirm-fix (zero_snow.fsh: screen-UV statt view-dir, fällt gerade, wandert nicht beim Umschauen). Wings 'Angel' sauberer (Federn an Vorderkante aufgefächert, Längen-Glocke, Deckfeder). Target-Marker Brackets: Float-Präzisions-Bug gefixt (nanoTime-Wrap statt currentTimeMillis/1000f) -> drehen jetzt. GUI überall öffenbar (ScreenOpenGuiMixin auf Screen.keyPressed(KeyEvent) + AuroraGui(parent) -> Rückkehr zum Eltern-Screen). Sky-Ursache: Himmel rendert korrekt (view-dir), die Beschwerde war der mitwandernde Snow.
- **Wings als Outline** (2026-11-15c): GlowWingsRenderer komplett auf Konturlinie umgestellt (keine Federn mehr). outline(style) liefert geschlossene Silhouette (Angel/Blade/Butterfly), strokePlane zeichnet 3 Glow-Lagen entlang der Kanten. feather()/lobe() bleiben ungenutzt.
- **ZERO Loader / Selbst-Updater** (2026-11-15d): update/ZeroUpdater.java prüft beim Start https://raw.githubusercontent.com/Quiqzy404/zero-config-server/main/dist/version.json (build/url/name). Bei höherem Build -> Download nach <jar>.update, Austausch beim Spiel-Schließen via Windows-.bat (loop move bis entsperrt). Build-Nummer in resources/zero_build.txt (AKTUELL 2). Hosting im zero-config-server-Repo unter dist/. DEPLOY-FLOW NEU: bei jedem Release zero_build.txt hochzählen, Jar+version.json(build) nach <repo>/dist pushen -> Clients updaten automatisch.
- **Glossy Floor Vollbild-SSR** (build 2): zero_glossy_full.fsh + Pipeline glossy_full (Vollbild-Dreieck wie Sky). Normale aus Tiefe (dFdx/dFdy), nur Flächen mit dot(n,up)>0.65 -> Spiegelung an Boden-Ebene (up aus ModelViewMat). GlossyFloorRenderer.render -> drawFull; alte Quad/Heightmap-Pfad in unusedScanPath geparkt. Wirkt jetzt auf Gras/alle waagrechten Flächen. zero_build.txt=2.
- **Perf build 3**: Glossy-Vollbild-SSR billiger (STEPS 40->28, REFINE 6->4). InvProj-als-Uniform verworfen (projectionMatrix() in fabric-rendering-v1 16.2.0 nicht verfügbar; worldState hat sie auch nicht), inverse() bleibt im Shader. zero_build.txt=3.
- **Glossy = alles glossy (build 4)**: zero_glossy_full.fsh Boden-Gate (dot(n,up)>0.65) entfernt; refN = rekonstruierte Flächennormale -> SSR auf ALLE Flächen (Boden+Wände). Könnte auch das 'geht gar nicht' beheben, falls das Gate alles verworfen hat. zero_build.txt=4.
- **Wings Redesign (build 5)**: selbst per hudlook-Selftest iteriert (3 Runden). Reine Outline las sich nicht als Flügel -> jetzt gefülltes Glow-Blatt (fillTri, Fächer von Mittelpunkt) + helle Kante (strokePlane dünner). Silhouette groß + nach oben geschwungen, sanfte Federspitzen; phi 28->22, Core heller (lerp 0.65). Neuer Selftest-Modus ZERO_SELFTEST_ONLY=hudlook (InGameScenario) -> run/screenshots/hud_look.png + wings_look.png. zero_build.txt=5.
- **Wings klein/kompakt (build 7)**: Direktvergleich zeigte: Referenz-Flügel sind KLEIN, rund, eng am Rücken (nicht groß gespreizt). Silhouette geschrumpft (max ~0.76), Mittelpunkt + Stroke-Breiten runter, kompakt am oberen Rücken. Selftest forciert cyan (GLOW_WINGS themeColor off, color 0x3AD9FF) zum Farb-Check. zero_build.txt=7.
- **Wings als Textur (build 8)**: eckige Geometrie komplett ersetzt durch eigene glatte Textur textures/wing.png (PIL: 5 Bezier-Federn + Vorderkante + Glow-Blur + Membran). GlowWingsRenderer neu: 2 gespiegelte Quads am Rücken via RenderTypes.entityTranslucentEmissive(WING_TEX), getönt nach Akzent, sanfter Flap, Rücken-Neigung. Smooth, mehrere Glow-Linien, cyan - matcht Referenz. zero_build.txt=8.
- **Wings soft/rund (build 9)**: Direktvergleich zeigte Referenz = weiches rundes WOLKEN-Flügel (gefüllt, buckeliger Oberrand), nicht Federlinien. wing.png neu (überlappende Kreise -> gefüllte soft Form + heller Rand + Glow-Blur). Renderer unverändert (Textur-Quads). zero_build.txt=9.
- **Wings = User-Kristallbild, 3D (build 10)**: User gab violettes Kristallflügel-PNG. Zu Textur verarbeitet (Alpha aus Helligkeit, schwarz->transparent). Renderer: 2 Hälften (li/re der Textur) als abgewinkelte Flächen (Dihedral 40° nach hinten) statt EINEM flachen Quad -> echte 3D-Flügel. themeColor an = Originalviolett, aus = getönt. zero_build.txt=10.
- **Wings größer + Glow (build 11)**: Textur neu mit gebackenem Bloom (3 Blur-Stufen + Rand-Padding). wingW 2.0 wingH 2.75 (größer), Dihedral 24° (mehr gespreizt/sichtbar statt edge-on). zero_build.txt=11.
- **Wings Outline-only (build 12)**: Textur neu - nur helle Rand-Linien behalten (lum>150 -> alpha, Füllung transparent) + Glow-Bloom. Renderer unverändert. zero_build.txt=12.
- **Fast Place/Use + Scroll-Keybinds (build 13)**: FastActionMixin (Minecraft.handleKeybinds HEAD -> rightClickDelay=0; BlockItem=FastPlace, sonst FastUse). Module FAST_PLACE/FAST_USE (player). Scroll-Keybinds: input/ScrollKeys (MOUSE 100/101 = Scroll Up/Down), KeyMappingAccessor (key-Feld), MouseHandlerScrollMixin (onScroll: im KeyBindsScreen selectedKey aufs Scrollen binden; im Spiel gebundenes Keybind per KeyMapping.click auslösen + Hotbar-Scroll canceln). Lang assets/minecraft/lang/en_us.json (Scroll Up/Down). Vanilla Controls: Bind wählen -> scrollen = gebunden. zero_build.txt=13.
- **Wings nur Außen-Outline (build 14)**: Textur = nur der ÄUSSERE Kontur der Flügel-Silhouette (cv2 findContours RETR_EXTERNAL auf Maske, approxPolyDP geglättet, polylines) als kräftige violette Glow-Linie (5 Blur-Stufen), innen transparent. Keine inneren Facetten-Linien mehr. 3D über die 2 abgewinkelten Quads. zero_build.txt=14.
- **Wings smooth+sauber (build 15)**: Pixelig gefixt via wing.png.mcmeta blur:true (Linear-Filter). Grau gefixt: Textur neu, Glow als eigene violette Ebene composited (NICHT additiv zu Weiß), höhere Auflösung (1536), dünnere saubere Linie. zero_build.txt=15.
- **Wings grauer Rand weg (build 16)**: Ursache = Linear-Filter mischt an Kanten mit schwarzen transparenten Pixeln -> grauer Saum. Fix: RGB UNIFORM violett (225,95,255) überall, nur Alpha macht Linie+Glow. Nach Resize RGB nochmal hart gesetzt. zero_build.txt=16.
- **RTP Hunter (build 17)**: misc/RtpHunterModule - Zustandsautomat: /rtp -> Menü-Klick (links/mitte/rechts per Item-Slots, Glas übersprungen) -> Wait -> Scan (Spieler in Range ODER aktiver Beacon-Beam via Chunk-BlockEntities) -> /tpahere <Name> + /invsee <Name> -> Container nach Keyword (STAR) prüfen -> Screenshot + Discord-Webhook multipart (@everyone + Name + Tool). Settings als Setting.Strings (Webhook/Name/Keyword/Commands editierbar in GUI), Choice/Int/Bool für Rest. zero_build.txt=17.
- **RTP Hunter stays (build 18)**: Bug - bei Spieler ohne STAR ging er auf IDLE -> insta-rtp. Fix: WAIT_INVSEE wartet 12t (invsee-GUI laden), liest Loot, Webhook nur bei STAR, dann IMMER STAY (kein rtp mehr). Jeder gefundene Spieler -> Bot bleibt stehen, GUI wird geschlossen. zero_build.txt=18.
- **RTP Hunter wait-for-team (build 19)**: Nach Fund -> WAIT_TEAM: wartet bis /tpahere-Kollege in <24m da ist ODER 20s (400t), dann weiter (IDLE->rtp). Teammate wird beim Spieler-Scan als Ziel ausgeschlossen. zero_build.txt=19.
- **RTP Hunter multi-tpa (build 20)**: TPA Here Name kann MEHRERE Namen -> /tpahere für jeden; teammateHere + nearestPlayer prüfen/excludieren alle Namen. zero_build.txt=20.
- **GUI Paste/Clipboard (build 21)**: In AuroraGui keyPressed `event.isPaste()` (InputWithModifiers) -> `minecraft.keyboardHandler.getClipboard()` fügt ein: in Strings-Editor + generisches Filterfeld in listQuery, in Config-Tab in cfgName/cfgCode, sonst in search. Strings-Tippfilter gelockert: alle druckbaren ASCII-Zeichen (inkl. `/ : . % _ - ` + Leerzeichen, kein führendes), Cap 16->256. Eingabe-Anzeige zeigt bei langen Strings das Ende (…+letzte 36). Platzhalter "Type or paste (Ctrl+V)". zero_build.txt=21.
- **Marker + Ping (build 22)**: render/MarkerStore (gemeinsamer Marker-Speicher, CopyOnWriteArrayList, Kind CHUNK/PING, envelope() fade-in/out, dim-Filter) + render/MarkerRenderer (WorldRenderEvents BEFORE_TRANSLUCENT, through-walls Pipeline wie SusChunk; CHUNK = 16x16-Kasten mit nach oben verlaufender Fuellung gradColumn + doppelte Glow-Kante + Strahl; PING = Strahl + schwebender pulsierender Diamant/Oktaeder). RTP Hunter markiert bei Fund den Gegner-Chunk (rot 0xFF4040) bzw Beacon-Chunk (gold 0xFFC24A), TTL 300s. Neues Modul misc/PingModule (Marker Ping, MISC): onFrame pollt mouseHandler.isMiddlePressed() -> Doppel-Mittelklick <400ms = Ping auf mc.hitResult (sonst eye+look*reach). Settings share/ttl/reach. Share -> connection.sendChat("ZPING x y z dim name"); mixin/PingChatMixin faengt ChatComponent.addMessage ab, PingModule.handleIncoming parst + setzt Marker + versteckt Zeile (eigener Echo nur versteckt). Farbe per WaypointsModule.colorOf(name). Registriert: QuiqzyClient MarkerRenderer, ModuleManager PING, mixins PingChatMixin. zero_build.txt=22.
- **Ping Share getarnt (build 23)**: PingModule broadcastet den Ping jetzt NICHT mehr als Klartext "ZPING x y z..." sondern als unsichtbare Zero-Width-Bits (MARKER U+2064, BIT0 U+200B, BIT1 U+200C) via sendChat. encode(payload) -> Bytes als 8 Zero-Width-Zeichen. handleIncoming sucht MARKER, sammelt folgende Zero-Width-Zeichen als Bits, dekodiert zu payload "x y z dim name". Fuer Nicht-Client-Spieler = leere Chat-Zeile. zero_build.txt=23.
- **RTP Chunk-Share + Webhook-Fix + Chunk-Visuals (build 24)**: render/MarkerShare zentralisiert den getarnten Zero-Width-Broadcast fuer Ping (P) UND Chunk (C), encode/handle, dispatch zu MarkerStore.addPing/addChunk (Chunk dedupt per coords -> Echo harmlos). PingModule nutzt jetzt MarkerShare.broadcastPing + delegiert handleIncoming. RTP Hunter: Setting share_chunk -> broadcastChunk bei Spieler/Beacon-Fund (Teammates mit Marker Ping an sehen ihn). Webhook: blank-Guard + In-Game-Feedback (say) bei Fund/STAR/kein-STAR/Status-Code, Setting send_all (Webhook on every find), Coords+Chunk im Webhook-Text. MarkerRenderer chunk() aufgewertet: 2-stufiger Verlauf, quadY Bodenplatte (pulse), Eck-Pfeiler gradColumn, aufsteigende Scan-Linie. zero_build.txt=24. In NoRisk-mods aktiv kopiert.
- **tpahere debugbar (build 25)**: RtpHunter.tpahere() - warnt wenn teamNames() leer (kein /tpahere), haengt Namen an falls Command kein %s hat (cmd+" "+name), und say()-Feedback pro gesendetem Command. zero_build.txt=25. (Game lief -> nur Repo, NoRisk per Updater.)
- **Full-Chunk + Viewer-Edition (build 26)**: MarkerRenderer.chunk() nutzt jetzt worldMin..worldMax (ganzer Chunk, volle Hoehe), Platte auf Gegner-Y, volle Eck-Pfeiler (box statt gradColumn), Scan-Linie. ModuleManager.VIEWER (liest /zero_edition.txt; "viewer" -> register() ueberspringt RtpHunter). zero_edition.txt=full im Repo. PingChatMixin empfaengt im Viewer immer (sonst wenn PING an). Viewer-Jar: FERTIG/zero-client-viewer-1.0.0.jar (fuer Freunde). zero_build.txt=26.
- **RTP Command-Queue (build 27)**: tpahere+invsee wurden alle im selben Tick gefeuert -> Server droppte den Burst (Log: say zeigte "-> /tpahere X" aber nichts kam an, invsee auch leer). Fix: ArrayDeque cmdQueue, onTick drained 1 Cmd/4 Ticks, tpahere()+invsee queueCmd statt sendCmd, WAIT_INVSEE wartet bis Queue leer dann 14t. onDisable leert Queue. /rtp bestaetigt = Zufalls-TP (rtpqueue = 1v1, separat). zero_build.txt=27.
- **/tpahere-Fix: Broadcast zuletzt (build 28)**: Manuell ging /tpahere, aus Modul nicht - Chat-Eingabe nutzt denselben sendCommand. Ursache: Chunk-Broadcast (getarnte, fast-leere sendChat) ging VOR den Commands -> hugosmp Spam-Flag blockt die folgenden Commands. Fix: Queue jetzt typed (Queued{text,chat}), Reihenfolge tpahere -> invsee -> Chunk-Broadcast (queueChat), Abstand 6t. MarkerShare.chunkPayload() liefert encoded String zum verzoegerten Senden. zero_build.txt=28.
- **invsee aus Paket + Webhook (build 29)**: Ursache "Webhook sendet nichts" = /invsee kickt (oder GUI-Timing), GUI-Lesen nach 14t schlug fehl. Fix: mixin ClientPacketListenerMixin @Inject handleContainerContent HEAD -> RTP_HUNTER.onContainerContent(id, items). Liest Items direkt aus ClientboundContainerSetContentPacket (nur Haupt-Thread via isSameThread, containerId!=0, state WAIT_INVSEE, invseeHandled-Guard), baut Item-Liste als Text, post() an Webhook (null-png guard), @everyone nur bei STAR-keyword sonst sendAll. GUI-Pfad = Fallback nach 30t. zero_build.txt=29. (Game lief -> NoRisk per Updater.)
- **KICK-FIX: EncoderException (build 30)**: Clip zeigte "Connection Lost: EncoderException Failed to encode packet serverbound/minecraft:chat". Ursache: MarkerShare-Broadcast kodierte 8 Zero-Width-Zeichen/Byte -> Nachricht >256 Zeichen -> Minecraft writeUtf(256) crasht -> Disconnect. Trat bei RTP-Fund (Chunk) UND Marker-Ping auf. Fix: encode() nutzt jetzt unsichtbare Tag-Zeichen U+E00xx (1 Zeichen/ASCII-Byte, via appendCodePoint), decode() liest codePointAt TAG_BASE+0x20..0x7E. send() + RtpHunter.sendChatRaw guarden auf MAX_CHAT=250 (MarkerShare.fits). StandardCharsets import raus. zero_build.txt=30. (Game lief -> NoRisk per Updater.)
- **Self-updating Viewer (build 31)**: ZeroUpdater jetzt edition-abhaengig: readEdition()/versionUrl() -> full = dist/version.json, viewer = dist/viewer/version.json. Viewer-Kanal im Repo: dist/viewer/version.json + dist/viewer/zero-client-viewer-1.0.0.jar. checkAsync() laeuft eh fuer alle Editions (QuiqzyClient:47). Deploy-Flow jetzt: Full -> dist/, Viewer -> dist/viewer/. Freunde installieren Viewer 1x, self-updated danach. zero_build.txt=31.
- **StunSlam Visible-Look smooth+hold (build 36)**: Problem build 35: sichtbarer Dreh war nur 1 Tick (sofort von Maus ueberschrieben) -> "dreht Kopf ned zu Gegner". Fix: visibleLook() dreht smooth (55% der Restdrehung/Tick) + setYHeadRot, und ein Hold (Felder visTarget/visTicks) haelt das ueber **lookHold** (Setting "Look Hold", default 6t, 0-20) Ticks nach dem Treffer -> Kopf dreht sichtbar hin und bleibt kurz, dann Kontrolle zurueck. Hold-Loop oben in onStartTick (nur Visible + firstPerson, sonst visTicks=0). leftClick setzt visTarget/visTicks + visibleLook() sofort. aimAt() entfernt. Import Mth. zero_build.txt=36.
- **StunSlam Aim Mode Visible/Silent (build 35)**: silent_aim (Bool) ersetzt durch **aim_mode** (Choice "Visible"/"Silent", default Visible). Dreh-Logik komplett in leftClick verlagert (nur beim umgelenkten Schlag, attackTarget!=null): aimServer() IMMER direkt vor mc.gameMode.attack (Treffer landet sofort, auch in F5); zusaetzlich sichtbares aimAt() NUR wenn aim_mode=Visible UND firstPerson() (mc.options.getCameraType().isFirstPerson()). -> In F5/3rd-Person dreht sich die Kamera NIE, in First Person nur im Schlag-Moment (kein Dauer-Lock, Bewegung bleibt normal). Decision-time + countdown aimAt entfernt (hatte die Bewegung gesperrt). Silent = nur Server-Paket. zero_build.txt=35. Delivery jetzt ueber Loader (dist); .disabled-Backup in NoRisk auf 35 aktualisiert.
- **StunSlam Server-Side Aim + Elytra-Guard (build 34)**: combat/StunSlamModule. NEU **Server-Side Aim** (silent_aim, default an): beim umgelenkten Schlag (Hit Last Target) wird NICHT mehr die echte Kamera gedreht (aimAt nur noch wenn silent aus); stattdessen schickt leftClick() direkt vor mc.gameMode.attack ein aimServer() = `player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw,pitch,onGround,horizontalCollision))` mit der Ziel-Richtung -> Server validiert den Treffer, Kamera bleibt, naechster normaler Move-Tick sendet eh die echte Rotation ("dreht sofort zurueck"). Rot-Konstruktor per javap verifiziert (float,float,boolean,boolean). Countdown-Aiming + redirect-aimAt nur noch wenn !silentAim. NEU **Only When Elytra Off** (no_elytra, default an): Guard in onStartTick Mace-Combo `if (noElytra && player.getItemBySlot(CHEST).is(Items.ELYTRA)) return;` -> mit Elytra an kein Combo/Shield-Break (greift erst nach Elytra-Swap). Imports LocalPlayer, ServerboundMovePlayerPacket, EquipmentSlot. zero_build.txt=34. (Game lief -> NoRisk per Auto-Updater; Full+Viewer in dist/.)
- **StunSlam Hit-Last-Target + Auto Hit (build 33)**: combat/StunSlamModule, Mace-Combo-Modus. NEU **Hit Last Target** (Setting aim_target + aim_reach 3-6b + memory_ms): updateTargetMemory() merkt sich jedes onStartTick die anvisierte LivingEntity (lastTarget/lastTargetTime), leftClick() frischt sie beim echten Treffer auf. Schlaegst du ins Leere (hitResult != EntityHitResult), liefert redirectTarget() das gemerkte Ziel wenn alive + frisch (<memory_ms) + distanceToBox(eye,box) <= aim_reach; dann aimAt() dreht den Blick auf die Box-Mitte und attackTarget wird gesetzt -> Combo trifft ES statt Luft. leftClick() jetzt instance + schlaegt attackTarget (sonst hitResult). maceHit() prueft attackTarget alive, clert es am Ende. Beim geplanten Mace-Tick (ticksUntilMaceHit) wird attackTarget weiter anvisiert. NEU **Auto Hit** (Setting auto_hit + auto_hit_delay t): loest den Combo ohne Klick aus sobald ein gueltiges Ziel in Reichweite ist (clicked||auto), autoCooldown zwischen Combos -> z.B. automatisch beim Ely-Mace-Dive. Klick wird erst nach bestaetigtem Ziel konsumiert (sonst bleibt er fuer Vanilla erhalten). Shield-Break-Teil in beginShieldBreak() ausgelagert. Imports AABB/Vec3. zero_build.txt=33. (Game lief -> NoRisk NICHT kopiert, per Auto-Updater; Full+Viewer in dist/ gepusht.)
- **Zuverlaessiges Marker-Sharing (/msg) + Inventar-Bild (build 32)**: getarnter Zero-Width-Broadcast kam auf hugosmp vermutlich nie an (Server strippt unsichtbare Zeichen). Neuer zuverlaessiger Weg: MarkerShare.VTOKEN "zM~" als SICHTBARER Token in einer privaten /msg-Fluesternachricht -> nur Empfaenger sieht sie, dessen Client versteckt die Zeile (handle() sucht erst VTOKEN, sonst den unsichtbaren MARKER-Pfad; beides parst dieselbe Payload P/C). MarkerShare.msgCommand(template,%s,%m), chunkWhisper(), pingWhisper(), sanitizeName(). PingModule: neue Settings friends (Setting.Strings) + msgCmd (default "/msg %s %m"), friendNames()/whisperToFriends(); placePing fluestert an Freunde + Broadcast als Fallback, warnt wenn keine Namen. RtpHunter: shareChunkTo() fluestert an shareRecipients() (teamNames ∪ PING.friendNames) + queueChat-Broadcast, ersetzt die alten queueChat-only Calls bei Spieler/Beacon. PingChatMixin empfaengt jetzt IMMER (handle bricht eh nur bei echten Markern ab -> Freunde sehen Marker auch mit Ping-Modul aus). INVENTAR-BILD: onContainerContent sammelt InvEntry{name,count,rgb,star}, renderInventoryPng() zeichnet per AWT ein dunkles 3-Spalten-Grid (Seltenheitsfarbe aus HoverName-Style, STAR rot umrandet, Anzahl-Pill, Header mit Name/Coords/Count), postImage() schickt das PNG an den Webhook (Text-Liste nur noch Fallback wenn Bild=null). AWT-Imports Color/Font/Graphics2D/RenderingHints/BufferedImage. zero_build.txt=32. (Game lief -> NoRisk NICHT kopiert, laeuft per Auto-Updater; Full+Viewer in dist/ gepusht.)
- **TiersMC-Section (build 41)**: neue Kategorie `Category.TIERSMC` (GUI-Icon MAP_PIN). Nachbau der 3 Module aus dem DonutLarp-Meteor-Addon (donutlarp-1.5.0.jar, geprüft: keine Malware, kein AC-Bypass – der Server schickt die Bases selbst). `render/ServerBaseNet` empfängt `donutlarp:bases` (op 0=clear, 2=noop, 3=Spawner[x,y,z,stack,UTF type], sonst Bases[id,x,y,z,sx,sy,sz]) + `donutlarp:activity` (n x [cx,cz,players]); NUR Empfang, Größen-/Anzahl-/Koordinaten-Limits, Typ-Strings gesäubert, alles try/catch. Module in `module/tiersmc/`: DLChunkFinder (gold, Y 64), DLSusFinder (rot, Y 63, area_radius + fuzz, gleiche Hash-Verschiebung wie Addon), DLActivityInspector (rot). Gemeinsame Basis DLModule (Toast/Chat/Sound, Max Distance, Y-Level, Height, Shape Mode, Side/Line Color). `render/TiersMcRenderer` = Platten durch Wände. Nicht live getestet.
- **Hit Flick + Auto Backstep (build 42)**: combat/HitFlickModule – Klick auf LivingEntity (nur wenn hitResult Entity, sonst Klick normal) wird abgefangen: Kamera dreht auf Ziel-Yaw + `flick_yaw` (Std 180 = nach hinten; Pitch Keep/Fixed), hält `flick_ticks`, dann Snap/Smooth zurück aufs Ziel + gameMode.attack (nur in Reichweite) + swing. Weapons Only (Schwert/Axt/Mace/Trident/Speer). Achtung: frisst den Klick vor StunSlam, wenn beide an. combat/AutoBackstepModule – Hook in MultiPlayerGameModeMixin.attack HEAD: nach Mace-Treffer (Mace Only, optional Only Smash Hits = fallDistance>=1.5) nach `delay` für `duration` Ticks keyDown gedrückt, keyUp/Sprint aus, danach physischer Tastenzustand zurück. Nicht live getestet.
- **Block ESP Rework (build 43)**: Keine Outline mehr (Settings outline/line_opacity/line_width entfernt), nur Farbe (`opacity`, Std 40%). Renderer zeichnet pro Block nur Seiten ohne gleichfarbigen Nachbarn -> Adern = eine saubere Form. Modul baut bei Änderungen (resultsVersion) einen `Snapshot` (Treffer, rgbAt, veinOf, Veins via 26er-Flood-Fill gleicher Farbe). Tracer jetzt in BlockEspRenderer (aus EspRenderer entfernt): 1 Tracer pro Ader zur Ader-Mitte, nächste zuerst bis `tracer_limit` (25), Alpha-Verlauf vom Start (transparent) zum Ziel (`tracer_opacity`). Vorher: 1 Linie pro Block + gemeinsames MAX_TRACERS mit Entities -> Chaos und manche Blöcke ohne Tracer. AuroraGui Block-Liste: Rechtsklick auf ausgewählten Block -> Farb-Panel (Farbverlauf-Leiste, 12 feste Farben, Reset), Farbpunkt unten rechts je Tile. Nicht live getestet.
- **Hit Flick ohne Pitch (build 44)**: flick_pitch/pitch_mode entfernt. Flick dreht nur Yaw relativ zu DEINEM Blick beim Klick (origYaw + flick_yaw), Pitch bleibt. Danach exakt zurück auf origYaw/origPitch (Smooth nähert an, letzter Schritt exakt), dann Schlag.
- **Auto Backstep neu (build 45)**: alte Logik (nach Mace-Hit rückwärts) + Mixin-Hook entfernt. Jetzt: Gegner (Fadenkreuz) blockt mit Schild + du stehst in seiner vorderen Hälfte (toMe·view > -0.05) -> Trigger On Click (Klick abgefangen) oder Automatic -> W/A/S/D-Steuerung läuft seitlich (kürzere Seite) um ihn herum hinter den Rücken (Wegpunkt erst Seite, dann look*-1.3). Sobald hinten (oder Schild runter) + Reichweite + Cooldown>=0.9 -> Aim Silent (Rot-Paket) oder Visible, attack + swing. Abbruch nach max_time oder zu weit weg; Tasten danach auf physischen Zustand. Settings: trigger, mace_only, range, max_time, auto_hit, aim_mode.
- **Panel GUI (build 46)**: neue Standard-GUI gui/PanelGui (Stil aus User-Video): eine Glas-Spalte pro Kategorie, neue Kategorie CLIENT (Click GUI, Accent Colour, Sounds). Modul: links an/aus, rechts Settings inline (Bool-Toggle, Int-Slider, Choice-Dropdown, Color mit Verlauf+Swatches, Listen -> AuroraGui.editor(parent,m,s), Keybind-Zeile). Kopf: links ein/ausklappen, rechts ziehen = verschieben. Tippen = Suche, Esc. Panel scrollt einzeln. Eigener Shader core/zero_panel.fsh + ZeroPipelines.PANEL + ZeroRender.panel(): Blur + Vibrancy (Sättigung 1.45) + Tönung + weiches Licht oben, kein Rand, Dither. Hintergrund NICHT geblurt (Click GUI bg_blur default aus). Keine Sounds in der PanelGui; einmalige Migration soundsOffV1 schaltet das Sounds-Modul aus. ClickGuiModule (CLIENT): Style Panels/Classic (Classic = AuroraGui), Scale, Panel Opacity, Bg Blur/Dim, Roundness, Icons, Accent Rows, Descriptions. Selbsttest ZERO_SELFTEST_ONLY=panels (panels_main/expand/search.png). Video-Features (Sky Shaders Liquid Gold/Silver/Rose Gold, World Shaders, Ambience Fog-Gradient + Winter/Storm/Sakura, Motion Blur ...) noch offen.
- **Sky Shaders + GUI-Feinschliff (build 47/48)**: CustomSkyModule ersetzt (id custom_sky bleibt, Name 'Sky Shaders'): Style Liquid Gold/Liquid Silver/Rose Gold/Glass Rain/Night/Rain Night (Shader-Presets 9-14 in zero_sky.fsh: liquidMetal = verwirbeltes fbmSoft, Bänder w=f*6.5+q.x*2.4+r.y*1.5 (vom User abgenommen), glassRain, nightSky), Speed (0.07, decimals 2, animTime*=speed*10), Pattern scale (1.19 -> SkyParams.w), Intensity (94 -> Helligkeit). Setting.Int.decimals()/display()/asFloat() neu (PanelGui zeigt display()). Build 48 FIX: Himmel lief bei BEFORE_TRANSLUCENT und malte über Wolken/Wände -> jetzt WorldRenderEvents.START_MAIN (vor Gelände, Wolken kommen danach drüber). Panel-GUI: ZeroBlur.capture(levels), Click GUI 'Glass Blur' (Std 2) + 'Active Row Whiteness' (aktives Modul = weißes Milchglas via ZeroRender.panel). Selbsttest ZERO_SELFTEST_ONLY=skyshaders (Steinwand vor Spieler, sky_*.png).
- **Build 49**: Sky-Animation langsamer (animTime *= speed*4 statt *10). Glass Rain = helles bläulich-weißes liquidMetal-Glas (62%) über hellem Himmel, Tropfen entfernt (User: sah scheiße aus). Rain Night ohne Regenstreifen (nur Wolken + Blitze).
- **Glossy Floor Fix (build 50)**: Beim Runterschauen zeigte der Spiegelstrahl am unteren Bildrand hinter die Kamera (p.z > -0.05 -> break, lastUv=-1 -> discard) -> kein Gloss unten. Jetzt Ersatz-Spiegelung: vertikal gespiegelter Bildpunkt (1 - y*0.85, Himmel/Horizont), found 0.45. Fresnel-Basis 0.30 -> 0.45 (stärker von oben). Beide Shader (zero_glossy.fsh + zero_glossy_full.fsh). Selbsttest glossy hat neuen Screenshot glossy_down.png (Pitch 55).
- **Build 51 – neuer Stil überall + Glossy-Fix**: AuroraHud.surface()/glassSurface() = ZeroRender.panel (Tönung 0x14161C 50%, Licht oben, kein Rand) für ALLE Themes; lens() = weißes Milchglas (40%). HUD-Blur captureBlur() ohne Glass-Theme-Pflicht, Stärke = Click GUI 'Glass Blur'. AuroraGui-Fenster (klassisch/Listen-Editor) = panel ohne Outline, HudDrag-Leiste + Glass Inventory = panel. Glossy: Fehltreffer projizieren die Strahlrichtung weich in die Ferne (r.z<-0.08 -> project(start+r*400)) statt lastUv (gab gestapelte Bögen), hinter Kamera = gespiegelter Bildpunkt.
- **Build 52 – Dock GUI + Background Blur**: neue Standard-GUI gui/DockGui (Click GUI 'GUI Style' style_v2: Dock/Panels/Classic, ClickGuiModule.create()/isMenu()). Glas-Blatt 600x330 mit Modul-Kacheln (3 Spalten, aus = dunkles Glas, an = weißes Milchglas, Mini-Schalter, Beschreibung, Keybind), Rechtsklick = Einstellungs-Ansicht (2 Spalten, Zurück-Knopf), Tippen = Suche über alle Module, Kategorie-Dock unten (Icons, Hover-Label). Setting-Widgets aus PanelGui übernommen (generiert per scratchpad/gen_dock.py). Neues Modul Visuals 'Background Blur' (BackgroundBlurModule + render/DepthBlurRenderer + core/zero_dof.fsh): END_MAIN (vor Hand), Tiefe kopieren, ZeroBlur.capture(strength), Vollbild-Mix nach Entfernung (Start/Fade/Strength/Amount/Blur Sky). Selbsttest ZERO_SELFTEST_ONLY=dock (dof_on/off, dock_main/settings). ACHTUNG Build-Check: javac meldet auf Deutsch 'Fehler' -> nach 'Fehler|error|FAIL' greppen.
- **Build 53 – HUD überall Glas + entfernbare Teile**: ZeroHud.glass() immer AuroraHud.surface (neues Glas, egal welcher HUD-Style). Keystrokes: gedrückt = AuroraHud.lens (weißes Milchglas) statt Akzent-Verlauf, Leertaste weiß. greySurface -> panel. Info-Leiste (AuroraHud.info): Segmente mit IDs name/fps/ping/server/clock, ZeroHud.markPart -> ZeroHud.PARTS; ausgeblendet über HudModule.hiddenParts (Strings, z.B. 'a_info:fps'). HUD-Editor (HudDrag): Hover über Teil -> Rahmen + × oben rechts, Klick auf × = ausblenden; Rechtsklick auf Element = Position + alle Teile zurück. Passt die Leiste mit Versatz nicht ins Bild (x1+off<3 || x2+off>vw-3) -> infoVertical (Teile untereinander, im Bild gehalten). Selbsttest ZERO_SELFTEST_ONLY=hudparts.
- **Build 54**: Neues Modul combat/AutoInvTotemModule ('Auto Inv Totem'): nur bei offenem InventoryScreen, nach Delay: Totem in Offhand (SWAP Button 40) und in Hotbar-Slot (Std 9, aus Hauptinventar), Replace-Option. Auto Double Hand: Setting swap_on_pop (Std an) + onSelfPop() aus PopMixin (Event 35 für mc.player) -> sofort setSelectedSlot auf Hotbar-Totem im selben Paket-Handler.
- **Build 55**: Dock-GUI auf User-Wunsch entfernt (DockGui.java + Selbsttest 'dock' gelöscht). GUI Style jetzt style_v3: Panels (Standard) / Classic.
