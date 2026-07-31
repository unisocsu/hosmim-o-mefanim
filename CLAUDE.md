# CLAUDE.md — חוסמים את הציר

הנחיות עבודה על המשחק. **קרא את המסמך הזה במקום לקרוא את הקוד** — ואז קרא רק
את הפרוסה הרלוונטית.

## מה זה

"חוסמים את הציר" — משחק RTS סאטירי בעברית (RTL): מפגינים חוסמים כבישים,
המשטרה מסלימה (שוטרים ← פרשים ← מכת"זית). תלת-ממד עם three.js r128.

**המשחק עצמאי לחלוטין (אופליין מלא)** — אפס בקשות רשת. זה חוזה, לא מצב:
כל שינוי חייב לשמור על אפס תלות חיצונית. אין CDN, אין fonts.googleapis,
אין API. שיאים = localStorage בלבד.

## ⚠️ כלל הטוקנים החשוב ביותר

**`index.html` הוא קובץ יחיד של ~3,400 שורות (170KB) שמכיל הכל** — HTML,
CSS וכל ה-JS. **אסור לקרוא אותו בשלמותו.** עבודה נכונה:

1. מצא את האזור במפת הסעיפים למטה (או בעוגני ה-grep).
2. `Grep` לעוגן ← `Read` עם offset/limit על הפרוסה בלבד.
3. מספרי שורות נודדים אחרי עריכות — סמוך על עוגנים, לא על מספרים.

## קבצים

```
index.html        ← המשחק כולו (HTML+CSS+JS). לקרוא בפרוסות בלבד!
three.min.js      ← three.js r128 מקומי (MIT). לא לגעת.
fonts.css + fonts/ ← Heebo + Frank Ruhl Libre מקומיים (OFL), 8 woff2.
sw.js             ← Service Worker. בהוספת קובץ למשחק — להוסיף ל-CORE ולהקפיץ גרסת CACHE!
manifest.json     ← PWA. intro.mp4=מוזיקת פתיחה, cops.mp4=סאונד משטרה.
icon-*.png, og.jpg, feature-graphic.png, privacy.html, .well-known/ ← נכסים.
desktop/          ← תצורת בניית EXE (ראה "בניית הפצות").
CLAUDE.md         ← המסמך הזה. לעדכן מפה/עוגנים אחרי שינוי מבני!
```

## מפת סעיפים של index.html (שורה ≈ כותרת)

| ~שורה | סעיף |
|---|---|
| 1–397 | head, CSS מלא, כל מסכי ה-HTML |
| 399 | קבועים ומצב המשחק (G) |
| 435 | אודיו — Web Audio, סינתזה בלבד (AUDIO) |
| 515 | מוזיקת רקע BGM (intro.mp4) + COPS (cops.mp4) |
| 542 | שיאים — Fame, localStorage בלבד |
| 570 | טקסטורות Canvas (TEX) — פשקווילים, שלטים, לוחיות |
| 841 | סצנה, מצלמה, תאורה, renderer |
| 895 | בוני סביבה (בניינים, רמזורים, עצים) |
| 1076 | שלוש המפות (בר אילן / כביש 4 / גשר המיתרים) |
| 1250 | רכבים — מפעל + תנועה |
| 1730 | דמויות — חרדים, שוטרים, נהגים, פרשים |
| 2139 | AI מפגינים |
| 2248 | נהגים זועמים |
| 2365 | משטרה — שוטרים גוררים |
| 2484 | פרשים |
| 2528 | מכת"זית — שלוש רמות |
| 2689 | הסלמה — גלים |
| 2717 | כלים מיוחדים (פשקווילים/שופר/שקיות) |
| 2771 | מצלמה — איזומטרית + מגע |
| 2811 | קלט RTS — בחירה ופקודות |
| 3087 | ניקוד, HUD, מבזקים |
| 3173 | זרימת משחק — שלבים, סיכום, היכל התהילה |
| 3290 | לולאה ראשית (RAF) |
| 3320 | חיווט ואתחול (כל ה-addEventListener) |

## ארכיטקטורה מפורטת

קובץ יחיד: HTML (מסכים 181–395) + CSS (37–177) + JS (397–3378).

### מצב גלובלי

**`const G={...}`** (~415) — האובייקט המרכזי:

| שדה | משמעות |
|---|---|
| `scene/camera/renderer` | three.js core, נוצרים פעם אחת ב-`initThree` |
| `world` | Group סטטי של המפה; מוחלף ב-`clearWorld` |
| `dyn` | Group ישויות דינמיות; מוחלף יחד עם world |
| `peeps[]` | מפגינים — `mkPeep` דוחף, `removePeep` מסיר |
| `cars[]` | רכבים אזרחיים — `spawnCar` / `removeCar` |
| `cops[]` / `horses[]` / `drivers[]` | שוטרים / פרשים / נהגים זועמים |
| `bubbles[]` | בועות דיבור — `say` / `updateBubbles` |
| `fx[]` | אפקטים `{update(dt)}`; `updateFx` מסנן מתים |
| `cannon` | מכת"זית יחידה או null |
| `train` | רכבת קלה או null; `G.trainTimer` טיימר לידה |
| `selected` | `Set<peep>` — הבחירה הנוכחית |
| `score` | ניקוד; לכתוב **רק** דרך `addScore` |
| `escTime` | שעון הסלמה — מתקדם רק כשיש חסימה בפועל; קובע גלים |
| `gameTime` | זמן שלב (טיימר HUD) |
| `jamPeak` | שיא ק"מ פקק (לסיכום) |
| `wave` | גל נוכחי 0–4 (`updateWaves`) |
| `stage` | אינדקס מפה 0–2 |
| `running` | שער הלולאה הראשית |
| `shofarUsed` | שופר חד־פעמי לשלב |
| `bagT` | שניות שנותרו להגנת שקיות ניילון |
| `runovers`/`fled` | מוצגים בסיכום (`sumInj`/`sumFled`) |
| `runId` | מזהה-דור, עולה ב-`startStage` — כל setTimeout תלוי-שלב בודק אותו |
| `laneDefs[]` | `{z,dir,speed}` פר נתיב — נקבע ב-`buildMap` |
| `roadLen/roadHalfW/sidewalkZ` | מידות כביש פר מפה |
| `spawnTimers[]` | טיימר ספאון פר נתיב |
| `muted` | השתקה — `tMute` |
| `totalBlockTime` | = `escTime`; "זמן חסימה" בסיכום |
| `camMode` | `'top'/'street'/'follow'/'free'` |

**שדות שנוספים ל-G מחוץ להגדרה**: `G.copCars=[]` ניידות · `G.alleys` נקודות
ספאון תגבורת פר מפה · `G.protestStarted` · `G.emptyT` מונה שניות כביש-ריק ·
`G.bigJamNews` · `G.finalScore` · `G._fr/_dpr/_low/_fpsMax/_good/_raiseNeed`
איכות אדפטיבית: יחסית לקצב-הרענון הנמדד (`_fpsMax`), ירידה אחרי 4 שניות מתחת
ל-72%, העלאה אחרי רצף שניות ≥92% שמתארך אחרי כל כישלון (היסטרזיס) ·
`G._waveTxt/_tankKey` מטמוני-DOM (כתיבה רק בשינוי).

**גלובליים אחרים**: עזרים `rnd/irnd/pick/clamp/lerp/dist2/fmtTime/$` (~405) ·
`MAPS` · `AUDIO` · `BGM` · `COPS` · `Fame` · `canvasTex`+`SERIF`+`TEX` ·
`MAT`/`_matCache`+`GEO`+`bx/cyl/texPlane/put` · `MAXCARS=40`+`CARCOLORS`+חומרי
רכב · `BRAKE=7, ACCEL=3.2` · `matC`+`FG`+`figGeos` · `UNIT_STATS` ·
`MAXPEEPS=80`+`SLOGANS` · `DRIVER_SHOUTS` · `CANNON_LEVELS` · `WAVES` · `CAM` ·
`raycaster/ndcV` · `MARKER` · `PTR` · `NEWS_STATIC/newsQ/newsT` · `scoreAcc` ·
`lastT`.

### מחזור חיים

- **טעינה**: `window load` → `initThree` → `initInput` → `wireUI` →
  `buildMap(0)` כרקע חי לתפריט → RAF loop → אחרי 500ms `showScr('menu')`.
- **התחלה**: `btnStart`/`.mapBtn` → `startRun(mapIdx)` (מאפס score) →
  `startStage(i)`: loading, אחרי 350ms `buildMap(i)` (שקורא `clearWorld`),
  איפוס דגלי שלב, 14 מפגינים פותחים + רכבים, `setCamMode('top')`, HUD,
  `G.running=true`, `BGM.play()`, `COPS.prime()`.
- **לולאה**: `loop(tms)`, RAF, dt מוגבל ל-0.05. כש-`G.running`:
  `updateTraffic → updatePeeps → updateDrivers → updateCops → updateHorses →
  updateCannon → updateWaves → scoreTick → updateNews` → בדיקת
  `peeps.length===0 && gameTime>6` → `endStage('cleared')`. תמיד (גם בתפריט):
  `updateBubbles`, `updateFx`, `updateCamera`, render; בתפריט המצלמה מסתובבת.
- **סיום שלב**: `endStage(reason)`. קוראים: הלולאה עם `'cleared'` (כל המפגינים
  פונו), `scoreTick` עם `'empty'` (כביש ריק 5 שניות — סוף סופי), כפתור `tEnd`
  עם `'manual'`. מוסיף `+100*שורדים`, עוצר סאונד, ממלא סיכום; `btnNext` מוצג
  רק אם `stage<2 && reason!=='empty'`.
- **"לשלב הבא"**: `btnNext` → `startStage(G.stage+1)` — **הניקוד נשמר** (רק
  `startRun` מאפס).
- **סיום ושמירה**: `btnFinish` → `finishGame`: תופס `G.finalScore`,
  `Fame.rankOf`; טופ-100 → `entryScr` + confetti; אחרת `showFame`.
  `btnSaveName` → `Fame.submit` → `showFame`.

### מסכים ו-DOM

`showScr(id)` מסתיר את כולם ומציג אחד: `loading` / `menu` / `howto` /
`fameScr` / `entryScr` / `summary`. ה-HUD (`#hud`) **אינו** ברשימה — מנוהל
ידנית: block ב-`startStage`, none ב-`endStage`.

| כפתור | handler (ב-`wireUI`) |
|---|---|
| `btnStart`, `.mapBtn[data-map]` | `startRun(idx)` + `AUDIO.init` |
| `btnFame`/`btnHow` (+Back) | `showFame()` / `showScr('menu')` |
| `btnNext` / `btnFinish` / `btnSaveName` | ראה מחזור חיים |
| `toolsBtn`+`toolsPop` | תפריט כלים נפתח (נסגר בקליק חיצוני) |
| `tPash/tShofar/tBag` | `toolPashkevil/toolShofar/toolBag` |
| `tBochur/tAvrech/tAskan` | `toolSummon(type)` |
| `tLie` / `tStand` / `tEnd` | `commandLie` / `commandStand` / `endStage('manual')` |
| `#camBtns [data-cam]` | `setCamMode` |
| `tMute` | טוגל `G.muted` + עצירת כל הסאונד |
| `selModeBtn` | מצב בחירה במגע — מוצג רק אם touch |

אלמנטים חיים ב-HUD: `tickerText` · `score` · `peepCount/timer/jam` + disabled
כלים (`scoreTick`) · `waveName` · `tankWrap/tankLabel/tankBar` · `trainBonus` ·
`selInfo` · `toast` · `selrect` · `loadMsg`.

### מערכות ישויות

| מערכת | ספאון | עדכון | הסרה | אחסון |
|---|---|---|---|---|
| מפגינים | `mkPeep(type,x,z)`; פתיחה; `toolPashkevil`/`toolSummon` | `updatePeeps` | `removePeep` (dispose חומרים) | `G.peeps` |
| רכבים | `spawnCar(lane)` מ-`updateTraffic` | `updateTraffic` → `updateCarKinematics` (+`laneObstacle`) | `removeCar` בקצה הכביש | `G.cars` |
| נהגים זועמים | `spawnDriver(car)` — תקוע >7 שנ' + מפגין קרוב, מקס' 6 | `updateDrivers`: walk→shout→push→return; מוותר אם `friendsNear`≥3 או שוכב | `removeDriver` | `G.drivers` |
| ניידות+שוטרים | `spawnPolice` (2 ניידות) → `spawnCopsFromCruiser` (3 כ"א) | `updateCops`: seek→walk→(argue 15ש' מול עסקן)→grab→drag; נצמד-לרכב חסין | אין — עד `clearWorld` | `G.copCars`, `G.cops` |
| פרשים | `spawnHorses` (3) | `updateHorses`: סריקת סינוס; רדיוס פחד 6 → panic | אין — עד `clearWorld` | `G.horses` |
| מכת"זית | `spawnCannon` (+260 חלקיקי Points) | `updateCannon`: arrive→work; מיכל 25ש' ← מילוי 15ש' ("חלון זהב"); רמה עולה כל 40ש' ריסוס | אין — עד `clearWorld` | `G.cannon` |
| רכבת קלה | בתוך `updateTrain` (רק stage 2) | נעצרת → blocked → +50/ש' | יציאה → null + טיימר 30–50ש' | `G.train` |

**מצבי מפגין** (switch ב-`updatePeeps`): `idle, moving, helping, panic,
blocking, lying, attached, recover, dragged, argue, flee, hit` + `pushed`
(מנוהל ע"י הנהג, אין case). שדות מפתח: `attachCar/attachPending` (הצמדות →
רכב תקוע), `copLock` (מניעת שוטר כפול), `wetCD`, `stain`,
`st=UNIT_STATS[type]`. עזרים: `steerPeep` (היגוי+הפרדה+עקיפה), `applyPose`,
`makeFlee` (−20), `detach`, `runOver` (−200), `animHumanoid` (כל הדמויות),
`faceToward`.

**בנאים**: `mkFigure` (שלד מפרקים; מגבעת/זקן/פאות/אפוד/פלאפון), `mkCopFig`,
`mkDriverFig`, `mkHorse`, `mkCarMesh(type)` — types:
`sedan/mazda/corolla/taxi/police/i10/suv/van/pickup/bus/mahtaz/tram`
(ExtrudeGeometry מפרופיל צד, `profileGeo`).

**גלים**: `WAVES` — נהגים t=0, משטרה 60, פרשים 150, מכת"זית 240 (לפי
`escTime`); `updateWaves` מפעיל ספאון + `COPS.play`.

**כלים**: `toolPashkevil` (−500, 10 מפגינים מ-`G.alleys`) · `toolShofar`
(−1000, הקפאה 5ש', חד־פעמי לשלב) · `toolBag` (−2000, `bagT=30`) ·
`toolSummon` (עלות מ-`UNIT_STATS.cost`).

**קלט RTS**: `initInput` — pointer events על `#c`; עכבר: שמאלי=בחירה/מלבן,
ימני=pan, אמצעי/Alt=סיבוב, גלגלת=זום, דאבל-קליק=שכיבה, Esc=ניקוי, l/ל=שכיבה;
מגע: אצבע=סיבוב (או בחירה ב-selectMode), צביטה=זום+pan, הקשה=פקודה, כפולה=
שכיבה. `clickAt` → `pickPeep` / `pickCar` (raycast) / `groundPoint` →
`commandAttach` / `commandMove` (פיזור ספירלת זהב). סמן ירוק/אדום: `dangerAt`
(מרחק בלימה פיזיקלי) + `showMarker`. מלבן: `drawRect/finishRect`.

**מצלמה**: `CAM`, `setCamMode`, `updateCamera` (מצב follow: התזה > מרכז
חוסמים > פרשים), `panCam`.

### מפות ושלבים

`buildMap(i)` → `clearWorld` ואז `mkRoadBase` + פנסים + פונקציית המפה.

| # | מפה | roadLen | halfW | נתיבים | בונה | ייחוד |
|---|---|---|---|---|---|---|
| 0 | צומת בר אילן | 200 | 3.7 | 2 (±1.8), מהירות 12 | `buildBarIlan` | צומת T, חנויות, עירוב |
| 1 | כביש 4 | 240 | 7.2 | 4, מהירות 20 | `buildKvish4` | גשר הולכי רגל, ספאון ×1.5 |
| 2 | גשר המיתרים | 200 | 6.0 | 2 (±3.6), מהירות 15 | `buildGesher` | מסילה + רכבת, תורן נטוי |

**נהרס בין שלבים** (`clearWorld`): `world`+`dyn` מוסרים מהסצנה **ועוברים
`disposeDeep`** (traverse שמשחרר geometry/material/map — משאבים משותפים כמו
GEO/MAT נטענים מחדש אוטומטית ברינדור הבא, זול); מערכי ישויות מתרוקנים,
`cannon/train=null`, `selected.clear`. **שורד**: renderer/scene/camera/תאורה/
שמיים, `MARKER` (על `G.scene`, רק מוסתר), והקאשים (`_matCache, CARGEO,
CARMATC, GEO_FIG, WGEO, M_RIM, HEADPOOL, TEX._lampPool/_smoke, _texShared,
_stinkTex`). טקסטורות שילוט קבועות (מונית/אגד/משטרה) — דרך `TEXC('name')`,
לא `TEX.name()` ישירות. אומת ב-renderer.info.memory: 5 מחזורי בנייה — אפס
טיפוס.

### אודיו

- **סינתזה** — `AUDIO`: `init/resume` (בקליק ראשון) · `tone` בסיס · `horn`
  צפירות · `ding` · `oy`+`thud` דריסה · `shofar` · `siren(on)` interval, כבה
  אוטומטית אחרי 9ש' · `startCrowd`/`setCrowd(n)` רחש קהל לפי חוסמים ·
  `spray(on)` מכת"זית.
- **מדיה**: `BGM` (intro.mp4) — play ב-`startStage`, stop ב-`endStage`/mute.
  `COPS` (cops.mp4) — `prime()` ב-`startStage` (שחרור autoplay במובייל בתוך
  מחוות לחיצה), `play()` בגל המשטרה.
- **השתקה**: `G.muted` נבדק בתוך tone/siren/spray/setCrowd/BGM/COPS; הטוגל
  גם עוצר הכל.

### ניקוד

`addScore(d)` — clamp ל-0. מקורות: **+10/ש' לכל רכב עומד** + **+50/ש' רכבת
חסומה** (`scoreTick`) · **+100×שורד** ב-`endStage` · **−200** דריסה · **−20**
בורח · עלויות כלים. פקק: `countStopped` = רכבים עם v<0.3; ק"מ = stopped/10;
מבזק "פקק ענק" בק"מ≥1. `scoreTick` רץ פעם בשנייה ואחראי גם על disabled כלים,
טיימר, קהל, ו-DPR אדפטיבי. סוף ניקודי: כביש ריק + 0 פקק 5ש' אחרי שההפגנה
החלה → `endStage('empty')`. היכל: `Fame`, מפתח `hosmim-et-hatzir-hof`,
טופ-100, שם עד 20 תווים, escape ל-`<>&` בהצגה.

### עוגני grep

| מערכת | עוגן |
|---|---|
| מצב גלובלי | `const G={` |
| מפות | `function buildMap` / `buildBarIlan` / `buildKvish4` / `buildGesher` |
| ניקוי עולם | `function clearWorld` |
| טקסטורות | `const TEX={` |
| רכבים | `function mkCarMesh` / `function updateCarKinematics` / `laneObstacle` |
| רכבת | `function updateTrain` |
| דריסה | `function runOver` |
| דמויות | `function mkFigure` / `animHumanoid` |
| מפגינים | `function updatePeeps` / `function mkPeep` / `UNIT_STATS` |
| נהגים | `function updateDrivers` / `DRIVER_SHOUTS` |
| משטרה | `function updateCops` / `function spawnPolice` |
| פרשים | `function updateHorses` |
| מכת"זית | `function updateCannon` / `CANNON_LEVELS` |
| גלים | `const WAVES=` |
| כלים | `function toolPashkevil` / `toolShofar` / `toolBag` / `toolSummon` |
| מצלמה | `function updateCamera` / `const CAM=` |
| קלט | `function initInput` / `function commandMove` / `function dangerAt` |
| ניקוד | `function scoreTick` / `function addScore` |
| מבזקים | `function newsFlash` |
| מסכים | `function showScr` / `function startStage` / `function endStage` |
| היכל | `hosmim-et-hatzir-hof` / `async function showFame` |
| לולאה | `function loop(tms)` |
| חיווט | `function wireUI` |
| אודיו | `const AUDIO={` / `intro.mp4` / `cops.mp4` |

## היסטוריית השיפוץ (מול המקור)

המקור: `hosmim-et-hatzir/hosmim-et-hatzir.github.io` ב-GitHub (של יוצר אחר,
**אין רישיון** — לא לפרסם כמאגר ציבורי משלנו; שיתוף פרטי בלבד).

1. **הוסרו כל הפרסומות** — AdSense (סקריפט+shim+אינטרסטיציאל), פרסומות-בית
   (#adSlot, #houseAdOverlay), טופס לידים (#leadForm) והנכסים שלהם (ad*.jpg,
   ads.txt). `endStage` מסתיים עכשיו ב-`showScr('summary')` נקי.
2. **עצמאות מלאה** — three.js+פונטים מקומיים; Fame הפך localStorage-בלבד
   (אין API/token/ping); sw.js v2 מקדים-מטמן הכל; privacy.html עודכן.
3. שני קבצי ה-HTML השמורים שהיו בתיקיית-העל נמחקו לבקשת המשתמש.
4. **מצב "צד המשטרה" + איפוס היכל** (2026-07-27): בורר צד בתפריט (`G.side`,
   `.sideBtn`); במצב משטרה — גלי מפגינים אוטונומיים (`PWAVES`+`updateDirector`:
   ספאון מהסמטאות, חסימה/שכיבה/הצמדות אוטונומיות, תווית גל, בדיקת ניצחון),
   כלי מפקד (`ptCruiser/ptHorse/ptCannon`, `spawnCruiser` יחידני), נקודת ריכוז
   (`G.rally`, קליק על הכביש, הטיה ×0.15 ב-seek), UI מוחלף פר-צד
   (`.pTool`/`scoreLbl`/תוויות סיכום), בלי היכל-תהילה לצד המשטרה
   (`finishGame` מחזיר לתפריט). איפוס היכל: `Fame.clear()` + `btnFameReset`
   באישור כפול. עוגנים: `updateDirector` / `PWAVES` / `sideBtn` / `btnFameReset`.
5. **אפיית מצב המשטרה** (2026-07-27, ultracode: 23 סוכנים — 4 עדשות ציד +
   אימות-עוין פר-ממצא + אנליסט איזון + מבקר עיצוב; 16 ממצאים אומתו ותוקנו):
   - **זליגות-צד**: BGM לא מתנגן במשטרה גם אחרי unmute; טוסטים/HUD של
     המכת"זית, הרכבת, הדריסה והסיכום (`sumScoreL/sumJamL/sumTimeL`) ממותגים
     פר-צד; דריסה במשטרה = "תחקיר מח"ש" (עדיין ‎-200).
   - **AI**: יעד הצמדות באגף הרכב (כמו `commandAttach` — יעד במרכז לא מושג);
     מכסת-3 כולל pending; שכיבה רק על הכביש; חילוצי-נהג לא משאירים 'blocking'
     מחוץ לכביש; הדירקטור מחזיר גם 'blocking' תקועים; המכת"זית לא נועלת על
     'dragged/pushed'; "יש לי אישור" חד-פעמי מול המפקד.
   - **איזון** (לפי האנליסט): שחיקה — גרירה שנייה ‎+30 והמפגין הולך הביתה
     (`p.dragN`; בלעדיה אין ניצחון בלי מכת"זית והפינוי הוא מזרקת-כסף); ציר
     פתוח ‎+10/ש' בלי דרישת אפס-פקק; פרשים 500 ושוברים שכיבה (60% למעבר,
     `p.horseCD`); גלים 6/35/40/45.
   - **פיצ'רים**: 🪝 גרר 600 (`ptTow` — מפנה את הרכב עם הכי הרבה צמודים;
     הרכב יוצא מ-`G.cars` בעת האיסוף — לא לקרוא `removeCar` אחריו!);
     📢 מגפון 250/CD-25ש' (`ptMeg`, `p.cowT=8` שהדירקטור מכבד; בלי חיוב
     כשאין מטרות); ⚠️ תנאי הפסד — 6+ על הכביש 90ש' ברציפות (דעיכה ×2,
     אזהרה ב-60ש', `endStage('overrun')`); 🚩 דגל ריכוז מתמיד (`ensureRallyFlag`,
     ב-`G.dyn`, מתאפס ב-`startStage`); 🎖 ביקורת המפכ"ל אחרי גל 3 (חלון 20ש',
     ציר פתוח = ‎+30/ש', כישלון ‎-150/הצלחה ‎+200); 🚌 הסעות מאורגנות 800 לצד
     המפגינים (`toolBus` — 8 מפגינים ליעד הפקודה האחרונה `G.lastMoveX`).
     תשתית משותפת: `shoulderVehicleFx` (רכב-שוליים: נכנס/עוצר/מבצע/יוצא).
   - שומר `safeAspect` ב-initThree (חלון 0×0 בטעינה = NaN בהקרנה).
   עוגנים חדשים: `shoulderVehicleFx` / `ptTow` / `ptMeg` / `toolBus` /
   `ensureRallyFlag` / `overT` / `visitAt` / `dragN` / `cowT`.
6. **התאמת דסקטופ מלאה** (2026-07-27): מערכת השהיה אמיתית (`setPaused` +
   `#pauseOv` עם המשך/התחל-מחדש/יציאה; רווח/P/Esc; Esc קודם מנקה בחירה;
   השהיה אוטומטית ב-window blur; `gameTimeout` — טיימר מכבד-השהיה שמחליף
   setTimeout בכל הספאונים המדורגים); מקלדת מלאה לפי `e.code` (פיזי — ידידותי
   לפריסה עברית): חיצים/WASD פאן, Q/E סיבוב, ‎+/-‎ זום, ספרות 1–7 כלים לפי
   צד (`hotkeyTool`), L/K שכיבה/קימה, M השתקה, Tab מיני-מפה, F11 מסך מלא
   (בדפדפן — `toggleFullscreen`; ב-EXE העטיפה מטפלת, מזוהה ב-`IS_ELECTRON`);
   מיני-מפה (`drawMinimap`, canvas ‎230×76 בפינה שמאלית-עליונה, 8Hz, מוצגת
   רק במשחק); העדפות נשמרות (`hosmim-prefs`: mute+minimap, `loadPrefs` ב-wireUI);
   טולטיפים עם קיצורים. עטיפת Electron: מופע יחיד
   (`requestSingleInstanceLock`), זכירת מיקום/גודל/מצב חלון
   (`window-state.json` ב-userData). עוגנים: `setPaused` / `gameTimeout` /
   `drawMinimap` / `hotkeyTool` / `applyKeyCam` / `hosmim-prefs`.
7. **פיקוד ישיר למשטרה + יחידות + נוחות** (2026-07-27, אחרי סריקה #2 של 21
   סוכנים — 9 מאומתים + 4 שנשפטו ידנית, כולם תוקנו):
   - **תיקונים**: KEYPAN מתנקה ב-blur (מקש תקוע אחרי Alt+Tab); unmute לא מזניק
     BGM מאחורי השהיה; Tab בתפריט לא הופך העדפה בשקט; COPS מתחדש אחרי השהיה
     (`COPS.resume`, `G._copsWasOn`); **GTQ** — תור טיימרים בשעון-משחק
     (`gameTimeout`→`runGTQ` בלולאה, מתנקה ב-runId) ששומר את מרווחי הספאון דרך
     השהיה; "התחל מפה מחדש" משחזר `G.stageStartScore` (לא מוחק את כל הריצה);
     פסק-דין המפכ"ל לא סופר בורחים/נגררים; הגרר מתקרב ב-lerp (בלי טלפורט);
     `startStage` מקפיא את השלב הישן בחלון הטעינה; idle-על-הכביש→blocking
     (משוחררי-גרר היו בלתי-נראים לשוטרים); `_fr` לא נספר בהשהיה (ניפוח FPS);
     מיני-מפה מוסתרת במובייל וצבע המכת"זית בה הובהר.
   - **פיקוד ישיר (מענה למשוב "משעמם, אין מה לעשות")**: מלבן-בחירה בוחר
     שוטרים (טבעות כחולות, `G.selCops`), קליק שולח אותם לנקודה (`cop.orderPt`
     — עדיפות מטרות סביב היעד, שמירה על העמדה כשאין), Ctrl+A בוחר הכל,
     דגל-הריכוז מכוון גם את המכת"זית ("ריכוז אש"), הבזק ירוק על התקציב בכל
     תגמול + tooltip ומבזק שמסבירים ממה הכסף מגיע.
   - **יחידות**: 📸 צלם (מפגינים, 400, עד 2, מקש 8) — `zalamNear` הילה r=8:
     תפיסה ×1.7 וגרירה ×0.6; 🕵 בלש סמוי (משטרה, 700, מקש 6) — `ptDet`:
     מסתנן מחופש דרך G.fx, נחשף ליד המטרה ומבצע מעצר-בזק, מצטרף ל-G.cops.
   - **נוחות**: קליק על המיני-מפה מזיז מצלמה; סליידר עוצמת-קול בתפריט ההשהיה
     (`setVolume`, נשמר ב-prefs.vol).
   עוגנים: `GTQ` / `runGTQ` / `orderPt` / `selCops` / `zalamNear` / `ptDet` /
   `setVolume` / `stageStartScore`.
   בנוסף: כפתורי המפות בתפריט הם **בורר** (`G.mapSel`, `.mapBtn.active`) —
   "התחל" משגר את הבחירה ומציג את שמה (`updateStartBtn`/`G._updStart`);
   לחיצה על כפתור מפה כבר לא משגרת ישירות.
8. **תיקון 22 באגים** (2026-07-27, אחרי סריקת 3 צוותים; קדם כרונולוגית ל-4–7): dispose מלא
   ב-clearWorld, עצירת רחש-קהל בסיום, TEXC, רזולוציה אדפטיבית יחסית-לרענון,
   detach בדריסה, runId נגד timeouts זומבים, מכת"זית לא פוגעת בנגררים,
   ספירת "5 שניות" אמיתית, גל 1 רק אחרי חסימה, BGM.resume, שומר THREE,
   COPS.prime במחווה, דילוג רינדור מאחורי מסכים, escape בהיכל, sw.js
   fallback לניווט בלבד (v3), start_url נקי, + שני תאים חדשים בסיכום
   (נפצעו/ברחו). פירוט מלא: git log/diff.

## בניית הפצות

**נוהל סנכרון (הוראת קבע מהמשתמש):** אחרי כל שינוי משמעותי בקוד — לבנות
מחדש **גם ZIP וגם EXE**, כך ששלושת התוצרים (מקור / ZIP / EXE) תמיד באותה
גרסה. שינויי תיעוד בלבד לא נחשבים.

**ZIP לשיתוף** (~634KB): קבצי המשחק + `להפעלה.html` (מפנה ל-index) +
`קראו אותי.txt`. נבנה בפייתון zipfile (שמות עבריים = דגל UTF-8 תקין;
Compress-Archive של PS5.1 משבש אותם). פלט: `../חוסמים את הציר.zip`.

**EXE נייד** (~90MB, בלי תלות בכלום): Electron portable.
```
desktop/package.json  ← תצורת electron-builder (target: portable)
desktop/main.js       ← עטיפה: חלון 1280×800, F11 מסך-מלא, בלי תפריט,
                         autoplay מותר, קישורים חיצוניים → דפדפן
```
בנייה (בתיקייה זמנית עם נתיב ASCII! electron-builder נשבר על נתיבים עבריים):
העתק את desktop/* + את קבצי המשחק ל-`app/` בתוכה ← `npm install` ←
`npx electron-builder --win portable`. אומת: Electron 43, builder 26.
SmartScreen יתריע (לא חתום) — "More info → Run anyway".

## מוסכמות

- הכל בעברית RTL; מחרוזות UI בתוך הקוד. שמות ניקוד: `toLocaleString('he-IL')`.
- `$('id')` = getElementById (helper). מסכים = div.scr + showScr(id).
- אין node_modules, אין בנדלר, אין TypeScript — עריכה ישירה של index.html.
- בדיקות: שרת מקומי `python -m http.server` ← דפדפן. אימות אופליין: לכבות
  את השרת ולרענן (SW מגיש מהמטמון). file:// עובד (אין fetch, אין modules).
- `git status` חייב להישאר קריא — המקור עדיין ב-git; diff מול HEAD מראה
  בדיוק את השיפוץ.

## מצאי באגים (סריקת 3 צוותים + אימות, 2026-07-27)

**כל 22 הממצאים תוקנו ואומתו ב-2026-07-27** (ראה "היסטוריית השיפוץ" סעיף 4).
אימות מרכזי: renderer.info.memory יציב על פני 5 מחזורי שלב (הדליפה מתה),
crowd-gain 0 אחרי endStage, 0 פריימים מרונדרים מאחורי מסכים, escape פעיל
בהיכל. הרשימה המקורית נשמרת למטה כתיעוד ידע על מלכודות המערכת — **אלה כבר
לא באגים פתוחים.**

<details><summary>הארכיון (תוקן)</summary>

### חמור

1. ✓✓ **`clearWorld` לא משחרר שום משאב GPU** (עוגן: `function clearWorld`,
   ~1078) — רק remove מהסצנה. כל בנייה יוצרת ~65–70 CanvasTextures (חזיתות,
   פשקווילים, שלטים, כבישים ≈ 10MB+ GPU) + עשרות חומרים; גם ישויות חיות בסוף
   שלב מדלגות על ה-dispose הפרטני שלהן (לוחיות, בועות, תותח). אפילו משחק יחיד
   מייתם עולם שלם (עולם-רקע לתפריט נבנה ב-load ונזרק ב-startStage). תסמין:
   זיכרון מטפס בכל ריפליי/שלב, קריסת WebGL context במובייל. תיקון: traverse
   על world+dyn ולשחרר geometry/material/map לפני ההחלפה.

### בינוני

2. ✓✓ **רחש הקהל ממשיך אחרי סיום שלב** (עוגן: `endStage`, שורת העצירות
   ~3223) — עוצרים סירנה/ריסוס/BGM/COPS אבל לא `AUDIO.setCrowd(0)`; הלולאה
   מוזנת רק מ-`scoreTick` שרץ רק כש-running. תסמין: זמזום מתחת לסיכום/תפריט
   לנצח. תיקון: `AUDIO.setCrowd(0)` ב-endStage.
3. ✓ **טקסטורות שילוט פר-רכב דולפות תוך כדי משחק** (עוגן: `TEX.taxi()` בתוך
   `mkCarMesh`) — מונית/אוטובוס/ניידת יוצרים CanvasTexture חדשה לכל מופע;
   `removeCar` משחרר רק את לוחית הרישוי. התוכן זהה בכל קריאה — לקאש פעם אחת
   (כמו `TEX._smoke`).
4. ✓ **רזולוציה אדפטיבית פגומה** (עוגן: `G._low`, ~3163) — (א) מכשיר שנעול
   על 30Hz (חיסכון סוללה) נמדד כ"איטי" ונדחף לרצפת DPR לתמיד — משחק מטושטש
   בלי סיבה; (ב) העלאה חוזרת אחרי שנייה טובה אחת בלי זיכרון לרמה שנכשלה —
   פינג-פונג חד/מטושטש כל ~5ש'. תיקון: השוואה לקצב-רענון, דרישת רצף שניות
   טובות, זכירת רמה כושלת.
5. ✓? **מפגין נדרס בזמן `attached` מקפיא רכב לנצח** (עוגנים: `function
   removePeep`, `function runOver`, `car.attached.length>0`) — runOver/
   removePeep לא קוראים `detach`, המפגין נשאר ב-`car.attached`, הרכב תקוע
   (v=0) עד סוף השלב ומשלם +10/ש' לנצח. תיקון: `detach(p)` בתוך removePeep.

### קל — משחקיות

6. ✓✓ **"תסתיים בעוד 5 שניות" = 4 בפועל** (עוגן: `G.emptyT`, ~3142) — טוסט
   ב-emptyT===1, סיום ב->=5, טיק 1Hz → 4 שניות מהטוסט. תיקון: >=6.
7. ✓✓ **גל 1 נורה ב-t=0; "התארגנות" בלתי-נגיש** (עוגן: `const WAVES=`) —
   `escTime>=0` תמיד אמת → w=1 מהפריים הראשון + מבזק "נהגים מאבדים סבלנות"
   בפתיחת שלב. תיקון: `t>0` לגל 1 או שער על `protestStarted`.
8. ✓ **setTimeout של ויכוח-שוטר שורד את השלב** (עוגן: `'argue'` בתוך
   `updateCops`, ~2429) — cop.state קופא ב-endStage, וה-callback (1.6ש')
   יכול להזריק בועת "אדוני, פנה את הציר!" לשלב הבא. תיקון: בדיקת G.running
   או מונה-דור.
9. ✓ **מכת"זית פוגעת במפגין `dragged`** (עוגן: לולאת הפגיעה ב-`updateCannon`,
   ~2630) — kנס −20 + flee למפגין שכבר נגרר; השוטר ממשיך להצמיד אותו וכותב
   'recover'. תיקון: לדלג על dragged/pushed בלולאת הפגיעה.
10. ? **פשקווילים pending זולגים לריצה חדשה באותה מפה** (עוגן:
    `toolPashkevil`) — השומר משווה `G.stage===stg` אבל לא מבחין בין ריצות;
    חלון ~3ש' עם 4 קליקים מהירים. תיקון: מונה-דור ב-startStage.

### קל — UI/אודיו/עטיפה

11. ✓✓ **הודעות "טוען..." מתות** (עוגנים: `'בודקים את ספר השיאים'`,
    `'פותחים את ספר הזהב'`) — שרידי עידן-הרשת; Fame מקומי ומיידי, המסך לא
    מספיק להיצבע. תיקון: להסיר את קפיצות מסך-הטעינה.
12. ✓✓ **שדות מתים** — `G.blockTimePeak/paused/newsExtra/startScore` (אפס
    שימוש); `G.runovers/fled` נספרים ומאופסים אך לא מוצגים בשום מקום (מועמדים
    לסיכום); `p.helpT` לא בשימוש. תיקון: מחיקה או חיווט לסיכום.
13. ✓ **unmute מאתחל את המוזיקה מ-0:00** (עוגן: `tMute`, ~3362 +
    `BGM.play` שעושה `currentTime=0`). תיקון: play בלי איפוס currentTime.
14. ✓ **מסך שגיאת-טעינה בלתי-נגיש** — `new THREE.Vector3()` בתוך הגדרת G
    (~425) רץ בזמן eval; אם three.min.js נכשל, הסקריפט מת לפני שנרשם handler
    ה-load עם ה-try/catch — נשארים על "טוען את הצומת..." לנצח. תיקון: שער
    `if(!window.THREE)` מוקדם.
15. ✓ **`COPS.prime()` מחוץ למחוות משתמש** — `startStage` רץ ב-setTimeout
    350ms, כך שה-prime (טריק שחרור autoplay) לא על stack המחווה — ב-iOS
    ה-play של גל המשטרה עלול להיחסם בשקט. תיקון: prime ישירות ב-onclick
    (כמו `AUDIO.init`).
16. ✓ **רינדור מלא מאחורי מסכים אטומים** (עוגן: הקריאה `renderer.render`
    בלולאה) — כולל shadow-pass של 2048² כשהתפריט/סיכום מכסים הכל — סוללה.
    תיקון: לדלג על render כשמסך אטום פתוח (או להאט ל-few fps).
17. ✓ **מיקרו-דליפות בנתיבי הסרה "נקיים"** — חומר טבעת-הבחירה לא ב-`p.mats`;
    `removeDriver` לא משחרר כלום (~6 חומרים לנהג). GC pressure.
18. ✓ **הקצאות פר-פריים במכת"זית** — `cannonNozzle`/`mid` Vector3 חדשים כל
    פריים; `stinkFx` בונה canvas+טקסטורה חדשים לכל פגיעת בואש (לקאש כמו
    `TEX._smoke`).
19. ✓ **כתיבות DOM כל פריים** — `waveName` (updateWaves), `updateTankHUD`
    בזמן ריסוס, `trainBonus.style` במפה 3. תיקון: לכתוב רק בשינוי ערך.
20. ✓ **`pingFx` מתייתם בהחלפת שלב** — נוסף ל-`G.scene` (לא `G.dyn`) בעוד
    שההסרה חיה ב-`G.fx` ש-clearWorld מרוקן — טבעת קפואה + חומר לא משוחרר.
    תיקון: להוסיף ל-G.dyn.
21. ✓ **sw.js: fallback של index לכל בקשה** — כשל אופליין של נכס לא-מוטמן
    מקבל HTML במקום כשל נקי. תיקון: להגביל את הנפילה ל-`req.mode==='navigate'`.
22. ✓ **זוטות** — `manifest.json` start_url `/?app=1` — דגל מת (אין קורא);
    סניטייזר ההיכל מוחק `<>&` במקום escape ("a<b" מוצג "ab") — קוסמטי.

</details>

### אזורים שנסרקו ונמצאו נקיים

איפוסי שלב מלאים ב-`startStage` · הגנת re-entry ב-endStage (running flag) ·
כל המעדכנים תחת `if(G.running)` · RAF יחיד, dt מוגבל 0.05 · אין באגי splice
(איטרציה לאחור בכל מקום) · resize/pixelRatio תקינים (cap 2) · raycast רק
בקליק · אין הצטברות אורות/מאזינים · חלקיקים חסומים בתקרות · אין שאריות
פרסומות/רשת (אפס hits) · innerHTML של ההיכל מסונן · אחסון עמיד לשחיתות/quota ·
sw.js CORE תואם אחד-לאחד לדיסק · RTL/מספרים תקין.
