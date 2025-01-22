package com.dji.myapplication;

import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.dji.wpmzsdk.common.data.KMZInfo;
import com.dji.wpmzsdk.manager.WPMZManager;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import dji.sdk.keyvalue.key.CameraKey;
import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.camera.CameraVideoStreamSourceType;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.ComponentIndexType;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
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
import dji.v5.manager.aircraft.rtk.RTKLocationInfo;
import dji.v5.manager.aircraft.rtk.RTKLocationInfoListener;
import dji.v5.manager.aircraft.rtk.RTKSystemState;
import dji.v5.manager.aircraft.rtk.RTKSystemStateListener;
import dji.v5.manager.aircraft.rtk.network.INetworkServiceInfoListener;
import dji.v5.manager.aircraft.simulator.InitializationSettings;
import dji.v5.manager.aircraft.simulator.SimulatorManager;
import dji.v5.manager.aircraft.waypoint3.WaypointActionListener;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.datacenter.MediaDataCenter;
import dji.v5.manager.datacenter.camera.CameraStreamManager;
import dji.v5.manager.interfaces.ICameraStreamManager;
import dji.v5.manager.interfaces.SDKManagerCallback;


public class NewActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private Button toggleButton;
    private  Button btnLoadMission;
    private  Button btnStartMission;
    private  Button stopMission;
    private SwitchMaterial switchSimulator;
    private SwitchMaterial switchRTK;
    private TextView tvDroneConnected;
    private TextView tvBatteryPercentage;
    private TextView tvSimulator;
    private TextView tvDroneYaw; // Pitch, Roll, Yaw
    private TextView tvGimbalPitchYaw; // Pitch, Yaw
//    private TextView distanceFromHome;
//    private TextView speed;
    private TextView tvRtk;
    private TextView tvLog;
    private GoogleMap gMap;
    private View mapView;
    private Marker droneMarker = null;
    DecimalFormat decimalFormat = new DecimalFormat("#.##");
    private SurfaceView svCameraStream;

    // Other stuff
    LocationCoordinate2D droneHomeLocation = new LocationCoordinate2D(43.062094, 12.549959);
    LocationCoordinate2D droneCurrentLocation = new LocationCoordinate2D(43.062094, 12.549959);
    private double droneHeading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN       // Hide status bar
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Hide navigation bar
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // Enable immersive mode
        );

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.new_activity);
        initUI();
        initRGBCamera();
        registerApp();
    }

    public void initUI() {
        switchSimulator = findViewById(R.id.switch_simulator);
        switchRTK = findViewById(R.id.switch_rtk);
        btnLoadMission = findViewById(R.id.button_load_mission);
        btnStartMission = findViewById(R.id.button_start_mission);
//        stopMission = findViewById(R.id.button_stop_mission);

        tvDroneConnected = findViewById(R.id.textview_is_drone_connected);
        tvBatteryPercentage = findViewById(R.id.textview_battery);
        tvSimulator = findViewById(R.id.textview_is_simulator_on);
        tvDroneYaw = findViewById(R.id.textview_drone_yaw);
        tvGimbalPitchYaw = findViewById(R.id.textview_gimbal_pitch_yaw);
//        distanceFromHome = findViewById(R.id.textview_distance_from_home);
//        speed = findViewById(R.id.textview_speed);

        tvLog = findViewById(R.id.textview_log);
        tvRtk = findViewById(R.id.textview_rtk);

        toggleButton = findViewById(R.id.button_toggle_view);
        svCameraStream = findViewById(R.id.sv_camera_stream);
        mapView = findViewById(R.id.map_view);
        mapView.setVisibility(View.GONE);

        toggleButton.setOnClickListener(v -> {
            if (svCameraStream.getVisibility() == View.VISIBLE) {
                svCameraStream.setVisibility(View.GONE);
                mapView.setVisibility(View.VISIBLE);
                toggleButton.setText("Camera");
            } else {
                svCameraStream.setVisibility(View.VISIBLE);
                initRGBCamera();
                mapView.setVisibility(View.GONE);
                toggleButton.setText("Map");
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map_view);
        if (mapFragment != null) {
            mapFragment.getMapAsync(new OnMapReadyCallback() {
                @Override
                public void onMapReady(@NonNull GoogleMap googleMap) {
                    if (gMap == null) {
                        gMap = googleMap;
                        initMapView();
                    }
                }
            });
        }

        switchSimulator.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "startSimulator");
                    startSimulator();
                } else {
                    Log.i(TAG, "startSimulator");
                    stopSimulator();
                }
            }
        });

        switchRTK.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.i(TAG, "startRTK");
                    startRTK();
                } else {
                    Log.i(TAG, "stopRTK");
                    stopRTK();
                }
            }
        });

        btnLoadMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "loadMission");
                loadMission(view);
            }
        });

        btnStartMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "startMission");
                startMission(view);
            }
        });

//        stopMission.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Log.i(TAG, "stopMission - onClick");
//
//                startMission.setEnabled(true);
//                stopMission.setEnabled(false);
//            }
//        });
    }

    private void loadMission(View view) {
        File kmzFile = Util.copyKMZToExternalStorage(view.getContext(), "mission.kmz");
        WaypointMissionManager.getInstance().init();
        KMZInfo kmzInfo = WPMZManager.getInstance().getKMZInfo(kmzFile.getAbsolutePath());

        Log.d("Command Execution :", "Checking file info: " + kmzFile.getAbsolutePath() + ": " + kmzInfo.getWaylineMissionConfigParseInfo().toString());

        setMissionOnMap(kmzInfo);

        WaypointMissionManager.getInstance().pushKMZFileToAircraft(kmzFile.getAbsolutePath(), new CommonCallbacks.CompletionCallbackWithProgress<Double>() {
            @Override
            public void onProgressUpdate(Double progress) {
                Log.d(TAG, "Upload progress: " + progress + "%");
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "KMZ mission uploaded successfully.");

                btnStartMission.setEnabled(true);
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

                        tvLog.setText("Starting WP=" + actionGroup + " A=" + actionId);
                    }

                    @Override
                    public void onExecutionFinish(int actionGroup, int actionId, @Nullable IDJIError error) {
                        Log.i(TAG, "onExecutionFinish - actionGroup=" + actionGroup + ", actionId=" + actionId);

                        tvLog.setText("Finishing WP=" + actionGroup + " A=" + actionId);
                    }
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.e(TAG, "Failed to upload KMZ mission: " + idjiError.toString());
            }
        });
    }

    private void startMission(View view) {
        WaypointMissionManager.getInstance().startMission("mission", new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "startMission - onClick - onSuccess");

                btnStartMission.setEnabled(false);
                stopMission.setEnabled(true);
            }

            @Override
            public void onFailure(@NonNull IDJIError idjiError) {
                Log.i(TAG, "startMission - onClick - onFailure: " + idjiError);
            }
        });
    }

    private void startRTK() {
        RTKCenter.getInstance().setAircraftRTKModuleEnabled(true, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "setAircraftRTKModuleEnabled - true - onSuccess");

                RTKCenter.getInstance().getAircraftRTKModuleEnabled(new CommonCallbacks.CompletionCallbackWithParam<Boolean>() {
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

                                        RTKCenter.getInstance().getCustomRTKManager().startNetworkRTKService(new CommonCallbacks.CompletionCallback() {
                                            @Override
                                            public void onSuccess() {
                                                Log.i(TAG, "startNetworkRTKService - onSuccess:");
                                            }

                                            @Override
                                            public void onFailure(@NonNull IDJIError idjiError) {
                                                Log.i(TAG, "startNetworkRTKService - onFailure:" + idjiError);
                                            }
                                        });

                                        RTKCenter.getInstance().addRTKLocationInfoListener(new RTKLocationInfoListener() {
                                            @Override
                                            public void onUpdate(RTKLocationInfo newValue) {
//                                                Log.i(TAG, "addRTKLocationInfoListener - onUpdate: " + newValue.toString());
                                                double stdLatitude = newValue.getRtkLocation().getStdLatitude();
                                                double stdLongitude = newValue.getRtkLocation().getStdLongitude();
                                                double stdAltitude = newValue.getRtkLocation().getStdAltitude();

                                                tvRtk.setText(String.format(
                                                        "%s, %s, %s",
                                                        decimalFormat.format(stdLatitude),
                                                        decimalFormat.format(stdLongitude),
                                                        decimalFormat.format(stdAltitude)
                                                ));

//                                                double latitude = newValue.getReal3DLocation().getLatitude();
//                                                double longitude = newValue.getReal3DLocation().getLongitude();
                                            }
                                        });

                                        RTKCenter.getInstance().addRTKSystemStateListener(new RTKSystemStateListener() {
                                            @Override
                                            public void onUpdate(RTKSystemState newValue) {
                                                Log.i(TAG, "addRTKSystemStateListener - onUpdate: " + newValue.toString());
                                            }
                                        });
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

        // Fix home here, maybe it will be better to use something else
        droneHomeLocation = new LocationCoordinate2D(43.062077, 12.550739);
        updateDroneHomeAndCurrentLocation();

        SimulatorManager.getInstance().enableSimulator(InitializationSettings.createInstance(droneHomeLocation, 15), new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "enableSimulator - onSuccess");
                tvSimulator.setText("true");

                SimulatorManager.getInstance().addSimulatorStateListener(state -> {
                    updateDroneHomeAndCurrentLocation();
                });
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.i(TAG, "enableSimulator - onFailure: " + error);
                tvSimulator.setText("error");
            }
        });
    }

    private void stopSimulator() {
        SimulatorManager.getInstance().disableSimulator(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "disableSimulator - onSuccess");
                tvSimulator.setText("false");
            }

            @Override
            public void onFailure(@NonNull IDJIError error) {
                Log.i(TAG, "disableSimulator - onFailure: " + error);
                tvSimulator.setText("error");
            }
        });
    }

    private void initRGBCamera() {
        CameraStreamManager.getInstance().addAvailableCameraUpdatedListener(new ICameraStreamManager.AvailableCameraUpdatedListener() {
            @Override
            public void onAvailableCameraUpdated(@NonNull List<ComponentIndexType> availableCameraList) {
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
            }
        });

        svCameraStream.setVisibility(View.VISIBLE);
    }

    private void setMissionOnMap(KMZInfo kmzInfo) {
        if (gMap == null || kmzInfo == null) {
            return;
        }

        gMap.clear();

        LatLng homeLatLng = new LatLng(droneHomeLocation.getLatitude(), droneHomeLocation.getLongitude());
        gMap.moveCamera(CameraUpdateFactory.newLatLng(homeLatLng));

        gMap.addMarker(new MarkerOptions()
                .position(homeLatLng)
                .title("Home")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));

        List<WaylineExecuteWaypoint> waypoints = kmzInfo.getWaylineWaylinesParseInfo()
                .getWaylines()
                .get(0)
                .getWaypoints();

        List<LatLng> polylinePoints = new ArrayList<>();
        polylinePoints.add(homeLatLng);

        for (WaylineExecuteWaypoint waypoint : waypoints) {
            double latitude = waypoint.getLocation().getLatitude();
            double longitude = waypoint.getLocation().getLongitude();
            int waypointIndex = waypoint.getWaypointIndex();

            LatLng waypointLatLng = new LatLng(latitude, longitude);
            polylinePoints.add(waypointLatLng);

            gMap.addMarker(new MarkerOptions()
                    .position(waypointLatLng)
                    .title("Waypoint " + waypointIndex)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }

        polylinePoints.add(homeLatLng);

        if (polylinePoints.size() > 1) {
            gMap.addPolyline(new PolylineOptions()
                    .add(polylinePoints.get(0), polylinePoints.get(1))
                    .color(Color.MAGENTA)
                    .width(5));
        }

        for (int i = 1; i < polylinePoints.size() - 2; i++) {
            gMap.addPolyline(new PolylineOptions()
                    .add(polylinePoints.get(i), polylinePoints.get(i + 1))
                    .color(Color.BLUE)
                    .width(5));
        }

        if (polylinePoints.size() > 2) {
            gMap.addPolyline(new PolylineOptions()
                    .add(polylinePoints.get(polylinePoints.size() - 2), polylinePoints.get(polylinePoints.size() - 1))
                    .color(Color.MAGENTA)
                    .width(5));
        }
    }

    private void initMapView() {
        gMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        gMap.moveCamera(CameraUpdateFactory.zoomTo(17));

        updateDroneHomeAndCurrentLocation();
        centerCameraOnDroneCurrentLocation();
    }

    private void updateDroneHomeAndCurrentLocation() {
        LatLng posHome = new LatLng(droneHomeLocation.getLatitude(), droneHomeLocation.getLongitude());
        gMap.addMarker(new MarkerOptions()
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .position(posHome));

        LatLng posCurrent = new LatLng(droneCurrentLocation.getLatitude(), droneCurrentLocation.getLongitude());

        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(posCurrent);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGPSCoordinates(droneCurrentLocation.getLatitude(), droneCurrentLocation.getLongitude())) {
                    droneMarker = gMap.addMarker(markerOptions);
                    if (droneMarker != null) {
                        droneMarker.setRotation((float) droneHeading);
                        centerCameraOnDroneCurrentLocation();
                    }
                }
            }
        });
    }

    private void centerCameraOnDroneCurrentLocation() {
        LatLng pos = new LatLng(droneCurrentLocation.getLatitude(), droneCurrentLocation.getLongitude());
        CameraUpdate cu = CameraUpdateFactory.newLatLng(pos);
        gMap.moveCamera(cu);
    }

    public static boolean checkGPSCoordinates(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
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
        KeyManager.getInstance().listen(KeyTools.createKey(GimbalKey.KeyGimbalAttitude), this, new CommonCallbacks.KeyListener<Attitude>() {
            @Override
            public void onValueChange(@Nullable Attitude attitude, @Nullable Attitude t1) {
                // Get gimbal attitude data. The yaw angle uses the north east down coordinate system.
                // If you need to get the yaw angle of the gimbal relative to the nose of the aircraft, please call KeyYawRelativeToAircraftHeading.
                if (attitude != null) {
                    tvGimbalPitchYaw.setText(String.format(
                            "%s, %s",
                            decimalFormat.format(attitude.getPitch()),
                            decimalFormat.format(attitude.getYaw())
                    ));
                }
            }
        });
    }

    private void listenerAircraftLocation3D() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D), this, new CommonCallbacks.KeyListener<LocationCoordinate3D>() {
            @Override
            public void onValueChange(@Nullable LocationCoordinate3D locationCoordinate3D, @Nullable LocationCoordinate3D t1) {
                if (locationCoordinate3D != null) {
                    droneCurrentLocation.setLatitude(locationCoordinate3D.getLatitude());
                    droneCurrentLocation.setLongitude(locationCoordinate3D.getLongitude());

                    Location homeLocation = new Location("home");
                    homeLocation.setLatitude(droneHomeLocation.getLatitude());
                    homeLocation.setLongitude(droneHomeLocation.getLongitude());

                    Location currentLocation = new Location("current");
                    currentLocation.setLatitude(droneCurrentLocation.getLatitude());
                    currentLocation.setLongitude(droneHomeLocation.getLongitude());

//                            distanceFromHome.setText(String.format("%s", decimalFormat.format(currentLocation.distanceTo(homeLocation))));

                    updateDroneHomeAndCurrentLocation();
                }
            }
        });
    }

    private void listenerHomeLocation() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyHomeLocation), this, new CommonCallbacks.KeyListener<LocationCoordinate2D>() {
            @Override
            public void onValueChange(@Nullable LocationCoordinate2D locationCoordinate2D, @Nullable LocationCoordinate2D t1) {
                if (locationCoordinate2D != null) {
                    droneHomeLocation.setLatitude(locationCoordinate2D.getLatitude());
                    droneHomeLocation.setLongitude(locationCoordinate2D.getLongitude());

                    updateDroneHomeAndCurrentLocation();
                    centerCameraOnDroneCurrentLocation(); // maybe too much, let's see...
                }
            }
        });
    }

    private void listenerAircraftAttitude() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude), this, new CommonCallbacks.KeyListener<Attitude>() {
            @Override
            public void onValueChange(@Nullable Attitude attitude, @Nullable Attitude t1) {
                if (attitude != null) {
                    droneHeading = attitude.getYaw();
                    tvDroneYaw.setText(String.format("%s", decimalFormat.format(attitude.getYaw())));
                }
            }
        });
    }

    private void listenerBatteryPercent() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyBatteryPowerPercent), this, new CommonCallbacks.KeyListener<Integer>() {
            @Override
            public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                if (newValue != null) {
                    tvBatteryPercentage.setText(newValue.toString() + "%");
                } else {
                    tvBatteryPercentage.setText("-");
                }
            }
        });
    }

    private void listenerDroneConnection() {
        KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyConnection), this, new CommonCallbacks.KeyListener<Boolean>() {
            @Override
            public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                if (newValue != null) {
                    tvDroneConnected.setText(newValue.toString());
                } else {
                    tvDroneConnected.setText("false");
                }
            }
        });
    }
}
