package com.dji.sdk.sample.demo.flightcontroller;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dji.sdk.sample.R;
import com.dji.sdk.sample.internal.utils.ToastUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import dji.thirdparty.okhttp3.MediaType;
import dji.thirdparty.okhttp3.OkHttpClient;
import dji.thirdparty.okhttp3.Request;
import dji.thirdparty.okhttp3.RequestBody;
import dji.thirdparty.okhttp3.Response;

/**
 * 语音控制无人机 Activity（重构版）
 * 图传、语音识别、飞控分别拆分到独立 Helper 类
 */
public class VoiceControlActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final String LOG_TAG = "VoiceControl";

    // UI
    private Button btnEnableVirtualStick, btnDisableVirtualStick, btnTakeOff, btnVoice, btnLand;
    private TextView tvVoiceResult, tvStatus;
    private SurfaceView videoSurface;

    // Helpers
    private VideoFeedManager videoFeedManager;
    private VoiceRecognitionHelper voiceHelper;
    private FlightControllerHelper flightHelper;

    private Handler handler = new Handler();

    // ==================== 生命周期 ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_control);

        videoSurface = findViewById(R.id.live_stream_surface);
        videoSurface.getHolder().addCallback(this);

        // 初始化图传管理
        videoFeedManager = new VideoFeedManager(this, videoSurface);

        // 初始化飞控管理
        flightHelper = new FlightControllerHelper(msg ->
                ToastUtils.setResultToToast(msg)
        );

        // 初始化语音识别
        voiceHelper = new VoiceRecognitionHelper(this, new VoiceRecognitionHelper.RecognitionCallback() {
            @Override
            public void onResult(String recognizedText) {
                runOnUiThread(() -> {
                    tvVoiceResult.setText("识别结果: " + recognizedText);
                    executeVoiceCommand(recognizedText);
                });
            }

            @Override
            public void onError(String errorMsg) {
                runOnUiThread(() -> tvVoiceResult.setText(errorMsg));
            }

            @Override
            public void onStatus(String statusMsg) {
                runOnUiThread(() -> tvVoiceResult.setText(statusMsg));
            }
        });

        initUI();
        checkPermissions();
        // 图传在 surfaceCreated 回调中自动初始化，无需手动调用
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (videoFeedManager != null) videoFeedManager.onSurfaceCreated();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (videoFeedManager != null) videoFeedManager.onSurfaceDestroyed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (videoFeedManager != null) videoFeedManager.destroy();
        if (voiceHelper != null) voiceHelper.release();
        if (flightHelper != null) flightHelper.release();
    }

    // ==================== 权限 ====================

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 100);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            ToastUtils.setResultToToast("需要麦克风权限才能使用语音控制");
        }
    }

    // ==================== UI ====================

    private void initUI() {
        btnEnableVirtualStick = findViewById(R.id.btn_enable_virtual_stick);
        btnDisableVirtualStick = findViewById(R.id.btn_disable_virtual_stick);
        btnTakeOff = findViewById(R.id.btn_take_off);
        btnVoice = findViewById(R.id.btn_voice);
        tvVoiceResult = findViewById(R.id.tv_voice_result);
        tvStatus = findViewById(R.id.textview_simulator);
        btnLand = findViewById(R.id.btn_land);

        btnLand.setOnClickListener(v -> flightHelper.landing());
        btnEnableVirtualStick.setOnClickListener(v -> flightHelper.enableVirtualStick());
        btnDisableVirtualStick.setOnClickListener(v -> flightHelper.disableVirtualStick());
        btnTakeOff.setOnClickListener(v -> flightHelper.takeOff());
        btnVoice.setOnClickListener(v -> voiceHelper.startListening());
    }

    // ==================== LLM 指令解析 (DeepSeek) ====================

    private void executeVoiceCommand(String spokenText) {
        String url = "https://api.deepseek.com/chat/completions";
        String apiKey = "yourApi_key";

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(60, TimeUnit.SECONDS)
                        .build();

                String prompt = buildPrompt(spokenText);
                Log.d(LOG_TAG, "发送给 DeepSeek: " + prompt);

                JSONObject body = new JSONObject();
                body.put("model", "deepseek-chat");
                body.put("temperature", 0.7);
                body.put("max_tokens", 1000);
                body.put("stream", false);

                JSONArray messages = new JSONArray();
                JSONObject message = new JSONObject();
                message.put("role", "user");
                message.put("content", prompt);
                messages.put(message);
                body.put("messages", messages);

                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(
                                MediaType.parse("application/json"),
                                body.toString()))
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Response response = client.newCall(request).execute();
                String result = response.body().string();

                if (!response.isSuccessful()) {
                    showError("HTTP错误: " + response.code());
                    return;
                }

                JSONObject json = new JSONObject(result);

                if (json.has("error")) {
                    showError("DeepSeek错误: " +
                            json.getJSONObject("error").getString("message"));
                    return;
                }

                String responseText = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content").trim();

                Log.d(LOG_TAG, "DeepSeek 响应: " + responseText);

                // 解析 JSON 指令
                parseAndExecute(responseText);

            } catch (Exception e) {
                Log.e(LOG_TAG, "LLM调用失败", e);
                showError("调用LLM失败: " + e.getClass().getSimpleName());
            }
        }).start();
    }

    private void parseAndExecute(String text) {
        String action = null;
        int value = 0;

        // 尝试直接解析 JSON
        try {
            JSONObject cmd = new JSONObject(text);
            action = cmd.getString("action");
            value = cmd.optInt("value", 0);
        } catch (JSONException e1) {
            // 尝试提取 {...} 块
            try {
                int start = text.indexOf('{');
                int end = text.lastIndexOf('}');
                if (start != -1 && end != -1 && end > start) {
                    JSONObject cmd = new JSONObject(text.substring(start, end + 1));
                    action = cmd.getString("action");
                    value = cmd.optInt("value", 0);
                }
            } catch (JSONException e2) {
                Log.e(LOG_TAG, "JSON解析失败", e2);
            }
        }

        if (action == null) {
            showError("无法解析指令: " + text);
            return;
        }

        final String finalAction = action;
        final int finalValue = value;
        runOnUiThread(() -> {
            tvVoiceResult.setText("指令: " + finalAction + " " + finalValue);
            executeFlightAction(finalAction, finalValue);
        });
    }

    private void executeFlightAction(String action, int value) {
        switch (action) {
            case "forward":
                flightHelper.moveForward(0.3f);
                handler.postDelayed(() -> flightHelper.hover(), value * 1000);
                break;
            case "backward":
                flightHelper.moveBackward(0.3f);
                handler.postDelayed(() -> flightHelper.hover(), value * 1000);
                break;
            case "left":
                flightHelper.turnLeft(value);
                break;
            case "right":
                flightHelper.turnRight(value);
                break;
            case "takeoff":
                flightHelper.takeOff();
                break;
            case "land":
                flightHelper.landing();
                break;
            case "hover":
                flightHelper.hover();
                break;
            case "unknown":
                tvVoiceResult.setText("指令无法识别");
                break;
            default:
                tvVoiceResult.setText("未知动作: " + action);
        }
    }

    private String buildPrompt(String command) {
        return "你是一个无人机飞行控制接口。将用户自然语言指令转换为严格 JSON。\n" +
                "\n约束：\n" +
                "1. 只输出 JSON，不要任何额外文本\n" +
                "2. 飞行距离最大 5 米，旋转角度最大 90 度\n" +
                "3. 无法理解时输出 {\"action\": \"unknown\", \"value\": 0}\n" +
                "\n可用的 action：\n" +
                "- takeoff: 起飞 {\"action\": \"takeoff\", \"value\": 0}\n" +
                "- land: 降落 {\"action\": \"land\", \"value\": 0}\n" +
                "- forward: 向前 {\"action\": \"forward\", \"value\": 米数}\n" +
                "- backward: 向后 {\"action\": \"backward\", \"value\": 米数}\n" +
                "- left: 左转 {\"action\": \"left\", \"value\": 角度}\n" +
                "- right: 右转 {\"action\": \"right\", \"value\": 角度}\n" +
                "- hover: 悬停 {\"action\": \"hover\", \"value\": 0}\n" +
                "\n用户：" + command + "\n模型：";
    }

    private void showError(String msg) {
        runOnUiThread(() -> tvVoiceResult.setText(msg));
    }
}
