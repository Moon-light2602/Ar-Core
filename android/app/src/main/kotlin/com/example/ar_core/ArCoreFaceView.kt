package com.example.flutter_arcore

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.example.flutter_arcore.utils.ArCoreUtils
import com.google.ar.core.AugmentedFace
import com.google.ar.core.Config
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.Scene
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.rendering.Texture
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.AugmentedFaceNode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

class ArCoreFaceView(private val activity: Activity, context: Context, id: Int):
    PlatformView, MethodChannel.MethodCallHandler {

    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks

    private var arSceneView: ArSceneView? = null
    private val RC_PERMISSIONS = 0x123
    private var installRequested: Boolean = false

    private val faceNodeMap = HashMap<AugmentedFace, AugmentedFaceNode>()
    private var faceSceneUpdateListener: Scene.OnUpdateListener

    private var objectDetector: ObjectDetector? = null
    private val drawView: DrawView
    private var arFragment: ArFragment? = null


    init {
        arFragment = ArFragment()
//        methodChannel.setMethodCallHandler(this)
        if (ArCoreUtils.checkIsSupportedDeviceOrFinish(activity)) {
            arSceneView = ArSceneView(context)
            ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            setupLifeCycle(context)
            setupObjectDetector()
            addDrawViewToLayout()
        }

        val scene = arSceneView?.scene

        faceSceneUpdateListener = Scene.OnUpdateListener { frameTime ->
            run {
                val faceList = arSceneView?.session?.getAllTrackables(AugmentedFace::class.java)

                faceList?.let {
                    for (face in faceList) {
                        Log.d("ArCoreFaceView", "face: ${face.meshVertices}")
                        if (!faceNodeMap.containsKey(face)) {
                            val faceNode = AugmentedFaceNode(face)
                            faceNode.setParent(scene)

                            ModelRenderable.builder()
                                .setSource(activity, R.raw.test5)
                                .build()
                                .thenAccept { modelRenderable ->
                                    faceNode.faceRegionsRenderable = modelRenderable
                                    modelRenderable.isShadowCaster = false
                                    modelRenderable.isShadowReceiver = false
                                }

                            //give the face a little blush
                            Texture.builder()
                                .setSource(activity, R.drawable.blush_texture)
                                .build()
                                .thenAccept { texture ->
                                    faceNode.faceMeshTexture = texture
                                }
                            faceNodeMap[face] = faceNode
                        }
                    }
                }

            }
        }

        arSceneView?.cameraStreamRenderPriority = Renderable.RENDER_PRIORITY_FIRST
        arSceneView?.scene?.addOnUpdateListener(faceSceneUpdateListener)
        drawView = DrawView(activity, null)
        addDrawViewToLayout()
//        loadMesh()
    }

    private fun addDrawViewToLayout() {
        val layout = LinearLayout(activity)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(arFragment?.view)
        layout.addView(drawView)
        activity.setContentView(layout)    }

    override fun getView(): View {
        return arSceneView as View
    }

    override fun dispose() {}

    private fun onResume() {
        if (arSceneView == null) {
            return
        }

        if (arSceneView?.session == null) {

            // request camera permission if not already requested
            if (!ArCoreUtils.hasCameraPermission(activity)) {
                ArCoreUtils.requestCameraPermission(activity, RC_PERMISSIONS)
            }

            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = ArCoreUtils.createArSession(activity, installRequested, true)
                if (session == null) {
                    installRequested = false
                    return
                } else {
                    val config = Config(session)
                    config.augmentedFaceMode = Config.AugmentedFaceMode.MESH3D
                    config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    session.configure(config)
                    arSceneView?.setupSession(session)
                }
            } catch (e: UnavailableException) {
                ArCoreUtils.handleSessionException(activity, e)
            }
        }

        try {
            arSceneView?.resume()
        } catch (ex: CameraNotAvailableException) {
            ArCoreUtils.displayError(activity, "Unable to get camera", ex)
            activity.finish()
            return
        }
    }

    private fun setupObjectDetector() {
        // Configure ML Kit Object Detector
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()

        objectDetector = ObjectDetection.getClient(options)
    }

    private fun captureBitmapFromARSceneView(): Bitmap? {
        if (arSceneView == null) {
            return null
        }

        val arFrame = arSceneView!!.arFrame ?: return null

        return try {
            // Acquire the camera image from the ARFrame
            val cameraImage = arFrame.acquireCameraImage()

            // Convert the camera image to a Bitmap
            val bitmap = ArCoreUtils.convertArImageToBitmap(cameraImage)

            // Release the acquired camera image
            cameraImage.close()

            bitmap
        } catch (e: NotYetAvailableException) {
            // Handle exception
            null
        }
    }

    private fun processImageWithMLKit(bitmap: Bitmap?) {
        if (bitmap == null) {
            return
        }

        // Create an ML Kit InputImage from the Bitmap
        val image = InputImage.fromBitmap(bitmap, 0)

        // Use ML Kit Object Detection to detect objects
        objectDetector?.process(image)
            ?.addOnSuccessListener { detectedObjects ->
                // Process the detected objects
                processDetectedObjects(detectedObjects)
            }
            ?.addOnFailureListener { e ->
                // Handle failure
            }
    }

    private fun processDetectedObjects(detectedObjects: List<DetectedObject>) {
        // Process the list of detected objects
        for (detectedObject in detectedObjects) {
            val boundingBox = detectedObject.boundingBox
            drawView.setData(boundingBox, "Object", arFragment?.arSceneView?.width ?: 0, arFragment?.arSceneView?.height ?: 0)
        }
    }
    private fun drawBoundingBoxOnImage(boundingBox: Rect) {
        // Implement the drawing logic to show bounding box on the captured image
        // For simplicity, let's assume you have a custom view named drawView
        // where you want to draw the bounding box.
        drawView.drawBoundingBox(boundingBox)
    }

    // ... Other existing code ...

    // Assuming you call this method to initiate the image capture and object detection
    private fun captureAndProcessImage() {
        val capturedBitmap = captureBitmapFromARSceneView()
        processImageWithMLKit(capturedBitmap)
    }
    private fun onPause() {
        if (arSceneView != null) {
            arSceneView?.pause()
        }
    }

    private fun onDestroy() {
        arSceneView?.scene?.removeOnUpdateListener(faceSceneUpdateListener)
        if (arSceneView != null) {
            arSceneView?.destroy()
            arSceneView = null
        }
    }

    fun removeNode(node: Node) {
        arSceneView?.scene?.removeChild(node)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        if (call.method == "captureAndProcessImage") {
            // Flutter method to capture image and process with ML Kit
            captureAndProcessImage()
            result.success(null)
        } else {
            result.notImplemented()
        }
    }

    private fun setupLifeCycle(context: Context) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.d("ArCoreFaceView", "onActivityCreated")
            }

            override fun onActivityStarted(activity: Activity) {
                Log.d("ArCoreFaceView", "onActivityStarted")
            }

            override fun onActivityResumed(activity: Activity) {
                onResume()
                Log.d("ArCoreFaceView", "onActivityResumed")
            }

            override fun onActivityPaused(activity: Activity) {
                onPause()
                Log.d("ArCoreFaceView", "onActivityPaused")
            }

            override fun onActivityStopped(activity: Activity) {
                onPause()
                Log.d("ArCoreFaceView", "onActivityStopped")
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
//                onDestroy()
            }
        }

        activity.application
            .registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

}


// lấy ảnh từ arSenceView -> (bytearray or bitmap)