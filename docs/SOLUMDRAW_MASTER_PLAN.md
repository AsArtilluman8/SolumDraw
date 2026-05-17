# SOLUMDRAW_MASTER_PLAN — финальная цель, MVP, debug и workflow

Дата: 2026-05-18
Статус: draft для обсуждения перед новым циклом разработки.

Этот документ фиксирует рельсы SolumDraw, чтобы проект не снова ушёл в fake-fill, мусорный UI, слепые патчи и непонятные debug-логи.

---

## 1. Главная цель

SolumDraw — не заливка картинки и не debug renderer.

Финальная цель:

```text
image / prompt / keyframe
↓
понимание объектов, жанра, важности областей
↓
план рисования как у художника
↓
честные brush strokes
↓
стадии: фон → силуэт → формы → свет/тень → детали → полировка
↓
качественный результат без дыр, квадратов, scanline и fake fill
```

Главное требование: рисовать кистью, как в Paint / Photoshop / Gartic Phone, а не прямоугольниками, scanline, island-fill или скрытым принтером.

---

## 2. Три финала проекта

### Final 1 — Image-to-Draw 1:1

Пользователь загружает изображение.
SolumDraw анализирует его и рисует почти 1:1 честными мазками.

Цель качества:

```text
MVP: 80–90% визуальной похожести, без дыр и fake-fill
Post-MVP: 90–95%
Final 1: 95–99%, если это не превращает процесс в принтер
```

Если абсолютная точность конфликтует с человеческим процессом, приоритет такой:

1. нет дыр и щелей;
2. нет fake-fill / квадратов / scanline;
3. процесс похож на художника;
4. сохраняются важные детали;
5. растёт точность к оригиналу.

### Final 2 — Text-to-Image Draw

Пользователь пишет текст или тэги.
Система создаёт новую картинку и рисует её как художник.

Это не MVP. Это отдельный этап после стабильного Image-to-Draw.

### Final 3 — Anime Frames / Keyframes

Система рисует серии кадров для аниме/сцен/боёв.

Цель:

```text
keyframes
↓
16–60 fps кадры
↓
сохраняются лицо, одежда, стиль и персонаж
↓
можно делать anime scenes, action, VFX, cinematic shots
```

Это поздний этап после собственной ML-памяти, стабильного стиля и контроля консистентности.

---

## 3. MVP SolumDraw

MVP должен быть неполным, но архитектурно правильным.

MVP должен дать:

- импорт изображения;
- чистую кнопку Draw;
- PainterEngineV2 вместо старого fake pipeline;
- реальную мягкую кисть;
- canvas feedback: оценка идёт по настоящему холсту;
- error map: где картинка ещё не похожа на target;
- stage pipeline: фон, силуэт, формы, refine, features, polish;
- visual debug без ручного распаковывания ZIP;
- report ZIP одним файлом для отправки агенту;
- понятные русские причины проблем.

MVP не обязан сразу:

- давать 99%;
- иметь свою обученную ML;
- идеально понимать все жанры;
- генерировать новое изображение по тексту;
- делать аниме-кадры.

MVP запрещает:

- квадраты как основной renderer;
- drawRect как основу кисти;
- scanline printer look;
- island-per-color как главный способ;
- fake coverage, где код считает пиксель закрытым, а на canvas дырка;
- большой поток статус-сообщений, который лагает UI;
- debug-кнопки в обычном рабочем UI.

---

## 4. Правильный Draw Engine V2

Главная схема:

```text
target image
↓
current canvas
↓
error map
↓
candidate strokes
↓
simulate strokes locally
↓
choose best strokes
↓
commit to canvas
↓
update error map
↓
next batch
```

Смысл: движок не должен строить весь план вслепую один раз. Он должен смотреть, что реально получилось на холсте, и выбирать следующий мазок.

Базовые модули:

- `PainterEngineV2` — главный контроллер стадий.
- `PainterCanvasModel` — реальный ARGB canvas.
- `PainterBrush` — мягкая круглая кисть.
- `PainterStroke` — описание мазка.
- `PainterErrorMap` — карта ошибки canvas-target.
- `PainterVisionMap` — объекты, области, важность, лицо, волосы, одежда, фон.
- `PainterStrokeGenerator` — создаёт варианты мазков.
- `PainterStrokeEvaluator` — проверяет, какой мазок улучшит результат.
- `PainterDebugReport` — сохраняет понятный visual/report output.

---

## 5. Стадии рисования

```text
Stage 0 — Background / Underpaint
быстро убрать белый холст, дать общий цвет

Stage 1 — Foreground Silhouette
закрыть главный объект без больших дыр

Stage 2 — Big Masses
кожа / волосы / одежда / тело / фон крупными зонами

Stage 3 — Form Refine
свет, тень, полутона, объём

Stage 4 — Directional Strokes
волосы вдоль прядей, одежда вдоль складок, лицо по форме

Stage 5 — Feature Lock
глаза, рот, нос, подбородок, важные контуры, символы, текст

Stage 6 — Detail Polish
мелкие детали там, где error map высокий

Stage 7 — Final Correction
убрать остаточные дыры и заметные ошибки маленькими мягкими мазками
```

Стадии должны отображаться в UI по-русски и в debug report.

---

## 6. UI workflow

Обычный режим должен быть простым:

```text
Импорт
Draw
Вид
Отчёт
```

Debug не должен торчать на главном экране.

Debug mode / bottom sheet:

```text
Анализ
ML
Маски
Ошибка
Стадии
Bench
Логи
Экспорт
```

Правило:

```text
обычный пользователь видит результат
разработчик открывает debug только когда надо понять проблему
```

Статус должен быть коротким:

```text
Stage: Силуэт
Progress: 31%
Problem: внутри объекта ещё 4.8% дыр
Next: repair before details
```

Нельзя спамить статус длинным логом.

---

## 7. Visual Debug

Пользователь должен видеть debug прямо в приложении, без обязательного распаковывания ZIP.

Режимы вида:

```text
Source — оригинал
Canvas — текущий результат
Side-by-side — оригинал + результат
Error Heatmap — где ошибка высокая
Gap Mask — где дыры/незакрытые места
Vision Map — что система считает объектами и важными зонами
Feature Overlay — глаза/рот/нос/подбородок/контуры
Stage Replay — кадры стадий
```

Feature overlay должен быть не просто точками, а понятной полупрозрачной обводкой:

- лицо — мягкий овал;
- глаза — контуры/капсулы;
- рот — контур зоны;
- нос — центральная зона;
- подбородок — нижняя форма;
- главный объект — полупрозрачная маска;
- важные детали — жёлтая/оранжевая подсветка.

Цвета должны сопровождаться подписью, а не быть единственным смыслом.

---

## 8. Output policy

Не засорять Download.

Рабочая папка:

```text
/storage/emulated/0/Download/SOLUMCreative/SolumDraw/latest/
```

После каждого запуска Draw перезаписывать latest:

```text
latest/source.png
latest/final_canvas.png
latest/error_heatmap.png
latest/gap_mask.png
latest/vision_overlay.png
latest/feature_overlay.png
latest/stage_00_background.png
latest/stage_01_silhouette.png
latest/stage_02_mass.png
latest/stage_03_refine.png
latest/stage_04_features.png
latest/stage_05_polish.png
latest/report_ru.txt
latest/metrics.json
latest/runtime_log.txt
latest/solumdraw_latest_report.zip
```

Правило:

- `latest/` всегда перезаписывается;
- мусор не копится;
- ZIP один, чтобы отправить агенту;
- внутри приложения можно открыть latest debug images без распаковки руками.

Archive создаётся только по отдельной кнопке `Сохранить отчёт`, не автоматически каждый раз.

---

## 9. Русский debug explanation

Каждая проблема должна объясняться просто.

Примеры:

```text
Проблема: дыры в силуэте.
Причина: foreground mask считает эту область объектом, но кисть туда не дошла.
Что делать: повторить repair stage до деталей.
```

```text
Проблема: лицо потеряло глаза.
Причина: feature priority низкий или face landmarks не найдены.
Что делать: включить fallback face heuristic и поднять priority зоны глаз.
```

```text
Проблема: результат похож на принтер.
Причина: слишком много маленьких мазков без object-level стадий.
Что делать: увеличить крупные стадии и рисовать по объектам/формам.
```

---

## 10. ML и research gate

ML подключается поэтапно, не хаотично.

Перед добавлением ML/lib/repo нужен Research Gate:

```text
что даёт
↓
насколько тяжёлое
↓
работает ли offline
↓
риск Android/Termux build
↓
качество на anime/portrait/object/landscape
↓
можно ли взять только идею без зависимости
↓
решение: REFERENCE_ONLY / SMALL_SLICE / ADAPTER / DEPENDENCY / REJECT
```

Предварительные направления:

| Направление | Для чего | Решение на MVP |
|---|---|---|
| ML Kit Object Detection | объекты / labels | оставить, уже есть |
| Face landmarks | глаза/нос/рот/подбородок | использовать, если стабильно |
| OpenCV | contours, masks, error, morphology | можно использовать аккуратно |
| MediaPipe segmentation | foreground mask | проверить отдельным research patch |
| U2-Net / anime segmentation | точный силуэт anime/art | позже, риск тяжелее |
| TFLite semantic segmentation | skin/hair/cloth/detail | после Painter MVP |
| Neural painter | предсказывать следующий мазок | не MVP |
| Own ML training | научить рисовать лучше/меньше strokes | после стабильных reports/dataset |

---

## 11. Roadmap патчей

### Stage A — Stabilize / Reset

Patch A0 — Docs + project rules
- добавить этот master plan;
- добавить правила патчей;
- добавить debug/report spec;
- зафиксировать MVP и финалы.

Patch A1 — UI cleanup
- обычный UI: Import / Draw / View / Report;
- debug спрятан в Debug mode;
- убрать статус-спам.

Patch A2 — Output/latest policy
- всё в `SOLUMCreative/SolumDraw/latest/`;
- latest перезаписывается;
- archive только вручную;
- один ZIP для отправки агенту.

Patch A3 — Visual Debug v1
- source/canvas/error/gap/vision/features/stages;
- просмотр прямо в приложении;
- report_ru.txt.

### Stage B — PainterEngineV2 MVP

Patch B1 — Painter core
- `PainterCanvasModel`;
- `PainterBrush`;
- `PainterStroke`;
- real brush renderer.

Patch B2 — Feedback loop
- `PainterErrorMap`;
- локальная оценка мазков;
- no fake drawn[] coverage.

Patch B3 — Stage pipeline
- background;
- silhouette;
- mass;
- refine;
- features;
- polish;
- final correction.

Patch B4 — VisionMap v1
- foreground/background;
- important regions;
- face/upper-body fallback;
- объектный порядок рисования.

Patch B5 — MVP lock
- убрать большие дыры;
- убрать printer look;
- baseline reports на 5–10 тестовых картинках.

### Stage C — Better vision / ML assist

Patch C1 — Face feature overlay
- глаза/рот/нос/подбородок;
- визуальная обводка;
- feature priority.

Patch C2 — Segmentation research
- MediaPipe/U2Net/TFLite сравнение;
- только report, без слепого подключения в production.

Patch C3 — Semantic zones v1
- skin/hair/cloth/background/detail;
- лучшее планирование стадий.

### Stage D — Painter quality

Patch D1 — Directional strokes
- волосы вдоль направления;
- одежда вдоль складок;
- контуры по форме.

Patch D2 — Stroke economy
- меньше действий без потери качества;
- крупные формы вместо прыжков по островам.

Patch D3 — Quality profiles
- fast / balanced / quality;
- контроль времени и числа strokes.

### Stage E — Own ML painter

Patch E1 — Dataset capture from reports
- сохранять target/canvas/strokes/error/gains;
- готовить данные для обучения.

Patch E2 — Night training prototype
- автономное обучение ночью;
- отчёт о качестве;
- не ломать приложение.

Patch E3 — Learned stroke planner
- ML предлагает следующий мазок;
- rule-based painter остаётся fallback.

### Stage F — Text-to-Image Draw

Patch F1 — prompt/tag intent
Patch F2 — image draft generation
Patch F3 — draw-process generation
Patch F4 — style/identity controls

### Stage G — Anime keyframes

Patch G1 — character consistency tools
Patch G2 — keyframe drawing
Patch G3 — frame interpolation/sequence logic
Patch G4 — action/VFX/anime scene pipeline

---

## 12. Patch workflow

Перед каждым патчем обязателен PRE-PATCH CHECK:

```text
Stage / Patch
Docs read
Scope
Out of scope
Evidence plan
Risk
```

Для ML/debug/vision/painter сложных задач добавлять Research Gate.

После патча ответ должен содержать:

- что изменилось;
- что пользователь должен увидеть;
- где APK/report/log;
- что проверить;
- known issues;
- следующий шаг.

---

## 13. Когда не гнаться за идеалом

До MVP разрешено:

- чинить архитектуру;
- делать diagnostic-first patches;
- получать среднее качество;
- тестировать варианты;
- менять план после evidence.

Перед MVP lock и Final lock обязательно:

- полировать качество;
- сравнивать на тестовом наборе;
- закрывать known issues;
- не идти дальше, если основная цель не достигнута.

---

## 14. Главный принцип

SolumDraw должен учиться рисовать как художник:

```text
не прыгать по случайным островам
не печатать пиксели
не закрывать мусорными квадратами

а:
видеть объект
↓
закрывать форму
↓
идти по контуру
↓
заливать форму кистью
↓
добавлять свет/тень
↓
усиливать важные детали
↓
исправлять ошибки по реальному canvas
```

Если патч не ведёт к этому — патч неправильный.
