package my.noveldokusha.features.reader.ui.settingDialogs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CenterFocusWeak
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import my.noveldokusha.coreui.components.MyOutlinedTextField
import my.noveldokusha.coreui.components.MySlider
import my.noveldokusha.coreui.components.SlimListItem
import my.noveldokusha.coreui.composableActions.debouncedAction
import my.noveldokusha.coreui.theme.InternalTheme
import my.noveldokusha.coreui.theme.rememberMutableStateOf
import my.noveldokusha.core.appPreferences.VoicePredefineState
import my.noveldokusha.features.reader.features.TextToSpeechSettingData
import my.noveldokusha.reader.R
import my.noveldokusha.text_to_speech.VoiceData


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VoiceReaderSettingDialog(
    state: TextToSpeechSettingData,
    floatingTtsState: my.noveldokusha.features.reader.ui.ReaderScreenState.Settings.FloatingTtsSettingsData? = null,
    parallelEnabled: MutableState<Boolean>? = null,
) {
    var openVoicesDialog by rememberSaveable { mutableStateOf(false) }
    var openOriginalVoiceDialog by rememberSaveable { mutableStateOf(false) }
    val dropdownCustomSavedVoicesExpanded = rememberSaveable { mutableStateOf(false) }

    Column {
        AnimatedVisibility(visible = state.isLoadingChapter.value) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        CircleShape
                    )
                )
            }
        }
        ElevatedCard(
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(8.dp)
            ) {
                var localPitch by remember { mutableFloatStateOf(state.voicePitch.value) }
                var localSpeed by remember { mutableFloatStateOf(state.voiceSpeed.value) }
                LaunchedEffect(state.voicePitch.value) { localPitch = state.voicePitch.value }
                LaunchedEffect(state.voiceSpeed.value) { localSpeed = state.voiceSpeed.value }

                MySlider(
                    value = localPitch,
                    valueRange = 0.1f..5f,
                    onValueChange = { localPitch = it },
                    text = stringResource(R.string.voice_pitch) + ": %.2f".format(localPitch),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    onValueChangeFinished = { state.setVoicePitch(localPitch) },
                )
                MySlider(
                    value = localSpeed,
                    valueRange = 0.1f..5f,
                    onValueChange = { localSpeed = it },
                    text = stringResource(R.string.voice_speed) + ": %.2f".format(localSpeed),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    onValueChangeFinished = { state.setVoiceSpeed(localSpeed) },
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AssistChip(
                        label = { Text(text = stringResource(id = R.string.start_here)) },
                        onClick = debouncedAction { state.playFirstVisibleItem() },
                        leadingIcon = { Icon(Icons.Filled.CenterFocusWeak, null, Modifier.size(14.dp)) },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    AssistChip(
                        label = { Text(text = stringResource(id = R.string.focus)) },
                        onClick = debouncedAction { state.scrollToActiveItem() },
                        leadingIcon = { Icon(Icons.Filled.CenterFocusStrong, null, Modifier.size(14.dp)) },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    AssistChip(
                        label = { Text(text = stringResource(id = R.string.voices)) },
                        onClick = { openVoicesDialog = !openVoicesDialog },
                        leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, null, Modifier.size(14.dp)) },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    if (parallelEnabled?.value == true) {
                        AssistChip(
                            label = { Text(text = stringResource(R.string.original_voice)) },
                            onClick = { openOriginalVoiceDialog = !openOriginalVoiceDialog },
                            leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, null, Modifier.size(14.dp)) },
                            modifier = Modifier.heightIn(min = 30.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                    AssistChip(
                        label = { Text(text = stringResource(R.string.saved_voices)) },
                        onClick = {
                            dropdownCustomSavedVoicesExpanded.let {
                                it.value = !it.value
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Bookmarks, null, Modifier.size(14.dp)) },
                        modifier = Modifier.heightIn(min = 30.dp),
                        colors = AssistChipDefaults.assistChipColors(
                            leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                    Box {
                        DropdownCustomSavedVoices(
                            expanded = dropdownCustomSavedVoicesExpanded,
                            list = state.customSavedVoices.value,
                            currentVoice = state.activeVoice.value,
                            currentVoiceSpeed = state.voiceSpeed.value,
                            currentVoicePitch = state.voicePitch.value,
                            onPredefinedSelected = {
                                state.setVoiceSpeed(it.speed)
                                state.setVoicePitch(it.pitch)
                                state.setVoiceId(it.voiceId)
                            },
                            setCustomSavedVoices = state.setCustomSavedVoices
                        )
                        VoiceSelectorDialog(
                            availableVoices = state.availableVoices,
                            currentVoice = state.activeVoice.value,
                            inputTextFilter = rememberSaveable { mutableStateOf("") },
                            setVoice = state.setVoiceId,
                            isDialogOpen = openVoicesDialog,
                            setDialogOpen = { openVoicesDialog = it }
                        )
                        VoiceSelectorDialog(
                            availableVoices = state.availableVoices,
                            currentVoice = state.availableVoices.find { it.id == state.originalVoiceId.value },
                            inputTextFilter = rememberSaveable { mutableStateOf("") },
                            setVoice = { state.setOriginalVoiceId(it) },
                            isDialogOpen = openOriginalVoiceDialog,
                            setDialogOpen = { openOriginalVoiceDialog = it }
                        )
                    }
                }

                if (floatingTtsState != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.tts_floating),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = floatingTtsState.isEnabled.value,
                                    onCheckedChange = { floatingTtsState.isEnabled.value = it }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.tts_floating_show_outside_app),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(4.dp))
                                Switch(
                                    checked = floatingTtsState.showOutsideApp.value,
                                    onCheckedChange = { floatingTtsState.showOutsideApp.value = it },
                                    enabled = floatingTtsState.isEnabled.value
                                )
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val alpha by animateFloatAsState(
                        targetValue = if (state.isThereActiveItem.value) 1f else 0.5f,
                        label = ""
                    )
                    IconButton(
                        onClick = debouncedAction(waitMillis = 1000) { state.playPreviousChapter() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.Rounded.FastRewind,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        )
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.playPreviousItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        )
                    }
                    IconButton(onClick = { state.setPlaying(!state.isPlaying.value) }) {
                        AnimatedContent(
                            targetState = state.isPlaying.value,
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            label = ""
                        ) { target ->
                            when (target) {
                                true -> Icon(
                                    Icons.Rounded.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                false -> Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 100) { state.playNextItem() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.NavigateNext,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(34.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        )
                    }
                    IconButton(
                        onClick = debouncedAction(waitMillis = 1000) { state.playNextChapter() },
                        enabled = state.isThereActiveItem.value,
                        modifier = Modifier.alpha(alpha),
                    ) {
                        Icon(
                            Icons.Rounded.FastForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(28.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                        )
                    }
                }
            }
        }


    }
}

@OptIn(ExperimentalFoundationApi::class, FlowPreview::class)
@Composable
private fun VoiceSelectorDialog(
    availableVoices: List<VoiceData>,
    currentVoice: VoiceData?,
    inputTextFilter: MutableState<String>,
    setVoice: (voiceId: String) -> Unit,
    isDialogOpen: Boolean,
    setDialogOpen: (Boolean) -> Unit,
) {
    val voicesSorted = remember { mutableStateListOf<VoiceData>() }
    val voicesFiltered = remember { mutableStateListOf<VoiceData>() }

    LaunchedEffect(availableVoices) {
        val sorted = withContext(Dispatchers.Default) {
            availableVoices.sortedWith(
                compareBy<VoiceData> { it.language }
                    .thenByDescending { it.quality }
                    .thenBy { it.needsInternet }
            )
        }
        voicesSorted.clear()
        voicesSorted.addAll(sorted)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { inputTextFilter.value to voicesSorted.toList() }
            .debounce(200)
            .collectLatest { (filter, sorted) ->
                val items = withContext(Dispatchers.Default) {
                    if (filter.isEmpty()) sorted
                    else sorted.filter { it.language.contains(filter, ignoreCase = true) }
                }
                voicesFiltered.clear()
                voicesFiltered.addAll(items)
            }
    }

    val listState = rememberLazyListState()
    val inputFocusRequester = remember { FocusRequester() }

    // Автоскролл к текущему голосу только при открытии диалога, не при смене голоса
    LaunchedEffect(isDialogOpen) {
        if (!isDialogOpen || currentVoice == null) return@LaunchedEffect
        snapshotFlow { voicesFiltered.toList() }
            .debounce(100)
            .collectLatest { filtered ->
                val index = filtered.indexOfFirst { it.id == currentVoice.id }
                if (index < 0) return@collectLatest

                // stickyHeader занимает индекс 0, голоса начинаются с индекса 1
                val targetIndex = index + 1

                // Первый скролл — чтобы элемент попал в viewport и был laid out
                listState.scrollToItem(index = targetIndex)

                // Берём реальную высоту stickyHeader из layoutInfo (он index=0)
                // и делаем отступ чтобы элемент был виден под шапкой, а не за ней
                val headerHeight = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == 0 }
                    ?.size ?: 0

                if (headerHeight > 0) {
                    listState.scrollToItem(
                        index = targetIndex,
                        scrollOffset = -headerHeight
                    )
                }
            }
    }

    if (isDialogOpen) Dialog(
        onDismissRequest = { setDialogOpen(false) }
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .shadow(10.dp, MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
        ) {
            stickyHeader {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column {
                        MyOutlinedTextField(
                            value = inputTextFilter.value,
                            onValueChange = { inputTextFilter.value = it },
                            placeHolderText = stringResource(R.string.search_voice_by_language),
                            modifier = Modifier
                                .padding(12.dp)
                                .focusRequester(inputFocusRequester)
                        )
                        HorizontalDivider(Modifier.padding(top = 0.dp))
                    }
                }
                LaunchedEffect(Unit) {
                    delay(300)
                    inputFocusRequester.requestFocus()
                }
            }

            if (voicesFiltered.isEmpty()) item {
                Text(
                    text = stringResource(R.string.no_matches),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }

            items(voicesFiltered) { voice ->
                val selected by remember(voice.id, currentVoice?.id) {
                    derivedStateOf { voice.id == currentVoice?.id }
                }
                Row(
                    modifier = Modifier
                        .heightIn(min = 54.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable(enabled = !selected) { setVoice(voice.id) }
                        .padding(horizontal = 16.dp)
                        .padding(4.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = voice.language,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.widthIn(min = 84.dp)
                    )
                    Row {
                        for (star in 0..4) {
                            val yay = voice.quality > star * 100
                            Icon(
                                imageVector = if (yay) Icons.Filled.StarRate else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (yay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.wrapContentHeight()
                    ) {
                        Text(
                            text = voice.id,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 10.sp,
                        )
                        if (voice.needsInternet) {
                            Text(
                                text = stringResource(R.string.needs_internet),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DropdownCustomSavedVoices(
    expanded: MutableState<Boolean>,
    list: List<VoicePredefineState>,
    currentVoice: VoiceData?,
    currentVoiceSpeed: Float,
    currentVoicePitch: Float,
    onPredefinedSelected: (VoicePredefineState) -> Unit,
    setCustomSavedVoices: (List<VoicePredefineState>) -> Unit,
) {
    var expandedAddNextEntry by rememberMutableStateOf(false)
    DropdownMenu(
        expanded = expanded.value,
        onDismissRequest = { expanded.value = !expanded.value },
    ) {
        SlimListItem(
            headlineContent = {
                Text(text = stringResource(R.string.save_current_voice))
            },
            leadingContent = {
                Icon(
                    Icons.Outlined.Save,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            modifier = Modifier.clickable { expandedAddNextEntry = true }
        )
        HorizontalDivider()
        if (list.isEmpty()) {
            Text(text = stringResource(R.string.no_voices_saved), Modifier.padding(16.dp))
        }
        list.forEachIndexed { index, predefinedVoice ->
            var deleteEntryExpand by rememberMutableStateOf(false)
            SlimListItem(
                headlineContent = {
                    Text(text = predefinedVoice.savedName)
                },
                modifier = Modifier.combinedClickable(
                    enabled = true,
                    onClick = { onPredefinedSelected(predefinedVoice) },
                    onLongClick = { deleteEntryExpand = true },
                )
            )
            if (deleteEntryExpand) AlertDialog(
                onDismissRequest = { deleteEntryExpand = false },
                title = { Text(text = stringResource(R.string.delete_voice)) },
                text = {
                    Text(
                        text = predefinedVoice.savedName,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    FilledTonalButton(onClick = {
                        deleteEntryExpand = false
                        setCustomSavedVoices(
                            list.toMutableList().also { it.removeAt(index) }
                        )
                    }) {
                        Text(text = stringResource(id = R.string.delete))
                    }
                },
                dismissButton = {
                    Button(onClick = { deleteEntryExpand = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }
    }

    if (expandedAddNextEntry) {
        var name by rememberMutableStateOf(value = "")
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(300)
            focusRequester.requestFocus()
        }
        AlertDialog(
            tonalElevation = 24.dp,
            onDismissRequest = { expandedAddNextEntry = false },
            title = { Text(text = stringResource(R.string.save_current_voice_parameters)) },
            text = {
                MyOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeHolderText = stringResource(R.string.name),
                    modifier = Modifier.focusRequester(focusRequester)
                )
            },
            confirmButton = {
                FilledTonalButton(onClick = onClick@{
                    val voice = currentVoice ?: return@onClick
                    val state = VoicePredefineState(
                        savedName = name,
                        voiceId = voice.id,
                        speed = currentVoiceSpeed,
                        pitch = currentVoicePitch
                    )
                    setCustomSavedVoices(list + state)
                    expandedAddNextEntry = false
                }) {
                    Text(text = stringResource(R.string.save_voice))
                }
            },
            dismissButton = {
                Button(onClick = { expandedAddNextEntry = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}


@Preview(group = "dialog")
@Composable
private fun VoiceSelectorDialogContentPreview() {
    InternalTheme {
        VoiceSelectorDialog(
            availableVoices = (0..7).map {
                VoiceData(
                    id = "$it",
                    language = "lang${it / 2}",
                    needsInternet = (it % 2) == 0,
                    quality = (it * 100) % 501,
                    enginePackage = "",
                )
            },
            setVoice = {},
            inputTextFilter = remember { mutableStateOf("hello") },
            currentVoice = VoiceData(
                id = "2",
                language = "",
                needsInternet = false,
                quality = 100,
                enginePackage = "",
            ),
            setDialogOpen = {},
            isDialogOpen = true
        )
    }
}