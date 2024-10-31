package com.example.artest;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.skt.Tmap.TMapData;
import com.skt.Tmap.TMapGpsManager;
import com.skt.Tmap.TMapMarkerItem;
import com.skt.Tmap.TMapPoint;
import com.skt.Tmap.TMapPolyLine;
import com.skt.Tmap.TMapView;

import java.util.ArrayList;
import java.util.List;


public class ARActivity extends AppCompatActivity implements TMapGpsManager.onLocationChangedCallback {
    private ArFragment arFragment;
    private TMapPolyLine fixedRoute;
    private List<TMapPoint> routePoints;
    private List<AnchorNode> anchorNodeList = new ArrayList<>();
    private TMapView tMapView;
    private TMapData tMapData;
    private Session arSession;
    private TMapMarkerItem compassModeMarker;
    private TMapPoint currentLocationPoint;
    private TMapPoint endPoint;
    private TMapMarkerItem currentLocationMarker;
    private TMapGpsManager tMapGPS;
    private boolean isNavigating = true;
    private boolean isDialogShowing = false;
    private boolean hasDialogBeenShown = false;
    private static final long MIN_TIME = 1000; // 1초마다 업데이트
    private static final float MIN_DISTANCE = 5; // 5미터 이동 시 업데이트
    private boolean isRouteCreated = false;
    private static final float ROUTE_HEIGHT = -1.0f; // 경로의 높이 (바닥에 가깝게 설정)
    private float deviceBearing = 0f; // 클래스 멤버 변수로 추가


    private SensorEventListener compassListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            deviceBearing = event.values[0]; // 나침반 값 (0-360도)
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private final Scene.OnUpdateListener sceneUpdateListener = new Scene.OnUpdateListener() {
        @Override
        public void onUpdate(FrameTime frameTime) {
            Frame frame = arFragment.getArSceneView().getArFrame();
            if (frame == null) {
                return;
            }

            if (frame.getCamera().getTrackingState() == TrackingState.TRACKING) {
                if (!isRouteCreated) {
                    createARLine();
                    isRouteCreated = true;
                }
            } else {
                // AR 트래킹이 불안정할 때의 처리
                showTrackingInstabilityWarning();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_ARTest);
        setContentView(R.layout.activity_ar);

        LinearLayout mapContainer = findViewById(R.id.map_container);
        tMapView = new TMapView(this);
        tMapView.setSKTMapApiKey("T4yNQTPVZucAWeyzLHP1tln0k0JbbM7FtsElt450");
        mapContainer.addView(tMapView);

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        if (arFragment == null) {
            Toast.makeText(this, "ArFragment를 초기화할 수 없습니다.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // 기존의 익명 리스너 대신 새로 정의한 sceneUpdateListener를 사용
        arFragment.getArSceneView().getScene().addOnUpdateListener(sceneUpdateListener);

        hideSystemUI();

        initializeGPS();

        // MainActivity에서 전달받은 목적지 좌표 가져오기
        double startLat = getIntent().getDoubleExtra("START_LAT", 0);
        double startLon = getIntent().getDoubleExtra("START_LON", 0);
        double endLat = getIntent().getDoubleExtra("END_LAT", 0);
        double endLon = getIntent().getDoubleExtra("END_LON", 0);
        TMapPoint startPoint = new TMapPoint(startLat, startLon);
        endPoint = new TMapPoint(endLat, endLon);

        // 경로 계산 및 AR 객체 생성
        calculateRouteAndCreateARObjects(startPoint, endPoint);

        currentLocationMarker = new TMapMarkerItem();
        currentLocationMarker.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.compass_location_marker));
        currentLocationMarker.setPosition(0.5f, 1.0f);
        currentLocationMarker.setName("현재 위치");
        tMapView.addMarkerItem("compass_location", compassModeMarker);

        Button buttonExitAr = findViewById(R.id.btn_exit_ar);
        buttonExitAr.setOnClickListener(v -> {
            Intent returnIntent = new Intent();
            returnIntent.putExtra("action", "stopNavigation");
            setResult(RESULT_OK, returnIntent);
            stopNavigation();
            finish();
        });

        // 사용자 위치 추적 모드 활성화
        tMapView.setTrackingMode(true);
        // 줌레벨 17 고정
        tMapView.setZoomLevel(20);
        // 나침반 모드 활성화
        tMapView.setCompassMode(true);
        // AR 모드에서는 사용자의 지도 조작을 막고 자동으로 위치에따라 움직여짐
        tMapView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                return true;
            }
        });




        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            Frame frame = arFragment.getArSceneView().getArFrame();
            if (frame != null && frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                runOnUiThread(() -> {
                    if (!isDialogShowing && !hasDialogBeenShown) {
                        isDialogShowing = true;
                        hasDialogBeenShown = true;
                        new AlertDialog.Builder(this)
                                .setMessage("카메라를 천천히 움직여 주변 환경을 스캔해주세요.")
                                .setTitle("환경 스캔 필요")
                                .setPositiveButton("확인", (dialog, which) -> {
                                    isDialogShowing = false;
                                })
                                .setOnCancelListener(dialog -> {
                                    isDialogShowing = false;
                                })
                                .show();
                    }
                });
            } else {
                isDialogShowing = false;
            }
        });
    }





    @Override
    protected void onResume() {
        super.onResume();

        try {
            if (arSession == null) {
                arSession = new Session(this);
            }

            com.google.ar.core.Config config = new com.google.ar.core.Config(arSession);
            config.setUpdateMode(com.google.ar.core.Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setDepthMode(com.google.ar.core.Config.DepthMode.AUTOMATIC);
            config.setPlaneFindingMode(com.google.ar.core.Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
            arSession.configure(config);

            if (arFragment.getArSceneView() != null) {
                arFragment.getArSceneView().setupSession(arSession);
            }

            arSession.resume();

            arFragment.getArSceneView().getScene().addOnUpdateListener(sceneUpdateListener);

        } catch (UnavailableArcoreNotInstalledException e) {
            showARCoreInstallDialog();
            return;
        } catch (UnavailableDeviceNotCompatibleException | UnavailableSdkTooOldException |
                 UnavailableApkTooOldException e) {
            e.printStackTrace();
            showErrorDialog("AR 기능을 사용할 수 없습니다.", true);
            return;
        } catch (Exception e) {
            e.printStackTrace();
            showErrorDialog("AR 세션을 초기화하는 중 오류가 발생했습니다.", true);
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 2);
        }

        if (checkLocationPermission()) {
            tMapGPS.OpenGps();
        }
        initCompass();
    }


    @Override
    protected void onPause() {
        super.onPause();
        tMapGPS.CloseGps();
        if (arSession != null) {
            arSession.pause();
        }
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(compassListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // AR 세션 정리
        if (arSession != null) {
            arSession.close();
            arSession = null;
        }

        // AR 프래그먼트 정리
        if (arFragment != null && arFragment.getArSceneView() != null) {
            arFragment.getArSceneView().destroy();
        }

        // GPS 정리
        if (tMapGPS != null) {
            tMapGPS.CloseGps();
        }

        // 리스너 제거
        if (arFragment != null && arFragment.getArSceneView() != null) {
            arFragment.getArSceneView().getScene().removeOnUpdateListener(sceneUpdateListener);
        }

        // AnchorNode 정리
        clearExistingARObjects();
    }

    private boolean checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return false;
        }
        return true;
    }

    private void updateCurrentLocationMarker(TMapPoint currentLocationPoint) {
        currentLocationMarker.setTMapPoint(currentLocationPoint);
        tMapView.addMarkerItem("compass_location", currentLocationMarker);
    }



    private void initializeGPS() {
        if (tMapGPS != null) {
            return;  // 이미 초기화되었다면 중복 실행 방지
        }
        tMapGPS = new TMapGpsManager(this);
        tMapGPS.setMinTime(MIN_TIME);
        tMapGPS.setMinDistance(MIN_DISTANCE);
        tMapGPS.setProvider(TMapGpsManager.NETWORK_PROVIDER);

        if (checkLocationPermission()) {
            tMapGPS.OpenGps();
        }
    }

    private void createARLine() {
        if (isFinishing() || isDestroyed() || isRouteCreated) {
            return;
        }

        List<Vector3> arPoints = new ArrayList<>();
        for (TMapPoint point : routePoints) {
            arPoints.add(convertToARVector(point));
        }

        try {
            Session session = arFragment.getArSceneView().getSession();
            if (session == null) {
                return;
            }

            // 경로의 중심점 계산
            Vector3 centerPoint = Vector3.zero();
            for (Vector3 point : arPoints) {
                centerPoint = Vector3.add(centerPoint, point);
            }
            centerPoint = centerPoint.scaled(1.0f / arPoints.size());
            centerPoint.y = ROUTE_HEIGHT;

            Pose centerPose = Pose.makeTranslation(centerPoint.x, centerPoint.y, centerPoint.z);
            Anchor anchor = session.createAnchor(centerPose);

            AnchorNode anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arFragment.getArSceneView().getScene());

            Node routeNode = new Node();
            routeNode.setParent(anchorNode);

            Vector3 finalCenterPoint = centerPoint;
            MaterialFactory.makeOpaqueWithColor(this, new com.google.ar.sceneform.rendering.Color(android.graphics.Color.BLUE))
                    .thenAccept(material -> {
                        for (int i = 0; i < arPoints.size() - 1; i++) {
                            Vector3 point1 = arPoints.get(i);
                            Vector3 point2 = arPoints.get(i + 1);
                            Vector3 difference = Vector3.subtract(point2, point1);
                            Vector3 directionFromTopToBottom = difference.normalized();
                            float distanceBetweenPoints = difference.length();
                            Vector3 center = Vector3.add(point1, point2).scaled(0.5f);

                            ModelRenderable cube = ShapeFactory.makeCube(
                                    new Vector3(0.05f, 0.05f, distanceBetweenPoints),
                                    Vector3.zero(),
                                    material);

                            Node lineSegment = new Node();
                            lineSegment.setParent(routeNode);
                            lineSegment.setRenderable(cube);
                            lineSegment.setLocalPosition(Vector3.subtract(center, finalCenterPoint));
                            lineSegment.setLocalRotation(Quaternion.lookRotation(directionFromTopToBottom, Vector3.up()));

                            Log.d("ARDebug", "Line segment created from " + point1 + " to " + point2);
                        }

                        // 시작점과 끝점에 마커 추가
                        addMarker(routeNode, Vector3.subtract(arPoints.get(0), finalCenterPoint), "출발");
                        addMarker(routeNode, Vector3.subtract(arPoints.get(arPoints.size() - 1), finalCenterPoint), "도착");

                        isRouteCreated = true;
                    })
                    .exceptionally(throwable -> {
                        Log.e("ARActivity", "Unable to create AR line", throwable);
                        runOnUiThread(() -> Toast.makeText(this, "AR 라인 생성 중 오류가 발생했습니다.", Toast.LENGTH_LONG).show());
                        return null;
                    });
        } catch (Exception e) {
            Log.e("ARError", "AR 객체 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }



    // onCreate에서 나침반 센서 초기화
    private void initCompass() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        Sensor compassSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        sensorManager.registerListener(compassListener, compassSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private Vector3 convertToARVector(TMapPoint point) {
        if (currentLocationPoint == null) {
            return Vector3.zero();
        }

        double startLat = Math.toRadians(currentLocationPoint.getLatitude());
        double startLon = Math.toRadians(currentLocationPoint.getLongitude());
        double endLat = Math.toRadians(point.getLatitude());
        double endLon = Math.toRadians(point.getLongitude());

        // Haversine Formula 구현
        double earthRadius = 6371000; // 지구 반지름 (미터)
        double deltaLat = endLat - startLat;
        double deltaLon = endLon - startLon;

        double a = Math.sin(deltaLat/2) * Math.sin(deltaLat/2) +
                Math.cos(startLat) * Math.cos(endLat) *
                        Math.sin(deltaLon/2) * Math.sin(deltaLon/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        float distance = (float)(earthRadius * c);

        // Bearing 계산
        double y = Math.sin(endLon - startLon) * Math.cos(endLat);
        double x = Math.cos(startLat) * Math.sin(endLat) -
                Math.sin(startLat) * Math.cos(endLat) * Math.cos(endLon - startLon);
        float bearing = (float)Math.toDegrees(Math.atan2(y, x));

        // 중요: bearing과 deviceBearing의 차이를 계산
        float relativeBearing = (bearing - deviceBearing + 360) % 360;
        float scale = 1.0f;

        // 상대적 방향을 기준으로 AR 좌표 계산
        float arX = (float) (Math.sin(Math.toRadians(relativeBearing)) * distance * scale);
        float arZ = -(float) (Math.cos(Math.toRadians(relativeBearing)) * distance * scale);

        return new Vector3(arX, ROUTE_HEIGHT, arZ);
    }



    private void addMarker(Node parentNode, Vector3 position, String text) {
        Node markerNode = new Node();
        markerNode.setParent(parentNode);
        markerNode.setLocalPosition(position);

        ViewRenderable.builder()
                .setView(this, R.layout.marker_layout)
                .build()
                .thenAccept(renderable -> {
                    markerNode.setRenderable(renderable);
                    TextView textView = (TextView) renderable.getView();
                    textView.setText(text);
                })
                .exceptionally(throwable -> {
                    Log.e("ARActivity", "Unable to create marker", throwable);
                    return null;
                });
    }


    private void showARCoreInstallDialog() {
        new AlertDialog.Builder(this)
                .setTitle("ARCore 설치 필요")
                .setMessage("ARCore가 설치되어야 해당 기능을 사용할 수 있습니다.")
                .setPositiveButton("설치하기", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.ar.core"));
                    startActivity(intent);
                })
                .setNegativeButton("취소", (dialog, which) -> finish())
                .show();
    }

    private void showErrorDialog(String message, boolean finishActivity) {
        new AlertDialog.Builder(this)
                .setTitle("오류")
                .setMessage(message)
                .setPositiveButton("확인", (dialog, which) -> {
                    if (finishActivity) finish();
                })
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tMapGPS.OpenGps();
            } else {
                showErrorDialog("위치 권한이 필요합니다.", true);
            }
        } else if (requestCode == 2) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (arSession != null) {
                    arFragment.getArSceneView().setupSession(arSession);
                }
            } else {
                showErrorDialog("카메라 권한이 필요합니다.", true);
            }
        }
    }


    private void showTrackingInstabilityWarning() {
        runOnUiThread(() -> {
            if (!isDialogShowing && !hasDialogBeenShown) {
                isDialogShowing = true;
                hasDialogBeenShown = true;
                new AlertDialog.Builder(this)
                        .setMessage("카메라를 천천히 움직여 주변 환경을 스캔해주세요.")
                        .setTitle("환경 스캔 필요")
                        .setPositiveButton("확인", (dialog, which) -> {
                            isDialogShowing = false;
                        })
                        .setOnCancelListener(dialog -> {
                            isDialogShowing = false;
                        })
                        .show();
            }
        });
    }



    private void clearExistingARObjects() {
        for (AnchorNode node : anchorNodeList) {
            if (node.getAnchor() != null) {
                node.getAnchor().detach();
            }
            node.setParent(null);
        }
        anchorNodeList.clear();
    }

    @Override
    public void onLocationChange(Location location) {
        currentLocationPoint = new TMapPoint(location.getLatitude(), location.getLongitude());
        tMapView.setLocationPoint(location.getLongitude(), location.getLatitude());
        tMapView.setCenterPoint(location.getLongitude(), location.getLatitude());
        updateCurrentLocationMarker(currentLocationPoint);
    }

    private void calculateRouteAndCreateARObjects(TMapPoint startPoint, TMapPoint endPoint) {
        TMapData tMapData = new TMapData();
        tMapData.findPathDataWithType(TMapData.TMapPathType.PEDESTRIAN_PATH, startPoint, endPoint, polyLine -> {
            if (polyLine != null) {
                fixedRoute = polyLine;
                routePoints = polyLine.getLinePoint();
                fixedRoute.setLineColor(Color.parseColor("#4169E1"));
                fixedRoute.setLineWidth(20);

                runOnUiThread(() -> {
                    tMapView.addTMapPath(fixedRoute);
                    createARLine();  // AR 선 생성
                });
            } else {
                runOnUiThread(() -> Toast.makeText(ARActivity.this, "경로를 찾을 수 없습니다.", Toast.LENGTH_LONG).show());
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }


    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }




    private void stopNavigation() {
        isNavigating = false;
        if (fixedRoute != null) {
            tMapView.removeTMapPath();
            fixedRoute = null;
        }

        for (AnchorNode node : anchorNodeList) {
            node.setParent(null);
        }
        anchorNodeList.clear();
    }
}