package fr.exolis.opensource.videoconversation;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;

import com.twilio.video.CameraCapturer;
import com.twilio.video.Camera2Capturer;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.twilio.video.VideoCapturer;

import java.util.HashMap;
import java.util.Map;
import tvi.webrtc.Camera1Enumerator;
import tvi.webrtc.Camera2Enumerator;
import tvi.webrtc.CapturerObserver;
import tvi.webrtc.SurfaceTextureHelper;

public class CameraCapturerCompat implements VideoCapturer {

	private final CameraCapturer camera1Capturer;
	private final Camera2Capturer camera2Capturer;
	private final VideoCapturer activeCapturer;
	private final Map<Source, String> camera1IdMap = new HashMap<>();
    private final Map<String, Source> camera1SourceMap = new HashMap<>();
    private final Map<Source, String> camera2IdMap = new HashMap<>();
    private final Map<String, Source> camera2SourceMap = new HashMap<>();
	private CameraManager cameraManager;

	public enum Source {
        FRONT_CAMERA,
        BACK_CAMERA
    }

	public CameraCapturerCompat(Context context, Source cameraSource) {
		if (Camera2Capturer.isSupported(context) && isLollipopApiSupported()) {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
			setCamera2Maps(context);
            camera2Capturer = new Camera2Capturer(context, camera2IdMap.get(cameraSource));
			activeCapturer = camera2Capturer;
			camera1Capturer = null;
        } else {
			setCamera1Maps();
            camera1Capturer = new CameraCapturer(context, camera1IdMap.get(cameraSource));
			activeCapturer = camera1Capturer;
			camera2Capturer = null;
        }
	}

	private boolean isLollipopApiSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private void setCamera2Maps(Context context) {
        Camera2Enumerator camera2Enumerator = new Camera2Enumerator(context);
        for (String cameraId : camera2Enumerator.getDeviceNames()) {
            if (isCameraIdSupported(cameraId)) {
                if (camera2Enumerator.isFrontFacing(cameraId)) {
                    camera2IdMap.put(Source.FRONT_CAMERA, cameraId);
                    camera2SourceMap.put(cameraId, Source.FRONT_CAMERA);
                }
                if (camera2Enumerator.isBackFacing(cameraId)) {
                    camera2IdMap.put(Source.BACK_CAMERA, cameraId);
                    camera2SourceMap.put(cameraId, Source.BACK_CAMERA);
                }
            }
        }
    }

    private void setCamera1Maps() {
        Camera1Enumerator camera1Enumerator = new Camera1Enumerator();
        for (String deviceName : camera1Enumerator.getDeviceNames()) {
            if (camera1Enumerator.isFrontFacing(deviceName)) {
                camera1IdMap.put(Source.FRONT_CAMERA, deviceName);
                camera1SourceMap.put(deviceName, Source.FRONT_CAMERA);
            }
            if (camera1Enumerator.isBackFacing(deviceName)) {
                camera1IdMap.put(Source.BACK_CAMERA, deviceName);
                camera1SourceMap.put(deviceName, Source.BACK_CAMERA);
            }
        }
    }

	public Source getCameraSource() {
		if (usingCamera1()) {
            return camera1SourceMap.get(camera1Capturer.getCameraId());
        } else {
            return camera2SourceMap.get(camera2Capturer.getCameraId());
        }
	}

	public void switchCamera() {
		Source cameraSource = getCameraSource();
        Map<Source, String> idMap = usingCamera1() ? camera1IdMap : camera2IdMap;
        String newCameraId =
                cameraSource == Source.FRONT_CAMERA
                        ? idMap.get(Source.BACK_CAMERA)
                        : idMap.get(Source.FRONT_CAMERA);

        if (usingCamera1()) {
            camera1Capturer.switchCamera(newCameraId);
        } else {
            camera2Capturer.switchCamera(newCameraId);
        }
	}

	private boolean usingCamera1() {
        return camera1Capturer != null;
    }

	@Override
    public void initialize(
            SurfaceTextureHelper surfaceTextureHelper,
            Context context,
            CapturerObserver capturerObserver) {
        activeCapturer.initialize(surfaceTextureHelper, context, capturerObserver);
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        activeCapturer.startCapture(width, height, framerate);
    }

    @Override
    public void stopCapture() throws InterruptedException {
        activeCapturer.stopCapture();
    }

    @Override
    public boolean isScreencast() {
        return activeCapturer.isScreencast();
    }

    @Override
    public void dispose() {
        activeCapturer.dispose();
    }

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	private boolean isCameraIdSupported(String cameraId) {
        boolean isMonoChromeSupported = false;
        boolean isPrivateImageFormatSupported = false;
        CameraCharacteristics cameraCharacteristics;
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

		final StreamConfigurationMap streamMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (streamMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isPrivateImageFormatSupported = streamMap.isOutputSupportedFor(ImageFormat.PRIVATE);
        }

		/*To enable this part, build should be done with cordova-android equal or higher than 9.0.0*/
        /*Integer colorFilterArrangement =
                cameraCharacteristics.get(
                        CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && colorFilterArrangement != null) {
            isMonoChromeSupported =
                    colorFilterArrangement
                            == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO
                            || colorFilterArrangement
                            == CameraMetadata.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_NIR;
        }*/

        return isPrivateImageFormatSupported && !isMonoChromeSupported;
    }
}
