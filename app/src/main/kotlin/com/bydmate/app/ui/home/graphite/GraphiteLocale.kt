package com.bydmate.app.ui.home.graphite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration

/*
 * The Graphite home in the app language. The cards' phrases are resources (strings_graphite.xml);
 * the short words, units and number / date rules the pure functions need are the tables below,
 * so the functions stay testable without Android. Russian is the default, as in the app.
 */

/** The app languages the Graphite home speaks. */
enum class GrLang { RU, EN, ZH, PT, PL, BE }

/** The language of a locale tag ("pt-BR", "zh_CN", "en"); anything else, or none, reads Russian. */
fun grLang(tag: String?): GrLang = when (tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')) {
    "en" -> GrLang.EN
    "zh" -> GrLang.ZH
    "pt" -> GrLang.PT
    "pl" -> GrLang.PL
    "be" -> GrLang.BE
    else -> GrLang.RU
}

/** The Graphite home's language; GraphiteHome provides it from the app configuration. */
val LocalGrLang = staticCompositionLocalOf { GrLang.RU }

/** The app language as MainActivity sets it (LocalConfiguration follows a switch in place). */
@Composable
fun rememberGrLang(): GrLang {
    val language = LocalConfiguration.current.locales[0].language
    return remember(language) { grLang(language) }
}

/** One language's words for the pure functions. Lists run Monday..Sunday and January..December. */
internal data class GrWords(
    val decimal: Char,
    val group: Char,
    /** Day labels of the week chart. */
    val weekdays: List<String>,
    /** Days and months as the top-bar date writes them. */
    val dateWeekdays: List<String>,
    val months: List<String>,
    val date: (weekday: String, day: Int, month: String, year: Int) -> String,
    val hour: String,
    val minute: String,
    /** Between a number and its unit in a duration ("1 ч", "1小时"). */
    val unitGap: String,
    val km: String,
    val kwh: String,
    val litre: String,
    val kmh: String,
    val rpm: String,
    val kohm: String,
    val mohm: String,
    val powerOn: String,
    val powerOff: String,
    val engineRunning: String,
    val acAuto: String,
    val climate: String,
    val ice: String,
    /** PM2.5 grades: excellent, good, fair, poor. */
    val pm25: List<String>,
    val cabin: String,
    val outside: String,
    val onTheRoad: String,
    val electricDrive: String,
    val avgSpeed: String,
    val menuCar: String,
    val menuTrips: String,
    val menuCharges: String,
    val menuAutomation: String,
    val menuSettings: String,
    /** In GrMaterial order. */
    val materials: List<String>,
    /** In GrRelief order. */
    val reliefs: List<String>,
)

private const val NNBSP = ' '
private const val NBSP = ' '

private val WORDS_RU = GrWords(
    decimal = ',', group = NNBSP,
    weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
    dateWeekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"),
    months = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    ),
    date = { w, d, m, y -> "$w, $d $m $y" },
    hour = "ч", minute = "мин", unitGap = " ",
    km = "км", kwh = "кВт·ч", litre = "л", kmh = "км/ч", rpm = "об/мин", kohm = "кОм", mohm = "МОм",
    powerOn = "Включен", powerOff = "Выключен", engineRunning = "Двигатель заведён",
    acAuto = "Авто", climate = "Климат", ice = "Гололёд",
    pm25 = listOf("отлично", "хорошо", "средне", "плохо"),
    cabin = "Салон", outside = "Снаружи",
    onTheRoad = "в пути", electricDrive = "электротяга", avgSpeed = "средняя скорость",
    menuCar = "Машина", menuTrips = "Поездки", menuCharges = "Зарядки", menuAutomation = "Автоматизации",
    menuSettings = "Настройки",
    materials = listOf("Без материала", "Стекло", "Металл", "Песок мелкий", "Бетон"),
    reliefs = listOf("Плоский", "Выпуклый", "Вогнутый"),
)

private val WORDS_EN = GrWords(
    decimal = '.', group = ',',
    weekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    dateWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ),
    date = { w, d, m, y -> "$w, $d $m $y" },
    hour = "h", minute = "min", unitGap = " ",
    km = "km", kwh = "kWh", litre = "l", kmh = "km/h", rpm = "rpm", kohm = "kΩ", mohm = "MΩ",
    powerOn = "On", powerOff = "Off", engineRunning = "Engine running",
    acAuto = "Auto", climate = "Climate", ice = "Ice risk",
    pm25 = listOf("excellent", "good", "moderate", "poor"),
    cabin = "Cabin", outside = "Outside",
    onTheRoad = "on the road", electricDrive = "electric", avgSpeed = "avg speed",
    menuCar = "Car", menuTrips = "Trips", menuCharges = "Charging", menuAutomation = "Automations",
    menuSettings = "Settings",
    materials = listOf("None", "Glass", "Metal", "Fine sand", "Concrete"),
    reliefs = listOf("Flat", "Convex", "Concave"),
)

private val WORDS_ZH = GrWords(
    decimal = '.', group = ',',
    weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
    dateWeekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日"),
    months = List(12) { "${it + 1}月" },
    date = { w, d, m, y -> "${y}年$m${d}日 $w" },
    hour = "小时", minute = "分钟", unitGap = "",
    km = "公里", kwh = "千瓦时", litre = "升", kmh = "公里/小时", rpm = "转/分", kohm = "kΩ", mohm = "MΩ",
    powerOn = "已上电", powerOff = "已下电", engineRunning = "发动机运行中",
    acAuto = "自动", climate = "空调", ice = "路面结冰",
    pm25 = listOf("优", "良", "中", "差"),
    cabin = "车内", outside = "车外",
    onTheRoad = "行驶时长", electricDrive = "纯电占比", avgSpeed = "平均速度",
    menuCar = "车辆", menuTrips = "行程", menuCharges = "充电", menuAutomation = "自动化",
    menuSettings = "设置",
    materials = listOf("无材质", "玻璃", "金属", "细砂", "混凝土"),
    reliefs = listOf("平面", "凸面", "凹面"),
)

private val WORDS_PT = GrWords(
    decimal = ',', group = NBSP,
    weekdays = listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"),
    dateWeekdays = listOf("seg.", "ter.", "qua.", "qui.", "sex.", "sáb.", "dom."),
    months = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    ),
    date = { w, d, m, y -> "$w, $d de $m de $y" },
    hour = "h", minute = "min", unitGap = " ",
    km = "km", kwh = "kWh", litre = "l", kmh = "km/h", rpm = "rpm", kohm = "kΩ", mohm = "MΩ",
    powerOn = "Ligado", powerOff = "Desligado", engineRunning = "Motor ligado",
    acAuto = "Auto", climate = "Clima", ice = "Risco de gelo",
    pm25 = listOf("ótimo", "bom", "médio", "ruim"),
    cabin = "Interior", outside = "Exterior",
    onTheRoad = "em viagem", electricDrive = "elétrico", avgSpeed = "velocidade média",
    menuCar = "Carro", menuTrips = "Viagens", menuCharges = "Carregamentos", menuAutomation = "Automações",
    menuSettings = "Configurações",
    materials = listOf("Sem material", "Vidro", "Metal", "Areia fina", "Concreto"),
    reliefs = listOf("Plano", "Convexo", "Côncavo"),
)

private val WORDS_PL = GrWords(
    decimal = ',', group = NBSP,
    weekdays = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd"),
    dateWeekdays = listOf("pon.", "wt.", "śr.", "czw.", "pt.", "sob.", "niedz."),
    months = listOf(
        "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
        "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
    ),
    date = { w, d, m, y -> "$w, $d $m $y" },
    hour = "godz.", minute = "min", unitGap = " ",
    km = "km", kwh = "kWh", litre = "l", kmh = "km/h", rpm = "obr./min", kohm = "kΩ", mohm = "MΩ",
    powerOn = "Włączony", powerOff = "Wyłączony", engineRunning = "Silnik pracuje",
    acAuto = "Auto", climate = "Klima", ice = "Gołoledź",
    pm25 = listOf("świetnie", "dobrze", "średnio", "źle"),
    cabin = "Wnętrze", outside = "Na zewnątrz",
    onTheRoad = "w drodze", electricDrive = "na prądzie", avgSpeed = "średnia prędkość",
    menuCar = "Samochód", menuTrips = "Przejazdy", menuCharges = "Ładowania", menuAutomation = "Automatyzacje",
    menuSettings = "Ustawienia",
    materials = listOf("Bez materiału", "Szkło", "Metal", "Drobny piasek", "Beton"),
    reliefs = listOf("Płaski", "Wypukły", "Wklęsły"),
)

private val WORDS_BE = GrWords(
    decimal = ',', group = NNBSP,
    weekdays = listOf("Пн", "Аў", "Ср", "Чц", "Пт", "Сб", "Нд"),
    dateWeekdays = listOf("Пн", "Аў", "Ср", "Чц", "Пт", "Сб", "Нд"),
    months = listOf(
        "студзеня", "лютага", "сакавіка", "красавіка", "мая", "чэрвеня",
        "ліпеня", "жніўня", "верасня", "кастрычніка", "лістапада", "снежня",
    ),
    date = { w, d, m, y -> "$w, $d $m $y" },
    hour = "гадз", minute = "хв", unitGap = " ",
    km = "км", kwh = "кВт·г", litre = "л", kmh = "км/г", rpm = "аб/хв", kohm = "кОм", mohm = "МОм",
    powerOn = "Уключаны", powerOff = "Выключаны", engineRunning = "Рухавік заведзены",
    acAuto = "Аўта", climate = "Клімат", ice = "Галалёд",
    pm25 = listOf("выдатна", "добра", "сярэдне", "дрэнна"),
    cabin = "Салон", outside = "Звонку",
    onTheRoad = "у дарозе", electricDrive = "электрацяга", avgSpeed = "сярэдняя хуткасць",
    menuCar = "Машына", menuTrips = "Паездкі", menuCharges = "Зарадкі", menuAutomation = "Аўтаматызацыі",
    menuSettings = "Налады",
    materials = listOf("Без матэрыялу", "Шкло", "Метал", "Дробны пясок", "Бетон"),
    reliefs = listOf("Плоскі", "Выпуклы", "Увагнуты"),
)

/** This language's words. */
internal val GrLang.words: GrWords
    get() = when (this) {
        GrLang.RU -> WORDS_RU
        GrLang.EN -> WORDS_EN
        GrLang.ZH -> WORDS_ZH
        GrLang.PT -> WORDS_PT
        GrLang.PL -> WORDS_PL
        GrLang.BE -> WORDS_BE
    }
