package com.github.se.eventradar.ui.qrCode

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.github.se.eventradar.viewmodel.qrCode.QrCodeAnalyser
import java.util.concurrent.Executors

@Composable
fun QrCodeScanner(analyser: QrCodeAnalyser) {

  val context = LocalContext.current

  var hasCameraPermission by remember {
    mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
  }
  val launcher =
      rememberLauncherForActivityResult(
          contract = ActivityResultContracts.RequestPermission(),
          onResult = { granted -> hasCameraPermission = granted })

  // only ask permission once
  LaunchedEffect(key1 = true) {
    if (!hasCameraPermission) {
      launcher.launch(Manifest.permission.CAMERA)
    }
  }
  // actual camera view
  Column(modifier = Modifier.fillMaxSize()) {
    if (hasCameraPermission) {
      Spacer(modifier = Modifier.height(80.dp))
      AndroidView(
          factory = { context ->
            val cameraExecutor = Executors.newSingleThreadExecutor()
            val previewView = PreviewView(context)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                  val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                  val preview =
                      Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                      }
                  val imageAnalyzer =
                      ImageAnalysis.Builder()
                          .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                          .build()
                          .also { it.setAnalyzer(cameraExecutor, analyser) }
                  val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                  try {
                    // Unbind use cases before rebinding
                    cameraProvider.unbindAll()

                    // Bind use cases to camera
                    cameraProvider.bindToLifecycle(
                        context as ComponentActivity, cameraSelector, preview, imageAnalyzer)
                  } catch (exc: Exception) {
                    Log.e("DEBUG", "Use case binding failed", exc)
                  }
                },
                ContextCompat.getMainExecutor(context))
            previewView
          },
          modifier = Modifier.weight(1.5f).aspectRatio(1f).padding(horizontal = 32.dp))
    }
  }
}
