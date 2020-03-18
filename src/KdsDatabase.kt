package de.moeri

import io.ktor.util.KtorExperimentalAPI
import org.sql2o.Sql2o
import java.sql.Date
import java.util.regex.Pattern


@KtorExperimentalAPI
object DB {
    private val urlCheck: Pattern = Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})")
    private val db: Sql2o = Sql2o(Config["db.url"], Config["db.user"], Config["db.pass"])

    fun insertIdea(idea: Idea) {
        if (!controlIdea(idea)) {
            throw IllegalArgumentException()
        }
        db.open().use {
            it.createQuery("INSERT INTO kds_idea (idea, source_link, date) VALUES (:idea, :source, :date)")
                .bind(idea)
                .executeUpdate()
        }
    }

    fun listIdeas(): List<Idea> {
        db.open().use {
            return it.createQuery("SELECT id, idea, date, source_link source FROM kds_idea")
                .executeAndFetch(Idea::class.java)
        }
    }

    private fun controlIdea(idea: Idea): Boolean {
        val ideaLen = idea.idea.length
        if (!(5 <= ideaLen || ideaLen <= 420)) {
            println("idea to long/short, $ideaLen")
            return false
        }

        if (!urlCheck.matcher(idea.source).matches()) {
            println("url wrong, ${idea.source}")
            return false
        }
        return true
    }
}



data class Idea(val idea: String, val date: Date, val source: String, val id: Int = 0)
