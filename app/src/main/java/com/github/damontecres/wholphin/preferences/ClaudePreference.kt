package com.github.damontecres.wholphin.preferences

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.ConditionalPreferences
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption

inline fun AppPreferences.updateClaudePreferences(block: ClaudePreferences.Builder.() -> Unit): AppPreferences =
    update {
        claudePreferences = claudePreferences.toBuilder().apply(block).build()
    }

/**
 * The Anthropic API key. The summary only shows whether it is set and its last characters, never the whole key.
 */
class ClaudeApiKeyPreference :
    AppStringPreference<AppPreferences>(
        title = R.string.claude_api_key,
        defaultValue = "",
        getter = { it.claudePreferences.apiKey },
        setter = { prefs, value -> prefs.updateClaudePreferences { apiKey = value.trim() } },
        summary = null,
    ) {
    override fun summary(
        context: Context,
        value: String?,
    ): String =
        if (value.isNullOrBlank()) {
            context.getString(R.string.claude_api_key_not_set)
        } else {
            context.getString(R.string.claude_api_key_set, value.takeLast(4))
        }
}

object ClaudePreference {
    val Settings =
        AppDestinationPreference<AppPreferences>(
            title = R.string.claude_settings,
            summary = R.string.claude_settings_summary,
            destination = Destination.Settings(PreferenceScreenOption.CLAUDE),
        )

    val Enabled =
        AppSwitchPreference<AppPreferences>(
            title = R.string.claude_companion,
            defaultValue = false,
            getter = { it.claudePreferences.enabled },
            setter = { prefs, value -> prefs.updateClaudePreferences { enabled = value } },
            summaryOn = R.string.claude_companion_summary,
            summaryOff = R.string.disabled,
        )

    val ApiKey = ClaudeApiKeyPreference()

    /** In the UI: Never, Sometimes, Often */
    private val frequencies = listOf(ClaudeFrequency.CLAUDE_NEVER, ClaudeFrequency.CLAUDE_SOMETIMES, ClaudeFrequency.CLAUDE_OFTEN)

    val Frequency =
        AppChoicePreference<AppPreferences, ClaudeFrequency>(
            title = R.string.claude_frequency,
            defaultValue = ClaudeFrequency.CLAUDE_SOMETIMES,
            displayValues = R.array.claude_frequency_options,
            indexToValue = { frequencies.getOrElse(it) { ClaudeFrequency.CLAUDE_SOMETIMES } },
            valueToIndex = { frequencies.indexOf(it).takeIf { i -> i >= 0 } ?: 1 },
            getter = { it.claudePreferences.frequency },
            setter = { prefs, value -> prefs.updateClaudePreferences { frequency = value } },
        )

    private val models = listOf(ClaudeModel.CLAUDE_HAIKU, ClaudeModel.CLAUDE_SONNET)

    val Model =
        AppChoicePreference<AppPreferences, ClaudeModel>(
            title = R.string.claude_model,
            defaultValue = ClaudeModel.CLAUDE_HAIKU,
            displayValues = R.array.claude_model_options,
            subtitles = R.array.claude_model_options_subtitles,
            indexToValue = { models.getOrElse(it) { ClaudeModel.CLAUDE_HAIKU } },
            valueToIndex = { models.indexOf(it).takeIf { i -> i >= 0 } ?: 0 },
            getter = { it.claudePreferences.model },
            setter = { prefs, value -> prefs.updateClaudePreferences { model = value } },
        )
}

val claudePreferenceGroups =
    listOf(
        PreferenceGroup(
            title = R.string.claude,
            preferences = listOf(ClaudePreference.Enabled),
            conditionalPreferences =
                listOf(
                    ConditionalPreferences(
                        { it.claudePreferences.enabled },
                        listOf(
                            ClaudePreference.ApiKey,
                            ClaudePreference.Frequency,
                            ClaudePreference.Model,
                        ),
                    ),
                ),
        ),
    )
