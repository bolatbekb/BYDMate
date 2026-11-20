# Скины BYDMate: как устроено и как добавить новый

Документ для разработчика. Описывает механизм, как он есть в `main` с релиза 4.4.1.20. Что и как выглядит у каждого скина и что проверено в машине — в `FORK.md`, блок «Скины». Здесь — устройство и пошаговая инструкция.

Пути ниже — от `app/src/main/kotlin/com/bydmate/app/`, если не сказано иное. Всё, что относится к скинам, — код форка. В файлах автора остались только однострочные хуки с меткой `// fork:`.

## 1. Идея

**Скин = палитра + набор.** Палитра (`SkinPalette`, `ui/theme/Skins.kt`) — все цвета, которые читает приложение: фон, карточки, четыре акцента, текст, сетка графиков, нижнее меню, цвета светофора SOC. Набор (`SkinKit`, `ui/skin/SkinKit.kt`) — всё остальное: шрифт текста и цифр, размер шрифта, углы, вид карточки, переключателя, ползунка, флажка, нижнего меню и фон экрана. Скин (`Skin`) — стабильный `id` для хранения, название, палитра и набор. Все скины перечислены в `Skins.ALL`.

**Classic — вид автора.** Набор по умолчанию (`SkinKit()`) ничего не меняет: системный шрифт, скруглённые углы без масштаба, `MATERIAL` везде, фон автора. Поэтому Classic совпадает с `main` попиксельно; это закреплено тестом `SkinThemeTest.classicKitIsTheAuthorLook`.

**Как скин доходит до экранов автора без правок их кода.** Два приёма:
- цвета. В `ui/theme/Color.kt` имена автора (`AccentGreen`, `CardSurface`, …) стали геттерами, которые читают `SkinState.palette`. Экраны компилируются как были;
- элементы. В `ui/skin/` лежат функции с теми же именами и параметрами, что у Material3 (`Card`, `Button`, `Switch`, `Text`, `RoundedCornerShape`, …). В файлах автора скрипт `tools/skin-imports.sh` заменяет импорт `androidx.compose.material3.Card` на `com.bydmate.app.ui.skin.Card // fork: skin`. Вызовы остаются авторскими.

**Что не скинируется и почему:**
- окна поверх других приложений и плавающий виджет (8 файлов в `[skip]` списка `tools/skin-imports.list`): у них свой `ComposeView` без темы приложения, скин лёг бы наполовину. Цвет шкалы SOC в виджете всё же следует скину: он берётся из общих цветов;
- ретро-стойки (KX, Integra, C-Drive) и главная Graphite хранят свой вид и рисуются в `SkinNeutralTheme` (`ui/skin/SkinTheme.kt`): шрифт, углы и размер шрифта автора, только масштаб текста пользователя;
- главная Graphite живёт в своей схеме (`GraphiteLookState`), а скин «Graphite» наоборот берёт её схему, см. раздел 4.

## 2. Схема слоёв

```
SharedPreferences "bydmate_skin", ключ skin_id
        |  SkinState.init(context) в BYDMateApp.onCreate  (SkinState: ui/theme/Skins.kt)
        v
SkinState.selected (snapshot state) --> current = selected.live(), palette
        |
        +--> ui/theme/Color.kt: геттеры AccentGreen, CardSurface, ... (читают SkinState.palette)
        |
        +--> ui/theme/Theme.kt BYDMateTheme: размер шрифта = пользовательский x sizeFactor;
        |    colorScheme из геттеров; Typography (skinTypography) и Shapes (skinShapes)
        |    строятся один раз на набор: remember(kit)
        v
Обёртки ui/skin/: Card, Button, Switch, Text, ...  (те же имена, что у M3)
        |  берут kit.controls / kit.corners / kit.fonts
        v
Look-функции (чистые, без Compose): cardLook, switchLook, buttonLook, chipLook,
fieldLook, checkLook, navLook, dividerLook ...  -->  data-классы *Look
        v
Отрисовка: Modifier.drawWithCache (SkinCardMarks, SkinGlow, SkinNavMarks,
SkinBackground, SkinSwitch, SkinSlider ...)
```

**Хранение.** `SkinState` (`ui/theme/Skins.kt`): `selected` — snapshot-состояние, `current` — скин «как он виден сейчас» (`Skin.live()`), `palette` — палитра. `init(context)` вызывается один раз в `BYDMateApp.onCreate` (`BYDMateApp.kt`, строка `SkinState.init(this)`), `select(context, skin)` пишет id и меняет `selected`. Неизвестный или пустой id даёт Classic (`Skins.byId`).

**Живое переключение.** Любой composable, прочитавший цветовой токен или `SkinState.current`, перекомпонуется при смене скина. Перезапуск не нужен.

**Look-функции.** Решение «как выглядит элемент в этом стиле» принимают чистые функции, которые возвращают маленький data-класс (`CardLook`, `SwitchLook`, `ButtonLook`, `ChipLook`, `FieldLook`, `CheckLook`, `NavLook`). Их тестируют без Compose. Обёртка только применяет результат. Лежат они в `ui/skin/SkinControlStyle.kt`, `SkinInputStyle.kt`, `SkinSwitchLooks.kt`, `SkinInputLooks.kt`.

**Отрисовка.** Кисти и размеры строятся внутри `drawWithCache`, на кадр ничего не выделяется. Паттерны фона запоминаются через `remember(цвет)`.

## 3. Каталог: что меняет каждое поле SkinKit

`SkinKit(fonts, corners, controls, background)`; `controls` — это `SkinControls(switch, card, slider, nav, check)`.

| Поле | Варианты | Где рисуется | Кто использует |
|---|---|---|---|
| `fonts.text` (`SkinFont`) | SYSTEM, INTER, EXO2, MANROPE, ROBOTO_CONDENSED, RAJDHANI | `ui/skin/SkinFonts.kt` (`family()`, `skinTypography`), применяется в `Theme.kt`; файлы шрифтов `app/src/main/res/font/`, лицензии `app/src/main/assets/licenses/` | Classic — SYSTEM; Cockpit — EXO2; Graphite Pro, OLED — INTER; Volt — MANROPE; Telemetry — ROBOTO_CONDENSED; Graphite — INTER (SYSTEM при шрифте DiLink) |
| `fonts.sizeFactor` | Float | `Theme.kt`: множится на пользовательский размер; `SkinNeutralTheme` его снимает | Cockpit 0,95; Graphite Pro, Volt, OLED 0,97; Graphite с шрифтом Inter 0,97 (`INTER_SIZE_FACTOR`), с DiLink 1; остальные 1 |
| `fonts.numeric` | null или `SkinFont` | `SkinNumbers.kt` (обёртка `Text`, `numberFont`, `forNumber`) | Cockpit — EXO2; Telemetry — RAJDHANI; Graphite — по шрифту главной |
| `fonts.glow` | Boolean | `SkinNumberGlow.kt` | Cockpit, Volt |
| `corners.style` | ROUNDED, CUT | `SkinShapes.kt` (обёртки `RoundedCornerShape`), `SkinTheme.kt` (`skinShapes`) | все ROUNDED; CUT пока никто |
| `corners.scale` | Float | то же | Cockpit 0 (прямые); Telemetry 0,5; Graphite Pro, OLED 1; Volt 1,5; Graphite ≈1,42 |
| `controls.card` (`CardStyle`) | MATERIAL, OUTLINED, FLAT, GLASS, STRIPE, BORDER_ONLY, GRAPHITE, BRACKETS | look: `cardLook`, `dividerLook`, `dialogHasBorder` (`SkinControlStyle.kt`); обёртка `SkinCard.kt`; метки `SkinCardMarks.kt` (полоса, скобки), `SkinGlow.kt`. Тот же стиль задаёт вид кнопок, фильтров и полей: `buttonLook`, `chipLook`, `fieldLook` в `SkinInputStyle.kt`; `SkinButtons.kt`, `SkinChip.kt`, `SkinTextField.kt`, `SkinDivider.kt`, `SkinAlertDialog.kt` | Classic — MATERIAL; Cockpit — BRACKETS; Graphite Pro — OUTLINED; Volt — GLASS; Telemetry — STRIPE; OLED — BORDER_ONLY; Graphite — GRAPHITE. FLAT пока никто |
| `controls.switch` (`SwitchStyle`) | MATERIAL, SQUARE, PILL, OUTLINE, GRAPHITE, HUD, IOS, GLOW | look: `switchLook` (`SkinControlStyle.kt`) и `SkinSwitchLooks.kt`; обёртка `SkinSwitch.kt` | Cockpit — HUD; Graphite Pro — IOS; Volt — GLOW; Telemetry — SQUARE; OLED — OUTLINE; Graphite — GRAPHITE. PILL пока никто |
| `controls.slider` (`SliderStyle`) | MATERIAL, THIN, SEGMENTED, GRADIENT, GLOW | `SkinSlider.kt`; расчёты `sliderFraction`, `litSegments` в `SkinControlStyle.kt` | Cockpit, Graphite — THIN; Telemetry — GRADIENT; Volt — GLOW. SEGMENTED пока никто |
| `controls.nav` (`NavStyle`) | MATERIAL, GRAPHITE, CUT, UNDERLINE, OUTLINE, TOP, DOT, DOCK | look: `navLook`; обёртка `SkinNavigationBar.kt`; метки `SkinNavMarks.kt` (линия сверху, точка, плавающий док) | Cockpit — TOP; Graphite Pro — DOT; Volt — DOCK; Telemetry — UNDERLINE; OLED — OUTLINE; Graphite — GRAPHITE. CUT пока никто |
| `controls.check` (`CheckStyle`) | MATERIAL, SQUARE | `checkLook` (`SkinInputStyle.kt`), `SkinChecks.kt` (флажок и радиокнопка; радиокнопка остаётся круглой) | Cockpit, Telemetry — SQUARE |
| `background` (`BackgroundStyle`) | AUTHOR, GRID, GLOW, SCANLINES, NEON, CARBON, GRAPHITE | `Modifier.skinBackground()` в `SkinBackground.kt` | Cockpit — GRID; Volt — NEON; Telemetry — CARBON; Graphite — GRAPHITE. GLOW и SCANLINES пока никто |

Значения «пока никто» — готовые заготовки: собрать новый скин из них дешевле всего.

## 4. Особые случаи

**Скин «Graphite».** `id = "graphite_home"` (`GRAPHITE_SKIN_ID` в `ui/skin/GraphiteSkin.kt`), в `Skins.ALL` стоит последним (`Skins.GRAPHITE_HOME`). Своего вида у него нет: палитру и набор строят `graphitePalette(scheme)` и `graphiteSkin(scheme, font)` из схемы главной Graphite (`GraphiteLookState`, `ui/home/graphite/`). `Skin.isGraphite` проставляется один раз, поэтому проверка в `SkinState.palette` — чтение поля. `Skin.live()` для Graphite возвращает скин под текущие схему и шрифт; все варианты строятся один раз (тест `GraphiteSkinTest.livePalettesAndKitsAreBuiltOnce`). Карточку, фон и переключатель через код главной рисует `ui/skin/GraphiteSkinParts.kt`. Стили `GRAPHITE` в enum'ах — это его вид. Схему меняют в отдельном блоке настроек «Graphite», а не в плитках скинов. Id `"graphite"` принадлежит скину Graphite Pro.

**Цвета-геттеры (`ui/theme/Color.kt`).** `val AccentGreen: Color get() = SkinState.palette.accentGreen // fork: skin`, а не константа. Константа вычисляется один раз и не следила бы за переключением. Следствия: цвет нельзя сохранять в top-level `val` и в `remember` без ключа; `DarkColorScheme` в `Theme.kt` тоже геттер.

**Шкала SOC.** `SocGreen` берёт `palette.accentGreen`, шкала следует акценту скина; `SocYellow` и `SocRed` остаются предупреждениями. Цвета оценки расхода (`ConsumptionGood/Mid/Bad`) читают `socGreen/Yellow/Red` палитры, одинаковые по смыслу в каждом скине.

**Шрифт цифр.** Автор пишет числа моноширинным шрифтом. Обёртка `Text` (`SkinNumbers.kt`) заменяет `FontFamily.Monospace` на `fonts.numeric` скина, если текст состоит из цифр и знаков (`isNumericOnly`). Текст с буквами (адреса, коды) остаётся моноширинным. Правило Rajdhani: только крупные числа, от 23 sp (`RAJDHANI_MIN_SIZE`), не жирнее Medium (`RAJDHANI_MAX_WEIGHT`); мелкие цифры идут запасным шрифтом (`numberFallback`). Шрифтам без одинаковой ширины цифр (INTER, EXO2, SYSTEM) добавляется `tnum` (`SkinTnum.kt`, в настройках шрифта ровно один раз).

**Свечение цифр.** `SkinNumberGlow.kt`: тень цвета акцента (альфа 0,40, размытие 22 dp) у числа без букв, моноширинного, от 23 sp, при `fonts.glow`. Это `Shadow` текста, а не `BlurMaskFilter`.

**Вложенные карточки.** `LocalSkinCardDepth` (`SkinCard.kt`): карточка отдаёт содержимому глубину +1, и `cardLook(style, palette, nested = true)` рисует внутреннюю карточку проще (у Telemetry полоса только у внешней, у Volt без второго слоя стекла, у Graphite свой фон и контур без второй тени). Глубина считается в пределах окна (`LocalSkinCardWindow`): `Dialog` наследует локали родителя, и без этого карточка диалога считалась бы вложенной.

**Диалоги.** Карточка на корне окна диалога получает `CardLook.inDialog()`: фон непрозрачный (кроме Graphite). `dialogHasBorder(style)` решает, рисовать ли обводку; его использует `AlertDialog` (`SkinAlertDialog.kt`). Диалог на `Dialog` с `Card` работает через ту же `Card`.

**Размер текста пользователя.** `BYDMateTheme` множит пользовательский масштаб на `sizeFactor` скина (шрифты шире Roboto уменьшаются) и отдаёт итог через `LocalAppFontScale`, чтобы его повторно применяли диалоги. Чистый пользовательский масштаб лежит в `LocalUserFontScale`; `SkinNeutralTheme` использует его и снимает `sizeFactor`.

## 5. Как добавить новый скин: пошагово

Пример: скин «Aurora».

1. **Палитра и набор** в `ui/theme/Skins.kt`: рядом с остальными добавить `val AURORA = Skin("aurora", "Aurora", SkinPalette(...), SkinKit(...))`. Обязательные токены палитры заполнить все; `navIndicator` и цвета светофора можно не задавать (есть значения по умолчанию). Название — собственное имя, не переводится. Не печатать в скине тип гибрида (DM-i, DM-O), текст логотипа — BYDMATE.
2. **id.** Уникальная строка в нижнем регистре, первый параметр `Skin(...)`. Это значение в `SharedPreferences`; после выпуска его не меняют, иначе сохранённый выбор станет Classic.
3. **Список.** Добавить скин в `Skins.ALL`, перед `GRAPHITE_HOME` (Graphite стоит последним по решению владельца). Больше нигде скины не перечислены: пикер `ui/settings/SkinPickerBlock.kt` строит плитки из `Skins.ALL`, а превью плитки рисует по самому скину (шрифт, углы, переключатель). Править пикер нужно, только если новый стиль элемента плитка должна показать иначе.
4. **Собрать набор из готовых вариантов** (раздел 3). Если хватает существующих — шаги 5–6 пропускают.
5. **Нужен новый вариант элемента?** Например, новый `SwitchStyle`:
   - добавить значение в enum в `ui/skin/SkinKit.kt`;
   - добавить look: для карточки, кнопок, фильтров и полей — ветку в `cardLook`, `buttonLook`, `chipLook`, `fieldLook`, `dividerLook`, `dialogHasBorder`; для переключателя — ветку в `switchLook` и функцию в `SkinSwitchLooks.kt`; для меню — `navLook`; для флажка — `checkLook`. Функция чистая: на входе стиль и палитра, на выходе data-класс;
   - обработать значение во всех `when` по этому enum. Компилятор укажет их, пока нет `else`; не заводить `else`, молча дающий вид автора. Для `NavStyle` это ещё `barModifier` и `keyMark` в `SkinNavMarks.kt`; для `BackgroundStyle` — `skinBackground()` в `SkinBackground.kt`;
   - отрисовку (метка, свечение, фон) делать через `drawWithCache` в отдельном файле `ui/skin/`. Detekt ругается на `TooManyFunctions` (`config/detekt/detekt.yml`, `@Composable` не считается): группу новых функций выносят в новый файл, а не в `SkinControlStyle.kt`;
   - новый шрифт: значение в `SkinFont`, файл `skin_*.ttf` в `app/src/main/res/font/`, `lazy`-семейство и ветка в `SkinFont.family()` (`SkinFonts.kt`), лицензия OFL в `app/src/main/assets/licenses/` (наличие файлов проверяет `SkinThemeTest.fontsAndLicencesAreShipped`).
6. **Тесты** в `app/src/test/kotlin/com/bydmate/app/ui/skin/`:
   - `SkinThemeTest`: `everyOtherSkinHasTheSpecKit` и `finalPalettes` закрепляют набор и палитру каждого скина; добавить новый;
   - `SkinControlStyleTest` (карточка, переключатель, ползунок; `everySwitchAndSliderStyleIsHandled` падает, если стиль не обработан), `SkinInputLookTest` (кнопки, фильтры, поля, флажки), `SkinNavLookTest` (меню; `skinsUseTheirNavStyles`), `SkinNumbersTest` (шрифт и свечение цифр), `SkinShapesTest` (углы) — добавить случаи под новый стиль;
   - тесты Classic, Graphite и OLED остаются зелёными без правок.
7. **Проверки.** Перед Gradle — `docs/superpowers/emu-tools/preflight.sh gradle` (WAIT значит ждать), Gradle не запускать рядом с эмулятором:
   - `./gradlew :app:testDebugUnitTest --tests '*skin*'`;
   - detekt (`./gradlew :app:detekt`) и lint (`./gradlew :app:lintDebug`);
   - `tools/skin-imports.sh --check` (должно сказать OK);
   - `tools/fork-marks.sh`.
8. **Эмулятор.** `docs/superpowers/emu-tools/preflight.sh emulator`, потом `APK=… SKINS="classic aurora" LANG_CODE=ru docs/superpowers/emu-tools/skins3.sh out-dir`: скрипт пишет `skin_id` в `shared_prefs/bydmate_skin.xml` и снимает главные экраны каждого скина (`skins.sh` и `look.sh` — старые и частные: первый для этапов 1–2, второй для главной Graphite). Смотреть: главную, «Машину», «Поездки», «Зарядки», «Автоматизацию», «Настройки», диалог, нижнее меню, на русском и португальском. Classic сравнивать с `main` попиксельно.
9. **Документы.** `FORK.md`, блок «Скины» (скин и его окончательный вид), и `release-notes/vX.md` перед тегом.

## 6. Как скин доходит до нового экрана автора

После слияния с автором новый или изменённый экран может снова импортировать `androidx.compose.material3.Card` и остаться без скина. Порядок:

1. `tools/skin-imports.sh --apply`. Он переписывает импорты файлов из `tools/skin-imports.list` (раздел `[imports]`; имена — строки `SKIN_M3_NAMES` и `SKIN_SHAPE_NAMES` в скрипте), заменяет градиент фона на `.skinBackground()` (раздел `[background]`) и обновляет `tools/skin-author-files.txt`. Повторный запуск ничего не меняет. Файл с `import …material3.*` получает явный импорт из `ui.skin` для имён, которые он реально вызывает.
2. `tools/skin-imports.sh --check`. Ненулевой выход и список проблем, если остался сырой импорт, вызов с полным именем (`androidx.compose.material3.FilterChip(`), импорт с псевдонимом (`as M3Card`), форма в top-level `val` (она не менялась бы на лету) или устарел список авторских файлов. Полное имя и псевдоним правят вручную, одной строкой с меткой `// fork: skin`; форму превращают в геттер: `val X get() = RoundedCornerShape(…) // fork: skin, live`.
3. `tools/fork-marks.sh` и сборка.

**Тест-охранник.** `SkinImportsTest` читает тот же список и `tools/skin-author-files.txt` (в CI нет remote автора, поэтому файл закоммичен). Он падает, если файл автора использует заменяемое имя и не стоит ни в `[imports]`, ни в `[skip]`. Тогда:
- экран под темой приложения — добавить путь в `[imports]` (и в `[background]`, если у него градиент `NavyDark`/`NavyDeep`) и выполнить `--apply`;
- окно вне темы приложения — добавить в `[skip]`. `--check` скажет, если файл из `[skip]` не существует или одновременно стоит в `[imports]`/`[background]`.

**`[skip]` сейчас:** `data/automation/ConfirmOverlayManager.kt`, `split/SplitPillView.kt`, `split/SplitStageActivity.kt`, `ui/overlay/ListeningOverlay.kt`, `ui/overlay/OverlayNotificationManager.kt`, `ui/widget/FloatingWidgetView.kt`, `ui/widget/TrashZoneView.kt`, `ui/widget/WidgetButtonPanel.kt`. Это Compose-окна вне `BYDMateTheme`. Если в `[skip]` оказался файл, который рисуется под темой приложения, его переносят в `[imports]`.

Чтобы подменять ещё одно имя M3: обёртка с тем же именем и параметрами в `ui/skin/`, имя в строку `SKIN_M3_NAMES` скрипта (`SkinImportsTest` читает эту же строку), затем `--apply` и `--check`.

## 7. Правила и подводные камни

- **Classic, Graphite и OLED не менять.** Правка общего кода (look-функции, обёртки) сохраняет их вид; его закрепляют тесты. Вид автора — это пропуск без изменений.
- **Цвета не кешировать.** Токены — геттеры. Нельзя класть цвет в top-level `val`, в `object`-константу или в `remember` без ключа-цвета: он не поменяется при переключении. Паттерны фона запоминаются с цветом в ключе (`rememberPattern`).
- **Никаких `BlurMaskFilter`.** Свечение рисуют слоями полупрозрачных прямоугольников (`drawRectGlow` в `SkinGlow.kt`) или `Shadow` текста. Целевое устройство — головное устройство машины (Snapdragon 778G, Android 12).
- **Без аллокаций на кадр.** Кисти, пути и размеры считаются в `drawWithCache`. `Typography` и `Shapes` строятся по `remember(kit)`.
- **Метки `// fork:` — только в файлах автора** (`Color.kt`, `Theme.kt`, импорты в экранах, `BYDMateApp.kt`, `AppNavigation.kt`, `SettingsScreen.kt` и др.). В `ui/skin/` и `ui/settings/SkinPickerBlock.kt` — файлы форка, метки там не нужны. Проверяет `tools/fork-marks.sh`; запускать перед релизом и после слияния с автором.
- **detekt `TooManyFunctions`.** Новые функции одной группы (look'и переключателей, метки, фон) выносят в отдельный файл, как `SkinSwitchLooks.kt`, `SkinInputLooks.kt`, `SkinCardMarks.kt`.
- **Скин не меняет поведение экрана.** Обёртка принимает те же параметры и сохраняет нажатия, доступность и размеры касаний; меняется только вид.
- **Не писать названия навигатора.** Ни в коде, ни в комментариях, коммитах, именах папок, ни в документации; образцы иконок остаются приватными.
- **Id скина не менять после выпуска:** он лежит у пользователей в `SharedPreferences`.
- **Эмулятор — только когда он свободен.** Проверить `adb devices` и `pgrep`, чужой запущенный эмулятор не останавливать и не переустанавливать, Gradle рядом с ним не запускать.
