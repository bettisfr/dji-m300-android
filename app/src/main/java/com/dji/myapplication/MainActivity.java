package com.dji.myapplication;

import static org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap;
import static org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement;
import static org.maplibre.android.style.layers.PropertyFactory.iconImage;
import static org.maplibre.android.style.layers.PropertyFactory.iconRotate;
import static org.maplibre.android.style.layers.PropertyFactory.lineColor;
import static org.maplibre.android.style.layers.PropertyFactory.lineWidth;

import android.animation.ObjectAnimator;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.wpmzsdk.common.data.KMZInfo;
import com.dji.wpmzsdk.manager.WPMZManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.maplibre.android.MapLibre;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.LineLayer;
import org.maplibre.android.style.layers.SymbolLayer;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.Feature;
import org.maplibre.geojson.FeatureCollection;
import org.maplibre.geojson.LineString;
import org.maplibre.geojson.Point;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.EmptyMsg;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.rtkbasestation.RTKCustomNetworkSetting;
import dji.sdk.keyvalue.value.rtkbasestation.RTKReferenceStationSource;
import dji.sdk.keyvalue.value.rtkbasestation.RTKServiceState;
import dji.sdk.wpmz.value.mission.WaylineExecuteWaypoint;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.common.register.DJISDKInitEvent;
import dji.v5.manager.KeyManager;
import dji.v5.manager.SDKManager;
import dji.v5.manager.aircraft.rtk.RTKCenter;
import dji.v5.manager.aircraft.rtk.network.INetworkServiceInfoListener;
import dji.v5.manager.aircraft.simulator.InitializationSettings;
import dji.v5.manager.aircraft.simulator.SimulatorManager;
import dji.v5.manager.aircraft.waypoint3.WaypointActionListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.camera.CameraStreamManager;
import dji.v5.manager.interfaces.ICameraStreamManager;
import dji.v5.manager.interfaces.SDKManagerCallback;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    DecimalFormat decimalFormat = new DecimalFormat("#.##");
    DecimalFormat integerFormat = new DecimalFormat("#");
    // Other stuff
    LocationCoordinate2D droneHomeLocation = new LocationCoordinate2D(43.062094, 12.549959);
    LocationCoordinate2D droneCurrentLocation = new LocationCoordinate2D(43.062094, 12.549959);
    private Button btnStartStopMission;
    private Button btnPauseResumeMission;
    private TextView tvDroneConnected;
    private TextView tvBatteryPercentage;
    private TextView tvSimulator;
    private TextView tvDroneYaw;
    private TextView tvDroneAltitude;
    private TextView tvGimbalPitchYaw;
    private TextView tvRtk;
    private TextView tvLog;
    private MapView mapView;
    private MapLibreMap gMap;
    private Style mapStyle;
    private SurfaceView svCameraStream;
    private boolean isRightPanelOpen = true;
    private boolean isLeftPanelOpen = true;
    private boolean isMissionStarted = false;
    private boolean isMissionPaused = false;
    private double droneHeading;

    public static boolean checkGPSCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        MapLibre.getInstance(this);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN       // Hide status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide navigation bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // Enable immersive mode
        );

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        initUI();
        initRGBCamera();
        registerApp();
    }

    public void initUI() {
        SwitchMaterial switchSimulator = findViewById(R.id.switch_simulator);
        SwitchMaterial switchRTK = findViewById(R.id.switch_rtk);
        Button btnLoadMission = findViewById(R.id.button_load_mission);
        btnStartStopMission = findViewById(R.id.button_start_stop_mission);
        btnPauseResumeMission = findViewById(R.id.button_pause_resume_mission);

        tvDroneConnected = findViewById(R.id.textview_is_drone_connected);
        tvBatteryPercentage = findViewById(R.id.textview_battery);
        tvSimulator = findViewById(R.id.textview_is_simulator_on);
        tvDroneYaw = findViewById(R.id.textview_drone_yaw);
        tvDroneAltitude = findViewById(R.id.textview_drone_altitude);
        tvGimbalPitchYaw = findViewById(R.id.textview_gimbal_pitch_yaw);

        tvLog = findViewById(R.id.textview_log);
        tvRtk = findViewById(R.id.textview_rtk);

        Button toggleButton = findViewById(R.id.button_toggle_view);
        svCameraStream = findViewById(R.id.sv_camera_stream);
        mapView = findViewById(R.id.map_view);
        mapView.setVisibility(View.GONE);

        View slidingRightPanel = findViewById(R.id.sliding_panel_right);
        Button toggleButtonRight = findViewById(R.id.button_toggle_panel_right);

        View slidingLeftPanel = findViewById(R.id.sliding_panel_left);
        Button toggleButtonLeft = findViewById(R.id.button_toggle_panel_left);

        toggleButtonLeft.setOnClickListener(v -> {
            if (isLeftPanelOpen) {
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        slidingLeftPanel, "translationX", 0f, -slidingLeftPanel.getWidth());
                animator.setDuration(300);
                animator.start();
                toggleButtonLeft.setText(getString(R.string.slide_right));
            } else {
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        slidingLeftPanel, "translationX", -slidingLeftPanel.getWidth(), 0f);
                animator.setDuration(300);
                animator.start();
                toggleButtonLeft.setText(getString(R.string.slide_left));
            }
            isLeftPanelOpen = !isLeftPanelOpen;
        });

        toggleButtonRight.setOnClickListener(v -> {
            if (isRightPanelOpen) {
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        slidingRightPanel, "translationX", 0f, slidingRightPanel.getWidth());
                animator.setDuration(300);
                animator.start();
                toggleButtonRight.setText(getString(R.string.slide_left));
            } else {
                ObjectAnimator animator = ObjectAnimator.ofFloat(
                        slidingRightPanel, "translationX", slidingRightPanel.getWidth(), 0f);
                animator.setDuration(300);
                animator.start();
                toggleButtonRight.setText(getString(R.string.slide_right));
            }
            isRightPanelOpen = !isRightPanelOpen;
        });

        toggleButton.setOnClickListener(v -> {
            if (svCameraStream.getVisibility() == View.VISIBLE) {
                svCameraStream.setVisibility(View.GONE);
                mapView.setVisibility(View.VISIBLE);
                toggleButton.setText(getString(R.string.toggle_left_view_camera));
            } else {
                svCameraStream.setVisibility(View.VISIBLE);
                initRGBCamera();
                mapView.setVisibility(View.GONE);
                toggleButton.setText(getString(R.string.toggle_left_view_map));
            }
        });

        switchSimulator.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Log.i(TAG, "startSimulator");
                startSimulator();
            } else {
                Log.i(TAG, "startSimulator");
                stopSimulator();
            }
        });

        switchRTK.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                Log.i(TAG, "startRTK");
                startRTK();
            } else {
                Log.i(TAG, "stopRTK");
                stopRTK();
            }
        });

        btnLoadMission.setOnClickListener(view -> {
            Log.i(TAG, "loadMission");
            loadMission(view);
        });

        btnStartStopMission.setOnClickListener(view -> {
            if (isMissionStarted) {
                Log.i(TAG, "stopMission - onClick");
                stopMission();
            } else {
                Log.i(TAG, "startMission - onClick");
                startMission();
            }
        });

        btnPauseResumeMission.setOnClickListener(view -> {
            if (isMissionPaused) {
                Log.i(TAG, "resumeMission - onClick");
                resumeMission();
            } else {
                Log.i(TAG, "pauseMission - onClick");
                pauseMission();
            }
        });

        mapView.getMapAsync(map -> {
            if (gMap == null) {
                gMap = map;

                gMap.setCameraPosition(new CameraPosition.Builder()
                        .target(new LatLng(droneHomeLocation.getLatitude(), droneHomeLocation.getLongitude()))
                        .zoom(18)
                        .build());

                gMap.setStyle(TestStyles.AWS_OPEN_DATA_STANDARD_LIGHT, style -> {
                    mapStyle = style;
//                    markerViewManager = new MarkerViewManager(mapView, gMap);
//                    lineManager = new LineManager(mapView, gMap, style);

                    updateDroneHomeAndCurrentLocation();
                });
            }
        });
    }

    private void loadMission(View view) {
        File kmzFile = Util.copyKMZToExternalStorage(view.getContext(), "mission.kmz");
        WaypointMissionManager.getInstance().init();
        KMZInfo kmzInfo = WPMZManager.getInstance().getKMZInfo(kmzFile.getAbsolutePath());

        Log.d("Command Execution :", "Checking file info: " + kmzFile.getAbsolutePath() + ": " + kmzInfo.getWaylineMissionConfigParseInfo().toString());

        setMissionOnMap(kmzInfo);

        WaypointMissionManager.getInstance().pushKMZFileToAircraft(kmzFile.getAbsolutePath(), new CommonCallbacks.CompletionCallbackWithProgress<>() {
            @Override
            public void onProgressUpdate(Double progress) {
                Log.d(TAG, "Upload progress: " + progress + "%");
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "KMZ mission uploaded successfully.");
                btnStartStopMission.setEnabled(true);
                btnStartStopMission.setText(getString(R.string.start_mission_button));

                WaypointMissionManager.getInstance().addWaypointActionListener(new WaypointActionListener() {
                    @Override
                    public void onExecutionStart(int actionId) {
                        Log.i(TAG, "onExecutionStart - actionId=" + actionId);
                    }

                    @Override
                    public void onExecutionFinish(int actionId, @Nullable IDJIError error) {
                        Log.i(TAG, "onExecutionFinish - actionId=" + actionId);
                    }

                    @Override
                    public void onExecutionStart(int actionGroup, int actionId) {
                        Log.i(TAG, "onExecutionStart - actionGroup=" + actionGroup + ", actionId=" + actionId);

                        tvLog.setText(getString(R.string.starting_wp_action, actionGroup, actionId));
                    }

                    @Override
                    public void onExecutionFinish(int actionGroup, int actionId, @Nullable IDJIError error) {
                        Log.i(TAG, "onExecutionFinish - actionGroup=" + actionGroup + ", actionId=" + actionId);

                        tvLog.setText(getString(R.string.finishing_wp_action, actionGroup, actionId));
                    }
                });
            }


            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.e(TAG, "Failed to upload KMZ mission: " + idjiError);
            }
        });
    }

    private void startMission() {
        WaypointMissionManager.getInstance().startMission("mission", new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "startMission - onClick - onSuccess");
                isMissionStarted = true;
                btnStartStopMission.setText(getString(R.string.stop_mission_button));
                btnPauseResumeMission.setEnabled(true);
                btnPauseResumeMission.setText(getString(R.string.pause_mission_button));
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "startMission - onClick - onFailure: " + idjiError);
            }
        });
    }

    private void stopMission() {
        WaypointMissionManager.getInstance().stopMission("mission", new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "stopMission - onClick - onSuccess");
                isMissionStarted = false;
                btnStartStopMission.setText(getString(R.string.start_mission_button));
                btnStartStopMission.setEnabled(false);
                btnPauseResumeMission.setEnabled(false);

                startRTH();
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "startMission - onClick - onFailure: " + idjiError);
            }
        });
    }

    private void startRTH() {
        KeyManager.getInstance().performAction(KeyTools.createKey(FlightControllerKey.KeyStartGoHome), new CommonCallbacks.CompletionCallbackWithParam<>() {
            @Override
            public void onSuccess(EmptyMsg emptyMsg) {
                Log.i(TAG, "startRTH - onSuccess");
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "startRTH - onFailure: " + idjiError);
            }
        });
    }

    private void pauseMission() {
        WaypointMissionManager.getInstance().pauseMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "pauseMission - onClick - onSuccess");
                isMissionPaused = true;
                btnPauseResumeMission.setEnabled(true);
                btnPauseResumeMission.setText(getString(R.string.resume_mission_button));
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "pauseMission - onClick - onFailure: " + idjiError);
            }
        });
    }

    private void resumeMission() {
        WaypointMissionManager.getInstance().resumeMission(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "resumeMission - onClick - onSuccess");
                isMissionPaused = false;
                btnPauseResumeMission.setEnabled(true);
                btnPauseResumeMission.setText(getString(R.string.pause_mission_button));
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "resumeMission - onClick - onFailure: " + idjiError);
            }
        });
    }

    private void startRTK() {
        RTKCenter.getInstance().setAircraftRTKModuleEnabled(true, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "setAircraftRTKModuleEnabled - true - onSuccess");

                RTKCenter.getInstance().getAircraftRTKModuleEnabled(new CommonCallbacks.CompletionCallbackWithParam<>() {
                    @Override
                    public void onSuccess(Boolean aBoolean) {
                        Log.i(TAG, "getAircraftRTKModuleEnabled - onSuccess: " + aBoolean.toString());

                        RTKCenter.getInstance().setRTKReferenceStationSource(RTKReferenceStationSource.CUSTOM_NETWORK_SERVICE, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "setRTKReferenceStationSource - onSuccess");

                                RTKCenter.getInstance().getCustomRTKManager().addNetworkRTKServiceInfoListener(new INetworkServiceInfoListener() {
                                    @Override
                                    public void onServiceStateUpdate(RTKServiceState state) {
                                        Log.i(TAG, "addNetworkRTKServiceInfoListener - onServiceStateUpdate:" + state.toString());

                                        RTKCenter.getInstance().getCustomRTKManager().setCustomNetworkRTKSettings(new RTKCustomNetworkSetting(
                                                "46.254.154.14",
                                                2101,
                                                "pinotti_c",
                                                "crpi03r04",
                                                "NRT30"
                                        ));

//                                        RTKCenter.getInstance().getCustomRTKManager().getNetworkRTKCoordinateSystem(new CommonCallbacks.CompletionCallbackWithParam<CoordinateSystem>() {
//                                            @Override
//                                            public void onSuccess(CoordinateSystem coordinateSystem) {
//                                                Log.i(TAG, "getNetworkRTKCoordinateSystem - onSuccess:" + coordinateSystem.toString());
//                                            }
//
//                                            @Override
//                                            public void onFailure(@NonNull IDJIError idjiError) {
//                                                Log.i(TAG, "getNetworkRTKCoordinateSystem - onFailure:" + idjiError);
//                                            }
//                                        });

//                                        RTKCenter.getInstance().getCustomRTKManager().startNetworkRTKService(new CommonCallbacks.CompletionCallback() {
//                                            @Override
//                                            public void onSuccess() {
//                                                Log.i(TAG, "startNetworkRTKService - onSuccess:");
//                                            }
//
//                                            @Override
//                                            public void onFailure(@NonNull IDJIError idjiError) {
//                                                Log.i(TAG, "startNetworkRTKService - onFailure:" + idjiError);
//                                            }
//                                        });

                                        RTKCenter.getInstance().addRTKLocationInfoListener(newValue -> {
                                            double stdLatitude = newValue.getRtkLocation().getStdLatitude();
                                            double stdLongitude = newValue.getRtkLocation().getStdLongitude();
                                            double stdAltitude = newValue.getRtkLocation().getStdAltitude();

                                            tvRtk.setText(String.format(
                                                    "%s, %s, %s",
                                                    decimalFormat.format(stdLatitude),
                                                    decimalFormat.format(stdLongitude),
                                                    decimalFormat.format(stdAltitude)
                                            ));
                                        });

                                        RTKCenter.getInstance().addRTKSystemStateListener(newValue ->
                                                Log.i(TAG, "addRTKSystemStateListener - onUpdate: " + newValue.toString())
                                        );
                                    }

                                    @Override
                                    public void onErrorCodeUpdate(IDJIError code) {
                                        Log.i(TAG, "addNetworkRTKServiceInfoListener - onErrorCodeUpdate: " + code.toString());
                                    }
                                });
                            }

                            @Override
                            public void onFailure(@NonNull IDJIError idjiError) {
                                Log.i(TAG, "setRTKReferenceStationSource - onFailure");
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError idjiError) {
                        Log.i(TAG, "getAircraftRTKModuleEnabled - onFailure: " + idjiError);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "setAircraftRTKModuleEnabled - true - onFailure");
            }
        });
    }

    private void stopRTK() {
        RTKCenter.getInstance().setAircraftRTKModuleEnabled(false, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "setAircraftRTKModuleEnabled - false - onSuccess");
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "setAircraftRTKModuleEnabled - false - onFailure: " + idjiError);
            }
        });
    }

    private void startSimulator() {
        stopRTK();

        SimulatorManager.getInstance().enableSimulator(InitializationSettings.createInstance(droneHomeLocation, 20), new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "enableSimulator - onSuccess");
                tvSimulator.setText(getString(R.string.true_output));
                updateDroneHomeAndCurrentLocation();

                SimulatorManager.getInstance().addSimulatorStateListener(state -> updateDroneHomeAndCurrentLocation());
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.i(TAG, "enableSimulator - onFailure: " + error);
                tvSimulator.setText(getString(R.string.error_output));
            }
        });
    }

    private void stopSimulator() {
        SimulatorManager.getInstance().disableSimulator(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "disableSimulator - onSuccess");
                tvSimulator.setText(getString(R.string.false_output));
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.i(TAG, "disableSimulator - onFailure: " + error);
                tvSimulator.setText(getString(R.string.error_output));
            }
        });
    }

    private void initRGBCamera() {
        CameraStreamManager.getInstance().addAvailableCameraUpdatedListener(availableCameraList -> {
            Log.i(TAG, "onAvailableCameraUpdated: " + availableCameraList.size());

            if (!availableCameraList.isEmpty()) {
                MediaDataCenter.getInstance().getCameraStreamManager().putCameraStreamSurface(
                        ComponentIndexType.LEFT_OR_MAIN,
                        svCameraStream.getHolder().getSurface(),
                        svCameraStream.getWidth(),
                        svCameraStream.getHeight(),
                        ICameraStreamManager.ScaleType.CENTER_CROP
                );

                KeyManager.getInstance().setValue(KeyTools.createKey(CameraKey.KeyCameraVideoStreamSource), CameraVideoStreamSourceType.ZOOM_CAMERA, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "onSuccess ZOOM_CAMERA");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError idjiError) {
                        Log.i(TAG, "onFailure ZOOM_CAMERA: " + idjiError);
                    }

                });
            }
        });

        svCameraStream.setVisibility(View.VISIBLE);
    }

    private void setMissionOnMap(KMZInfo kmzInfo) {
        if (mapStyle == null || kmzInfo == null) {
            return;
        }

        // Remove existing layers and sources to avoid duplication
        if (mapStyle.getLayer("home-marker-layer") != null) {
            mapStyle.removeLayer("home-marker-layer");
        }
        if (mapStyle.getLayer("waypoint-marker-layer") != null) {
            mapStyle.removeLayer("waypoint-marker-layer");
        }
        if (mapStyle.getLayer("home-to-firstwaypoint-layer") != null) {
            mapStyle.removeLayer("home-to-firstwaypoint-layer");
        }
        if (mapStyle.getLayer("lastwaypoint-to-home-layer") != null) {
            mapStyle.removeLayer("lastwaypoint-to-home-layer");
        }
        if (mapStyle.getLayer("waypoint-to-waypoint-layer") != null) {
            mapStyle.removeLayer("waypoint-to-waypoint-layer");
        }
        if (mapStyle.getSource("home-marker-source") != null) {
            mapStyle.removeSource("home-marker-source");
        }
        if (mapStyle.getSource("waypoint-marker-source") != null) {
            mapStyle.removeSource("waypoint-marker-source");
        }
        if (mapStyle.getSource("home-to-firstwaypoint-source") != null) {
            mapStyle.removeSource("home-to-firstwaypoint-source");
        }
        if (mapStyle.getSource("lastwaypoint-to-home-source") != null) {
            mapStyle.removeSource("lastwaypoint-to-home-source");
        }
        if (mapStyle.getSource("waypoint-to-waypoint-source") != null) {
            mapStyle.removeSource("waypoint-to-waypoint-source");
        }

        // Add magenta line from home to the first waypoint
        List<Point> homeToFirstWaypointPoints = new ArrayList<>();
        homeToFirstWaypointPoints.add(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude()));
        WaylineExecuteWaypoint firstWaypoint = kmzInfo.getWaylineWaylinesParseInfo()
                .getWaylines()
                .get(0)
                .getWaypoints()
                .get(0);  // Get the first waypoint
        homeToFirstWaypointPoints.add(Point.fromLngLat(firstWaypoint.getLocation().getLongitude(), firstWaypoint.getLocation().getLatitude()));

        GeoJsonSource homeToFirstWaypointSource = new GeoJsonSource("home-to-firstwaypoint-source", LineString.fromLngLats(homeToFirstWaypointPoints));
        mapStyle.addSource(homeToFirstWaypointSource);

        LineLayer homeToFirstWaypointLayer = new LineLayer("home-to-firstwaypoint-layer", "home-to-firstwaypoint-source");
        homeToFirstWaypointLayer.setProperties(
                lineColor("magenta"),  // Set the line color to magenta
                lineWidth(4.0f)        // Set the line width
        );
        mapStyle.addLayer(homeToFirstWaypointLayer); // Add line layer from home to first waypoint

        // Add magenta line from last waypoint to home
        List<Point> lastWaypointToHomePoints = new ArrayList<>();
        List<WaylineExecuteWaypoint> waypoints = kmzInfo.getWaylineWaylinesParseInfo()
                .getWaylines()
                .get(0)
                .getWaypoints();
        WaylineExecuteWaypoint lastWaypoint = waypoints.get(waypoints.size() - 1);  // Get the last waypoint
        lastWaypointToHomePoints.add(Point.fromLngLat(lastWaypoint.getLocation().getLongitude(), lastWaypoint.getLocation().getLatitude()));
        lastWaypointToHomePoints.add(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude()));

        GeoJsonSource lastWaypointToHomeSource = new GeoJsonSource("lastwaypoint-to-home-source", LineString.fromLngLats(lastWaypointToHomePoints));
        mapStyle.addSource(lastWaypointToHomeSource);

        LineLayer lastWaypointToHomeLayer = new LineLayer("lastwaypoint-to-home-layer", "lastwaypoint-to-home-source");
        lastWaypointToHomeLayer.setProperties(
                lineColor("magenta"),  // Set the line color to magenta
                lineWidth(4.0f)        // Set the line width
        );
        mapStyle.addLayer(lastWaypointToHomeLayer); // Add line layer from last waypoint to home

        // Add blue lines connecting the waypoints (except home to first and last to home)
        List<Point> waypointToWaypointPoints = new ArrayList<>();
        for (int i = 0; i < waypoints.size() - 1; i++) {
            WaylineExecuteWaypoint currentWaypoint = waypoints.get(i);
            WaylineExecuteWaypoint nextWaypoint = waypoints.get(i + 1);

            waypointToWaypointPoints.add(Point.fromLngLat(currentWaypoint.getLocation().getLongitude(), currentWaypoint.getLocation().getLatitude()));
            waypointToWaypointPoints.add(Point.fromLngLat(nextWaypoint.getLocation().getLongitude(), nextWaypoint.getLocation().getLatitude()));
        }

        // Create GeoJsonSource for the waypoint-to-waypoint connections
        GeoJsonSource waypointToWaypointSource = new GeoJsonSource("waypoint-to-waypoint-source", LineString.fromLngLats(waypointToWaypointPoints));
        mapStyle.addSource(waypointToWaypointSource);

        // Create LineLayer for waypoint-to-waypoint connections (blue)
        LineLayer waypointToWaypointLayer = new LineLayer("waypoint-to-waypoint-layer", "waypoint-to-waypoint-source");
        waypointToWaypointLayer.setProperties(
                lineColor("blue"),  // Set the line color to blue
                lineWidth(4.0f)     // Set the line width
        );

        // Add line layer for waypoint-to-waypoint connections
        mapStyle.addLayer(waypointToWaypointLayer);

        // Add home location marker (green) at the end
        mapStyle.addImage("home-marker-icon", BitmapFactory.decodeResource(getResources(), R.drawable.marker_green));
        GeoJsonSource homeMarkerSource = new GeoJsonSource("home-marker-source");
        List<Feature> homeFeatures = new ArrayList<>();
        homeFeatures.add(Feature.fromGeometry(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude())));
        homeMarkerSource.setGeoJson(FeatureCollection.fromFeatures(homeFeatures));
        mapStyle.addSource(homeMarkerSource);

        SymbolLayer homeMarkerLayer = new SymbolLayer("home-marker-layer", "home-marker-source");
        homeMarkerLayer.setProperties(
                iconImage("home-marker-icon"), // Green home marker icon
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );
        mapStyle.addLayer(homeMarkerLayer); // Add home marker layer

        // Add waypoint markers (red) at the end
        mapStyle.addImage("waypoint-marker-icon", BitmapFactory.decodeResource(getResources(), R.drawable.marker_red));
        GeoJsonSource waypointMarkerSource = new GeoJsonSource("waypoint-marker-source");
        List<Feature> waypointFeatures = new ArrayList<>();

        for (WaylineExecuteWaypoint waypoint : waypoints) {
            double latitude = waypoint.getLocation().getLatitude();
            double longitude = waypoint.getLocation().getLongitude();
            waypointFeatures.add(Feature.fromGeometry(Point.fromLngLat(longitude, latitude)));
        }

        waypointMarkerSource.setGeoJson(FeatureCollection.fromFeatures(waypointFeatures));
        mapStyle.addSource(waypointMarkerSource);

        SymbolLayer waypointMarkerLayer = new SymbolLayer("waypoint-marker-layer", "waypoint-marker-source");
        waypointMarkerLayer.setProperties(
                iconImage("waypoint-marker-icon"), // Red waypoint marker icon
                iconAllowOverlap(true),
                iconIgnorePlacement(true)
        );

        mapStyle.addLayer(waypointMarkerLayer); // Add waypoint markers layer
    }

    private void updateDroneHomeAndCurrentLocation() {
        if (mapStyle != null) {
            // Update home location marker (no rotation)
            Bitmap homeIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.marker_red);
            if (mapStyle.getSource("home-location-source") != null) {
                GeoJsonSource source = mapStyle.getSourceAs("home-location-source");
                if (source != null) {
                    source.setGeoJson(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude()));
                }
            } else {
                GeoJsonSource source = new GeoJsonSource("home-location-source", Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude()));
                mapStyle.addSource(source);
                SymbolLayer layer = new SymbolLayer("home-marker-layer", "home-location-source");
                layer.setProperties(
                        iconImage("marker-home-icon"),
                        iconAllowOverlap(true),
                        iconIgnorePlacement(true)
                );
                mapStyle.addLayer(layer);
                mapStyle.addImage("marker-home-icon", homeIconBitmap);
            }

            // Update current location marker (only if valid GPS coordinates) with rotation for the drone
            if (checkGPSCoordinates(droneCurrentLocation.getLatitude(), droneCurrentLocation.getLongitude())) {
                Bitmap currentIconBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.aircraft);
                if (mapStyle.getSource("current-location-source") != null) {
                    GeoJsonSource source = mapStyle.getSourceAs("current-location-source");
                    if (source != null) {
                        source.setGeoJson(Point.fromLngLat(droneCurrentLocation.getLongitude(), droneCurrentLocation.getLatitude()));
                    }
                } else {
                    GeoJsonSource source = new GeoJsonSource("current-location-source", Point.fromLngLat(droneCurrentLocation.getLongitude(), droneCurrentLocation.getLatitude()));
                    mapStyle.addSource(source);
                    SymbolLayer layer = new SymbolLayer("current-marker-layer", "current-location-source");
                    layer.setProperties(
                            iconImage("marker-current-icon"),
                            iconAllowOverlap(true),
                            iconIgnorePlacement(true)
                    );
                    mapStyle.addLayer(layer);
                    mapStyle.addImage("marker-current-icon", currentIconBitmap);
                }

                // Update the layer with the icon's rotation based on droneHeading
                SymbolLayer layer = (SymbolLayer) mapStyle.getLayer("current-marker-layer");
                if (layer != null) {
                    layer.setProperties(
                            iconRotate((float) droneHeading)  // Rotate the icon based on droneHeading
                    );
                }
            }
        }
    }

    private void registerApp() {
        SDKManager.getInstance().init(this, new SDKManagerCallback() {
            @Override
            public void onRegisterSuccess() {
                Log.i(TAG, "onRegisterSuccess: ");
            }

            @Override
            public void onRegisterFailure(IDJIError error) {
                Log.i(TAG, "onRegisterFailure: " + error);
            }

            @Override
            public void onProductDisconnect(int productId) {
                Log.i(TAG, "onProductDisconnect: " + productId);
            }

            @Override
            public void onProductConnect(int productId) {
                Log.i(TAG, "onProductConnect: " + productId);

                listenerDroneConnection();
                listenerBatteryPercent();
                listenerAircraftAttitude();
                listenerHomeLocation();
                listenerAircraftLocation3D();
                listenerGimbalAttitude();
            }

            @Override
            public void onProductChanged(int productId) {
                Log.i(TAG, "onProductChanged: " + productId);
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
                Log.i(TAG, "onInitProcess: ");
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    SDKManager.getInstance().registerApp();
                }
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                Log.i(TAG, "onDatabaseDownloadProgress: " + current / total);
            }
        });
    }

    private void listenerGimbalAttitude() {
        KeyManager.getInstance().listen(KeyTools.createKey(GimbalKey.KeyGimbalAttitude), this, (attitude, t1) -> {
            // Get gimbal attitude data. The yaw angle uses the north east down coordinate system.
            // If you need to get the yaw angle of the gimbal relative to the nose of the aircraft, please call KeyYawRelativeToAircraftHeading.
            if (attitude != null) {
                tvGimbalPitchYaw.setText(String.format(
                        "%s, %s",
                        integerFormat.format(attitude.getPitch()),
                        integerFormat.format(attitude.getYaw())
                ));
            }
        });
    }

    private void listenerAircraftLocation3D() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D), this, (locationCoordinate3D, t1) -> {
            if (locationCoordinate3D != null) {
                droneCurrentLocation.setLatitude(locationCoordinate3D.getLatitude());
                droneCurrentLocation.setLongitude(locationCoordinate3D.getLongitude());

                tvDroneAltitude.setText(String.format("%s", decimalFormat.format(locationCoordinate3D.getAltitude())));

                Location homeLocation = new Location("home");
                homeLocation.setLatitude(droneHomeLocation.getLatitude());
                homeLocation.setLongitude(droneHomeLocation.getLongitude());

                Location currentLocation = new Location("current");
                currentLocation.setLatitude(droneCurrentLocation.getLatitude());
                currentLocation.setLongitude(droneHomeLocation.getLongitude());

                updateDroneHomeAndCurrentLocation();
            }
        });
    }

    private void listenerHomeLocation() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyHomeLocation), this, (locationCoordinate2D, t1) -> {
            if (locationCoordinate2D != null) {
                droneHomeLocation.setLatitude(locationCoordinate2D.getLatitude());
                droneHomeLocation.setLongitude(locationCoordinate2D.getLongitude());

                updateDroneHomeAndCurrentLocation();
//                centerCameraOnDroneCurrentLocation(); // maybe too much, let's see...
            }
        });
    }

    private void listenerAircraftAttitude() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude), this, (attitude, t1) -> {
            if (attitude != null) {
                droneHeading = attitude.getYaw();
                tvDroneYaw.setText(String.format("%s", integerFormat.format(attitude.getYaw())));
            }
        });
    }

    private void listenerBatteryPercent() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyBatteryPowerPercent), this, (oldValue, newValue) -> {
            if (newValue != null) {
                tvBatteryPercentage.setText(getString(R.string.battery_percentage_format, newValue));
            } else {
                tvBatteryPercentage.setText(getString(R.string.empty_label));
            }
        });
    }

    private void listenerDroneConnection() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyConnection), this, (oldValue, newValue) -> {
            if (newValue != null) {
                tvDroneConnected.setText(newValue.toString());
            } else {
                tvDroneConnected.setText(getString(R.string.false_output));
            }
        });
    }
}
