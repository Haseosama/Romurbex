package com.romurbex.app.ui.detail

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/** Visionneuse plein écran : défilement entre les photos + zoom au double-tap sur chacune. */
@Composable
fun PhotoViewerDialog(photos: List<Uri>, startIndex: Int, onDismiss: () -> Unit) {
    if (photos.isEmpty()) return
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, photos.lastIndex)) { photos.size }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(uri = photos[page])
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White)
            }
            if (photos.size > 1) {
                Text(
                    "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
                )
            }
        }
    }
}

/**
 * Zoom au double-tap plutôt qu'au pincement : un détecteur de pincement continu capture aussi
 * les glissements à un doigt, ce qui empêchait le [HorizontalPager] parent de recevoir le swipe
 * pour changer de photo. Le double-tap est un geste discret qui ne rentre pas en conflit avec
 * le swipe ; une fois zoomée, l'image se déplace au glissement (un seul doigt suffit, plus
 * besoin du second doigt du pincement).
 */
@Composable
private fun ZoomableImage(uri: Uri) {
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }

    val panModifier = if (scale > 1f) {
        Modifier.pointerInput(uri) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offset += dragAmount
            }
        }
    } else {
        Modifier
    }

    AsyncImage(
        model = uri,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uri) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                )
            }
            .then(panModifier)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y,
            ),
    )
}
