package datamaintain.db.driver.jdbc

import datamaintain.core.db.driver.DatamaintainDriver
import datamaintain.core.script.ExecutedScript
import datamaintain.core.script.ExecutionStatus
import datamaintain.core.script.FileScript
import datamaintain.core.script.ScriptWithContent
import datamaintain.core.step.executor.Execution
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path
import java.sql.*


private val logger = KotlinLogging.logger {}

class JdbcDriver(private val jdbcUri: String,
                 private val tmpFilePath: Path,
                 private val clientPath: Path,
                 private val printOutput: Boolean,
                 private val saveOutput: Boolean
) : DatamaintainDriver {
    private val connection: Connection = DriverManager.getConnection(jdbcUri)

    companion object {
        const val EXECUTED_SCRIPTS_COLLECTION = "executedScripts"
    }

    override fun executeScript(script: ScriptWithContent): Execution {
        val statement = connection.createStatement()

        val scriptPath = when (script) {
            is FileScript -> script.path
            else -> {
                tmpFilePath.toFile().writeText(script.content)
                tmpFilePath
            }
        }
        var exitCode: Int

        try {
            val `in` = BufferedReader(FileReader(scriptPath.toString()))
            var str: String
            val sb = StringBuffer()
            while (`in`.readLine().also { str = it } != null) {
                sb.append("$str\n ")
            }
            `in`.close()
            exitCode = statement.executeUpdate(sb.toString())
        } catch (e: SQLException) {
            System.err.println("Failed to Execute" + scriptPath + ". The error is" + e.message)
            exitCode = e.errorCode
        }

        return Execution(if (exitCode == 0) ExecutionStatus.OK else ExecutionStatus.KO, null /* TODO ?*/)
    }

    override fun listExecutedScripts(): Sequence<ExecutedScript> {
        val statement = connection.createStatement()
        val executionOutput: ResultSet = statement.executeQuery("SELECT * from $EXECUTED_SCRIPTS_COLLECTION")
        val executedScript = mutableListOf<ExecutedScript>()
        while (executionOutput.next()) {
            // TODO: put this in a KResultParser
            executedScript.plus(ExecutedScript(
                    name = executionOutput.getString("name"),
                    checksum = executionOutput.getString("checksum"),
                    executionDurationInMillis = executionOutput.getLong("executionDurationInMillis"),
                    executionOutput = executionOutput.getString("executionOutput"),
                    executionStatus = ExecutionStatus.valueOf(executionOutput.getString("executionStatus")),
                    identifier = executionOutput.getString("identifier")
            ))
        }
        return executedScript.asSequence()
    }

    override fun markAsExecuted(executedScript: ExecutedScript): ExecutedScript {
        val insertStmt = connection.prepareStatement("INSERT INTO $EXECUTED_SCRIPTS_COLLECTION VALUES (?, ?, ?, ?, ?, ?)")

        try {
            connection.autoCommit = false
            with(executedScript) {
                insertStmt.setString(1, name)
                insertStmt.setString(2, checksum)
                executionDurationInMillis
                        ?.let { insertStmt.setLong(3, it) }
                        ?: insertStmt.setNull(3, Types.INTEGER)
                insertStmt.setString(4, executionOutput)
                insertStmt.setString(5, executionStatus.name)
                insertStmt.setString(6, identifier)
            }
            insertStmt.executeQuery()
            connection.commit()
        } catch (e: SQLException) {
            connection.rollback()
            throw IllegalStateException("Query $insertStmt fail with exit code ${e.errorCode} an output : ${e.message}")
        }
        return executedScript
    }
}
