package com.scenedeck.android.feature.connections

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.guava.await

/**
 * Full-screen QR pairing: CameraX preview + ML Kit barcode analysis. Emits the first valid
 * `obsws://` payload via [onDetected].
 */
@Composable
fun QrScannerScreen(
    onDetected: (ObswsTarget) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(onDetected = onDetected, onClose = onClose)
        } else {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.camera_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text(stringResource(R.string.grant_permission))
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_back)) }
            }
        }
    }
}

@Composable
private fun CameraPreview(onDetected: (ObswsTarget) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val previewView = remember { PreviewView(context) }
    val reported = remember { AtomicBoolean(false) }
    val currentOnDetected by rememberUpdatedState(onDetected)
    val disposed = remember { AtomicBoolean(false) }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            analyzerExecutor.shutdown()
            barcodeScanner.close()
        }
    }

    LaunchedEffect(previewView, lifecycleOwner) {
        val cameraProvider =
            try {
                ProcessCameraProvider.getInstance(context).await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                return@LaunchedEffect
            }
        val preview =
            Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
        val analysis =
            ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

        analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
            analyzePairingFrame(imageProxy, barcodeScanner, reported, disposed) {
                currentOnDetected(it)
            }
        }

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            awaitCancellation()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // Camera unavailable (busy / no back camera) — user can still go back.
        } finally {
            analysis.clearAnalyzer()
            cameraProvider.unbind(preview, analysis)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )
        Column(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(24.dp)) {
            Text(
                text = "Point at an obsws:// QR code",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
        ) {
            Text("Cancel")
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzePairingFrame(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    reported: AtomicBoolean,
    disposed: AtomicBoolean,
    onDetected: (ObswsTarget) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (reported.get() || disposed.get() || mediaImage == null) {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    try {
        scanner
            .process(image)
            .addOnSuccessListener { barcodes ->
                val payload = barcodes.firstNotNullOfOrNull { barcode ->
                    barcode.rawValue?.let(::parseObswsUri)
                }
                if (payload != null && !disposed.get() && reported.compareAndSet(false, true))
                    onDetected(payload)
            }
            .addOnCompleteListener { imageProxy.close() }
    } catch (_: IllegalStateException) {
        // Disposal can close ML Kit between the disposed check and process().
        imageProxy.close()
    }
}
