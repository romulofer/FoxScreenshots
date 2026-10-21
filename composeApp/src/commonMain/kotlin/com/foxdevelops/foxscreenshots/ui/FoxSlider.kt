package com.foxdevelops.foxscreenshots.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** The slider the app uses everywhere: a thin track and a round thumb, instead of the chunky default. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        modifier = modifier,
        interactionSource = interaction,
        thumb = { SliderDefaults.Thumb(interactionSource = interaction, thumbSize = DpSize(18.dp, 18.dp)) },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                modifier = Modifier.height(6.dp),
                thumbTrackGapSize = 0.dp,
                trackInsideCornerSize = 3.dp,
                drawStopIndicator = null,
            )
        },
    )
}
