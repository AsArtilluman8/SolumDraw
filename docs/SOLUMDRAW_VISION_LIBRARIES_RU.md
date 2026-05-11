# SolumDraw — библиотеки и методы анализа

Цель: не писать всё вручную, а использовать готовые CV/ML-блоки там, где это разумно для Android/Termux.

## 1. gallery-dl

Репозиторий: mikf/gallery-dl.

Использование:

```text
- сбор изображений для dataset
- Pinterest/галереи/страницы
- только изображения
- без видео и gif
- лимиты размера
- manifest
```

Запрещённые типы:

```text
mp4, webm, gif, mov, m4v, mkv, avi, zip, rar, 7z
```

Разрешённые типы:

```text
jpg, jpeg, png, webp
```

## 2. OpenCV / opencv-mobile

Нужно для низкоуровневого зрения:

```text
- Canny/Sobel edges
- findContours
- connectedComponents
- morphology close/open
- approxPolyDP
- HoughLines
- threshold/adaptiveThreshold
```

Что даст SolumDraw:

```text
- контуры без квадратов и точек
- острова областей
- силуэты
- прямоугольники UI
- линии архитектуры
- карта областей для маршрута
```

Важно: OpenCV-бинарники подключать отдельным патчем после проверки сборки. Если полный OpenCV тяжёлый, идти через opencv-mobile или fallback CPU.

## 3. OCR

Кандидаты:

```text
- ML Kit Text Recognition
- Tesseract
- свой лёгкий text-like detector как fallback
```

Задача:

```text
- отличать UI/document/logo от травы/камней/шумных линий
- находить настоящий текст
- повышать точность analyzer
```

## 4. Face/person

Кандидаты:

```text
- ML Kit Face Detection
- MediaPipe Face Detection
- TFLite person detector
```

Задача:

```text
- портрет
- лицо
- fullbody
- персонаж
- anime character
```

## 5. ML classifier

Стек:

```text
- Python + PyTorch CPU в proot/Termux
- MobileNetV3 Small или EfficientNet-lite
- вход 224x224
- экспорт ONNX или TFLite
- Android inference
```

Задача:

```text
- жанр
- стиль
- top3
- confidence
```

Не использовать как генератор картинки. ML сначала помогает анализировать, выбирать области, маршрут и параметры кисти.

## 6. Imagination Mode позже

Финальная генерация должна быть stroke-based:

```text
prompt -> scene plan -> layout -> shapes -> strokes -> critic -> repair
```

Не основной путь:

```text
prompt -> generated image -> copy image
```

Такой режим можно оставить как временный вариант, но архитектурно SolumDraw должен генерировать программу рисования.
