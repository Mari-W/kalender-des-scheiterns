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
            it.createQuery("INSERT INTO entries (type, source, date, desc, name, picture) VALUES (:type, :source, :date, :desc, :name, :picture)")
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

    fun list(): List<Entry> {
        db.open().use {
            return it.createQuery("SELECT id, type, date, source, description, name, picture, status FROM entries ORDER BY MONTH(date), DAY(date)")
                .executeAndFetch(Entry::class.java)
        }
    }

    fun listBy(order: String): List<Entry> {

        if (order == "date")
            return list()

        var a = "ASC"
        var b = order
        if (order == "historic") {
            a = "DESC"
            b = "type"
        } else if (order == "personal") b = "type"

        db.open().use {
            return it.createQuery("SELECT id, type, date, source, description, name, picture, status FROM entries ORDER BY $b $a")
                .executeAndFetch(Entry::class.java)
        }
    }

    private fun check(entry: Entry): Boolean {
        val len = entry.description.length
        return if (!urlCheck.matcher(entry.source).matches())
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
    val status: Status
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