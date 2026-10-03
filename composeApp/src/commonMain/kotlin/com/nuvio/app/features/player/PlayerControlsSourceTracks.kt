package com.nuvio.app.features.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.nuvio.app.features.streams.StreamAudioQualityFilter
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamLanguageUnknown
import com.nuvio.app.features.streams.StreamQualityBucket
import com.nuvio.app.features.streams.StreamsUiState
import com.nuvio.app.features.streams.languageCounts
import com.nuvio.app.features.streams.languageKeysByCount
import com.nuvio.app.features.streams.matchingLanguageKey
import com.nuvio.app.features.streams.preferredAudioTargets
import com.nuvio.app.features.streams.rememberStreamAudioQualityFilter
import com.nuvio.app.features.streams.streamLanguageCode
import com.nuvio.app.features.streams.streamLanguageLabel
import com.nuvio.app.features.streams.streamQualityLabel
import com.nuvio.app.features.streams.streamTraits
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.player_source_tracks_best_quality
import nuvio.composeapp.generated.resources.player_source_tracks_cancel
import nuvio.composeapp.generated.resources.player_source_tracks_loading
import nuvio.composeapp.generated.resources.player_source_tracks_missing_audio
import nuvio.composeapp.generated.resources.player_source_tracks_none
import nuvio.composeapp.generated.resources.player_source_tracks_quality_title
import nuvio.composeapp.generated.resources.player_source_tracks_scope_all_audio
import nuvio.composeapp.generated.resources.player_source_tracks_scope_all_qualities
import nuvio.composeapp.generated.resources.player_source_tracks_scope_current_audio
import nuvio.composeapp.generated.resources.player_source_tracks_scope_current_stream
import nuvio.composeapp.generated.resources.player_source_tracks_sources
import nuvio.composeapp.generated.resources.player_source_tracks_switch_audio_to
import nuvio.composeapp.generated.resources.stream_filter_all_languages
import nuvio.composeapp.generated.resources.stream_filter_all_qualities
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

// Fork: what the desktop player controls (web view) show for sources and audio/quality:
// audio language + quality chips in the "Fontes" and episode stream lists (instead of addon chips),
// the "Todos" scope of the audio panel and the quality panel. Kotlin owns the choices; the web view
// draws them and sends back the index of what was clicked.

/** A row of the audio "Todos" list or of the quality panel. */
data class PlayerControlSourceOptionItem(
    val label: String = "",
    val detail: String = "",
    val isCurrent: Boolean = false,
    /** Quality panel "Todas": set when no source of this quality has the audio playing. */
    val missingAudioMessage: String = "",
    val languageLabels: List<String> = emptyList(),
)

/** Sent to the web view as one object, next to the existing source/episode fields. */
data class PlayerControlsSourceTracksState(
    val sourceQualityFilters: List<PlayerControlFilterItem> = emptyList(),
    val episodeStreamQualityFilters: List<PlayerControlFilterItem> = emptyList(),
    val audioScopeCurrentLabel: String = "Current stream",
    val audioScopeAllLabel: String = "All",
    val audioSourceOptions: List<PlayerControlSourceOptionItem> = emptyList(),
    val qualityLabel: String = "Quality",
    /** Empty when the audio playing is unknown: the quality panel then lists every quality. */
    val qualityScopeCurrentLabel: String = "",
    val qualityScopeAllLabel: String = "All",
    val qualityCurrentAudioOptions: List<PlayerControlSourceOptionItem> = emptyList(),
    val qualityAllOptions: List<PlayerControlSourceOptionItem> = emptyList(),
    val loadingLabel: String = "",
    val emptyLabel: String = "",
    val switchAudioToLabel: String = "",
    val cancelLabel: String = "",
)

internal class PlayerControlsSourceTracks(
    /** Replace the addon chips of the existing sourceFilters / episodeStreamFilters fields. */
    val sourceLanguageFilters: List<PlayerControlFilterItem>,
    val sourceVisibleIndexes: Set<Int>,
    val episodeLanguageFilters: List<PlayerControlFilterItem>,
    val episodeVisibleIndexes: Set<Int>,
    val state: PlayerControlsSourceTracksState,
    private val sourceFilter: StreamAudioQualityFilter,
    private val episodeFilter: StreamAudioQualityFilter,
    private val options: SourceTrackOptions,
) {
    /** Handles the web view messages of these panels; false for any other message. */
    fun handleEvent(runtime: PlayerScreenRuntime, type: String, value: Double): Boolean {
        val index = value.toInt()
        when (type) {
            "selectSourceLanguage" -> sourceFilter.selectLanguageChip(index)
            "selectSourceQuality" -> sourceFilter.selectQualityChip(index)
            "selectEpisodeStreamLanguage" -> episodeFilter.selectLanguageChip(index)
            "selectEpisodeStreamQuality" -> episodeFilter.selectQualityChip(index)
            "selectSourceAudioLanguage" -> {
                val language = options.languages.getOrNull(index)?.key ?: return true
                runtime.selectSourceAudioLanguage(language)
            }
            "selectSourceQualityCurrentAudio" -> {
                val quality = options.qualitiesWithCurrentAudio.getOrNull(index)?.bucket ?: return true
                runtime.selectSourceQuality(quality, options.currentLanguage)
            }
            "selectSourceQualityAll" -> {
                val quality = options.allQualities.getOrNull(index)?.bucket ?: return true
                runtime.selectSourceQuality(quality, options.currentLanguage)
            }
            // value = quality index * 1000 + language index, from the "switch audio to" prompt.
            "selectSourceQualityAudio" -> {
                val quality = options.allQualities.getOrNull(index / 1000) ?: return true
                val language = quality.languages.getOrNull(index % 1000) ?: return true
                runtime.selectSourceQuality(quality.bucket, language)
            }
            else -> return false
        }
        return true
    }
}

private const val ChipAll = "all"

private fun StreamAudioQualityFilter.selectLanguageChip(index: Int) {
    onLanguageSelected(if (index <= 0) ChipAll else languages.getOrNull(index - 1)?.key ?: return)
}

private fun StreamAudioQualityFilter.selectQualityChip(index: Int) {
    onQualitySelected(if (index <= 0) ChipAll else qualities.getOrNull(index - 1)?.key ?: return)
}

@Composable
private fun StreamAudioQualityFilter.languageChips(): List<PlayerControlFilterItem> {
    if (totalCount == 0) return emptyList()
    val all = PlayerControlFilterItem(
        id = ChipAll,
        label = "${stringResource(Res.string.stream_filter_all_languages)} · $totalCount",
        isSelected = selectedLanguage == ChipAll,
    )
    return listOf(all) + languages.map { option ->
        PlayerControlFilterItem(
            id = option.key,
            label = "${streamLanguageLabel(option.key)} · ${option.count}",
            isSelected = selectedLanguage == option.key,
        )
    }
}

@Composable
private fun StreamAudioQualityFilter.qualityChips(): List<PlayerControlFilterItem> {
    if (qualities.isEmpty()) return emptyList()
    val all = PlayerControlFilterItem(
        id = ChipAll,
        label = "${stringResource(Res.string.stream_filter_all_qualities)} · $languageMatchCount",
        isSelected = selectedQuality == ChipAll,
    )
    return listOf(all) + qualities.map { option ->
        PlayerControlFilterItem(
            id = option.key,
            label = "${streamQualityLabel(StreamQualityBucket.valueOf(option.key))} · ${option.count}",
            isSelected = selectedQuality == option.key,
        )
    }
}

@Composable
private fun sourcesCount(n: Int): String = pluralStringResource(Res.plurals.player_source_tracks_sources, n, n)

/**
 * Quality rows. With [missingAudioLabel] (the "Todas" scope), a quality without that audio carries
 * the message and the audio choices of the prompt shown before switching.
 */
@Composable
private fun qualityItems(list: List<SourceQualityOption>, missingAudioLabel: String?): List<PlayerControlSourceOptionItem> =
    list.map { option ->
        val label = streamQualityLabel(option.bucket)
        val missing = missingAudioLabel != null && !option.hasCurrentAudio
        PlayerControlSourceOptionItem(
            label = label,
            detail = sourcesCount(option.streamCount),
            isCurrent = option.isCurrent,
            missingAudioMessage = if (missing) {
                stringResource(Res.string.player_source_tracks_missing_audio, label, missingAudioLabel.orEmpty())
            } else {
                ""
            },
            languageLabels = if (missing) option.languages.map { streamLanguageLabel(it) } else emptyList(),
        )
    }

/** Indexes (in the flat stream list the panels number) of the streams the chips keep. */
private fun visibleIndexes(all: StreamsUiState, filtered: StreamsUiState): Set<Int> {
    val kept = filtered.groups.flatMap { it.streams }.toHashSet()
    return all.groups.flatMap { it.streams }.withIndex()
        .filter { it.value in kept }
        .mapTo(HashSet()) { it.index }
}

@Composable
internal fun PlayerScreenRuntime.rememberPlayerControlsSourceTracks(
    sourceState: StreamsUiState,
    episodeState: StreamsUiState,
): PlayerControlsSourceTracks {
    val sourceFilter = rememberStreamAudioQualityFilter(sourceState)
    val episodeFilter = rememberStreamAudioQualityFilter(episodeState)
    val targets = preferredAudioTargets(playerSettingsUiState, contentLanguage)
    val options = remember(sourceState.groups, audioTracks, selectedAudioIndex, activeSourceUrl, activeSourceIdentityKey, targets) {
        buildSourceTrackOptions(sourceState.groups.flatMap { it.streams }, targets)
    }

    val currentLanguageLabel = options.currentLanguage?.let { streamLanguageLabel(it) }

    val audioOptions = options.languages.map { option ->
        val sources = sourcesCount(option.streamCount)
        PlayerControlSourceOptionItem(
            label = streamLanguageLabel(option.key),
            detail = option.bestQuality?.let {
                stringResource(Res.string.player_source_tracks_best_quality, streamQualityLabel(it), sources)
            } ?: sources,
            isCurrent = option.isCurrent,
        )
    }

    return PlayerControlsSourceTracks(
        sourceLanguageFilters = sourceFilter.languageChips(),
        sourceVisibleIndexes = remember(sourceState, sourceFilter.filteredUiState) {
            visibleIndexes(sourceState, sourceFilter.filteredUiState)
        },
        episodeLanguageFilters = episodeFilter.languageChips(),
        episodeVisibleIndexes = remember(episodeState, episodeFilter.filteredUiState) {
            visibleIndexes(episodeState, episodeFilter.filteredUiState)
        },
        state = PlayerControlsSourceTracksState(
            sourceQualityFilters = sourceFilter.qualityChips(),
            episodeStreamQualityFilters = episodeFilter.qualityChips(),
            audioScopeCurrentLabel = stringResource(Res.string.player_source_tracks_scope_current_stream),
            audioScopeAllLabel = stringResource(Res.string.player_source_tracks_scope_all_audio),
            audioSourceOptions = audioOptions,
            qualityLabel = stringResource(Res.string.player_source_tracks_quality_title),
            qualityScopeCurrentLabel = currentLanguageLabel
                ?.let { stringResource(Res.string.player_source_tracks_scope_current_audio, it) }
                .orEmpty(),
            qualityScopeAllLabel = stringResource(Res.string.player_source_tracks_scope_all_qualities),
            qualityCurrentAudioOptions = qualityItems(options.qualitiesWithCurrentAudio, null),
            qualityAllOptions = qualityItems(options.allQualities, currentLanguageLabel),
            loadingLabel = stringResource(Res.string.player_source_tracks_loading),
            emptyLabel = stringResource(Res.string.player_source_tracks_none),
            switchAudioToLabel = stringResource(Res.string.player_source_tracks_switch_audio_to),
            cancelLabel = stringResource(Res.string.player_source_tracks_cancel),
        ),
        sourceFilter = sourceFilter,
        episodeFilter = episodeFilter,
        options = options,
    )
}

// --- Options and actions ---------------------------------------------------------------------

internal data class SourceLanguageOption(
    val key: String,
    val streamCount: Int,
    val bestQuality: StreamQualityBucket?,
    val isCurrent: Boolean,
)

internal data class SourceQualityOption(
    val bucket: StreamQualityBucket,
    val streamCount: Int,
    val isCurrent: Boolean,
    val hasCurrentAudio: Boolean,
    /** Audio languages found in this quality, preferred first (for the "switch audio to" prompt). */
    val languages: List<String>,
)

internal class SourceTrackOptions(
    val currentLanguage: String?,
    val languages: List<SourceLanguageOption>,
    val qualitiesWithCurrentAudio: List<SourceQualityOption>,
    val allQualities: List<SourceQualityOption>,
)

private fun sameLanguage(a: String, b: String): Boolean =
    a == b || a.substringBefore('-') == b.substringBefore('-')

private fun PlayerScreenRuntime.currentSourceStream(streams: List<StreamItem>): StreamItem? =
    streams.firstOrNull { isCurrentPlayerControlStream(it) }

/** Language of the audio track playing, in the source list's terms (e.g. "pt-br", "ja"). */
private fun PlayerScreenRuntime.currentAudioLanguage(current: StreamItem?): String? {
    val streamLanguages = current?.streamTraits()?.audioLanguages.orEmpty()
    val trackLanguage = audioTracks.firstOrNull { it.index == selectedAudioIndex }
        ?.language
        ?.let(::streamLanguageCode)
        ?: return streamLanguages.firstOrNull()
    return streamLanguages.firstOrNull { it == trackLanguage }
        ?: streamLanguages.firstOrNull { sameLanguage(it, trackLanguage) }
        ?: trackLanguage
}

private fun PlayerScreenRuntime.buildSourceTrackOptions(
    streams: List<StreamItem>,
    preferredTargets: List<String>,
): SourceTrackOptions {
    val traits = streams.map { it.streamTraits() }
    val current = currentSourceStream(streams)
    val currentLanguage = currentAudioLanguage(current)
    val currentQuality = current?.streamTraits()?.quality

    val counts = languageCounts(traits) - StreamLanguageUnknown
    val keysByCount = languageKeysByCount(counts)
    val preferredKeys = preferredTargets.mapNotNull { matchingLanguageKey(keysByCount, it) }.distinct()
    val orderedKeys = preferredKeys + keysByCount.filterNot { it in preferredKeys }

    val languages = orderedKeys.map { key ->
        SourceLanguageOption(
            key = key,
            streamCount = counts[key] ?: 0,
            bestQuality = traits.filter { key in it.audioLanguages }.minOfOrNull { it.quality },
            isCurrent = currentLanguage != null && sameLanguage(key, currentLanguage),
        )
    }

    fun qualities(language: String?): List<SourceQualityOption> = StreamQualityBucket.entries.mapNotNull { bucket ->
        val inBucket = traits.filter { it.quality == bucket }
        fun hasLanguage(t: com.nuvio.app.features.streams.StreamTraits, l: String) = t.audioLanguages.any { sameLanguage(it, l) }
        val matching = if (language == null) inBucket else inBucket.filter { hasLanguage(it, language) }
        if (matching.isEmpty()) return@mapNotNull null
        SourceQualityOption(
            bucket = bucket,
            streamCount = matching.size,
            isCurrent = bucket == currentQuality,
            hasCurrentAudio = currentLanguage == null || inBucket.any { hasLanguage(it, currentLanguage) },
            languages = orderedKeys.filter { key -> inBucket.any { key in it.audioLanguages } },
        )
    }

    return SourceTrackOptions(
        currentLanguage = currentLanguage,
        languages = languages,
        qualitiesWithCurrentAudio = currentLanguage?.let { qualities(it) }.orEmpty(),
        allQualities = qualities(null),
    )
}

/** Best source for a language and/or quality: exact language first, then best quality, list order. */
private fun bestSourceStream(streams: List<StreamItem>, language: String?, quality: StreamQualityBucket?): StreamItem? =
    streams.mapNotNull { stream ->
        val traits = stream.streamTraits()
        if (quality != null && traits.quality != quality) return@mapNotNull null
        val languageRank = when {
            language == null -> 0
            language in traits.audioLanguages -> 0
            traits.audioLanguages.any { sameLanguage(it, language) } -> 1
            else -> return@mapNotNull null
        }
        Triple(stream, languageRank, traits.quality.ordinal)
    }.sortedWith(compareBy({ it.second }, { it.third })).firstOrNull()?.first

/** "Todos" scope: plays the language; on the stream playing if it carries it, else on its best source. */
private fun PlayerScreenRuntime.selectSourceAudioLanguage(language: String) {
    val embedded = audioTracks.firstOrNull { track ->
        track.language?.let(::streamLanguageCode)?.let { sameLanguage(it, language) } == true
    }
    if (embedded != null) {
        // Same as picking the track in "Stream atual".
        selectedAudioIndex = embedded.index
        persistAudioPreference(embedded)
        playerController?.selectAudioTrack(embedded.index)
        playerControlsCloseModalsToken += 1
        return
    }
    val stream = bestSourceStream(sourceStreamsState.groups.flatMap { it.streams }, language, quality = null) ?: return
    switchSourceForTrackPanel(stream, language)
}

private fun PlayerScreenRuntime.selectSourceQuality(quality: StreamQualityBucket, language: String?) {
    val streams = sourceStreamsState.groups.flatMap { it.streams }
    // The panel only offers a quality without the current audio through its "switch audio to" prompt.
    val stream = bestSourceStream(streams, language, quality) ?: return
    if (isCurrentPlayerControlStream(stream)) {
        playerControlsCloseModalsToken += 1
        return
    }
    switchSourceForTrackPanel(stream, language)
}

private fun PlayerScreenRuntime.switchSourceForTrackPanel(stream: StreamItem, language: String?) {
    if (requestP2pConsentForPlayerControls(stream = stream, episode = null)) return
    // The new stream restores this title's saved audio choice, so it starts on the language picked here.
    if (language != null) {
        updateTrackPreference { it.copy(audioLanguage = language, audioName = null, audioTrackId = null) }
    }
    switchToSource(stream)
    playerControlsCloseModalsToken += 1
}
