package com.qiniu.pili.droid.rtcstreaming.demo.activity.streaming;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.faceunity.wrapper.faceunity;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.qiniu.pili.droid.rtcstreaming.RTCAudioInfo;
import com.qiniu.pili.droid.rtcstreaming.RTCAudioLevelCallback;
import com.qiniu.pili.droid.rtcstreaming.RTCConferenceOptions;
import com.qiniu.pili.droid.rtcstreaming.RTCConferenceState;
import com.qiniu.pili.droid.rtcstreaming.RTCConferenceStateChangedListener;
import com.qiniu.pili.droid.rtcstreaming.RTCFrameCapturedCallback;
import com.qiniu.pili.droid.rtcstreaming.RTCFrameMixedCallback;
import com.qiniu.pili.droid.rtcstreaming.RTCMediaStreamingManager;
import com.qiniu.pili.droid.rtcstreaming.RTCRemoteWindowEventListener;
import com.qiniu.pili.droid.rtcstreaming.RTCStartConferenceCallback;
import com.qiniu.pili.droid.rtcstreaming.RTCStreamStats;
import com.qiniu.pili.droid.rtcstreaming.RTCUserEventListener;
import com.qiniu.pili.droid.rtcstreaming.RTCVideoWindow;
import com.qiniu.pili.droid.rtcstreaming.demo.R;
import com.qiniu.pili.droid.rtcstreaming.demo.core.StreamUtils;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.EffectAndFilterSelectAdapter;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.FaceunityControlView;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.MiscUtil;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.authpack;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.gles.FullFrameRect;
import com.qiniu.pili.droid.rtcstreaming.demo.faceunity.gles.Texture2dProgram;
import com.qiniu.pili.droid.streaming.AVCodecType;
import com.qiniu.pili.droid.streaming.CameraStreamingSetting;
import com.qiniu.pili.droid.streaming.StreamStatusCallback;
import com.qiniu.pili.droid.streaming.StreamingPreviewCallback;
import com.qiniu.pili.droid.streaming.StreamingProfile;
import com.qiniu.pili.droid.streaming.StreamingSessionListener;
import com.qiniu.pili.droid.streaming.StreamingState;
import com.qiniu.pili.droid.streaming.StreamingStateChangedListener;
import com.qiniu.pili.droid.streaming.SurfaceTextureCallback;
import com.qiniu.pili.droid.streaming.WatermarkSetting;
import com.qiniu.pili.droid.streaming.widget.AspectFrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示使用 SDK 内部的 Video/Audio 采集，实现连麦 & 推流
 */
public class RTCStreamingActivity extends AppCompatActivity {

    private static final String TAG = "RTCStreamingActivity";
    private static final int MESSAGE_ID_RECONNECTING = 0x01;
    private static final int MINBITRATE = 10 * 1024;
    private static final int MAXBITRATE = 10000 * 1024;

    private TextView mStatusTextView;
    private TextView mStatTextView;
    private Button mControlButton;
    private CheckBox mMuteCheckBox;
    private CheckBox mConferenceCheckBox;
    private FloatingActionButton mMuteSpeakerButton;
    private FloatingActionButton mBitrateAdjustButton;
    private FloatingActionButton mTogglePlaybackButton;

    private Toast mToast = null;
    private ProgressDialog mProgressDialog;

    private RTCMediaStreamingManager mRTCStreamingManager;

    private StreamingProfile mStreamingProfile;

    private boolean mIsActivityPaused = true;
    private boolean mIsPublishStreamStarted = false;
    private boolean mIsConferenceStarted = false;
    private boolean mIsInReadyState = false;
    private int mCurrentCamFacingIndex;

    private GLSurfaceView mCameraPreviewFrameView;
    private RTCVideoWindow mRTCVideoWindowA;
    private RTCVideoWindow mRTCVideoWindowB;

    private int mRole;
    private String mRoomName;

    private boolean mIsPreviewMirror = false;
    private boolean mIsEncodingMirror = false;
    private boolean mIsSpeakerMuted = false;

    private boolean mIsStatsEnabled = false;

    private String mBitrateControl;

    private boolean mIsPlayingback = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture_streaming);

        /**
         * Step 1: init sdk, you can also move this to Application.onCreate
         */
        RTCMediaStreamingManager.init(getApplicationContext());

        /**
         * Step 2: find & init views
         */
        AspectFrameLayout afl = (AspectFrameLayout) findViewById(R.id.cameraPreview_afl);
        afl.setShowMode(AspectFrameLayout.SHOW_MODE.FULL);
        mCameraPreviewFrameView = (GLSurfaceView) findViewById(R.id.cameraPreview_surfaceView);

        mRole = getIntent().getIntExtra("role", StreamUtils.RTC_ROLE_VICE_ANCHOR);
        mRoomName = getIntent().getStringExtra("roomName");
        boolean isSwCodec = getIntent().getBooleanExtra("swcodec", true);
        boolean isLandscape = getIntent().getBooleanExtra("orientation", false);
        setRequestedOrientation(isLandscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        boolean isBeautyEnabled = getIntent().getBooleanExtra("beauty", false);
        boolean isWaterMarkEnabled = getIntent().getBooleanExtra("watermark", false);
        boolean isDebugModeEnabled = getIntent().getBooleanExtra("debugMode", false);
        boolean isCustomSettingEnabled = getIntent().getBooleanExtra("customSetting", false);
        boolean audioLevelCallback = getIntent().getBooleanExtra("audioLevelCallback", false);
        mBitrateControl = getIntent().getStringExtra("bitrateControl");
        mIsStatsEnabled = getIntent().getBooleanExtra("enableStats", false);

        mControlButton = (Button) findViewById(R.id.ControlButton);
        mStatusTextView = (TextView) findViewById(R.id.StatusTextView);
        mStatTextView = (TextView) findViewById(R.id.StatTextView);
        mMuteCheckBox = (CheckBox) findViewById(R.id.MuteCheckBox);
        mMuteCheckBox.setOnClickListener(mMuteButtonClickListener);
        mConferenceCheckBox = (CheckBox) findViewById(R.id.ConferenceCheckBox);
        mConferenceCheckBox.setOnClickListener(mConferenceButtonClickListener);
        mMuteSpeakerButton = (FloatingActionButton) findViewById(R.id.muteSpeaker);
        mBitrateAdjustButton = (FloatingActionButton) findViewById(R.id.adjust_bitrate);
        mTogglePlaybackButton = (FloatingActionButton) findViewById(R.id.toggle_playback);

        if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
            mConferenceCheckBox.setVisibility(View.VISIBLE);
        } else {
            mBitrateAdjustButton.setVisibility(View.GONE);
            mTogglePlaybackButton.setVisibility(View.GONE);
        }

        CameraStreamingSetting.CAMERA_FACING_ID facingId = chooseCameraFacingId();
        mCurrentCamFacingIndex = facingId.ordinal();

        /**
         * Step 3: config camera settings
         */
        CameraStreamingSetting cameraStreamingSetting = new CameraStreamingSetting();
        cameraStreamingSetting.setCameraFacingId(facingId)
                .setContinuousFocusModeEnabled(true)
                .setRecordingHint(false)
                .setResetTouchFocusDelayInMs(3000)
                .setFocusMode(CameraStreamingSetting.FOCUS_MODE_CONTINUOUS_PICTURE)
                .setCameraPrvSizeLevel(CameraStreamingSetting.PREVIEW_SIZE_LEVEL.MEDIUM)
                .setCameraPrvSizeRatio(CameraStreamingSetting.PREVIEW_SIZE_RATIO.RATIO_16_9);

        if (isBeautyEnabled) {
            cameraStreamingSetting.setBuiltInFaceBeautyEnabled(true); // Using sdk built in face beauty algorithm
            cameraStreamingSetting.setFaceBeautySetting(new CameraStreamingSetting.FaceBeautySetting(0.8f, 0.8f, 0.6f)); // sdk built in face beauty settings
            cameraStreamingSetting.setVideoFilter(CameraStreamingSetting.VIDEO_FILTER_TYPE.VIDEO_FILTER_BEAUTY); // set the beauty on/off
        }

        /**
         * Step 4: create streaming manager and set listeners
         */
        AVCodecType codecType = isSwCodec ? AVCodecType.SW_VIDEO_WITH_SW_AUDIO_CODEC : AVCodecType.HW_VIDEO_YUV_AS_INPUT_WITH_HW_AUDIO_CODEC;
        mRTCStreamingManager = new RTCMediaStreamingManager(getApplicationContext(), afl, mCameraPreviewFrameView, codecType);
        mRTCStreamingManager.setConferenceStateListener(mRTCStreamingStateChangedListener);
        mRTCStreamingManager.setRemoteWindowEventListener(mRTCRemoteWindowEventListener);
        mRTCStreamingManager.setUserEventListener(mRTCUserEventListener);
        mRTCStreamingManager.setDebugLoggingEnabled(isDebugModeEnabled);

        /**
         * The audio level callback will cause 5% cpu occupation
         */
        if (audioLevelCallback) {
            mRTCStreamingManager.setAudioLevelCallback(mRTCAudioLevelCallback);
        }

        /**
         * Step 5: set conference options
         */
        RTCConferenceOptions options = new RTCConferenceOptions();
        if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
            // anchor should use a bigger size, must equals to `StreamProfile.setPreferredVideoEncodingSize` or `StreamProfile.setEncodingSizeLevel`
            // RATIO_16_9 & VIDEO_ENCODING_SIZE_HEIGHT_480 means the output size is 848 x 480
            options.setVideoEncodingSizeRatio(RTCConferenceOptions.VIDEO_ENCODING_SIZE_RATIO.RATIO_16_9);
            options.setVideoEncodingSizeLevel(RTCConferenceOptions.VIDEO_ENCODING_SIZE_HEIGHT_480);
            options.setVideoBitrateRange(300 * 1024, 800 * 1024);
            // 15 fps is enough
            options.setVideoEncodingFps(15);
        } else {
            // vice anchor can use a smaller size
            // RATIO_4_3 & VIDEO_ENCODING_SIZE_HEIGHT_240 means the output size is 320 x 240
            // 4:3 looks better in the mix frame
            options.setVideoEncodingSizeRatio(RTCConferenceOptions.VIDEO_ENCODING_SIZE_RATIO.RATIO_4_3);
            options.setVideoEncodingSizeLevel(RTCConferenceOptions.VIDEO_ENCODING_SIZE_HEIGHT_240);
            options.setVideoBitrateRange(300 * 1024, 800 * 1024);
            // 15 fps is enough
            options.setVideoEncodingFps(15);
        }
        options.setHWCodecEnabled(!isSwCodec);
        mRTCStreamingManager.setConferenceOptions(options);

        /**
         * Step 6: create the remote windows
         */
        RTCVideoWindow windowA = new RTCVideoWindow(findViewById(R.id.RemoteWindowA), (GLSurfaceView) findViewById(R.id.RemoteGLSurfaceViewA));
        RTCVideoWindow windowB = new RTCVideoWindow(findViewById(R.id.RemoteWindowB), (GLSurfaceView) findViewById(R.id.RemoteGLSurfaceViewB));

        /**
         * Step 7: configure the mix stream position and size (only anchor)
         */
        if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
            // set mix overlay params with absolute value
            // the w & h of remote window equals with or smaller than the vice anchor can reduce cpu consumption
            if (isLandscape) {
                windowA.setAbsolutetMixOverlayRect(options.getVideoEncodingWidth() - 320, 100, 320, 240);
                windowB.setAbsolutetMixOverlayRect(0, 100, 320, 240);
            } else {
                windowA.setAbsolutetMixOverlayRect(options.getVideoEncodingHeight() - 240, 100, 240, 320);
                windowB.setAbsolutetMixOverlayRect(options.getVideoEncodingHeight() - 240, 420, 240, 320);
            }

            // set mix overlay params with relative value
            // windowA.setRelativeMixOverlayRect(0.65f, 0.2f, 0.3f, 0.3f);
            // windowB.setRelativeMixOverlayRect(0.65f, 0.5f, 0.3f, 0.3f);
        }

        /**
         * Step 8: add the remote windows
         */
        mRTCStreamingManager.addRemoteWindow(windowA);
        mRTCStreamingManager.addRemoteWindow(windowB);

        mRTCVideoWindowA = windowA;
        mRTCVideoWindowB = windowB;

        /**
         * Step 9: do prepare, anchor should config streaming profile first
         */
        mRTCStreamingManager.setMixedFrameCallback(new RTCFrameMixedCallback() {
            @Override
            public void onVideoFrameMixed(byte[] data, int width, int height, int fmt, long timestamp) {
                Log.d(TAG, "Mixed video: " + data.toString() + "  Format: " + fmt);
            }

            @Override
            public void onAudioFrameMixed(byte[] pcm, long timestamp) {
                Log.d(TAG, "Mixed audio: " + pcm.toString());
            }
        });

        mRTCStreamingManager.setSurfaceTextureCallback(mSurfaceTextureCallback);
        mRTCStreamingManager.setStreamingPreviewCallback(mStreamingPreviewCallback);

        if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
            mRTCStreamingManager.setStreamStatusCallback(mStreamStatusCallback);
            mRTCStreamingManager.setStreamingStateListener(mStreamingStateChangedListener);
            mRTCStreamingManager.setStreamingSessionListener(mStreamingSessionListener);

            mStreamingProfile = new StreamingProfile();
            mStreamingProfile.setVideoQuality(StreamingProfile.VIDEO_QUALITY_MEDIUM2)
                    .setAudioQuality(StreamingProfile.AUDIO_QUALITY_MEDIUM1)
                    .setEncoderRCMode(StreamingProfile.EncoderRCModes.BITRATE_PRIORITY)
                    .setFpsControllerEnable(true)
                    .setSendingBufferProfile(new StreamingProfile.SendingBufferProfile(0.2f, 0.8f, 3.0f, 20 * 1000))
                    .setBitrateAdjustMode(
                            mBitrateControl.equals("auto") ? StreamingProfile.BitrateAdjustMode.Auto
                                    : (mBitrateControl.equals("manual") ? StreamingProfile.BitrateAdjustMode.Manual
                                    : StreamingProfile.BitrateAdjustMode.Disable));

            //Set AVProfile Manually, which will cover `setXXXQuality`
            if (isCustomSettingEnabled) {
                StreamingProfile.AudioProfile aProfile = new StreamingProfile.AudioProfile(44100, 96 * 1024);
                StreamingProfile.VideoProfile vProfile = new StreamingProfile.VideoProfile(15, 800 * 1024, 15 * 2);
                StreamingProfile.AVProfile avProfile = new StreamingProfile.AVProfile(vProfile, aProfile);
                mStreamingProfile.setAVProfile(avProfile);
            }

            if (isLandscape) {
                mStreamingProfile.setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.LAND);
                mStreamingProfile.setPreferredVideoEncodingSize(options.getVideoEncodingWidth(), options.getVideoEncodingHeight());
            } else {
                mStreamingProfile.setEncodingOrientation(StreamingProfile.ENCODING_ORIENTATION.PORT);
                mStreamingProfile.setPreferredVideoEncodingSize(options.getVideoEncodingHeight(), options.getVideoEncodingWidth());
            }

            WatermarkSetting watermarksetting = null;
            if (isWaterMarkEnabled) {
                watermarksetting = new WatermarkSetting(this);
                watermarksetting.setResourceId(R.drawable.qiniu_logo)
                        .setSize(WatermarkSetting.WATERMARK_SIZE.MEDIUM)
                        .setAlpha(100)
                        .setCustomPosition(0.5f, 0.5f);
            }
            mRTCStreamingManager.prepare(cameraStreamingSetting, null, watermarksetting, mStreamingProfile);
        } else {
            mControlButton.setText("开始连麦");
            mRTCStreamingManager.prepare(cameraStreamingSetting, null);
        }

        mProgressDialog = new ProgressDialog(this);


        FaceunityControlView faceunityControlView = (FaceunityControlView) findViewById(R.id.faceunity_control);
        faceunityControlView.setOnViewEventListener(eventListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsActivityPaused = false;
        /**
         * Step 10: You must start capture before conference or streaming
         * You will receive `Ready` state callback when capture started success
         */
        mRTCStreamingManager.startCapture();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsActivityPaused = true;
        /**
         * Step 11: You must stop capture, stop conference, stop streaming when activity paused
         */
        mRTCStreamingManager.stopCapture();
        stopConference();
        stopPublishStreaming();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        /**
         * Step 12: You must call destroy to release some resources when activity destroyed
         */
        mRTCStreamingManager.destroy();
        /**
         * Step 13: You can also move this to your MainActivity.onDestroy
         */
        RTCMediaStreamingManager.deinit();
    }

    public void onClickKickoutUserA(View v) {
        mRTCStreamingManager.kickoutUser(R.id.RemoteGLSurfaceViewA);
    }

    public void onClickKickoutUserB(View v) {
        mRTCStreamingManager.kickoutUser(R.id.RemoteGLSurfaceViewB);
    }

    public void onClickCaptureFrame(View v) {
        mRTCStreamingManager.captureFrame(new RTCFrameCapturedCallback() {
            @Override
            public void onFrameCaptureSuccess(Bitmap bitmap) {
                String filepath = Environment.getExternalStorageDirectory() + "/captured.jpg";
                saveBitmapToSDCard(filepath, bitmap);
                sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + filepath)));
                showToast("截帧成功, 存放在 " + filepath, Toast.LENGTH_SHORT);
            }

            @Override
            public void onFrameCaptureFailed(int errorCode) {
                showToast("截帧失败，错误码：" + errorCode, Toast.LENGTH_SHORT);
            }
        });
    }

    public void onClickPreviewMirror(View v) {
        if (mRTCStreamingManager.setPreviewMirror(!mIsPreviewMirror)) {
            mIsPreviewMirror = !mIsPreviewMirror;
            showToast(getString(R.string.mirror_success), Toast.LENGTH_SHORT);
        }
    }

    public void onClickEncodingMirror(View v) {
        if (mRTCStreamingManager.setEncodingMirror(!mIsEncodingMirror)) {
            mIsEncodingMirror = !mIsEncodingMirror;
            showToast(getString(R.string.mirror_success), Toast.LENGTH_SHORT);
        }
    }

    public void onClickSwitchCamera(View v) {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        mCurrentCamFacingIndex = (mCurrentCamFacingIndex + 1) % CameraStreamingSetting.getNumberOfCameras();
        CameraStreamingSetting.CAMERA_FACING_ID facingId;
        if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK.ordinal()) {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        } else if (mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT.ordinal()) {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            facingId = CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        }
        if (mRTCStreamingManager.switchCamera(facingId)) {
            mCameraPreviewFrameView.queueEvent(new Runnable() {
                @Override
                public void run() {
                    faceunity.fuOnCameraChange();
                    mSurfaceTextureCallback.onSurfaceDestroyed();
                    mFrameId = 0;
                }
            });
        }
    }

    public void onClickAdjustBitrate(View v) {
        if (mBitrateControl.equals("manual")) {
            MaterialDialog.Builder builder = new MaterialDialog.Builder(this);
            builder.title("请输入目标码率")
                    .customView(R.layout.dialog_bitrate_adjust)
                    .positiveText("确认")
                    .positiveColor(Color.parseColor("#03a9f4"))
                    .negativeText("取消")
                    .callback(new MaterialDialog.Callback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            String textInput = ((EditText) dialog.findViewById(R.id.adjust_bitrate)).getText().toString().trim();
                            int bitrate = 0;
                            if (!textInput.isEmpty()) {
                                bitrate = Integer.parseInt(textInput);
                            }
                            if (bitrate < MINBITRATE || bitrate > MAXBITRATE) {
                                Toast.makeText(RTCStreamingActivity.this, "请输入规定范围内的码率", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            boolean result = mRTCStreamingManager.adjustVideoBitrate(bitrate);
                            if (result) {
                                Toast.makeText(RTCStreamingActivity.this, "调整成功", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(RTCStreamingActivity.this, "调整失败", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            dialog.dismiss();
                        }
                    })
                    .build()
                    .show();
        } else {
            Toast.makeText(this, "当前模式为非手动调节码率模式", Toast.LENGTH_LONG).show();
        }
    }

    public void onClickMuteSpeaker(View v) {
        if (mRTCStreamingManager.muteSpeaker(!mIsSpeakerMuted)) {
            mIsSpeakerMuted = !mIsSpeakerMuted;
            mMuteSpeakerButton.setTitle(mIsSpeakerMuted ? getResources().getString(R.string.button_unmute_speaker) : getResources().getString(R.string.button_mute_speaker));
            showToast(getString(mIsSpeakerMuted ? R.string.others_muted : R.string.others_unmuted),
                    Toast.LENGTH_SHORT);
        } else {
            showToast(getString(R.string.others_fail), Toast.LENGTH_SHORT);
        }
    }

    public void onClickTogglePlayback(View v) {
        if (!mIsPublishStreamStarted) {
            showToast("请先开始直播！", Toast.LENGTH_SHORT);
            return;
        }
        if (mIsPlayingback) {
            mRTCStreamingManager.stopPlayback();
            mTogglePlaybackButton.setTitle("开启返听");
        } else {
            mRTCStreamingManager.startPlayback();
            mTogglePlaybackButton.setTitle("关闭返听");
        }
        mIsPlayingback = !mIsPlayingback;
    }

    public void onClickLogStats(View v) {
        HashMap<String, RTCStreamStats> stats = mRTCStreamingManager.getStreamStats();
        if (null != stats) {
            for (Map.Entry<String, RTCStreamStats> entry : stats.entrySet()) {
                Log.i(TAG, "用户 id: " + entry.getKey()
                        + "   当前帧率" + entry.getValue().getFrameRate()
                        + "   当前上行" + entry.getValue().getSentBitrate() + "bps"
                        + "   当前下行" + entry.getValue().getRecvBitrate() + "bps"
                        + "   丢包率" + entry.getValue().getPacketLossPercent() + "%");
            }
        } else {
            showToast(getString(R.string.log_stats_failed), Toast.LENGTH_SHORT);
        }
    }

    public void onClickRemoteWindowA(View v) {
        FrameLayout window = (FrameLayout) v;
        if (window.getChildAt(0).getId() == mCameraPreviewFrameView.getId()) {
            mRTCStreamingManager.switchRenderView(mCameraPreviewFrameView, mRTCVideoWindowA.getGLSurfaceView());
        } else {
            mRTCStreamingManager.switchRenderView(mRTCVideoWindowA.getGLSurfaceView(), mCameraPreviewFrameView);
        }
    }

    public void onClickRemoteWindowB(View v) {
        FrameLayout window = (FrameLayout) v;
        if (window.getChildAt(0).getId() == mCameraPreviewFrameView.getId()) {
            mRTCStreamingManager.switchRenderView(mCameraPreviewFrameView, mRTCVideoWindowB.getGLSurfaceView());
        } else {
            mRTCStreamingManager.switchRenderView(mRTCVideoWindowB.getGLSurfaceView(), mCameraPreviewFrameView);
        }
    }

    public void onClickExit(View v) {
        finish();
    }

    private boolean startConference() {
        if (mIsConferenceStarted) {
            return true;
        }
        mProgressDialog.setMessage("正在加入连麦 ... ");
        mProgressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                startConferenceInternal();
            }
        }).start();
        return true;
    }

    private boolean startConferenceInternal() {
        String roomToken = StreamUtils.requestRoomToken(StreamUtils.getTestUserId(this), mRoomName);
        if (roomToken == null) {
            dismissProgressDialog();
            showToast("无法获取房间信息 !", Toast.LENGTH_SHORT);
            return false;
        }
        mRTCStreamingManager.startConference(StreamUtils.getTestUserId(this), mRoomName, roomToken, new RTCStartConferenceCallback() {
            @Override
            public void onStartConferenceSuccess() {
                dismissProgressDialog();
                showToast(getString(R.string.start_conference), Toast.LENGTH_SHORT);
                updateControlButtonText();
                mIsConferenceStarted = true;
                // Will cost 2% more cpu usage if enabled
                mRTCStreamingManager.setStreamStatsEnabled(mIsStatsEnabled);
                /**
                 * Because `startConference` is called in child thread
                 * So we should check if the activity paused.
                 */
                if (mIsActivityPaused) {
                    stopConference();
                }
            }

            @Override
            public void onStartConferenceFailed(int errorCode) {
                setConferenceBoxChecked(false);
                dismissProgressDialog();
                showToast(getString(R.string.failed_to_start_conference) + errorCode, Toast.LENGTH_SHORT);
            }
        });
        return true;
    }

    private boolean stopConference() {
        if (!mIsConferenceStarted) {
            return true;
        }
        mRTCStreamingManager.stopConference();
        mIsConferenceStarted = false;
        setConferenceBoxChecked(false);
        showToast(getString(R.string.stop_conference), Toast.LENGTH_SHORT);
        updateControlButtonText();
        return true;
    }

    private boolean startPublishStreaming() {
        if (mIsPublishStreamStarted) {
            return true;
        }
        if (!mIsInReadyState) {
            showToast(getString(R.string.stream_state_not_ready), Toast.LENGTH_SHORT);
            return false;
        }
        mProgressDialog.setMessage("正在准备推流... ");
        mProgressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                startPublishStreamingInternal();
            }
        }).start();
        return true;
    }

    private boolean startPublishStreamingInternal() {
        String publishAddr = StreamUtils.requestPublishAddress(mRoomName);
        if (publishAddr == null) {
            dismissProgressDialog();
            showToast("无法获取房间信息/推流地址 !", Toast.LENGTH_SHORT);
            return false;
        }

        try {
            if (StreamUtils.IS_USING_STREAMING_JSON) {
                mStreamingProfile.setStream(new StreamingProfile.Stream(new JSONObject(publishAddr)));
            } else {
                mStreamingProfile.setPublishUrl(publishAddr);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        } catch (URISyntaxException e) {
            e.printStackTrace();
            dismissProgressDialog();
            showToast("无效的推流地址 !", Toast.LENGTH_SHORT);
            return false;
        }

        mRTCStreamingManager.setStreamingProfile(mStreamingProfile);
        if (!mRTCStreamingManager.startStreaming()) {
            dismissProgressDialog();
            showToast(getString(R.string.failed_to_start_streaming), Toast.LENGTH_SHORT);
            return false;
        }
        dismissProgressDialog();
        showToast(getString(R.string.start_streaming), Toast.LENGTH_SHORT);
        updateControlButtonText();
        mIsPublishStreamStarted = true;
        /**
         * Because `startPublishStreaming` need a long time in some weak network
         * So we should check if the activity paused.
         */
        if (mIsActivityPaused) {
            stopPublishStreaming();
        }
        return true;
    }

    private boolean stopPublishStreaming() {
        if (!mIsPublishStreamStarted) {
            return true;
        }
        mRTCStreamingManager.stopStreaming();
        mIsPublishStreamStarted = false;
        showToast(getString(R.string.stop_streaming), Toast.LENGTH_SHORT);
        updateControlButtonText();
        return false;
    }

    private StreamingStateChangedListener mStreamingStateChangedListener = new StreamingStateChangedListener() {
        @Override
        public void onStateChanged(final StreamingState state, Object o) {
            switch (state) {
                case PREPARING:
                    setStatusText(getString(R.string.preparing));
                    Log.d(TAG, "onStateChanged state:" + "preparing");
                    break;
                case READY:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "ready");
                    break;
                case CONNECTING:
                    Log.d(TAG, "onStateChanged state:" + "connecting");
                    break;
                case STREAMING:
                    setStatusText(getString(R.string.streaming));
                    Log.d(TAG, "onStateChanged state:" + "streaming");
                    break;
                case SHUTDOWN:
                    mIsInReadyState = true;
                    setStatusText(getString(R.string.ready));
                    Log.d(TAG, "onStateChanged state:" + "shutdown");
                    break;
                case UNKNOWN:
                    Log.d(TAG, "onStateChanged state:" + "unknown");
                    break;
                case SENDING_BUFFER_EMPTY:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer empty");
                    break;
                case SENDING_BUFFER_FULL:
                    Log.d(TAG, "onStateChanged state:" + "sending buffer full");
                    break;
                case OPEN_CAMERA_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "open camera failed");
                    showToast(getString(R.string.failed_open_camera), Toast.LENGTH_SHORT);
                    break;
                case AUDIO_RECORDING_FAIL:
                    Log.d(TAG, "onStateChanged state:" + "audio recording failed");
                    showToast(getString(R.string.failed_open_microphone), Toast.LENGTH_SHORT);
                    break;
                case IOERROR:
                    /**
                     * Network-connection is unavailable when `startStreaming`.
                     * You can do reconnecting or just finish the streaming
                     */
                    Log.d(TAG, "onStateChanged state:" + "io error");
                    showToast(getString(R.string.io_error), Toast.LENGTH_SHORT);
                    sendReconnectMessage();
                    // stopPublishStreaming();
                    break;
                case DISCONNECTED:
                    /**
                     * Network-connection is broken after `startStreaming`.
                     * You can do reconnecting in `onRestartStreamingHandled`
                     */
                    Log.d(TAG, "onStateChanged state:" + "disconnected");
                    setStatusText(getString(R.string.disconnected));
                    // we will process this state in `onRestartStreamingHandled`
                    break;
            }
        }
    };

    private StreamingSessionListener mStreamingSessionListener = new StreamingSessionListener() {
        @Override
        public boolean onRecordAudioFailedHandled(int code) {
            return false;
        }

        /**
         * When the network-connection is broken, StreamingState#DISCONNECTED will notified first,
         * and then invoked this method if the environment of restart streaming is ready.
         *
         * @return true means you handled the event; otherwise, given up and then StreamingState#SHUTDOWN
         * will be notified.
         */
        @Override
        public boolean onRestartStreamingHandled(int code) {
            Log.d(TAG, "onRestartStreamingHandled, reconnect ...");
            return mRTCStreamingManager.startStreaming();
        }

        @Override
        public Camera.Size onPreviewSizeSelected(List<Camera.Size> list) {
            for (Camera.Size size : list) {
                if (size.height >= 480) {
                    return size;
                }
            }
            return null;
        }
    };

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what != MESSAGE_ID_RECONNECTING || mIsActivityPaused || !mIsPublishStreamStarted) {
                return;
            }
            if (!StreamUtils.isNetworkAvailable(RTCStreamingActivity.this)) {
                sendReconnectMessage();
                return;
            }
            Log.d(TAG, "do reconnecting ...");
            mRTCStreamingManager.startStreaming();
        }
    };

    private void sendReconnectMessage() {
        showToast("正在重连...", Toast.LENGTH_SHORT);
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_ID_RECONNECTING), 500);
    }

    private RTCConferenceStateChangedListener mRTCStreamingStateChangedListener = new RTCConferenceStateChangedListener() {
        @Override
        public void onConferenceStateChanged(RTCConferenceState state, int extra) {
            switch (state) {
                case READY:
                    // You must `StartConference` after `Ready`
                    showToast(getString(R.string.ready), Toast.LENGTH_SHORT);
                    break;
                case CONNECT_FAIL:
                    showToast(getString(R.string.failed_to_connect_rtc_server), Toast.LENGTH_SHORT);
                    finish();
                    break;
                case VIDEO_PUBLISH_FAILED:
                case AUDIO_PUBLISH_FAILED:
                    showToast(getString(R.string.failed_to_publish_av_to_rtc) + extra, Toast.LENGTH_SHORT);
                    finish();
                    break;
                case VIDEO_PUBLISH_SUCCESS:
                    showToast(getString(R.string.success_publish_video_to_rtc), Toast.LENGTH_SHORT);
                    break;
                case AUDIO_PUBLISH_SUCCESS:
                    showToast(getString(R.string.success_publish_audio_to_rtc), Toast.LENGTH_SHORT);
                    break;
                case USER_JOINED_AGAIN:
                    showToast(getString(R.string.user_join_other_where), Toast.LENGTH_SHORT);
                    finish();
                    break;
                case USER_KICKOUT_BY_HOST:
                    showToast(getString(R.string.user_kickout_by_host), Toast.LENGTH_SHORT);
                    finish();
                    break;
                case OPEN_CAMERA_FAIL:
                    showToast(getString(R.string.failed_open_camera), Toast.LENGTH_SHORT);
                    break;
                case AUDIO_RECORDING_FAIL:
                    showToast(getString(R.string.failed_open_microphone), Toast.LENGTH_SHORT);
                    break;
                default:
                    return;
            }
        }
    };

    private RTCUserEventListener mRTCUserEventListener = new RTCUserEventListener() {
        @Override
        public void onUserJoinConference(String remoteUserId) {
            Log.d(TAG, "onUserJoinConference: " + remoteUserId);
        }

        @Override
        public void onUserLeaveConference(String remoteUserId) {
            Log.d(TAG, "onUserLeaveConference: " + remoteUserId);
        }
    };

    private RTCRemoteWindowEventListener mRTCRemoteWindowEventListener = new RTCRemoteWindowEventListener() {
        @Override
        public void onRemoteWindowAttached(RTCVideoWindow window, String remoteUserId) {
            Log.d(TAG, "onRemoteWindowAttached: " + remoteUserId);
        }

        @Override
        public void onRemoteWindowDetached(RTCVideoWindow window, String remoteUserId) {
            Log.d(TAG, "onRemoteWindowDetached: " + remoteUserId);
        }

        @Override
        public void onFirstRemoteFrameArrived(String remoteUserId) {
            Log.d(TAG, "onFirstRemoteFrameArrived: " + remoteUserId);
        }
    };

    private RTCAudioLevelCallback mRTCAudioLevelCallback = new RTCAudioLevelCallback() {
        @Override
        public void onAudioLevelChanged(RTCAudioInfo rtcAudioInfo) {
            Log.d(TAG, "onAudioLevelChanged: " + rtcAudioInfo.toString());
        }
    };

    private View.OnClickListener mMuteButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mRTCStreamingManager.mute(mMuteCheckBox.isChecked());
        }
    };

    private View.OnClickListener mConferenceButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mConferenceCheckBox.isChecked()) {
                startConference();
            } else {
                stopConference();
            }
        }
    };

    public void onClickStreaming(View v) {
        if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
            if (!mIsPublishStreamStarted) {
                startPublishStreaming();
            } else {
                stopPublishStreaming();
            }
        } else {
            if (!mIsConferenceStarted) {
                startConference();
            } else {
                stopConference();
            }
        }
    }

    private void setStatusText(final String status) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mStatusTextView.setText(status);
            }
        });
    }

    private void updateControlButtonText() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRole == StreamUtils.RTC_ROLE_ANCHOR) {
                    if (mIsPublishStreamStarted) {
                        mControlButton.setText(getString(R.string.stop_streaming));
                    } else {
                        mControlButton.setText(getString(R.string.start_streaming));
                    }
                } else {
                    if (mIsConferenceStarted) {
                        mControlButton.setText(getString(R.string.stop_conference));
                    } else {
                        mControlButton.setText(getString(R.string.start_conference));
                    }
                }
            }
        });
    }

    private void setConferenceBoxChecked(final boolean enabled) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConferenceCheckBox.setChecked(enabled);
            }
        });
    }

    private StreamStatusCallback mStreamStatusCallback = new StreamStatusCallback() {
        @Override
        public void notifyStreamStatusChanged(final StreamingProfile.StreamStatus streamStatus) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String stat = "bitrate: " + streamStatus.totalAVBitrate / 1024 + " kbps"
                            + "\naudio: " + streamStatus.audioFps + " fps"
                            + "\nvideo: " + streamStatus.videoFps + " fps";
                    mStatTextView.setText(stat);
                }
            });
        }
    };

    private void dismissProgressDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.dismiss();
            }
        });
    }

    private void showToast(final String text, final int duration) {
        if (mIsActivityPaused) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(RTCStreamingActivity.this, text, duration);
                mToast.show();
            }
        });
    }

    private CameraStreamingSetting.CAMERA_FACING_ID chooseCameraFacingId() {
        if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_3RD;
        } else if (CameraStreamingSetting.hasCameraFacing(CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT)) {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_FRONT;
        } else {
            return CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK;
        }
    }

    private static boolean saveBitmapToSDCard(String filepath, Bitmap bitmap) {
        try {
            FileOutputStream fos = new FileOutputStream(filepath);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.close();
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

    //-----------------------------------------------------faceunity---------------------------------------------------

    private byte[] mCameraNV21Byte;
    private byte[] fuImgNV21Bytes;

    private int mFrameId = 0;

    private int mFacebeautyItem = 0; //美颜道具
    private int mEffectItem = 0; //贴纸道具
    private int[] itemsArray = {mFacebeautyItem, mEffectItem};

    private float mFacebeautyColorLevel = 0.2f;
    private float mFacebeautyBlurLevel = 6.0f;
    private float mFacebeautyCheeckThin = 1.0f;
    private float mFacebeautyEnlargeEye = 0.5f;
    private float mFacebeautyRedLevel = 0.5f;
    private int mFaceShape = 3;
    private float mFaceShapeLevel = 0.5f;

    private String mFilterName = EffectAndFilterSelectAdapter.FILTERS_NAME[0];

    private boolean isNeedEffectItem = true;
    private String mEffectFileName = EffectAndFilterSelectAdapter.EFFECT_ITEM_FILE_NAME[1];

    private HandlerThread mCreateItemThread;
    private Handler mCreateItemHandler;

    private int faceTrackingStatus = 0;

    private boolean isRunning = false;

    private long lastOneHundredFrameTimeStamp = 0;
    private int currentFrameCnt = 0;
    private long oneHundredFrameFUTime = 0;

    private boolean isBenchmarkFPS = true;
    private boolean isBenchmarkTime = false;

    private FullFrameRect mFullScreenFUDisplay;
    private int fboTextureId = -1;
    private int fboId = -1;

    private static final float ROTATE_90[] = {0.0F, 1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};
    private static final float ROTATE_270[] = {0.0F, -1.0F, 0.0F, 0.0F, -1.0F, -0.0F, -0.0F, -0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F};

    private SurfaceTextureCallback mSurfaceTextureCallback = new SurfaceTextureCallback() {
        @Override
        public void onSurfaceCreated() {
            Log.e(TAG, "onSurfaceCreated " + Thread.currentThread().getId());
            mCreateItemThread = new HandlerThread("faceunity-efect");
            mCreateItemThread.start();
            mCreateItemHandler = new CreateItemHandler(mCreateItemThread.getLooper());

            mFullScreenFUDisplay = new FullFrameRect(new Texture2dProgram(
                    Texture2dProgram.ProgramType.TEXTURE_2D));

            try {
                InputStream is = getAssets().open("v3.mp3");
                byte[] v3data = new byte[is.available()];
                int len = is.read(v3data);
                is.close();
                faceunity.fuSetup(v3data, null, authpack.A());
                //faceunity.fuSetMaxFaces(1);
                Log.e(TAG, "fuSetup v3 len " + len);
                Log.e(TAG, "fuSetup version " + faceunity.fuGetVersion());

                is = getAssets().open("face_beautification.mp3");
                byte[] itemData = new byte[is.available()];
                len = is.read(itemData);
                Log.e(TAG, "beautification len " + len);
                is.close();
                mFacebeautyItem = faceunity.fuCreateItemFromPackage(itemData);
                itemsArray[0] = mFacebeautyItem;

                isRunning = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceChanged(int width, int height) {
            Log.e(TAG, "onSurfaceChanged  width " + width + " height " + height);
            GLES20.glViewport(0, 0, width, height);
        }

        @Override
        public void onSurfaceDestroyed() {
            Log.e(TAG, "onSurfaceDestroyed");

            isRunning = false;

            mFrameId = 0;

            if (mCreateItemHandler != null) {
                mCreateItemHandler.removeMessages(CreateItemHandler.HANDLE_CREATE_ITEM);
                mCreateItemHandler = null;
                mCreateItemThread.quitSafely();
                mCreateItemThread = null;
            }

            if (fboTextureId > 0) {
                int[] textures = new int[]{fboTextureId};
                GLES20.glDeleteTextures(1, textures, 0);
                fboTextureId = -1;
            }

            if (fboId > 0) {
                int[] frameBuffers = new int[]{fboId};
                GLES20.glDeleteFramebuffers(1, frameBuffers, 0);
                fboId = -1;
            }


            //Note: 切忌使用一个已经destroy的item
            faceunity.fuDestroyItem(mEffectItem);
            itemsArray[1] = mEffectItem = 0;
            faceunity.fuDestroyItem(mFacebeautyItem);
            itemsArray[0] = mFacebeautyItem = 0;
            faceunity.fuOnDeviceLost();
            faceunity.fuDestroyAllItems();
            isNeedEffectItem = true;

            lastOneHundredFrameTimeStamp = 0;
            oneHundredFrameFUTime = 0;
        }

        @Override
        public int onDrawFrame(int texId, int width, int height, float[] floats) {
//            Log.e(TAG, "onDrawFrame start " + Thread.currentThread().getId());
            if (++currentFrameCnt == 100) {
                currentFrameCnt = 0;
                long tmp = System.nanoTime();
                if (isBenchmarkFPS)
                    Log.e(TAG, "dualInput FPS : " + (1000.0f * MiscUtil.NANO_IN_ONE_MILLI_SECOND / ((tmp - lastOneHundredFrameTimeStamp) / 100.0f)));
                lastOneHundredFrameTimeStamp = tmp;
                if (isBenchmarkTime)
                    Log.e(TAG, "dualInput cost time avg : " + oneHundredFrameFUTime / 100.f / MiscUtil.NANO_IN_ONE_MILLI_SECOND);
                oneHundredFrameFUTime = 0;
            }

            if (mCreateItemHandler == null) {
                return texId;
            }


            if (mCameraNV21Byte == null || mCameraNV21Byte.length == 0 || fuImgNV21Bytes == null || fuImgNV21Bytes.length == 0) {
                Log.e(TAG, "camera nv21 bytes null");
                return texId;
            }

            if (!isRunning) {
                return fboTextureId;
            }
            float[] m = mCurrentCamFacingIndex == Camera.CameraInfo.CAMERA_FACING_FRONT ? ROTATE_90 : ROTATE_270;

            if (isNeedEffectItem) {
                isNeedEffectItem = false;
                mCreateItemHandler.sendEmptyMessage(CreateItemHandler.HANDLE_CREATE_ITEM);
            }

            final int isTracking = faceunity.fuIsTracking();
            if (isTracking != faceTrackingStatus) {
                faceTrackingStatus = isTracking;
            }

            faceunity.fuItemSetParam(mFacebeautyItem, "color_level", mFacebeautyColorLevel);
            faceunity.fuItemSetParam(mFacebeautyItem, "blur_level", mFacebeautyBlurLevel);
            faceunity.fuItemSetParam(mFacebeautyItem, "filter_name", mFilterName);
            faceunity.fuItemSetParam(mFacebeautyItem, "cheek_thinning", mFacebeautyCheeckThin);
            faceunity.fuItemSetParam(mFacebeautyItem, "eye_enlarging", mFacebeautyEnlargeEye);
            faceunity.fuItemSetParam(mFacebeautyItem, "face_shape", mFaceShape);
            faceunity.fuItemSetParam(mFacebeautyItem, "face_shape_level", mFaceShapeLevel);
            faceunity.fuItemSetParam(mFacebeautyItem, "red_level", mFacebeautyRedLevel);

            //faceunity.fuItemSetParam(mFacebeautyItem, "use_old_blur", 1);

//            if(七牛连麦1.2.1) { // 用于旋转纹理
            if (fboTextureId == -1 || fboId == -1) {
                createFBO(width, height);
                Log.e(TAG, "glBindFramebuffer  width " + width + " height " + height);
            }

            int[] originalViewPort = new int[4];
            GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, originalViewPort, 0);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
            GLES20.glViewport(0, 0, width, height);
//            }

            boolean isOESTexture = true; //camera默认的是OES的
            int flags = isOESTexture ? faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE : 0;
            boolean isNeedReadBack = true; //是否需要写回，如果是，则入参的byte[]会被修改为带有fu特效的
            flags = isNeedReadBack ? flags | faceunity.FU_ADM_FLAG_ENABLE_READBACK : flags;
            flags |= mCurrentCamFacingIndex == Camera.CameraInfo.CAMERA_FACING_FRONT ? 0 : faceunity.FU_ADM_FLAG_FLIP_X;

            long fuStartTime = System.nanoTime();
            /**
             * 这里拿到fu处理过后的texture，可以对这个texture做后续操作，如硬编、预览。
             */
            int fuTex = faceunity.fuDualInputToTexture(mCameraNV21Byte, texId, flags,
                    width, height, mFrameId++, itemsArray, width, height, fuImgNV21Bytes);
            long fuEndTime = System.nanoTime();
            oneHundredFrameFUTime += fuEndTime - fuStartTime;

//            if(七牛连麦1.2.1) { // 用于旋转纹理

            mFullScreenFUDisplay.drawFrame(fuTex, m);

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glViewport(originalViewPort[0], originalViewPort[1], originalViewPort[2], originalViewPort[3]);
//            }

//            Log.e(TAG, "onDrawFrame end ");
            // 由于七牛1.2.1版本 对GL_TEXTURE_2D纹理不在进行旋转操作，因此需要在代码中使用FBO进行旋转纹理操作
            return fboTextureId;
        }
    };

    private StreamingPreviewCallback mStreamingPreviewCallback = new StreamingPreviewCallback() {
        @Override
        public boolean onPreviewFrame(byte[] bytes, int width, int height, int rotation, int fmt, long tsInNanoTime) {
//            Log.e(TAG, "onPreviewFrame start " + Thread.currentThread().getId() + " rotation " + rotation);
            if (mCameraNV21Byte == null || fuImgNV21Bytes == null || mCameraNV21Byte.length != bytes.length || mCameraNV21Byte.length != fuImgNV21Bytes.length) {
                mCameraNV21Byte = new byte[bytes.length];
                fuImgNV21Bytes = new byte[bytes.length];
            }
            System.arraycopy(bytes, 0, mCameraNV21Byte, 0, bytes.length);
            System.arraycopy(fuImgNV21Bytes, 0, bytes, 0, bytes.length);
//            Log.e(TAG, "onPreviewFrame end ");
            return true;
        }
    };

    private void createFBO(int width, int height) {
        int[] temp = new int[1];
//generate fbo id
        GLES20.glGenFramebuffers(1, temp, 0);
        fboId = temp[0];
//generate texture
        GLES20.glGenTextures(1, temp, 0);
        fboTextureId = temp[0];
//generate render buffer
        GLES20.glGenRenderbuffers(1, temp, 0);
        int renderBufferId = temp[0];
//Bind Frame buffer
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId);
//Bind texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTextureId);
//Define texture parameters
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
//Bind render buffer and define buffer dimension
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, renderBufferId);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width, height);
//Attach texture FBO color attachment
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTextureId, 0);
//Attach render buffer to depth attachment
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderBufferId);
//we are done, reset
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    class CreateItemHandler extends Handler {

        static final int HANDLE_CREATE_ITEM = 1;

        CreateItemHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLE_CREATE_ITEM:
                    try {
                        if (mEffectFileName.equals("none")) {
                            itemsArray[1] = mEffectItem = 0;
                        } else {
                            InputStream is = getAssets().open(mEffectFileName);
                            byte[] itemData = new byte[is.available()];
                            int len = is.read(itemData);
                            Log.e(TAG, "effect len " + len);
                            is.close();
                            final int tmp = itemsArray[1];
                            itemsArray[1] = mEffectItem = faceunity.fuCreateItemFromPackage(itemData);
                            mCameraPreviewFrameView.queueEvent(new Runnable() {
                                @Override
                                public void run() {
                                    faceunity.fuItemSetParam(mEffectItem, "isAndroid", 1.0);
                                    faceunity.fuItemSetParam(mEffectItem, "rotationAngle",
                                            mCurrentCamFacingIndex == CameraStreamingSetting.CAMERA_FACING_ID.CAMERA_FACING_BACK.ordinal() ? 270 : 90);
                                    if (tmp != 0 && tmp != mEffectItem) {
                                        faceunity.fuDestroyItem(tmp);
                                    }
                                }
                            });
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }


    private FaceunityControlView.OnViewEventListener eventListener = new FaceunityControlView.OnViewEventListener() {

        @Override
        public void onBlurLevelSelected(int level) {
            switch (level) {
                case 0:
                    mFacebeautyBlurLevel = 0;
                    break;
                case 1:
                    mFacebeautyBlurLevel = 1.0f;
                    break;
                case 2:
                    mFacebeautyBlurLevel = 2.0f;
                    break;
                case 3:
                    mFacebeautyBlurLevel = 3.0f;
                    break;
                case 4:
                    mFacebeautyBlurLevel = 4.0f;
                    break;
                case 5:
                    mFacebeautyBlurLevel = 5.0f;
                    break;
                case 6:
                    mFacebeautyBlurLevel = 6.0f;
                    break;
            }
        }

        @Override
        public void onCheekThinSelected(int progress, int max) {
            mFacebeautyCheeckThin = 1.0f * progress / max;
        }

        @Override
        public void onColorLevelSelected(int progress, int max) {
            mFacebeautyColorLevel = 1.0f * progress / max;
        }

        @Override
        public void onEffectItemSelected(String effectItemName) {
            if (effectItemName.equals(mEffectFileName)) {
                return;
            }
            mCreateItemHandler.removeMessages(CreateItemHandler.HANDLE_CREATE_ITEM);
            mEffectFileName = effectItemName;
            isNeedEffectItem = true;
        }

        @Override
        public void onEnlargeEyeSelected(int progress, int max) {
            mFacebeautyEnlargeEye = 1.0f * progress / max;
        }

        @Override
        public void onFilterSelected(String filterName) {
            mFilterName = filterName;
        }

        @Override
        public void onRedLevelSelected(int progress, int max) {
            mFacebeautyRedLevel = 1.0f * progress / max;
        }

        @Override
        public void onFaceShapeLevelSelected(int progress, int max) {
            mFaceShapeLevel = (1.0f * progress) / max;
        }

        @Override
        public void onFaceShapeSelected(int faceShape) {
            mFaceShape = faceShape;
        }
    };

}
