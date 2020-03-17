package de.moeri

import org.sql2o.Sql2o
import java.sql.Date
import java.util.regex.Pattern

val urlCheck = Pattern.compile("(https?://(?:www\\.|(?!www))[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|www\\.[a-zA-Z0-9][a-zA-Z0-9-]+[a-zA-Z0-9]\\.[^\\s]{2,}|https?://(?:www\\.|(?!www))[a-zA-Z0-9]+\\.[^\\s]{2,}|www\\.[a-zA-Z0-9]+\\.[^\\s]{2,})")
val db: Sql2o = Sql2o("jdbc:mysql://78.47.156.57:3306/kds", "root", "moeriland42")

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
        return it.createQuery("SELECT * FROM kds_idea")
            .executeAndFetch(Idea::class.java)
    }
}

fun controlIdea(idea: Idea): Boolean {
    val ideaLen = idea.idea.length
    if (5 <= ideaLen || ideaLen <= 420) {
        println("idea to long/short, $ideaLen")
        return false
    }

    if (!urlCheck.matcher(idea.source).matches()) {
        println("url wrong, ${idea.source}")
        return false
    }


    return true
}

data class Idea(val idea: String, val date: Date, val source: String, val id: Int = 0)
