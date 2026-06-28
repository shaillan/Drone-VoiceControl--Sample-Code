package com.dji.sdk.sample.demo.flightcontroller;

import android.app.Activity;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 百度语音识别模块 —— 录音 + 在线识别
 */
public class VoiceRecognitionHelper {

    private static final String TAG = "VoiceRecognitionHelper";

    // 百度语音配置
    private static final String API_KEY = "NdWwDA5EuGkZgyqDzyPhRrBT";
    private static final String SECRET_KEY = "ptdFtHYiuDT4aWITmD4rTmhkKKSQmntF";
    private static final int SAMPLE_RATE = 16000;

    private Activity activity;
    private Handler handler = new Handler(Looper.getMainLooper());
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String audioFilePath;
    private RecognitionCallback callback;

    public interface RecognitionCallback {
        void onResult(String recognizedText);
        void onError(String errorMsg);
        void onStatus(String statusMsg);
    }

    public VoiceRecognitionHelper(Activity activity, RecognitionCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    // ==================== 对外接口 ====================

    public void startListening() {
        if (isRecording) {
            callback.onStatus("正在识别中");
            return;
        }
        startRecording();
    }

    public void release() {
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }
        isRecording = false;
    }

    // ==================== 录音 ====================

    private void startRecording() {
        try {
            audioFilePath = activity.getExternalCacheDir().getAbsolutePath() + "/temp_audio.m4a";
            File audioFile = new File(audioFilePath);
            if (audioFile.exists()) audioFile.delete();

            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            mediaRecorder.setAudioChannels(1);
            mediaRecorder.setAudioEncodingBitRate(32000);
            mediaRecorder.setOutputFile(audioFilePath);

            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            callback.onStatus("请说话...");

            // 3 秒后自动停止
            handler.postDelayed(this::stopAndRecognize, 3000);

        } catch (Exception e) {
            Log.e(TAG, "录音初始化失败", e);
            callback.onError("录音初始化失败: " + e.getMessage());
        }
    }

    private void stopAndRecognize() {
        if (!isRecording || mediaRecorder == null) return;

        try {
            isRecording = false;
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            File audioFile = new File(audioFilePath);
            long size = audioFile.length();
            Log.d(TAG, "录音文件大小: " + size + " 字节");

            if (size < 1000) {
                callback.onStatus("录音太短，请再说一遍");
                return;
            }

            callback.onStatus("识别中...");
            recognizeFile(audioFilePath);

        } catch (Exception e) {
            Log.e(TAG, "停止录音失败", e);
            callback.onError("录音停止失败: " + e.getMessage());
        }
    }

    // ==================== 百度识别 ====================

    private void recognizeFile(String filePath) {
        new Thread(() -> {
            try {
                String accessToken = fetchAccessToken();
                if (accessToken == null) {
                    notifyError("获取 access_token 失败");
                    return;
                }

                // 读取音频并 Base64
                File audioFile = new File(filePath);
                byte[] audioData = new byte[(int) audioFile.length()];
                FileInputStream fis = new FileInputStream(audioFile);
                fis.read(audioData);
                fis.close();

                String base64Audio = android.util.Base64.encodeToString(
                        audioData, android.util.Base64.NO_WRAP);

                // 调百度 API
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
                params.put("dev_pid", 1537);
                params.put("speech", base64Audio);
                params.put("len", audioData.length);

                DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                out.write(params.toString().getBytes());
                out.flush();
                out.close();

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code == 200 ? conn.getInputStream() : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resultJson = new JSONObject(sb.toString());
                int errNo = resultJson.optInt("err_no", -1);

                if (errNo == 0) {
                    JSONArray results = resultJson.getJSONArray("result");
                    if (results != null && results.length() > 0) {
                        String text = results.getString(0);
                        notifyResult(text);
                    } else {
                        notifyError("未识别到内容，请重试");
                    }
                } else {
                    String errMsg = resultJson.optString("err_msg", "未知错误");
                    notifyError("识别失败: " + errMsg);
                }

            } catch (Exception e) {
                Log.e(TAG, "识别异常", e);
                notifyError("识别异常: " + e.getMessage());
            }
        }).start();
    }

    private String fetchAccessToken() {
        try {
            String urlStr = "https://aip.baidubce.com/oauth/2.0/token"
                    + "?grant_type=client_credentials"
                    + "&client_id=" + API_KEY
                    + "&client_secret=" + SECRET_KEY;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            JSONObject json = new JSONObject(sb.toString());
            return json.optString("access_token");
        } catch (Exception e) {
            Log.e(TAG, "获取 token 失败", e);
            return null;
        }
    }

    // ==================== 回调辅助 ====================

    private void notifyResult(String text) {
        handler.post(() -> callback.onResult(text));
    }

    private void notifyError(String msg) {
        handler.post(() -> callback.onError(msg));
    }
}
