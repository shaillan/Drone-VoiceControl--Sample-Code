package com.dji.sdk.sample.demo.flightcontroller;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.MotionEvent;
import android.util.Log;
import android.media.MediaRecorder;
import android.media.AudioRecord;
import android.media.AudioFormat;
import android.speech.RecognizerIntent;
// Java
import android.content.ActivityNotFoundException;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;
import dji.sdk.sdkmanager.DJISDKManager;

import org.json.JSONArray;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.sdkmanager.DJISDKManager.SDKManagerCallback;
import dji.common.error.DJIError;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.base.BaseProduct;
@SuppressLint("NewApi")
public class VoiceControlActivity extends AppCompatActivity {

    // 百度语音配置（填你从控制台拿到的值）
    private static final String API_KEY = "NdWwDA5EuGkZgyqDzyPhRrBt";
    private static final String SECRET_KEY = "ptdFtHYiuDT4aWITmD4rTmhkKKSQmntF";

    // UI 控件
    private Button btnEnableVirtualStick, btnDisableVirtualStick, btnTakeOff, btnVoice,btnLand;
    private TextView tvVoiceResult, tvStatus;

    // DJI 飞控
    private FlightController flightController;
    private Handler handler = new Handler();
    private SendVirtualStickDataTask sendTask;
    private float pitch, roll, yaw, throttle;

    private BroadcastReceiver mReceiver;

    // 录音相关
    // 录音相关 (用 MediaRecorder 替换 AudioRecord)
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String audioFilePath;

    private AudioRecord audioRecord;
    private static final int VOICE_RECOGNITION_REQUEST_CODE = 1001;
    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_control);

        initUI();
        checkPermissions();

        int minBufferSize = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        Log.d("VoiceControl", "最小缓冲区大小: " + minBufferSize);

        // 测试麦克风权限
        PackageManager pm = getPackageManager();
        boolean hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        Log.d("VoiceControl", "设备有麦克风: " + hasMic);

    }

    private void initUI() {
        btnEnableVirtualStick = findViewById(R.id.btn_enable_virtual_stick);
        btnDisableVirtualStick = findViewById(R.id.btn_disable_virtual_stick);
        btnTakeOff = findViewById(R.id.btn_take_off);
        btnVoice = findViewById(R.id.btn_voice);
        tvVoiceResult = findViewById(R.id.tv_voice_result);
        tvStatus = findViewById(R.id.textview_simulator);

        btnLand = findViewById(R.id.btn_land);
        btnLand.setOnClickListener(v -> landing());

        btnEnableVirtualStick.setOnClickListener(v -> enableVirtualStick());
        btnDisableVirtualStick.setOnClickListener(v -> disableVirtualStick());
        btnTakeOff.setOnClickListener(v -> takeOff());

        btnVoice.setOnClickListener(v -> startVoiceRecognition());
    }

    // ==================== 语音识别（纯在线API） ====================

    private void startVoiceRecognition() {
        if (isRecording) {
            ToastUtils.setResultToToast("正在识别中");
            return;
        }

        // 检查麦克风权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }

        tvVoiceResult.setText("请说话...");

        // 开始录音
        startRecording();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == VOICE_RECOGNITION_REQUEST_CODE && resultCode == RESULT_OK) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && results.size() > 0) {
                String command = results.get(0);
                tvVoiceResult.setText("识别结果: " + command);
                executeVoiceCommand(command);
            }
        }
    }
    private void startRecording() {
        try {
            // 确保文件路径
            audioFilePath = getExternalCacheDir().getAbsolutePath() + "/temp_audio.m4a";
            File audioFile = new File(audioFilePath);
            if (audioFile.exists()) audioFile.delete();

            mediaRecorder = new MediaRecorder();

            // 配置录音参数
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(16000);  // 必须和百度一致
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioEncodingBitRate(32000);
            mediaRecorder.setOutputFile(audioFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            tvVoiceResult.setText("请说话...");

            // 4秒后停止
            handler.postDelayed(this::stopRecordingAndRecognize, 3000);

        } catch (Exception e) {
            e.printStackTrace();
            tvVoiceResult.setText("录音初始化失败: " + e.getMessage());
        }
    }

    private void stopRecordingAndRecognize() {
        if (!isRecording || mediaRecorder == null) return;

        try {
            isRecording = false;
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            // 检查文件大小
            File audioFile = new File(audioFilePath);
            long fileSize = audioFile.length();
            Log.d("VoiceControl", "录音文件大小: " + fileSize + " 字节");

            if (fileSize < 1000) {  // 小于1KB视为无效
                tvVoiceResult.setText("录音太短，请再说一遍");
                return;
            }

            tvVoiceResult.setText("识别中...");
            recognizeAudioFile(audioFilePath);

        } catch (Exception e) {
            e.printStackTrace();
            tvVoiceResult.setText("录音停止失败: " + e.getMessage());
        }
    }
    private void recognizeAudioFile(String filePath) {
        new Thread(() -> {
            try {
                // 1. 获取Access Token
                String accessToken = getAccessToken();
                if (accessToken == null) {
                    runOnUiThread(() -> tvVoiceResult.setText("获取access_token失败，请检查API Key"));
                    return;
                }

                // 2. 读取录音文件并Base64编码
                File audioFile = new File(filePath);
                byte[] audioData = new byte[(int) audioFile.length()];
                FileInputStream fis = new FileInputStream(audioFile);
                fis.read(audioData);
                fis.close();

                String base64Audio = android.util.Base64.encodeToString(audioData, android.util.Base64.NO_WRAP);

                // 3. 调用百度语音识别API
                URL url = new URL("https://vop.baidu.com/server_api");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject params = new JSONObject();
                params.put("format", "m4a");
                params.put("rate", SAMPLE_RATE);
                params.put("channel", 1);
                params.put("cuid", "android_drone");
                params.put("token", accessToken);
                params.put("dev_pid", 1537);  // 普通话
                params.put("speech", base64Audio);
                params.put("len", audioData.length);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                out.write(params.toString().getBytes());
                out.flush();
                out.close();

                int responseCode = conn.getResponseCode();
                BufferedReader reader;
                if (responseCode == 200) {
                    reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                } else {
                    reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                }

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // 4. 解析识别结果
                JSONObject resultJson = new JSONObject(response.toString());
                int errNo = resultJson.optInt("err_no", -1);
                if (errNo == 0) {
                    JSONArray results = resultJson.getJSONArray("result");
                    if (results != null && results.length() > 0) {
                        String recognizedText = results.getString(0);
                        runOnUiThread(() -> {
                            tvVoiceResult.setText("识别结果: " + recognizedText);
                            executeVoiceCommand(recognizedText);
                        });
                    } else {
                        runOnUiThread(() -> tvVoiceResult.setText("未识别到内容，请重试"));
                    }
                } else {
                    String errMsg = resultJson.optString("err_msg", "未知错误");
                    runOnUiThread(() -> tvVoiceResult.setText("识别失败: " + errMsg));
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> tvVoiceResult.setText("识别异常: " + e.getMessage()));
            }
        }).start();
    }

    private String getAccessToken() {
        try {
            String urlStr = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials" +
                    "&client_id=" + API_KEY +
                    "&client_secret=" + SECRET_KEY;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            return json.optString("access_token");

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ==================== 飞控方法 ====================

    private void executeVoiceCommand(String command) {

       // String url = "http://192.168.3.180:11434/api/generate";
        String lower = command.toLowerCase();
        if (lower.contains("起飞")) {
            takeOff();
            tvVoiceResult.setText("执行: 起飞");
        } else if (lower.contains("降落")) {
            landing();
            tvVoiceResult.setText("执行: 降落");
        } else if (lower.contains("前进")) {
            moveForward(0.5f);
            tvVoiceResult.setText("执行: 前进");
            handler.postDelayed(()->{
                moveForward(0);
            },3000);
        } else if (lower.contains("后退")) {
            moveForward(-0.5f);
            tvVoiceResult.setText("执行: 后退");
            handler.postDelayed(()->{
                moveForward(0);
            },3000);
        } else if (lower.contains("左转")) {
            turnLeft(0.5f);
            tvVoiceResult.setText("执行: 左转");
            handler.postDelayed(() -> {
                turnLeft(0);  // 停止转向
            }, 1200);

        } else if (lower.contains("右转")) {
            turnRight(0.5f);
            tvVoiceResult.setText("执行: 右转");
            handler.postDelayed(()->{
                turnRight(0);
            },2000);
        } else if (lower.contains("悬停")) {
            hover();
            tvVoiceResult.setText("执行: 悬停");
        } else if(lower.contains("向后转")){
            turnaround(0.5f);
            tvVoiceResult.setText("执行：向后转");
            handler.postDelayed(()->{
                if(yaw!=0){
                    yaw=0;
                    tvVoiceResult.setText("掉头完成");
                }
            },2000);
        }
        else {
            tvVoiceResult.setText("未识别指令: " + command);
        }
    }

    private void enableVirtualStick() {

        if(ModuleVerificationUtil.isFlightControllerAvailable()){
            flightController=DJISampleApplication.getAircraftInstance().getFlightController();
        }

        if(flightController == null){
            ToastUtils.setResultToToast("飞行遥控器不可用，确保飞机已连接");
            return;
        }

        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        flightController.setVirtualStickModeEnabled(true, error -> {
            if (error == null) {
                // 添加这一行，开启高级虚拟摇杆模式
                flightController.setVirtualStickAdvancedModeEnabled(true);
              //  this.pitch=0.3f;
                startSendingData();
                ToastUtils.setResultToToast("虚拟摇杆已开启");
            } else {
                ToastUtils.setResultToToast("开启失败: " + error.getDescription());
            }
        });
    }

    private void disableVirtualStick() {
        if (flightController == null) return;
        flightController.setVirtualStickModeEnabled(false, error -> {
            if (error == null) stopSendingData();
        });
    }

    private void takeOff() {
        if(ModuleVerificationUtil.isFlightControllerAvailable()){
            flightController=DJISampleApplication.getAircraftInstance().getFlightController();
        }
        if (flightController != null) {
            flightController.startTakeoff(error -> {
                if (error != null) {
                    ToastUtils.setResultToToast("起飞失败: " + error.getDescription());
                }
            });
        } else{
            ToastUtils.setResultToToast("飞行控制器不可用");
        }
    }

    private void landing() {
        if (flightController != null) {
            flightController.startLanding(error -> {
                if (error != null) {
                    ToastUtils.setResultToToast("降落失败: " + error.getDescription());
                }
            });
        }
    }

    private void moveForward(float value) { this.pitch = value; }
    private void turnLeft(float value) { this.yaw = -value; }
    private void turnRight(float value) { this.yaw = value; }
    private void hover() { pitch = roll = yaw = throttle = 0; }
    private void turnaround(float value){ this.yaw=-value; }

    private void initFlightController() {
        ToastUtils.setResultToToast("尝试获取 FlightController...");
        if (ModuleVerificationUtil.isFlightControllerAvailable()) {
            flightController = DJISampleApplication.getAircraftInstance().getFlightController();
            ToastUtils.setResultToToast("flightController 获取成功 = " + flightController);
        }
        else {
            ToastUtils.setResultToToast("FlightController 不可用，请确认飞机已连接");
        }

        if (flightController != null) {
            flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
            flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
            flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
            flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);
        }
    }

    private void startSendingData() {
        if (sendTask != null) return;
        sendTask = new SendVirtualStickDataTask();
        handler.postDelayed(sendTask, 0, 100);
    }

    private void stopSendingData() {
        if (sendTask != null) {
            handler.removeCallbacks(sendTask);
            sendTask = null;
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放录音资源
        if (audioRecord != null) {
            try {
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop();
                }
            } catch (Exception e) {
                // ignore
            }
            audioRecord.release();
            audioRecord = null;
        }
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception e) {
                // ignore
            }
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (isRecording) {
            isRecording = false;
        }
        stopSendingData();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.setResultToToast("需要麦克风权限才能使用语音控制");
        }
    }

    private class SendVirtualStickDataTask implements Runnable {
        @Override
        public void run() {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(new FlightControlData(roll, pitch, yaw, throttle), null);
            }
            handler.postDelayed(this, 100);
        }
    }
}