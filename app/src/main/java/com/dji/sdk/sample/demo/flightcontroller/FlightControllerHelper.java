package com.dji.sdk.sample.demo.flightcontroller;

import android.os.Handler;
import android.util.Log;

import com.dji.sdk.sample.internal.controller.DJISampleApplication;
import com.dji.sdk.sample.internal.utils.ModuleVerificationUtil;
import com.dji.sdk.sample.internal.utils.ToastUtils;

import dji.common.flightcontroller.virtualstick.FlightControlData;
import dji.common.flightcontroller.virtualstick.FlightCoordinateSystem;
import dji.common.flightcontroller.virtualstick.RollPitchControlMode;
import dji.common.flightcontroller.virtualstick.VerticalControlMode;
import dji.common.flightcontroller.virtualstick.YawControlMode;
import dji.sdk.flightcontroller.FlightController;

/**
 * DJI 飞行控制器管理 —— 虚拟摇杆、起飞降落、方向控制
 */
public class FlightControllerHelper {

    private static final String TAG = "FlightControllerHelper";

    private FlightController flightController;
    private Handler handler = new Handler();
    private SendTask sendTask;

    // 飞行参数
    private float pitch, roll, yaw, throttle;

    public interface StatusCallback {
        void onStatus(String msg);
    }

    private StatusCallback callback;

    public FlightControllerHelper(StatusCallback callback) {
        this.callback = callback;
    }

    // ==================== 虚拟摇杆 ====================

    public void enableVirtualStick() {
        ensureFlightController();
        if (flightController == null) {
            callback.onStatus("飞行遥控器不可用，确保飞机已连接");
            return;
        }

        flightController.setVerticalControlMode(VerticalControlMode.VELOCITY);
        flightController.setRollPitchControlMode(RollPitchControlMode.VELOCITY);
        flightController.setYawControlMode(YawControlMode.ANGULAR_VELOCITY);
        flightController.setRollPitchCoordinateSystem(FlightCoordinateSystem.BODY);

        flightController.setVirtualStickModeEnabled(true, error -> {
            if (error == null) {
                flightController.setVirtualStickAdvancedModeEnabled(true);
                startSendingData();
                callback.onStatus("虚拟摇杆已开启");
            } else {
                callback.onStatus("开启失败: " + error.getDescription());
            }
        });
    }

    public void disableVirtualStick() {
        if (flightController != null) {
            flightController.setVirtualStickModeEnabled(false, error -> {
                if (error == null) stopSendingData();
            });
        }
    }

    // ==================== 起飞 / 降落 ====================

    public void takeOff() {
        ensureFlightController();
        if (flightController != null) {
            flightController.startTakeoff(error -> {
                if (error != null) {
                    callback.onStatus("起飞失败: " + error.getDescription());
                }
            });
        } else {
            callback.onStatus("飞行控制器不可用");
        }
    }

    public void landing() {
        if (flightController != null) {
            flightController.startLanding(error -> {
                if (error != null) {
                    callback.onStatus("降落失败: " + error.getDescription());
                }
            });
        }
    }

    // ==================== 方向控制 ====================

    public void moveForward(float value) { this.pitch = value; }
    public void moveBackward(float value) { this.pitch = -value; }
    public void turnLeft(float value) { this.yaw = -value; }
    public void turnRight(float value) { this.yaw = value; }
    public void turnaround(float value) { this.yaw = -value; }
    public void hover() { pitch = roll = yaw = throttle = 0; }

    // ==================== 生命周期 ====================

    public void release() {
        stopSendingData();
    }

    public FlightController getFlightController() {
        ensureFlightController();
        return flightController;
    }

    // ==================== 内部实现 ====================

    private void ensureFlightController() {
        if (flightController == null && ModuleVerificationUtil.isFlightControllerAvailable()) {
            flightController = DJISampleApplication.getAircraftInstance().getFlightController();
        }
    }

    private void startSendingData() {
        if (sendTask != null) return;
        sendTask = new SendTask();
        handler.postDelayed(sendTask,  100);
    }

    private void stopSendingData() {
        if (sendTask != null) {
            handler.removeCallbacks(sendTask);
            sendTask = null;
        }
    }

    private class SendTask implements Runnable {
        @Override
        public void run() {
            if (flightController != null) {
                flightController.sendVirtualStickFlightControlData(
                        new FlightControlData(roll, pitch, yaw, throttle), null);
            }
            handler.postDelayed(this, 100);
        }
    }
}
