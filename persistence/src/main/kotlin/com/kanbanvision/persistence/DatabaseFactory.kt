package com.kanbanvision.persistence

import com.kanbanvision.persistence.tables.Boards
import com.kanbanvision.persistence.tables.Cards
import com.kanbanvision.persistence.tables.Columns
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init(url: String, driver: String, user: String, password: String, poolSize: Int = 10) {
        val hikari = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = user
            this.password = password
            maximumPoolSize = poolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        Database.connect(HikariDataSource(hikari))
        transaction {
            SchemaUtils.createMissingTablesAndColumns(Boards, Columns, Cards)
        }
    }
}
