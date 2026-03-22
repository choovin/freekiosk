package com.freekiosk

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.facebook.react.bridge.*
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class QrScannerModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val NAME = "QrScannerModule"
        private const val TIMEOUT_MS = 30000L
    }

    private var cameraExecutor: ExecutorService? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var isScanning = AtomicBoolean(false)
    private var scanResolver: PromiseResolver? = null
    private var timeoutHandler: android.os.Handler? = null
    private var timeoutRunnable: Runnable? = null

    private data class PromiseResolver(val promise: Promise, val result: WritableMap)

    override fun getName(): String = NAME

    @ReactMethod
    fun scanQr(promise: Promise) {
        // Check if already scanning
        if (isScanning.get()) {
            promise.reject("ALREADY_SCANNING", "A scan is already in progress")
            return
        }

        // Check camera permission
        val permission = ContextCompat.checkSelfPermission(
            reactContext,
            Manifest.permission.CAMERA
        )
        if (permission != PackageManager.PERMISSION_GRANTED) {
            promise.reject("PERMISSION_DENIED", "Camera permission is required for QR scanning")
            return
        }

        isScanning.set(true)

        // Get the result holder ready before camera setup
        val result = Arguments.createMap()
        val resolver = PromiseResolver(promise, result)
        scanResolver = resolver

        // Set up timeout
        timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (isScanning.get()) {
                stopCamera()
                isScanning.set(false)
                promise.reject("TIMEOUT", "QR scan timed out after 30 seconds")
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, TIMEOUT_MS)

        // Start camera
        val cameraProviderFuture = ProcessCameraProvider.getInstance(reactContext)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val activity = reactApplicationContext.currentActivity
                if (activity == null) {
                    rejectScan("ACTIVITY_NOT_AVAILABLE", "Activity is not available")
                    return@addListener
                }

                val lifecycleOwner = activity as? LifecycleOwner
                if (lifecycleOwner == null) {
                    rejectScan("LIFECYCLE_ERROR", "Activity does not implement LifecycleOwner")
                    return@addListener
                }

                // Set up ImageAnalysis for QR scanning
                imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor ?: run {
                            cameraExecutor = Executors.newSingleThreadExecutor()
                            cameraExecutor!!
                        }) { imageProxy ->
                            processImage(imageProxy)
                        }
                    }

                // Unbind any existing use cases
                cameraProvider?.unbindAll()

                // Bind the use case to camera
                val camera = cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    imageAnalysis
                )

                if (camera == null) {
                    rejectScan("CAMERA_ERROR", "Failed to bind camera")
                }

                android.util.Log.d(NAME, "Camera started for QR scanning")

            } catch (e: Exception) {
                android.util.Log.e(NAME, "Camera initialization failed: ${e.message}")
                rejectScan("CAMERA_ERROR", "Failed to initialize camera: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(reactContext))
    }

    private fun processImage(imageProxy: ImageProxy) {
        if (!isScanning.get()) {
            imageProxy.close()
            return
        }

        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val source = PlanarYUVLuminanceSource(
                data,
                imageProxy.width,
                imageProxy.height,
                0, 0,
                imageProxy.width,
                imageProxy.height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader().apply {
                setHints(mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true
                ))
            }

            try {
                val result = reader.decode(binaryBitmap)
                android.util.Log.d(NAME, "QR code found: ${result.text}, format: ${result.barcodeFormat}")

                // Cancel timeout
                timeoutHandler?.removeCallbacks(timeoutRunnable!!)

                // Stop camera
                stopCamera()

                // Resolve promise
                scanResolver?.let { resolver ->
                    resolver.result.putString("data", result.text)
                    resolver.result.putString("format", result.barcodeFormat.toString())
                    resolver.promise.resolve(resolver.result)
                }
                isScanning.set(false)

            } catch (e: NotFoundException) {
                // No QR code found in this frame, continue scanning
                android.util.Log.v(NAME, "No QR code found in frame, continuing scan...")
            } catch (e: Exception) {
                android.util.Log.e(NAME, "Error decoding QR: ${e.message}")
                // Continue scanning on other errors
            }

        } catch (e: Exception) {
            android.util.Log.e(NAME, "Error processing image: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            android.util.Log.d(NAME, "Camera stopped")
        } catch (e: Exception) {
            android.util.Log.e(NAME, "Error stopping camera: ${e.message}")
        }
    }

    private fun rejectScan(code: String, message: String) {
        stopCamera()
        isScanning.set(false)
        timeoutHandler?.removeCallbacks(timeoutRunnable ?: Runnable {})
        scanResolver?.promise?.reject(code, message)
        scanResolver = null
    }

    @ReactMethod
    fun stopScanning(promise: Promise) {
        timeoutHandler?.removeCallbacks(timeoutRunnable ?: Runnable {})
        stopCamera()
        isScanning.set(false)
        scanResolver?.promise?.reject("STOPPED", "Scan was stopped by user")
        scanResolver = null
        promise.resolve(true)
    }

    override fun onCatalystInstanceDestroy() {
        super.onCatalystInstanceDestroy()
        timeoutHandler?.removeCallbacks(timeoutRunnable ?: Runnable {})
        stopCamera()
        cameraExecutor?.shutdown()
        try {
            cameraExecutor?.awaitTermination(1, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            // Ignore
        }
        cameraExecutor = null
        isScanning.set(false)
        scanResolver = null
    }
}
