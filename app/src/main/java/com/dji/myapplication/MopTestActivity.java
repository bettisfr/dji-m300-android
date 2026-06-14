package com.dji.myapplication;

import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dji.sdk.keyvalue.value.mop.PipelineDeviceType;
import dji.sdk.keyvalue.value.mop.TransmissionControlType;
import dji.v5.common.error.IDJIError;
import dji.v5.common.register.DJISDKInitEvent;
import dji.v5.manager.SDKManager;
import dji.v5.manager.interfaces.IPipelineManager;
import dji.v5.manager.interfaces.SDKManagerCallback;
import dji.v5.manager.mop.DataResult;
import dji.v5.manager.mop.Pipeline;
import dji.v5.manager.mop.PipelineConnectionListener;
import dji.v5.manager.mop.PipelineManager;

public class MopTestActivity extends AppCompatActivity {
    private static final String TAG = "MopTestActivity";
    private static final int CHANNEL_ID = 49152;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final StringBuilder logText = new StringBuilder();

    private IPipelineManager pipelineManager;
    private Pipeline pipeline;
    private TextView statusView;
    private TextView logView;
    private ScrollView logScrollView;
    private Button connectButton;
    private List<Button> commandButtons;

    private final PipelineConnectionListener connectionListener =
            this::onPipelineConnectionUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_mop_test);

        statusView = findViewById(R.id.mop_status);
        logView = findViewById(R.id.mop_log);
        logScrollView = findViewById(R.id.mop_log_scroll);
        connectButton = findViewById(R.id.mop_connect);
        Button pingButton = findViewById(R.id.mop_ping);
        Button inferButton = findViewById(R.id.mop_infer);
        Button yawMinusButton = findViewById(R.id.mop_yaw_minus);
        Button yawPlusButton = findViewById(R.id.mop_yaw_plus);
        Button pitchMinusButton = findViewById(R.id.mop_pitch_minus);
        Button pitchPlusButton = findViewById(R.id.mop_pitch_plus);
        commandButtons = Arrays.asList(
                pingButton,
                inferButton,
                yawMinusButton,
                yawPlusButton,
                pitchMinusButton,
                pitchPlusButton);

        connectButton.setOnClickListener(view -> connect());
        pingButton.setOnClickListener(view -> sendCommand("PING"));
        inferButton.setOnClickListener(view -> runInference());
        yawMinusButton.setOnClickListener(view -> sendCommand("GIMBAL_YAW -10"));
        yawPlusButton.setOnClickListener(view -> sendCommand("GIMBAL_YAW 10"));
        pitchMinusButton.setOnClickListener(view -> sendCommand("GIMBAL_PITCH -10"));
        pitchPlusButton.setOnClickListener(view -> sendCommand("GIMBAL_PITCH 10"));
        setCommandButtonsEnabled(false);

        registerDjiApp();
    }

    private void registerDjiApp() {
        if (SDKManager.getInstance().isRegistered()) {
            appendLog("DJI application already registered");
            connectButton.setEnabled(true);
            return;
        }

        appendLog("Initializing DJI MSDK 5...");
        SDKManager.getInstance().init(this, new SDKManagerCallback() {
            @Override
            public void onRegisterSuccess() {
                appendLog("DJI application registered");
                runOnUiThread(() -> connectButton.setEnabled(true));
            }

            @Override
            public void onRegisterFailure(IDJIError error) {
                appendLog("DJI registration failed: " + formatError(error));
            }

            @Override
            public void onProductDisconnect(int productId) {
                appendLog("Aircraft disconnected: " + productId);
            }

            @Override
            public void onProductConnect(int productId) {
                appendLog("Aircraft connected: " + productId);
            }

            @Override
            public void onProductChanged(int productId) {
                appendLog("Aircraft changed: " + productId);
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp();
                }
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                Log.d(TAG, "Database download: " + current + "/" + total);
            }
        });
    }

    private void connect() {
        if (pipelineManager == null) {
            pipelineManager = PipelineManager.getInstance();
            pipelineManager.init();
            pipelineManager.addPipelineConnectionListener(connectionListener);
        }

        setStatus("Connecting...");
        appendLog("Connecting to ONBOARD channel " + CHANNEL_ID + " (STABLE)");
        ioExecutor.execute(() -> {
            IDJIError error = pipelineManager.connectPipeline(
                    CHANNEL_ID,
                    PipelineDeviceType.ONBOARD,
                    TransmissionControlType.STABLE);
            if (error != null) {
                appendLog("Connect failed: " + formatError(error));
                setStatus("Connection failed");
            }
        });
    }

    private void onPipelineConnectionUpdate(Map<Integer, Pipeline> pipelines) {
        Pipeline connectedPipeline = pipelines.get(CHANNEL_ID);
        if (connectedPipeline == null) {
            pipeline = null;
            setStatus("Disconnected");
            setCommandButtonsEnabled(false);
            return;
        }

        pipeline = connectedPipeline;
        setStatus("Connected");
        appendLog("MOP pipeline connected: " + connectedPipeline);
        setCommandButtonsEnabled(true);
    }

    private void sendCommand(String command) {
        if (pipeline == null) {
            return;
        }

        setCommandButtonsEnabled(false);
        ioExecutor.execute(() -> {
            exchangeCommand(command, true);
            if (command.startsWith("GIMBAL_")) {
                try {
                    Thread.sleep(2500);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            setCommandButtonsEnabled(true);
        });
    }

    private void runInference() {
        if (pipeline == null) {
            return;
        }

        setCommandButtonsEnabled(false);
        ioExecutor.execute(() -> {
            String response = exchangeCommand("INFER", true);
            if (!"ACCEPTED INFER".equals(response)) {
                setCommandButtonsEnabled(true);
                return;
            }

            for (int attempt = 0; attempt < 40; attempt++) {
                try {
                    Thread.sleep(750);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }

                String status = exchangeCommand("INFER_STATUS", false);
                if (status == null) {
                    break;
                }
                if (status.startsWith("RESULT ")) {
                    appendLog("RX: " + status);
                    setCommandButtonsEnabled(true);
                    return;
                }
            }
            appendLog("Inference timed out");
            setCommandButtonsEnabled(true);
        });
    }

    private String exchangeCommand(String command, boolean logTraffic) {
        Pipeline currentPipeline = pipeline;
        if (currentPipeline == null) {
            return null;
        }

        if (logTraffic) {
            appendLog("TX: " + command);
        }
        byte[] bytes = (command + "\n").getBytes(StandardCharsets.UTF_8);
        DataResult writeResult = currentPipeline.writeData(bytes);
        if (writeResult.getError() != null) {
            appendLog("Write failed: " + formatError(writeResult.getError()));
            return null;
        }

        byte[] buffer = new byte[4096];
        DataResult readResult = currentPipeline.readData(buffer);
        if (readResult.getError() != null) {
            appendLog("Read failed: " + formatError(readResult.getError()));
            return null;
        }
        if (readResult.getLength() <= 0) {
            return "";
        }

        String response = new String(
                buffer,
                0,
                readResult.getLength(),
                StandardCharsets.UTF_8).trim();
        if (logTraffic) {
            appendLog("RX: " + response);
        }
        return response;
    }

    private void setCommandButtonsEnabled(boolean enabled) {
        runOnUiThread(() -> {
            for (Button button : commandButtons) {
                button.setEnabled(enabled);
            }
        });
    }

    private void setStatus(String status) {
        runOnUiThread(() -> statusView.setText(status));
    }

    private void appendLog(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> {
            if (logText.length() > 0) {
                logText.append('\n');
            }
            logText.append(message);
            logView.setText(logText.toString());
            logScrollView.post(() -> logScrollView.fullScroll(View.FOCUS_DOWN));
        });
    }

    private static String formatError(IDJIError error) {
        return error == null ? "none" : error.errorCode() + ": " + error.description();
    }

    @Override
    protected void onDestroy() {
        if (pipelineManager != null) {
            pipelineManager.removePipelineConnectionListener(connectionListener);
            pipelineManager.disconnectPipeline(
                    CHANNEL_ID,
                    PipelineDeviceType.ONBOARD,
                    TransmissionControlType.STABLE);
            pipelineManager.destroy();
        }
        ioExecutor.shutdownNow();
        super.onDestroy();
    }
}
