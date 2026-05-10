# SolumDraw Analyzer v3 Workflow

SolumDraw — не просто классификатор. Цель: понять картинку, выбрать важные области, построить человеческий порядок рисования и потом рисовать не как принтер.

## Что важно сейчас

1. Сначала точный анализ.
2. Потом маршрут рисования.
3. Потом улучшение stroke/drawer.
4. ML подключать после понятного benchmark и ошибок.

## Одиночный анализ

Одиночный Analyze должен быть визуальным:

- исходник
- overlay анализа
- карта внимания
- контуры
- палитра
- гистограмма
- русский вывод
- план рисования
- риски ошибки

В Android Patch 17 после Analyze preview переключается на overlay, а кнопка View переключает: исходник / анализ / белый холст.

## Batch benchmark

Большой benchmark не должен сохранять сотни картинок. Нужны лёгкие файлы:

- benchmark_results.csv
- mistakes.csv
- predictions.jsonl
- benchmark_summary.json
- BENCHMARK_REPORT.md

## Основной dataset

Приложение должно читать:

```text
/storage/emulated/0/Download/SolumDrawDataset_v1/SolumDrawDataset_v1/
```

Формат:

```text
<class>/image.jpg
<class>/image.json
```

Sidecar JSON нужен только для проверки benchmark. Analyzer не должен читать подсказки из notes/evidence во время анализа.

## Accuracy

```text
1.0 = 100%
0.5 = 50%
0.0 = 0%
```

Текущие v1/v2 слабые, потому что видят пиксельную статистику, но плохо понимают объекты.

## Следующие этапы

- Patch 17: dataset bench + progress + визуальный solo UI.
- Patch 18: evidence-группы анализатора.
- Patch 19: исправление ложного UI/text/logo.
- Patch 20: отчёт ошибок benchmark.
- Потом route planner.
