# SolumDraw — roadmap до финальной цели

SolumDraw — система рисования человеческими движениями кисти.

## Цель

```text
image / prompt / tags
-> анализ жанра, объектов, областей, контуров
-> visual debug
-> route planner
-> stroke engine
-> critic / repair
-> drawing program
```

Два режима:

```text
1. Copy Mode — почти точное рисование по исходнику.
2. Imagination Mode — рисование из головы по идее/prompt.
```

## Этап 1 — CV и visual debug

### Patch 26 — OpenCV-style contours + islands

- edge map
- Canny/Sobel
- connected components
- острова областей
- сглаженные контуры
- внешний силуэт
- внутренние линии

View:

```text
Source / Edges / Contours / Islands / Roles / Route / Canvas
```

### Patch 27 — Visual Debug Report v2

Отчёт:

```text
01_source.png
02_edges.png
03_contours.png
04_islands.png
05_roles.png
06_route.png
07_draw_plan.png
08_mistakes.png
analysis_report_ru.html
analysis.json
```

### Patch 28 — Honest Analyzer v4

Каждый жанр считается независимо:

```text
UI, anime, portrait, landscape, architecture, logo, vector, lineart, photo, texture, document
```

Потом Evidence Fusion выбирает top1/top3.

## Этап 2 — dataset pipeline

### Patch 29 — gallery-dl dataset collector

- только изображения
- запрет mp4/webm/gif/mov/zip
- лимит размера
- лимит количества
- временный cache
- rejected/duplicates
- manifest.json

Структура:

```text
~/SolumDraw/datasets/SolumDrawDataset_v2/
  images/
  labels/
  labels.csv
  dataset_manifest.json
  rejected/
  duplicates/
```

### Patch 30 — dataset cleaner + benchmark

- битые файлы
- видео/gif/webm
- большие файлы
- дубли
- train/val split
- benchmark zip в Download

## Этап 3 — Copy Mode

### Patch 31 — Accurate Copy v1

Фон -> крупные области -> силуэт -> контуры -> заливки -> тени -> блики -> детали.

### Patch 32 — Error Critic

Ищет дырки, грязь, недостающий цвет, сломанный контур, лишние штрихи.

### Patch 33 — Budget Reducer

Бюджеты: 10000, 5000, 2000, 1000, 500, 300, 100 действий.

## Этап 4 — Human Stroke Engine

### Patch 34 — Brush Controller

Stroke поля:

```text
tool, size, alpha, pressure, speed, pause_ms, angle, smoothness
```

### Patch 35 — Human Motion

Ускорение, замедление, паузы в углах, плавные кривые, широкие мазки для фона, короткие штрихи для деталей.

## Этап 5 — OCR / Face / Object

### Patch 36 — OCR

Настоящий текст для UI/document/logo.

### Patch 37 — Face/person

Лицо, голова, тело, portrait, fullbody, character.

### Patch 38 — Object/saliency

Главный объект, фон, второстепенные объекты, точки внимания.

## Этап 6 — ML classifier

### Patch 39 — ML-ready pipeline

resize 224x224, train/val split, class_index.json, stats, report.

### Patch 40 — MobileNetV3/EfficientNet-lite classifier

Жанр, стиль, top3, confidence.

### Patch 41 — Android inference

ONNX/TFLite модель в приложении.

## Этап 7 — ML route/scoring

### Patch 42 — Region scorer

Оценивает: фон, лицо, тело, деталь, шум, текст, UI, важная область.

### Patch 43 — Route scorer

Выбирает порядок рисования.

### Patch 44 — Brush scorer

Выбирает размер кисти, скорость, alpha, нажим, тип мазка.

## Этап 8 — Imagination Mode

Не Stable Diffusion. Не шум в пиксели.

```text
prompt -> scene plan -> layout -> shapes -> sketch strokes -> lineart -> color/light -> critic/repair
```

Patch 45: Prompt -> Scene Plan.
Patch 46: Scene Plan -> Layout.
Patch 47: Layout -> Shapes.
Patch 48: Shapes -> Sketch.
Patch 49: Sketch -> Clean Lineart.
Patch 50: Color / Light.
Patch 51: Critic / Repair.

## Финальный Drawing Program

```json
{
  "idea": "fantasy forest character",
  "composition": {},
  "regions": [],
  "layers": [],
  "strokes": [
    {
      "tool": "soft_brush",
      "stage": "background",
      "path": [[10, 20], [40, 30]],
      "size": 30,
      "pressure": 0.4,
      "speed": 0.6,
      "pause_ms": 20
    }
  ]
}
```
