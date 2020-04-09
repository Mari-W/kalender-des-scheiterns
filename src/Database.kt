package de.moeri

import io.ktor.util.KtorExperimentalAPI
import org.sql2o.Sql2o
import java.sql.Date

@KtorExperimentalAPI
object Database {

    lateinit var db: Sql2o

    fun init() {
        db = Sql2o(Config["db.url"], Config["db.user"], Config["db.pass"])
    }

    fun insert(ip: String, entry: Entry): Boolean {
        db.open().use {
            val limit = it.createQuery("SELECT rate_limit(:ip);")
                .addParameter("ip", ip)
                .executeScalar(Int::class.java)
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
                it.createQuery("""
                        SELECT MONTH(e.date) month, DAY(e.date) day, COUNT(*) cnt, SUM(CASE WHEN e.status='CHOSEN' THEN 1 ELSE 0 END) > 0 chosen
                        FROM entries e WHERE e.status='APPROVED' OR e.status='CHOSEN' GROUP BY MONTH(e.date), DAY(e.date)
                        """)
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
                                    monthLen[dateAmount.month - 1] ?: error("month not found lol in if"), "#ff007a"
                                )
                            )
                        } else {
                            ret.add(DateEvent(1, 1, dateAmount.month, dateAmount.day - 1, "#ff007a"))
                        }
                    }
                } else {
                    if (dateAmount.day == 1) {
                        ret.add(
                            DateEvent(
                                last.month, last.day + 1, dateAmount.month - 1,
                                monthLen[dateAmount.month - 1] ?: error("month not found lol in else"), "#ff007a"
                            )
                        )
                    } else {
                        ret.add(DateEvent(last.month, last.day + 1, dateAmount.month, dateAmount.day - 1, "#ff007a"))
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
                ret.add(DateEvent(1, 1, 12, 31, "#ff007a"))
            } else {
                if (last.month != 12 && last.day != 31) {
                    ret.add(DateEvent(last.month, last.day + 1, 12, 31, "#ff007a"))
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

    fun listEvents(): List<Entry> {
        db.open().use {
            return it.createQuery(
                "SELECT id, type, date, source, description, name, email, status FROM entries WHERE status in ('APPROVED', 'CHOSEN') ORDER BY MONTH(date), DAY(date)"
            )
                .executeAndFetch(Entry::class.java)
        }
    }
}

data class Entry(
    val id: Int = 0,
    val type: Type,
    var source: String = "",
    val date: Date,
    val description: String,
    val name: String = "Unknown",
    val email: String = "Unknown",
    val status: Status = Status.PENDING
) {
    init {
        if (!urlRegex.matcher(source).matches()) {
            source = ""
        }
    }
}

data class DateAmount(
    val month: Int,
    val day: Int,
    val cnt: Int,
    val chosen: Boolean
) {
    fun getColor(): String {
        return when {
            chosen -> {
                "#47cfad"
            }
            cnt < 1 -> {
                "#ff007a"
            }
            else -> {
                "#ffed5e"
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
            "#ff007a" -> {
                "Keine eingetragenen Ereignisse :("
            }
            "#ffed5e" -> {
                "Ein paar Ereignisse wurden schon eingetragen"
            }
            else -> {
                "Wir haben bereits einige eingetragene Ereignisse"
            }
        }
    val details: String
        get() = when (color) {
            "#ff007a" -> {
                "Sei eine*r der ersten, die*der an diesem Tag etwas einträgt!"
            }
            "#ffed5e" -> {
                "Füge weitere hinzu, um die Auswahl zu vervollständigen!"
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
    DENIED,
    CHOSEN
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