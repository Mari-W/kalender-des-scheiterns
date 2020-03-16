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
    return listOf(Idea("DIE PARTEI wurde gegründet", Date.valueOf("02/08/2004"), "https://www.google.com/search?q=die+partei+gr%C3%BCndung"))
}

data class Idea(val idea: String, val date: Date, val source: String)
