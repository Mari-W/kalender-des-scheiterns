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


    fun insert(entry: Entry) {
        if (!check(entry)) {
            throw IllegalArgumentException()
        }
        db.open().use {
            it.createQuery("INSERT INTO entries (type, source, date, description, name, picture) VALUES (:type, :source, :date, :description, :name, :picture)")
                .bind(entry)
                .executeUpdate()
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


    /**
     * should return list of string which already have a submission
     */
    fun dates(): List<DateAmount> {
        return db.open().use {
            it.createQuery(
                """
                SELECT x.month, x.day, co.color FROM colors co JOIN (
                    SELECT MONTH(e.date) month, DAY(e.date) day, COUNT(*) cnt FROM entries e WHERE e.status='APPROVED' GROUP BY MONTH(e.date), DAY(e.date)) x
                ON co.from_num <= x.cnt AND x.cnt <= co.to_num ORDER BY month, day ASC;
            """
            ).executeAndFetch(DateAmount::class.java)
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
                "SELECT id, type, date, source, description, name, picture, status FROM entries " + (if (s != null) "WHERE status = '$s'" else "") + " ORDER BY $o $a "
            )
                .executeAndFetch(Entry::class.java)
        }

    }

    private fun check(entry: Entry): Boolean {
        val len = entry.description.length
        return if (entry.type == Type.HISTORIC && !urlCheck.matcher(entry.source).matches())
            false
        else 5 <= len || len <= 420
    }
}


data class Entry(
    val id: Int = 0,
    val type: Type,
    val source: String = "",
    val date: Date,
    val description: String,
    val picture: String = "",
    val name: String = "Unknown",
    val status: Status = Status.PENDING
)

data class DateAmount(
    val month: Short,
    val day: Short,
    val color: String
)

enum class Type {
    PERSONAL,
    HISTORIC
}

enum class Status {
    APPROVED,
    PENDING,
    DENIED
}