package de.moeri

import org.sql2o.Sql2o
import java.sql.Date

val db: Sql2o = Sql2o("jdbc:mysql://78.47.156.57:3306/kds", "root", "moeriland42")

fun insertIdea(idea: Idea) {
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

data class Idea(val idea: String, val date: Date, val source: String, val id: Int = 0)
