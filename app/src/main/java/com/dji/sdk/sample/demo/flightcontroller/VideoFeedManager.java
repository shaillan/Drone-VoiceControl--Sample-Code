package com.dji.sdk.sample.demo.flightcontroller;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

import dji.sdk.camera.VideoFeeder;
import dji.sdk.codec.DJICodecManager;

/**
 * 管理 DJI 无人机图传（视频流）的显示
 *
 * 核心逻辑：
 * 1. Surface 创建后立即初始化编码器和注册监听器（不需要等飞机连接）
 * 2. 飞机连上后会自动推送视频数据 → VideoFeeder → 解码 → SurfaceView
 */
public class VideoFeedManager {

    private static final String TAG = "VideoFeedManager";

    private Context context;
    private SurfaceView videoSurface;
    private DJICodecManager codecManager;
    private boolean initialized = false;

    public VideoFeedManager(Context context, SurfaceView videoSurface) {
        this.context = context;
        this.videoSurface = videoSurface;
    }

    // ==================== 对外接口 ====================

    /**
     * Surface 创建后立即调用 —— 不管飞机连没连，先把接收端准备好
     */
    public void onSurfaceCreated() {
        if (initialized) return;

        if (videoSurface == null || !videoSurface.getHolder().getSurface().isValid()) {
            Log.w(TAG, "Surface 无效，暂不初始化");
            return;
        }

        // 1. 创建解码器
        try {
            codecManager = new DJICodecManager(
                    context,
                    videoSurface.getHolder(),
                    0, 0
            );
            Log.d(TAG, "DJICodecManager 创建成功");
        } catch (Exception e) {
            Log.e(TAG, "创建 DJICodecManager 失败", e);
            return;
        }

        // 2. 注册视频数据监听（提前准备好，飞机连上后自动接收）
        try {
            VideoFeeder.VideoFeed primaryFeed = VideoFeeder.getInstance().getPrimaryVideoFeed();
            if (primaryFeed != null) {
                primaryFeed.addVideoDataListener(new VideoFeeder.VideoDataListener() {
                    @Override
                    public void onReceive(byte[] videoData, int dataSize) {
                        if (codecManager != null && videoData != null && dataSize > 0) {
                            codecManager.sendDataToDecoder(videoData, dataSize);
                        }
                    }
                });
                Log.d(TAG, "视频数据监听器注册成功");
            } else {
                Log.e(TAG, "getPrimaryVideoFeed() 返回 null");
            }
        } catch (Exception e) {
            Log.e(TAG, "注册视频监听失败", e);
            return;
        }

        initialized = true;
        Log.d(TAG, "图传模块初始化完成，等待飞机推送视频流...");
    }

    /**
     * Surface 销毁时调用
     */
    public void onSurfaceDestroyed() {
        initialized = false;
        if (codecManager != null) {
            try {
                codecManager.destroyCodec();
            } catch (Exception e) {
                Log.e(TAG, "销毁编码器异常", e);
            }
            codecManager = null;
        }
        Log.d(TAG, "图传模块已释放");
    }

    /**
     * 释放所有资源（Activity onDestroy 时调用）
     */
    public void destroy() {
        onSurfaceDestroyed();
    }
}
