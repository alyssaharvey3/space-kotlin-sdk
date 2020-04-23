package space.jetbrains.api.runtime

import space.jetbrains.api.runtime.Weekday.Companion
import space.jetbrains.api.runtime.moment.Moment
import space.jetbrains.api.runtime.moment.moment
import kotlin.js.Date

// TODO
//actual class SDate actual constructor(actual val iso: String) {
//    actual override fun toString(): String = iso
//}

actual class SDate(val moment: Moment) : Comparable<SDate> {
    actual constructor(iso: String) : this(moment(iso, "YYYY-MM-DD").local())

    actual val year: Int get() = moment.year().toInt()
    actual val month: Int get() = moment.month().toInt()
    actual val dayOfMonth: Int get() = moment.date().toInt()

    actual val iso: String get() = toString()

    actual override fun equals(other: Any?): Boolean {
        if (this === other) return true

        if (other == null || this::class.js != other::class.js) return false

        other as SDate

        if (year != other.year) return false
        if (month != other.month) return false
        if (dayOfMonth != other.dayOfMonth) return false

        return true
    }

    actual override fun hashCode(): Int = year * 1000 + month * 100 + dayOfMonth

    override operator fun compareTo(other: SDate): Int = when {
        moment.isSame(other.moment, "day") -> 0
        moment.isAfter(other.moment) -> 1
        else -> -1
    }

    actual override fun toString(): String = moment.format("YYYY-MM-DD")
}

fun Moment.sDate(): SDate = SDate(this)

actual fun SDate.withDay(day: Int): SDate = moment.clone().day(day).sDate()

actual fun SDate.plusDays(days: Long): SDate = moment.clone().add(days.toDouble(), "days").sDate()
actual fun SDate.plusMonths(months: Long) = moment.clone().add(months.toDouble(), "months").sDate()
actual fun SDate.plusYears(years: Long) = moment.clone().add(years.toDouble(), "years").sDate()

actual fun daysBetween(a: SDate, b: SDate): Long = b.moment.diff(a.moment, "days").toLong()
actual fun monthsBetween(a: SDate, b: SDate): Long = b.moment.diff(a.moment, "months").toLong()

actual val SDate.weekday: Weekday get() = Weekday.byIsoNumber(moment.weekday().toInt())

actual fun sDate(year: Int, month: Int, day: Int): SDate = moment(Date(year, month, day)).local().sDate()

actual val today: SDate get() = SDate(moment().local().startOf("day"))
