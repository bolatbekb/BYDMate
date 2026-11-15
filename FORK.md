# Форк BYDMate

Это форк [AndyShaman/BYDMate](https://github.com/AndyShaman/BYDMate): `bolatbekb/BYDMate`.
Форк добавляет свои функции и регулярно забирает изменения автора. Как пользоваться приложением, описано в [README.md](README.md). Здесь описано только то, чем форк отличается от оригинала и как его поддерживать.

Версия форка: **4.4.1.2** (основа: 4.4.1 автора).

## Что добавлено

| Функция | Где в приложении | Код |
|---|---|---|
| Цветовые скины: Classic, Cockpit, Graphite Pro, Volt, Telemetry, OLED | Настройки → Приложение → «Оформление» | `ui/theme/Skins.kt`, `ui/settings/SkinPickerBlock.kt` |
| Демо-режим: имитация поездки и 90 дней истории в отдельной базе | Настройки → Приложение → «Демо» | `demo/*`, `ui/settings/DemoModeBlock.kt` |
| Ретро-главные «Стойка KX» и «Стойка Integra»: кассетная дека с реалистичной кассетой и управлением музыкой; вместо нижней панели вкладок — клавиши-переключатель входов с гравированными значками. Свёрстаны под 1280×604 dp (экран Leopard 5 без панелей машины) | Настройки → Приложение → «Главный экран» | `ui/home/*` (общая кассета — `RetroParts.kt`), `ui/settings/HomeStylePickerBlock.kt` |
| Скриншот всего экрана вместе с панелями машины, через ADB `screencap`, с задержкой 5 с | Настройки → Приложение | `fork/ScreenshotTaker.kt`, `ui/settings/ScreenshotBlock.kt` |
| Размер окна, панели машины и плотность (dpi) в шапке диагностического лога | Настройки → запись лога | `fork/DisplayInfo.kt` |
| Обновления из релизов форка, а не автора | автопроверка и кнопка в настройках | `fork/ForkConfig.kt` |
| Оптимизации опроса машины (см. ниже) | — | правки в `data/*` |
| Сборка подписанного APK и релиз в GitHub Actions | — | `.github/workflows/fork-release.yml` |

### Оптимизации опроса машины
- **Якорь зарядки.** `AutoserviceChargingDetector` записывает якорь стоянки в базу только при изменении SOC, сдвиге пробега на 0,2 км или раз в минуту, а не каждую секунду.
- **Правила автоматизации.** `AutomationEngine` кеширует разобранные условия правил (LRU, 64 записи) и не разбирает JSON на каждом тике.
- **Папка энергоданных.** `EnergyDataReader.isAvailable()` кеширует проверку папки на 30 секунд.
- **Выключенный экран.** `SharedAdaptiveLoop` опрашивает машину в движении раз в 5 с вместо 1 с.
- **Ретро-главные** берут живые данные не чаще раза в секунду.

## Как устроен форк: устойчивость к изменениям автора

Главное правило: **новый код лежит в новых файлах, а в файлах автора остаются только короткие вызовы.** Каждая такая правка помечена комментарием `// fork:` (или `fork` в KDoc). Найти их все можно так:

```bash
git grep -n "fork" -- app/src/main/kotlin | grep -v "/fork/\|/demo/\|/ui/home/"
```

Другие приёмы:
- **Свои пакеты.** Новый код лежит в `fork/`, `demo/`, `ui/home/` и в отдельных файлах `*Block.kt` настроек. У автора таких файлов нет, поэтому при слиянии они не конфликтуют.
- **Свои файлы строк.** Тексты интерфейса лежат в `strings_skins.xml`, `strings_demo.xml` и `strings_screenshot.xml` во всех шести языках (ru, en, be, pl, pt, zh). Авторский `strings.xml` форк не трогает: его автор меняет чаще всего.
- **Замены в DI, а не в логике.** Демо-режим подменяет источники данных в `di/AppModule.kt` и `di/NativeModule.kt` (`DemoBindings`, `DemoAwareParsReader`). Код чтения машины остаётся авторским.
- **Методы со значением по умолчанию.** Новый метод интерфейса `AdbOnDeviceClient.screencap()` объявлен с реализацией по умолчанию, поэтому авторские тестовые подделки этого интерфейса не нужно менять.
- **Свои workflow.** `fork-release.yml` — отдельный файл, авторский `ci.yml` не меняется.

### Где возможен конфликт: правки в файлах автора

Всего 17 файлов, порядка 150–170 строк. При конфликте ищи в них метку `fork`.

| Файл | Что изменено |
|---|---|
| `app/build.gradle.kts` | `versionCode` и `versionName` форка: **конфликт при каждом релизе автора**, см. «Версии» |
| `BYDMateApp.kt` | `SkinState.init`, `HomeStyleState.init`, `DisplayInfo.init` в `onCreate` |
| `ui/theme/Color.kt` | цвета стали геттерами из `SkinState`, чтобы скин менялся на лету |
| `ui/theme/Theme.kt` | `DarkColorScheme` стал геттером |
| `ui/settings/SettingsScreen.kt` | `PrimaryColor` стал геттером |
| `ui/settings/SettingsAppVoice.kt` | вызовы блоков скина, главной, демо и скриншота в разделе «Приложение» |
| `ui/navigation/AppNavigation.kt` | `DashboardScreen` обёрнут в `ForkHome(onOpenTab) { … }`; нижняя панель скрыта, пока на главной ретро-стойка (`hidesBottomBar`) |
| `di/AppModule.kt` | демо-подмены: база, ADB, autoservice, helper; `screenOn` для цикла опроса |
| `di/NativeModule.kt` | привязка `DemoAwareParsReader` |
| `service/UpdateChecker.kt` | адрес релизов берётся из `ForkConfig.UPDATE_REPO` |
| `ui/settings/DiagnosticDumpWriter.kt` | одна строка: вывод `DisplayInfo.lines()` |
| `data/autoservice/AdbOnDeviceClient.kt` | метод `screencap()` в интерфейсе и реализации |
| `data/charging/AutoserviceChargingDetector.kt` | ограничение записи якоря (`anchorFresh`) |
| `data/automation/AutomationEngine.kt` | кеш разобранных условий |
| `data/local/EnergyDataReader.kt` | кеш `isAvailable()` |
| `data/loop/SharedAdaptiveLoop.kt` | параметр `screenOn` и `nextInterval()` |
| `test/.../SettingsViewModelTest.kt` | эталонная шапка лога: из сравнения убраны строки `display…` форка |

## Как забирать изменения автора

```bash
git checkout main
git fetch upstream
git merge upstream/main
```

1. **Конфликт в `app/build.gradle.kts`** будет при каждом релизе автора. Оставь номер автора и добавь номер сборки форка, см. «Версии».
2. **Другие конфликты** разбирай по таблице выше: правка форка обычно одна строка с меткой `fork`. Её нужно перенести в новую версию кода автора.
3. **Проверка:**
   ```bash
   ./gradlew :app:detekt :app:lintDebug :app:testDebugUnitTest
   ```
4. **Отправка:** `git push origin main`.

Remote `upstream` настроен только на чтение (push URL — `DISABLED`), поэтому случайно отправить что-то в репозиторий автора невозможно.

## Версии

- **`versionName`** — версия автора плюс номер сборки форка: `4.4.1.1`, `4.4.1.2`, …; после релиза автора 4.4.2 — `4.4.2.1`.
- **`versionCode`** — код автора × 10 плюс номер сборки форка: `5221` = 522 × 10 + 1. Так код форка всегда выше кода официальной сборки той же версии, а Android требует, чтобы код новой версии был не меньше установленного.

Приложение сравнивает версии по числам через точку, поэтому `4.4.1.1` для него новее, чем `4.4.1`.

## Релиз

1. Подними `versionName` и `versionCode` в `app/build.gradle.kts`.
2. **Напиши пояснение к релизу** в `release-notes/v<версия>.md`: по-русски, простыми словами для водителя — что изменилось на экране и в поведении, что исправлено, нужно ли что-то сделать после обновления. Не список коммитов. Без этого файла CI откажется выпускать релиз, а сам текст станет описанием релиза на GitHub.
3. Закоммить и отправь в `main`.
4. Поставь тег и отправь его:
   ```bash
   git tag -a v4.4.1.2 -m "BYDMate 4.4.1.2 (fork)"
   git push origin v4.4.1.2
   ```
5. Workflow **Fork release APK** соберёт подписанный APK и создаст релиз на странице [Releases](https://github.com/bolatbekb/BYDMate/releases). Приложение в машине предложит обновиться.

Когда запускается сборка:
- **push в `main`** — APK попадает в Actions → запуск → Artifacts, скачать можно только залогинившись в GitHub;
- **тег `v…`** — дополнительно создаётся релиз;
- **вручную** — кнопка «Run workflow».

Если тег не совпадает с `versionName`, сборка остановится с ошибкой, и релиз с неправильным номером не появится.

Тесты при push в `main` не запускаются: авторский `ci.yml` гоняет только lint и detekt, а тесты — только на pull request. Чтобы CI прогнал тесты, делай работу в ветке и открывай PR в `main` форка.

### Ключ подписи

- **Файлы:** `~/.android-keys/bydmate-fork.jks` (ключ) и `~/.android-keys/bydmate-fork.properties` (пароль и алиас). Для локальной сборки они подключаются через `keystore.properties` в корне проекта; этот файл в `.gitignore`.
- **В CI** ключ лежит в секретах репозитория: `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
- **Терять ключ нельзя.** APK с другим ключом не установится поверх установленного: придётся удалять приложение, а вместе с ним и данные (кроме бэкапа). Храни копию ключа и пароля вне этой машины.

## Переход с официальной версии и обратно

Форк подписан другим ключом, поэтому поставить его поверх официальной версии нельзя: её нужно удалить, а вместе с ней удаляются её данные. Данные переносятся через бэкап:

1. В официальной версии: Настройки → Сервис и данные → **Сохранить конфигурацию**. Отметь все пункты, включая «Ключи и токены»: по умолчанию он выключен. Файл `bydmate_backup_….zip` ляжет в «Загрузки».
2. Удали официальную версию. Файл в «Загрузках» сохранится.
3. Установи APK форка и пройди мастер первого запуска.
4. Настройки → Сервис и данные → **Выбрать файл** → бэкап → «Восстановить». Приложение перезапустится.
5. Подтверди на магнитоле запрос «Разрешить отладку по USB?»: у новой установки другой ключ ADB.

Обратный путь (форк → официальная версия) такой же. Проверено на эмуляторе 15.11.2026 в обе стороны между официальным 4.4.1 и форком 4.4.1.1:
- поездки, зарядки, точки маршрутов, валюта и тарифы переносятся полностью;
- схема базы у форка своя не заведена, она совпадает с авторской (сейчас 25).

## Известные особенности

- **Бэкап в демо-режиме.** Экспорт и восстановление всегда работают с реальной базой (`bydmate.db`), а не с демо-базой (`bydmate_demo.db`). Данные это не портит, но бэкап демо-данных так сделать нельзя.
- **Тесты и часовой пояс.** Три теста `TelegramReportBuilderTest` падают на машине с часовым поясом, отличным от UTC. У автора они падают так же, на GitHub Actions проходят.
- **Эмулятор.** Официальный APK собран только под arm64. На x86_64-эмуляторе с Android 11+ он работает через встроенную трансляцию ARM.
- **Голосовые модели и словарь ударений** по-прежнему скачиваются с релизов автора (`TtsVoiceCatalog`, `TtsModelManager`). Так и задумано: файлы лежат там.

## Сторонние материалы

Фотографии кассет на ретро-экранах (`res/drawable-nodpi/cassette_*`; полный текст: `app/src/main/assets/licenses/cassettes.md`):

- tdk_sa60.png (+ _hubL/_hubR): "Compact Cassette - TDK SA 60.JPG" by Andrey Korzun, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0), source: https://commons.wikimedia.org/wiki/File:Compact_Cassette_-_TDK_SA_60.JPG — cropped, perspective-corrected, handwriting removed by BYDMate fork.
- tdk_arx90.png (+ _hubL/_hubR): "Compact Cassette - TDK AR-X90.JPG" by Andrey Korzun, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0), source: https://commons.wikimedia.org/wiki/File:Compact_Cassette_-_TDK_AR-X90.JPG — cropped, perspective-corrected, handwriting removed by BYDMate fork.
- tdk_d60.png (+ _hubL/_hubR): "TDK D 1985 euro market 01.jpg" by Retired electrician, [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/), source: https://commons.wikimedia.org/wiki/File:TDK_D_1985_euro_market_01.jpg — cropped, perspective-corrected by BYDMate fork (no handwriting on this shell; window-frame ribs retouched out of the hub discs).
- tdk_d46.png (+ _hubL/_hubR): "TDK D46 audio cassette.jpg" by Hannes Grobe/AWI, [CC BY 3.0](https://creativecommons.org/licenses/by/3.0), source: https://commons.wikimedia.org/wiki/File:TDK_D46_audio_cassette.jpg — cropped, perspective-corrected by BYDMate fork (no handwriting on this shell; window-frame ribs retouched out of the hub discs).
- res/font/caveat.ttf: шрифт Caveat © 2014 The Caveat Project Authors (https://github.com/googlefonts/caveat), [SIL Open Font License 1.1](https://openfontlicense.org), источник: https://github.com/google/fonts/tree/main/ofl/caveat; текст лицензии: `app/src/main/assets/licenses/caveat-OFL.txt`.
