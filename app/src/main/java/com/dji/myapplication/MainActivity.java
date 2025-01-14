package com.dji.myapplication;

import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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

import java.io.File;
import java.text.DecimalFormat;

import dji.sdk.keyvalue.key.FlightControllerKey;
import dji.sdk.keyvalue.key.GimbalKey;
import dji.sdk.keyvalue.key.KeyTools;
import dji.sdk.keyvalue.value.common.Attitude;
import dji.sdk.keyvalue.value.common.LocationCoordinate2D;
import dji.sdk.keyvalue.value.common.LocationCoordinate3D;
import dji.sdk.keyvalue.value.common.Velocity3D;
import dji.v5.common.callback.CommonCallbacks;
import dji.v5.common.error.IDJIError;
import dji.v5.common.register.DJISDKInitEvent;
import dji.v5.manager.KeyManager;
import dji.v5.manager.SDKManager;
import dji.v5.manager.aircraft.simulator.InitializationSettings;
import dji.v5.manager.aircraft.simulator.SimulatorManager;
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager;
import dji.v5.manager.interfaces.SDKManagerCallback;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private TextView isDroneConnected;
    private TextView battery;
    private TextView isSimulatorOn;
    private TextView droneYaw; // Pitch, Roll, Yaw
    private TextView gimbalPitchYaw; // Pitch, Yaw
    private TextView positionXYZ;
    private TextView speed;
    private GoogleMap gMap;
    private Marker droneMarker = null;

    // Other stuff
    DecimalFormat decimalFormat = new DecimalFormat("#.#");
    private double droneLatitude;
    private double droneLongitude;
    private double droneHeading;

    LocationCoordinate2D home = new LocationCoordinate2D(43.062496, 12.550976);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        initUI();
//        mapView.onCreate(savedInstanceState);
        registerApp();
    }

    public void initUI() {
        Button startSimulator = findViewById(R.id.button_start_simulator);
        Button stopSimulator = findViewById(R.id.button_stop_simulator);
        Button loadMission = findViewById(R.id.button_load_mission);
        Button startMission = findViewById(R.id.button_start_mission);
        Button stopMission = findViewById(R.id.button_stop_mission);

        isDroneConnected = findViewById(R.id.textview_is_drone_connected);
        battery = findViewById(R.id.textview_battery);
        isSimulatorOn = findViewById(R.id.textview_is_simulator_on);
        droneYaw = findViewById(R.id.textview_drone_yaw);
        gimbalPitchYaw = findViewById(R.id.textview_gimbal_pitch_yaw);
        positionXYZ = findViewById(R.id.textview_position_x_y_z);
        speed = findViewById(R.id.textview_speed);

        startSimulator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InitializationSettings data = InitializationSettings.createInstance(home, 15);

                SimulatorManager.getInstance().enableSimulator(data, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        isSimulatorOn.setText("true");
                        Log.i(TAG, "enableSimulator - onSuccess");
                        setSimulatorStateListener();

                        LatLng pos = new LatLng(home.getLatitude(), home.getLongitude());
                        gMap.addMarker(new MarkerOptions()
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                                .position(pos));

                        updateDroneLocation();
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        isSimulatorOn.setText("error");
                        Log.i(TAG, "enableSimulator - onFailure: " + error);
                    }
                });
            }
        });

        stopSimulator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SimulatorManager.getInstance().disableSimulator(new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {
                        isSimulatorOn.setText("false");
                        Log.i(TAG, "disableSimulator - onSuccess");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError error) {
                        isSimulatorOn.setText("error");
                        Log.i(TAG, "disableSimulator - onFailure: " + error);
                    }
                });
            }
        });

        loadMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "loadMission - onClick");

                // Copy KMZ file to external storage
                File kmzFile = Util.copyKMZToExternalStorage(view.getContext(), "mission.kmz");

                // Initialize WaypointMissionManager
                WaypointMissionManager.getInstance().init();

                // Get KMZ Info (Optional)
                KMZInfo ii = WPMZManager.getInstance().getKMZInfo(kmzFile.getAbsolutePath());
                Log.d("Command Execution :", "Checking file info: " + kmzFile.getAbsolutePath() + ": " + ii.getWaylineMissionConfigParseInfo().toString());

                // Push KMZ File to Aircraft
                WaypointMissionManager.getInstance().pushKMZFileToAircraft(kmzFile.getAbsolutePath(), new CommonCallbacks.CompletionCallbackWithProgress<Double>() {
                    @Override
                    public void onProgressUpdate(Double progress) {
                        Log.d(TAG, "Upload progress: " + progress + "%");
                    }

                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "KMZ mission uploaded successfully.");
                    }

                    @Override
                    public void onFailure(@NonNull IDJIError idjiError) {
                        Log.e(TAG, "Failed to upload KMZ mission: " + idjiError.toString());
                    }
                });
            }
        });


        startMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "startMission - onClick");

                WaypointMissionManager.getInstance().startMission("test", new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onSuccess() {

                    }

                    @Override
                    public void onFailure(@NonNull IDJIError idjiError) {

                    }
                });
            }
        });

        stopMission.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
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
    }

    private void initMapView() {
        // Add a marker to the map
        gMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        gMap.moveCamera(CameraUpdateFactory.zoomTo(18));

        updateDroneLocation();
        cameraUpdate();
    }

    // Update the drone location based on states from MCU.
    private void updateDroneLocation() {
        LatLng pos = new LatLng(droneLatitude, droneLongitude);
        // Create MarkerOptions object
        final MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(pos);
        markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.aircraft));

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (droneMarker != null) {
                    droneMarker.remove();
                }

                if (checkGpsCoordination(droneLatitude, droneLongitude)) {
                    droneMarker = gMap.addMarker(markerOptions);
                    if (droneMarker != null) {
                        droneMarker.setRotation((float) droneHeading);
                        cameraUpdate();
                    }
                }
            }
        });
    }

    private void cameraUpdate() {
        LatLng pos = new LatLng(droneLatitude, droneLongitude);
        CameraUpdate cu = CameraUpdateFactory.newLatLng(pos);
        gMap.moveCamera(cu);
    }

    public static boolean checkGpsCoordination(double latitude, double longitude) {
        return (latitude > -90 && latitude < 90 && longitude > -180 && longitude < 180) && (latitude != 0f && longitude != 0f);
    }

    private void setSimulatorStateListener() {
        SimulatorManager.getInstance().addSimulatorStateListener(state -> {
            updateDroneLocation();
        });
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

                KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyConnection), this, new CommonCallbacks.KeyListener<Boolean>() {
                    @Override
                    public void onValueChange(@Nullable Boolean oldValue, @Nullable Boolean newValue) {
                        if (newValue != null) {
                            isDroneConnected.setText(newValue.toString());
                        } else {
                            isDroneConnected.setText("false");
                        }
                    }
                });

                KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyBatteryPowerPercent), this, new CommonCallbacks.KeyListener<Integer>() {
                    @Override
                    public void onValueChange(@Nullable Integer oldValue, @Nullable Integer newValue) {
                        if (newValue != null) {
                            battery.setText(newValue.toString());
                        } else {
                            battery.setText("-");
                        }
                    }
                });

                KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftVelocity), this, new CommonCallbacks.KeyListener<Velocity3D>() {
                    @Override
                    public void onValueChange(@Nullable Velocity3D oldValue, @Nullable Velocity3D newValue) {
                        if (newValue != null) {
                            if (newValue.getX() != null) {
                                speed.setText(String.format(
                                        "%s, %s, %s",
                                        decimalFormat.format(newValue.getX()),
                                        decimalFormat.format(newValue.getY()),
                                        decimalFormat.format(newValue.getZ())
                                ));
                            } else {
                                speed.setText("-");
                            }
                        }
                    }
                });

                KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftAttitude), this, new CommonCallbacks.KeyListener<Attitude>() {
                    @Override
                    public void onValueChange(@Nullable Attitude attitude, @Nullable Attitude t1) {
                        if (attitude != null) {
                            droneHeading = attitude.getYaw();
                            droneYaw.setText(String.format("%s", decimalFormat.format(attitude.getYaw())));
                        }
                    }
                });

                KeyManager.getInstance().listen(KeyTools.createKey(FlightControllerKey.KeyAircraftLocation3D), this, new CommonCallbacks.KeyListener<LocationCoordinate3D>() {
                    @Override
                    public void onValueChange(@Nullable LocationCoordinate3D locationCoordinate3D, @Nullable LocationCoordinate3D t1) {
                        if (locationCoordinate3D != null) {
                            droneLatitude = locationCoordinate3D.getLatitude();
                            droneLongitude = locationCoordinate3D.getLongitude();
                            double droneAltitude = locationCoordinate3D.getAltitude();

                            // Create a Location object for home
                            Location homeLocation = new Location("home");
                            homeLocation.setLatitude(home.getLatitude());
                            homeLocation.setLongitude(home.getLongitude());

                            Location tempY = new Location("tempY");
                            tempY.setLatitude(droneLatitude);
                            tempY.setLongitude(home.getLongitude());
                            float yDiff = tempY.distanceTo(homeLocation);

                            if (droneLatitude < home.getLatitude()) {
                                yDiff = -yDiff;
                            }

                            Location tempX = new Location("tempX");
                            tempX.setLatitude(home.getLatitude());
                            tempX.setLongitude(droneLongitude);
                            float xDiff = tempX.distanceTo(homeLocation);

                            if (droneLongitude < home.getLongitude()) {
                                xDiff = -xDiff;
                            }

                            positionXYZ.setText(String.format(
                                    "%.2f, %.2f, %.2f",
                                    xDiff, yDiff, droneAltitude
                            ));
                        }
                    }
                });

                KeyManager.getInstance().listen(KeyTools.createKey(GimbalKey.KeyGimbalAttitude), this, new CommonCallbacks.KeyListener<Attitude>() {
                    @Override
                    public void onValueChange(@Nullable Attitude attitude, @Nullable Attitude t1) {
                        // Get gimbal attitude data. The yaw angle uses the north east down coordinate system.
                        // If you need to get the yaw angle of the gimbal relative to the nose of the aircraft, please call KeyYawRelativeToAircraftHeading.
                        if (attitude != null) {
                            gimbalPitchYaw.setText(String.format(
                                    "%s, %s",
                                    decimalFormat.format(attitude.getPitch()),
                                    decimalFormat.format(attitude.getYaw())
                            ));
                        }
                    }
                });

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
}
