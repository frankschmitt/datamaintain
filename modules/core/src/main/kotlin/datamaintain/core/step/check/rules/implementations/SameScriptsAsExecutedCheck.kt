package datamaintain.core.step.check.rules.implementations

import datamaintain.core.script.ExecutedScript
import datamaintain.core.script.ScriptWithContent
import datamaintain.core.step.check.rules.ScriptType
import datamaintain.core.step.check.rules.contracts.FullContextCheckRule

class SameScriptsAsExecutedCheck(
        executedScripts: Sequence<ExecutedScript>
) : FullContextCheckRule(executedScripts) {
    override fun check(scripts: Sequence<ScriptWithContent>) {
        val executedScriptChecksumsNotFoundInScannedScripts = executedScripts
                .map { it.checksum }
                .minus(scripts.map { it.checksum })
                .toList()

        if (executedScriptChecksumsNotFoundInScannedScripts.isNotEmpty()) {
            val executedScriptNames = executedScripts
                    .filter { executedScriptChecksumsNotFoundInScannedScripts.contains(it.checksum) }
                    .map { it.name }
                    .toList()

            throw IllegalStateException("ERROR - ${getName()} - Some executed scripts are not present : " +
                    "$executedScriptNames")
        }
    }

    override fun getName(): String {
        return NAME
    }

    override fun scriptType(): ScriptType {
        return ScriptType.SCANNED_SCRIPT
    }

    companion object {
        @JvmStatic
        val NAME = "SameScriptsAsExecutedCheck"
    }
}