package datamaintain.cli.app.utils

import datamaintain.cli.app.DbType
import datamaintain.cli.app.DbTypeNotFoundException
import datamaintain.core.config.DatamaintainConfig
import datamaintain.core.db.driver.DatamaintainDriverConfig
import datamaintain.db.driver.jdbc.JdbcDriverConfig
import datamaintain.db.driver.mongo.MongoDriverConfig
import java.util.*

private fun loadDriverConfig(props: Properties): DatamaintainDriverConfig {
    var dbType: String? = null

    if (props.containsKey("db.type")) {
        dbType = props.getProperty("db.type")
    }

    return when (dbType) {
        DbType.MONGO.value -> MongoDriverConfig.buildConfig(props)
        DbType.JDBC.value -> JdbcDriverConfig.buildConfig(props)
        else -> throw DbTypeNotFoundException(props.getProperty("db.type"))
    }
}

fun loadConfig(props: Properties): DatamaintainConfig {
    val driverConfig = loadDriverConfig(props)
    return DatamaintainConfig.buildConfig(driverConfig, props)
}