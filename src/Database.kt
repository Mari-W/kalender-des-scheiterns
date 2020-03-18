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

    fun list(): List<Entry> {
        db.open().use {
            return it.createQuery("SELECT id, type, date, source, description, name, picture, status FROM entries ORDER BY MONTH(date), DAY(date)")
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