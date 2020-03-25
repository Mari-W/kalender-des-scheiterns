package de.moeri

import io.ktor.util.KtorExperimentalAPI
import org.sql2o.Sql2o
import java.sql.Date
import java.util.regex.Pattern

@KtorExperimentalAPI
object Database {

    lateinit var db: Sql2o

    fun init() {
        db = Sql2o(Config["db.url"], Config["db.user"], Config["db.pass"])
    }

    private val urlCheck: Pattern =
        Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})")


    fun insert(ip: String, entry: Entry): Boolean {
        if (!check(entry)) {
            throw IllegalArgumentException()
        }
        db.open().use {
            val limit = it.createQuery("SELECT rate_limit(:ip);")
                .addParameter("ip", ip)
                .executeUpdate()
                .result
            if (limit.toBoolean()) {
                it.createQuery("INSERT INTO entries (type, source, date, description, name, email) VALUES (:type, :source, :date, :description, :name, :email)")
                    .bind(entry)
                    .executeUpdate()
                return true
            }
            return false
        }
    }

    fun changeStatus(id: Int, status: String) {
        db.open().use {
            it.createQuery("UPDATE entries SET status=:status WHERE id=:id ")
                .addParameter("id", id)
                .addParameter("status", status)
                .executeUpdate()
        }
    }


    fun dates(): List<DateEvent> {
        return db.open().use {
            val old =
                it.createQuery("SELECT MONTH(e.date) month, DAY(e.date) day, COUNT(*) cnt FROM entries e WHERE e.status='APPROVED' GROUP BY MONTH(e.date), DAY(e.date)")
                    .executeAndFetch(DateAmount::class.java)
            val ret = mutableListOf<DateEvent>()
            var last: DateAmount? = null
            for (dateAmount in old) {
                if (last == null) {
                    if (dateAmount.month != 1 || dateAmount.day != 1) {
                        if (dateAmount.day == 1) {
                            ret.add(
                                DateEvent(
                                    1, 1, dateAmount.month - 1,
                                    monthLen[dateAmount.month - 1] ?: error("month not found lol in if"), "#FF0000"
                                )
                            )
                        } else {
                            ret.add(DateEvent(1, 1, dateAmount.month, dateAmount.day - 1, "#FF0000"))
                        }
                    }
                } else {
                    if (dateAmount.day == 1) {
                        ret.add(
                            DateEvent(
                                last.month, last.day + 1, dateAmount.month - 1,
                                monthLen[dateAmount.month - 1] ?: error("month not found lol in else"), "#FF0000"
                            )
                        )
                    } else {
                        ret.add(DateEvent(last.month, last.day + 1, dateAmount.month, dateAmount.day - 1, "#FF0000"))
                    }
                }
                last = dateAmount
                ret.add(
                    DateEvent(
                        dateAmount.month,
                        dateAmount.day,
                        dateAmount.month,
                        dateAmount.day,
                        dateAmount.getColor()
                    )
                )
            }
            if (last == null) {
                ret.add(DateEvent(1, 1, 12, 31, "#FF0000"))
            } else {
                if (last.month != 12 && last.day != 31) {
                    ret.add(DateEvent(last.month, last.day, 12, 31, "#FF0000"))
                }
            }
            ret.toList()
        }
    }

    fun list(status: String, order: String): List<Entry> {
        val s = if (status != "all") Status.valueOf(status.toUpperCase()) else null
        var o = order
        var a = "ASC"

        when (order) {
            "historic" -> {
                a = "DESC"
                o = "type"
            }
            "personal" -> {
                o = "type"
            }
            "date" -> o = "MONTH(date), DAY(date)"
        }


        db.open().use {
            return it.createQuery(
                "SELECT id, type, date, source, description, name, email, status FROM entries " + (if (s != null) "WHERE status = '$s'" else "") + " ORDER BY $o $a "
            )
                .executeAndFetch(Entry::class.java)
        }

    }

    private fun check(entry: Entry): Boolean {
        val len = entry.description.length
        return if (entry.type == Type.HISTORIC && !urlCheck.matcher(entry.source).matches())
            false
        else 5 <= len || len <= 250
    }
}

data class Entry(
    val id: Int = 0,
    val type: Type,
    val source: String = "",
    val date: Date,
    val description: String,
    val name: String = "Unknown",
    val email: String = "Unknown",
    val status: Status = Status.PENDING
)

data class DateAmount(
    val month: Int,
    val day: Int,
    val cnt: Int
) {
    fun getColor(): String {
        return when {
            cnt < 1 -> {
                "#FF0000"
            }
            cnt < 7 -> {
                "#FFF700"
            }
            else -> {
                "#00FF00"
            }
        }
    }
}

data class DateEvent(
    val fromMonth: Int,
    val fromDay: Int,
    val toMonth: Int,
    val toDay: Int,
    val color: String
) {
    val name: String
        get() = when (color) {
            "#FF0000" -> {
                "Keine eingetragenen Ereignisse. :("
            }
            "#FFF700" -> {
                "Ein paar Ereignisse wurden schon eingetragen."
            }
            else -> {
                "Wir haben bereits einige eingetragene Ereignisse."
            }
        }
    val details: String
        get() = when (color) {
            "#FF0000" -> {
                "Sei einer der ersten, der an diesem Tag etwas einträgt!"
            }
            "#FFF700" -> {
                "Füge weitere hinzu um die Auswahl zu vervollständigen!"
            }
            else -> {
                "Vielleicht weißt du uns ja mit den roten Stellen weiter zu helfen!"
            }
        }
}


enum class Type {
    PERSONAL,
    HISTORIC
}

enum class Status {
    APPROVED,
    PENDING,
    DENIED
}

private val monthLen = mapOf(
    1 to 31,
    2 to 29,
    3 to 31,
    4 to 30,
    5 to 31,
    6 to 30,
    7 to 31,
    8 to 31,
    9 to 30,
    10 to 31,
    11 to 30,
    12 to 31
)