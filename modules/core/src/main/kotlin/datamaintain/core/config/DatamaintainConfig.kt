package datamaintain.core.config

import datamaintain.core.config.ConfigKey.Companion.overrideBySystemProperties
import datamaintain.core.config.CoreConfigKey.*
import datamaintain.core.db.driver.DatamaintainDriverConfig
import datamaintain.core.exception.DatamaintainBuilderMandatoryException
import datamaintain.core.script.ScriptAction
import datamaintain.core.script.Tag
import datamaintain.core.script.TagMatcher
import datamaintain.core.step.executor.ExecutionMode
import mu.KotlinLogging
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

private val logger = KotlinLogging.logger {}

data class DatamaintainConfig @JvmOverloads constructor(val name: String? = null,
                                                        val workingDirectory: Path = Paths.get(System.getProperty("user.dir")),
                                                        val path: Path = Paths.get(SCAN_PATH.default!!),
                                                        val identifierRegex: Regex = Regex(SCAN_IDENTIFIER_REGEX.default!!),
                                                        val doesCreateTagsFromFolder: Boolean = CREATE_TAGS_FROM_FOLDER.default!!.toBoolean(),
                                                        val whitelistedTags: Set<Tag> = setOf(),
                                                        val blacklistedTags: Set<Tag> = setOf(),
                                                        val tagsToPlayAgain: Set<Tag> = setOf(),
                                                        val overrideExecutedScripts: Boolean = PRUNE_OVERRIDE_UPDATED_SCRIPTS.default!!.toBoolean(),
                                                        val tagsMatchers: Set<TagMatcher> = setOf(),
                                                        val checkRules: Sequence<String> = emptySequence(),
                                                        val executionMode: ExecutionMode = defaultExecutionMode,
                                                        val defaultScriptAction: ScriptAction = defaultAction,
                                                        val driverConfig: DatamaintainDriverConfig,
                                                        val verbose: Boolean = VERBOSE.default!!.toBoolean(),
                                                        val porcelain: Boolean = PRINT_RELATIVE_PATH_OF_SCRIPT.default!!.toBoolean(),
                                                        val flags: List<String> = emptyList()) {

    private constructor(builder: Builder): this(
        builder.name,
        builder.workingDirectory,
        builder.path,
        builder.identifierRegex,
        builder.doesCreateTagsFromFolder,
        builder.whitelistedTags,
        builder.blacklistedTags,
        builder.tagsToPlayAgain,
        builder.overrideExecutedScripts,
        builder.tagsMatchers,
        builder.checkRules.asSequence(),
        builder.executionMode,
        builder.defaultScriptAction,
        builder.driverConfig,
        builder.verbose,
        builder.porcelain,
        builder.flags
    )

    companion object {
        private val defaultExecutionMode = ExecutionMode.NORMAL
        public val defaultAction = ScriptAction.RUN

        @JvmStatic
        fun buildConfig(configInputStream: InputStream, driverConfig: DatamaintainDriverConfig): DatamaintainConfig {
            val props = Properties()
            props.load(configInputStream)
            return buildConfig(driverConfig, props)
        }

        @JvmStatic
        @JvmOverloads
        fun buildConfig(driverConfig: DatamaintainDriverConfig, props: Properties = Properties()): DatamaintainConfig {
            overrideBySystemProperties(props, values().asList())

            val workingDirectoryPath = buildWorkingDirectoryPath(props)
            enrichFromParentConfig(workingDirectoryPath, props)

            var executionMode = ExecutionMode.fromNullable(props.getNullableProperty(EXECUTION_MODE), defaultExecutionMode)

            val scriptAction = ScriptAction.fromNullable(props.getNullableProperty(DEFAULT_SCRIPT_ACTION), defaultAction)

            val scanPath = buildAbsoluteScanPath(workingDirectoryPath, props)

            return DatamaintainConfig(
                    props.getProperty(CONFIG_NAME.key),
                    workingDirectoryPath,
                    scanPath,
                    Regex(props.getProperty(SCAN_IDENTIFIER_REGEX)),
                    props.getProperty(CREATE_TAGS_FROM_FOLDER).toBoolean(),
                    extractTags(props.getNullableProperty(TAGS_WHITELISTED)),
                    extractTags(props.getNullableProperty(TAGS_BLACKLISTED)),
                    extractTags(props.getNullableProperty(PRUNE_TAGS_TO_RUN_AGAIN)),
                    props.getProperty(PRUNE_OVERRIDE_UPDATED_SCRIPTS).toBoolean(),
                    props.getStringPropertiesByPrefix(TAG.key)
                            .map { TagMatcher.parse(it.first.replace("${TAG.key}.", ""), it.second, scanPath) }
                            .toSet(),
                    extractSequence(props.getNullableProperty(CHECK_RULES)),
                    executionMode,
                    scriptAction,
                    driverConfig,
                    props.getProperty(VERBOSE).toBoolean(),
                    props.getProperty(PRINT_RELATIVE_PATH_OF_SCRIPT).toBoolean(),
                    extractSequence(props.getNullableProperty(FLAGS)).toList()
            )
        }

        private fun buildWorkingDirectoryPath(props: Properties): Path {
            return props.getProperty(WORKING_DIRECTORY_PATH)
                    .let { Paths.get(it) }
                    .also { it.toAbsolutePath().normalize() }
        }

        private fun buildAbsoluteScanPath(workingDirectoryPath: Path, props: Properties): Path {
            var scanPath = Paths.get(props.getProperty(SCAN_PATH))

            if (!scanPath.isAbsolute) {
                scanPath = workingDirectoryPath.resolve(scanPath)
            }

            return scanPath.toAbsolutePath().normalize()
        }

        private fun enrichFromParentConfig(workingDirectoryPath: Path, props: Properties) {
            getParentConfigFile(workingDirectoryPath, props)?.also {
                enrichFromParentProperties(workingDirectoryPath, props, it)
            }
        }

        private fun enrichFromParentProperties(workingDirectoryPath: Path, props: Properties, file: File) {
            val parentProps = Properties()

            file.inputStream().use {
                parentProps.load(it)
            }

            logger.info { ("Load new config keys from parent config located at" +
                    " ${file.toPath().toAbsolutePath().normalize()}").trimMargin() }

            parentProps.forEach {
                props.putIfAbsent(it.key, it.value)
            }

            getParentConfigFile(workingDirectoryPath, parentProps)?.also {
                enrichFromParentProperties(workingDirectoryPath, props, it)
            }
        }

        private fun getParentConfigFile(workingDirectoryPath: Path, props: Properties): File? {
            return props.getProperty(PARENT_CONFIG_PATH.key)
                    ?.let { Paths.get(it) }
                    ?.let {
                        if (!it.isAbsolute) workingDirectoryPath.resolve(it) else it
                    }
                    ?.toAbsolutePath()
                    ?.normalize()
                    ?.toFile()
        }

        private fun extractTags(tags: String?): Set<Tag> {
            return tags?.split(",")
                    ?.map { Tag(it) }
                    ?.toSet()
                    ?: setOf()
        }

        private fun extractSequence(string: String?): Sequence<String> {
            return if (string.isNullOrEmpty()) {
                sequenceOf()
            } else {
                string.splitToSequence(",")
            }
        }
    }

    fun log() {
        logger.info { "Configuration: " }

        workingDirectory.also { logger.info { "- working directory -> $it" } }
        name?.also { logger.info { "- name -> $it" } }
        path.let { logger.info { "- path -> $it" } }
        identifierRegex.let { logger.info { "- identifier regex -> ${it.pattern}" } }
        defaultScriptAction.let { logger.info { "- script action -> ${it}" } }
        executionMode.let { logger.info { "- execution mode -> $it" } }
        tagsMatchers.let { logger.info { "- tags -> $tagsMatchers" } }
        whitelistedTags.let { logger.info { "- whitelisted tags -> $it" } }
        blacklistedTags.let { logger.info { "- blacklisted tags -> $it" } }
        tagsToPlayAgain.let { logger.info { "- tags to play again -> $it" } }
        overrideExecutedScripts.let { logger.info { "- Allow override executed script -> ${it}" } }
        checkRules.let { logger.info { "- rules -> ${checkRules.toList()}" } }
        verbose.let { logger.info { "- verbose -> $it" } }
        porcelain.let { logger.info { "- porcelain -> $it" } }
        flags.let { logger.info { "- flags -> $it" } }
        logger.info { "" }
    }

    class Builder {
        // mandatory
        lateinit var driverConfig: DatamaintainDriverConfig
            private set

        // optional
        var name: String? = null
            private set
        var workingDirectory: Path = Paths.get(System.getProperty("user.dir"))
            private set
        var path: Path = Paths.get(SCAN_PATH.default!!)
            private set
        var identifierRegex: Regex = Regex(SCAN_IDENTIFIER_REGEX.default!!)
            private set
        var doesCreateTagsFromFolder: Boolean = CREATE_TAGS_FROM_FOLDER.default!!.toBoolean()
            private set
        var whitelistedTags: MutableSet<Tag> = mutableSetOf()
            private set
        var blacklistedTags: MutableSet<Tag> = mutableSetOf()
            private set
        var tagsToPlayAgain: MutableSet<Tag> = mutableSetOf()
            private set
        var overrideExecutedScripts: Boolean = PRUNE_OVERRIDE_UPDATED_SCRIPTS.default!!.toBoolean()
            private set
        var tagsMatchers: MutableSet<TagMatcher> = mutableSetOf()
            private set
        var checkRules: MutableList<String> = mutableListOf()
            private set
        var executionMode: ExecutionMode = defaultExecutionMode
            private set
        var defaultScriptAction: ScriptAction = defaultAction
            private set
        var verbose: Boolean = VERBOSE.default!!.toBoolean()
            private set
        var porcelain: Boolean = PRINT_RELATIVE_PATH_OF_SCRIPT.default!!.toBoolean()
            private set
        var flags: MutableList<String> = mutableListOf()
            private set

        fun withName(name: String) = apply { this.name = name }
        fun withWorkingDirectory(workingDirectory: Path) = apply { this.workingDirectory = workingDirectory }
        fun withPath(path: Path) = apply { this.path = path }
        fun withIdentifierRegex(identifierRegex: Regex) = apply { this.identifierRegex = identifierRegex }
        fun withDoesCreateTagsFromFolder(doesCreateTagsFromFolder: Boolean) = apply { this.doesCreateTagsFromFolder = doesCreateTagsFromFolder }
        fun withOverrideExecutedScripts(overrideExecutedScripts: Boolean) = apply { this.overrideExecutedScripts = overrideExecutedScripts }
        fun withExecutionMode(executionMode: ExecutionMode) = apply { this.executionMode = executionMode }
        fun withDefaultScriptAction(defaultScriptAction: ScriptAction) = apply { this.defaultScriptAction = defaultScriptAction }
        fun withDriverConfig(driverConfig: DatamaintainDriverConfig) = apply { this.driverConfig = driverConfig }
        fun withVerbose(verbose: Boolean) = apply { this.verbose = verbose }
        fun withPorcelain(porcelain: Boolean) = apply { this.porcelain = porcelain }

        // Collection
        fun addWhitelistedTag(whitelistedTag: Tag) = apply { this.whitelistedTags.add(whitelistedTag) }
        fun addBlacklistedTag(blacklistedTag: Tag) = apply { this.blacklistedTags.add(blacklistedTag) }
        fun addTagToPlayAgain(tagToPlayAgain: Tag) = apply { this.tagsToPlayAgain.add(tagToPlayAgain) }
        fun addTagMatcher(tagsMatcher: TagMatcher) = apply { this.tagsMatchers.add(tagsMatcher) }
        fun addCheckRule(checkRule: String) = apply { this.checkRules.add(checkRule) }
        fun addFlag(flag: String) = apply { this.flags.add(flag) }

        fun build(): DatamaintainConfig {
            if (!::driverConfig.isInitialized) {
                throw DatamaintainBuilderMandatoryException("DatamaintainConfigBuilder", "driverConfig")
            }

            return DatamaintainConfig(this)
        }
    }
}

interface ConfigKey {
    val key: String
    val default: String?

    companion object {
        fun overrideBySystemProperties(props: Properties, configKeys: List<ConfigKey>) {
            configKeys.forEach { configKey ->
                val property: String? = System.getProperty(configKey.key)
                property?.let { props.put(configKey.key, it) }
            }
        }
    }
}


enum class CoreConfigKey(override val key: String,
                         override val default: String? = null) : ConfigKey {
    // GLOBAL
    CONFIG_NAME("name"),
    WORKING_DIRECTORY_PATH("working.directory.path", System.getProperty("user.dir")),
    PARENT_CONFIG_PATH("parent.config.path"),
    DB_TYPE("db.type", "mongo"),
    VERBOSE("verbose", "false"),
    DEFAULT_SCRIPT_ACTION("default.script.action", "RUN"),
    PRINT_RELATIVE_PATH_OF_SCRIPT("porcelain", "false"),
    FLAGS("flags"),

    // SCAN
    SCAN_PATH("scan.path", "./scripts/"),
    SCAN_IDENTIFIER_REGEX("scan.identifier.regex", "(.*)"),
    CREATE_TAGS_FROM_FOLDER("scan.tags.createFromFolder", "false"),
    TAG("tag"),

    // FILTER
    TAGS_WHITELISTED("filter.tags.whitelisted"),
    TAGS_BLACKLISTED("filter.tags.blacklisted"),

    // PRUNER
    PRUNE_TAGS_TO_RUN_AGAIN("prune.tags.to.run.again"),
    PRUNE_OVERRIDE_UPDATED_SCRIPTS("prune.scripts.override.executed", "false"),

    // CHECKER
    CHECK_RULES("check.rules"),

    // EXECUTE
    EXECUTION_MODE("execute.mode", "NORMAL")
}

fun Properties.getProperty(configKey: ConfigKey): String =
        if (configKey.default != null) {
            getProperty(configKey, configKey.default!!)
        } else {
            getNullableProperty(configKey) ?: throw IllegalArgumentException("$configKey is mandatory")
        }

fun Properties.getNullableProperty(configKey: ConfigKey): String? = this.getProperty(configKey.key)

fun Properties.getStringPropertiesByPrefix(prefix: String): Set<Pair<String, String>> {
    return this.entries
            .filter { (it.key as String).startsWith(prefix) }
            .map { Pair(it.key as String, it.value as String) }
            .toSet()
}

fun Properties.getProperty(configKey: ConfigKey, defaultValue: String): String =
        this.getProperty(configKey.key, defaultValue)


