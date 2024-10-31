package com.example.artest;

import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.splashscreen.SplashScreen;
import androidx.cardview.widget.CardView;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import android.location.Location;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.Manifest;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import android.graphics.Bitmap;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.pm.PackageManager;

import com.skt.Tmap.TMapData;
import com.skt.Tmap.TMapGpsManager;
import com.skt.Tmap.TMapMarkerItem;
import com.skt.Tmap.TMapPoint;
import com.skt.Tmap.TMapPolyLine;
import com.skt.Tmap.TMapView;
import com.skt.Tmap.poi_item.TMapPOIItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TMapGpsManager.onLocationChangedCallback {

    private boolean keepSplashOnScreen = true;
    private Handler handler;
    private TMapView tMapView;
    private TMapPoint endPoint;
    private TMapPolyLine fixedRoute;
    private TMapData tMapData;
    private ListView searchResultsList;
    private ArrayAdapter<String> searchResultsAdapter;
    private List<TMapPOIItem> poiItems;
    private TMapPOIItem selectedPOI;
    private Button buttonFindRoute;
    private TextView textViewDistanceTime;
    private TMapMarkerItem currentLocationMarker;
    private boolean isNavigating = false;
    private Button buttonFindRouteAR;
    private CardView cardViewDistanceTime;
    private CardView cardViewSearch;
    private Button buttonCancelNavigation;
    private EditText searchInput;
    private Location currentLocation;

    private LinearLayout layoutRouteButtons1;
    private LinearLayout layoutRouteButtons2;
    private AppCompatImageButton buttonMyCompass;
    private boolean isCompassModeOn = false;
    private TMapMarkerItem compassModeMarker;
    private TMapGpsManager tMapGPS;
    private boolean userPannedMap = false;
    private static final int AR_ACTIVITY_REQUEST_CODE = 1001;
    private static final long MIN_TIME = 3000; // 3초마다 업데이트
    private static final float MIN_DISTANCE = 10; // 10미터 이동 시 업데이트
    private double totalDistance;
    private int totalTime;
    private ActivityResultLauncher<Intent> arActivityResultLauncher;
    private boolean initialLocationSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_ARTest);
        setContentView(R.layout.activity_main);

        LinearLayout linearLayoutTmap = findViewById(R.id.linearLayoutTmap);
        tMapView = new TMapView(this);
        tMapView.setSKTMapApiKey("T4yNQTPVZucAWeyzLHP1tln0k0JbbM7FtsElt450");
        linearLayoutTmap.addView(tMapView);

        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> keepSplashOnScreen);

        splashScreen.setOnExitAnimationListener(view -> {
            View content = findViewById(android.R.id.content);
            ObjectAnimator.ofFloat(content, View.ALPHA, 0f, 1f)
                    .setDuration(1000)
                    .start();
            view.remove();
        });

        handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(() -> {
            keepSplashOnScreen = false;
        }, 2000);

        hideSystemUI();

        initializeGPS();



        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        buttonMyCompass = findViewById(R.id.buttonMyCompass);
        buttonMyCompass.setOnClickListener(v -> toggleCompassMode());


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        } else {
            displayCurrentLocation();
        }


        tMapData = new TMapData();
        checkLocationPermission();


        tMapView.setOnTouchListener((v, event) -> {
            if (isCompassModeOn) {
                new Handler().postDelayed(() -> tMapView.setCompassMode(true), 500);
            }
            return false;
        });

        Button searchButton = findViewById(R.id.search_button);
        searchInput = findViewById(R.id.search_input);
        View buttonMyLocation = findViewById(R.id.buttonMyLocation);
        buttonFindRoute = findViewById(R.id.buttonFindRoute);
        buttonFindRouteAR = findViewById(R.id.buttonFindRouteAR);
        cardViewDistanceTime = findViewById(R.id.cardViewDistanceTime);
        cardViewSearch = findViewById(R.id.cardViewSearch);
        textViewDistanceTime = findViewById(R.id.textViewDistanceTime);
        buttonCancelNavigation = findViewById(R.id.buttonCancelNavigation);
        layoutRouteButtons1 = findViewById(R.id.layoutRouteButtons1);
        layoutRouteButtons2 = findViewById(R.id.layoutRouteButtons2);
        searchResultsList = findViewById(R.id.search_results_list);
        searchResultsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
        searchResultsList.setAdapter(searchResultsAdapter);

        buttonMyLocation.setOnClickListener(v -> {
            userPannedMap = false;
            moveToCurrentLocation();
        });

        currentLocationMarker = new TMapMarkerItem();
        currentLocationMarker.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.current_location_marker));
        currentLocationMarker.setPosition(0.5f, 1.0f);
        currentLocationMarker.setName("현재 위치");
        tMapView.addMarkerItem("current_location", currentLocationMarker);

        compassModeMarker = new TMapMarkerItem();
        compassModeMarker.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.compass_location_marker));
        compassModeMarker.setPosition(0.5f, 1.0f);
        compassModeMarker.setName("현재 위치 (나침반 모드)");
        tMapView.addMarkerItem("compass_location", compassModeMarker);


        searchButton.setOnClickListener(v -> {
            String keyword = searchInput.getText().toString();
            if (!keyword.isEmpty()) {
                hideKeyboard();
                searchLocation(keyword);
            } else {
                Toast.makeText(MainActivity.this, "장소·주소를 입력하세요.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonCancelNavigation.setOnClickListener(v -> stopNavigation());

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String keyword = searchInput.getText().toString();
                if (!keyword.isEmpty()) {
                    hideKeyboard();
                    searchLocation(keyword);
                } else {
                    Toast.makeText(MainActivity.this, "장소·주소를 입력하세요.", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        });

        searchResultsList.setOnItemClickListener((parent, view, position, id) -> {
            selectedPOI = poiItems.get(position);
            TMapPoint point = selectedPOI.getPOIPoint();
            tMapView.setCenterPoint(point.getLongitude(), point.getLatitude());
            tMapView.setZoomLevel(15);
            TMapMarkerItem markerItem = new TMapMarkerItem();
            Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.ic_marker);
            markerItem.setIcon(bitmap);
            markerItem.setTMapPoint(point);
            markerItem.setPosition(0.5f, 1.0f);
            tMapView.addMarkerItem("search_result", markerItem);
            searchResultsList.setVisibility(View.GONE);
            layoutRouteButtons2.setVisibility(View.VISIBLE);

            buttonFindRoute.setEnabled(true);
            buttonFindRouteAR.setEnabled(true);
        });

        buttonFindRoute.setOnClickListener(v -> {
            if (selectedPOI != null) {
                endPoint = selectedPOI.getPOIPoint();
                if (currentLocation != null) {
                    TMapPoint startPoint = new TMapPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
                    findInitialRoute(startPoint, endPoint);
                } else {
                    Toast.makeText(MainActivity.this, "현재 위치를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(MainActivity.this, "검색 결과를 선택해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonFindRouteAR.setOnClickListener(v -> {
            if (selectedPOI != null) {
                Intent intent = new Intent(MainActivity.this, ARActivity.class);
                intent.putExtra("START_LAT", currentLocationMarker.getTMapPoint().getLatitude());
                intent.putExtra("START_LON", currentLocationMarker.getTMapPoint().getLongitude());
                intent.putExtra("END_LAT", selectedPOI.getPOIPoint().getLatitude());
                intent.putExtra("END_LON", selectedPOI.getPOIPoint().getLongitude());
                arActivityResultLauncher.launch(intent);
                tMapView.removeMarkerItem("search_result");
                searchInput.setText("");
            } else {
                Toast.makeText(MainActivity.this, "장소·주소를 입력하세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // AR 액티비티 결과를 처리하기 위한 launcher 등록
        arActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null && "stopNavigation".equals(data.getStringExtra("action"))) {
                            stopNavigation();
                        }
                    }
                }
        );
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void startNavigation() {
        isNavigating = true;
        showRouteInfoView();
        layoutRouteButtons1.setVisibility(View.GONE);
        layoutRouteButtons2.setVisibility(View.GONE);
        cardViewDistanceTime.setVisibility(View.VISIBLE);
        tMapView.removeMarkerItem("search_result");
        searchInput.setText("");
    }

    private void stopNavigation() {
        isNavigating = false;
        if (fixedRoute != null) {
            tMapView.removeTMapPath();
            fixedRoute = null;
        }
        showSearchView();
        layoutRouteButtons1.setVisibility(View.VISIBLE);
        layoutRouteButtons2.setVisibility(View.VISIBLE);
        cardViewDistanceTime.setVisibility(View.GONE);
        buttonFindRoute.setEnabled(false);
        buttonFindRouteAR.setEnabled(false);
    }

    private void searchLocation(String keyword) {
        tMapData.findAllPOI(keyword, poiItem -> runOnUiThread(() -> {
            if (poiItem != null && poiItem.size() > 0) {
                poiItems = poiItem;
                final List<String> poiNames = new ArrayList<>();
                for (TMapPOIItem item : poiItem) {
                    poiNames.add(item.getPOIName());
                }
                searchResultsAdapter.clear();
                searchResultsAdapter.addAll(poiNames);
                searchResultsAdapter.notifyDataSetChanged();
                searchResultsList.setVisibility(View.VISIBLE);
                layoutRouteButtons2.setVisibility(View.GONE);
            } else {
                Toast.makeText(MainActivity.this, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show();
                layoutRouteButtons2.setVisibility(View.VISIBLE);
            }
        }));
    }

    private void findInitialRoute(TMapPoint startPoint, TMapPoint endPoint) {
        TMapData tMapData = new TMapData();
        tMapView.setCenterPoint(currentLocation.getLongitude(), currentLocation.getLatitude());
        tMapData.findPathDataWithType(TMapData.TMapPathType.PEDESTRIAN_PATH, startPoint, endPoint, polyLine -> {
            if (polyLine == null) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("경로 안내 실패")
                            .setMessage("해당 주소는 도보로 이동할 수 없습니다.\n다른 교통수단을 이용해 주세요.")
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                    stopNavigation();
                });
                return;
            }

            runOnUiThread(() -> {
                fixedRoute = polyLine;
                fixedRoute.setLineColor(Color.parseColor("#4169E1"));  // 폴리라인 색상 = 테마색
                fixedRoute.setLineWidth(20);  // 폴리라인 두께를 20로 설정
                tMapView.addTMapPath(fixedRoute);
                totalDistance = fixedRoute.getDistance();
                totalTime = (int) (totalDistance / 60.0);  // 걷는 속도를 60m/분으로 가정
                updateInitialRouteInfo();
                startNavigation();
            });
        });
    }

    private void updateInitialRouteInfo() {
        double distanceInKm = Math.round((totalDistance / 1000.0) * 10) / 10.0;
        int hours = totalTime / 60;
        int minutes = totalTime % 60;
        int kcal = totalTime * 3;

        String timeInfo;
        if (hours > 0) {
            timeInfo = String.format(Locale.getDefault(), "%d시간 %d분 ", hours, minutes);
        } else {
            timeInfo = String.format(Locale.getDefault(), "%d분 ", minutes);
        }
        String distanceInfo = String.format(Locale.getDefault(), "%.1fkm ", distanceInKm);
        String calorieInfo = String.format(Locale.getDefault(), "%dkcal", kcal);

        SpannableString spannableString = new SpannableString(timeInfo + " " + distanceInfo + " " + calorieInfo);

        int timeEnd = timeInfo.length();
        int firstLineEnd = timeEnd + 1 + distanceInfo.length();

        // 시간 정보 스타일 설정 (가장 크게, 파란색, 굵게)
        spannableString.setSpan(new RelativeSizeSpan(2.5f), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new ForegroundColorSpan(Color.parseColor("#4169E1")), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 거리 정보 스타일 설정 (시간보다 작게)
        spannableString.setSpan(new RelativeSizeSpan(1.1f), timeEnd + 1, firstLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // 칼로리 정보 스타일 설정 (가장 작게)
        spannableString.setSpan(new RelativeSizeSpan(1.1f), firstLineEnd + 1, spannableString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableString.setSpan(new StyleSpan(Typeface.BOLD), 0, timeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        textViewDistanceTime.setText(spannableString);
        textViewDistanceTime.setVisibility(View.VISIBLE);
        cardViewDistanceTime.setVisibility(View.VISIBLE);
    }

    private void toggleCompassMode() {
        isCompassModeOn = !isCompassModeOn;
        if (isCompassModeOn) {
            tMapView.setCompassMode(true);
            Toast.makeText(this, "나침반 모드를 사용합니다.", Toast.LENGTH_SHORT).show();
        } else {
            tMapView.setCompassMode(false);
            Toast.makeText(this, "나침반 모드를 종료합니다.", Toast.LENGTH_SHORT).show();
        }
        updateLocationMarker();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isCompassModeOn) {
            tMapView.setCompassMode(true);
        }
        startLocationUpdates();
        updateLocationMarker();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopLocationUpdates();
        tMapView.setCompassMode(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AR_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            if (data != null && "stopNavigation".equals(data.getStringExtra("action"))) {
                stopNavigation();
            }
        }
    }



    private void updateLocationMarker() {
        if (currentLocation != null) {
            TMapPoint currentPoint = new TMapPoint(currentLocation.getLatitude(), currentLocation.getLongitude());

            if (isCompassModeOn) {
                compassModeMarker.setTMapPoint(currentPoint);
                tMapView.removeMarkerItem("current_location");
                tMapView.addMarkerItem("compass_location", compassModeMarker);
            } else {
                currentLocationMarker.setTMapPoint(currentPoint);
                tMapView.removeMarkerItem("compass_location");
                tMapView.addMarkerItem("current_location", currentLocationMarker);
            }

            if (isNavigating) {
                tMapView.setCenterPoint(currentLocation.getLongitude(), currentLocation.getLatitude());

            }
        }
    }

    private void moveToCurrentLocation() {
        if (checkLocationPermission()) {
            if (currentLocation != null) {
                TMapPoint currentPoint = new TMapPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
                if (currentLocationMarker == null) {
                    currentLocationMarker = new TMapMarkerItem();
                    currentLocationMarker.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.current_location_marker));
                    currentLocationMarker.setPosition(0.5f, 1.0f);
                    currentLocationMarker.setName("현재 위치");
                    tMapView.addMarkerItem("current_location", currentLocationMarker);
                }
                currentLocationMarker.setTMapPoint(currentPoint);
                tMapView.setCenterPoint(currentLocation.getLongitude(), currentLocation.getLatitude(), true);
            } else {
                Toast.makeText(MainActivity.this, "현재 위치를 찾는 중입니다.\n잠시만 기다려주세요.", Toast.LENGTH_SHORT).show();
                if (tMapGPS != null) {
                    tMapGPS.setProvider(TMapGpsManager.NETWORK_PROVIDER);
                    tMapGPS.OpenGps();
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                moveToCurrentLocation();
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void displayCurrentLocation() {
        if (checkLocationPermission()) {
            if (currentLocation != null) {
                updateCurrentLocationMarker();
            } else {
                Toast.makeText(MainActivity.this, "위치 정보를 가져오는 중입니다.\n잠시만 기다려주세요.", Toast.LENGTH_SHORT).show();

                if (tMapGPS == null) {
                    tMapGPS = new TMapGpsManager(this);
                    tMapGPS.setMinTime(MIN_TIME);
                    tMapGPS.setMinDistance(MIN_DISTANCE);
                    tMapGPS.setProvider(TMapGpsManager.NETWORK_PROVIDER);
                    tMapGPS.OpenGps();
                }
            }
        } else {
            Toast.makeText(MainActivity.this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            checkLocationPermission();
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    private void initializeGPS() {
        tMapGPS = new TMapGpsManager(this);
        tMapGPS.setMinTime(MIN_TIME);
        tMapGPS.setMinDistance(MIN_DISTANCE);
        tMapGPS.setProvider(TMapGpsManager.NETWORK_PROVIDER);
        tMapGPS.OpenGps();
    }


    private void updateCurrentLocationMarker() {
        if (currentLocation != null) {
            TMapPoint currentPoint = new TMapPoint(currentLocation.getLatitude(), currentLocation.getLongitude());

            if (currentLocationMarker == null) {
                currentLocationMarker = new TMapMarkerItem();
                currentLocationMarker.setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.current_location_marker));
                currentLocationMarker.setPosition(0.5f, 1.0f);
                currentLocationMarker.setName("현재 위치");
                tMapView.addMarkerItem("current_location", currentLocationMarker);
            }

            currentLocationMarker.setTMapPoint(currentPoint);

            if (isNavigating) {
                tMapView.setCenterPoint(currentLocation.getLongitude(), currentLocation.getLatitude());
            }
        }
    }

    @Override
    public void onLocationChange(Location location) {
        currentLocation = location;
        TMapPoint currentPoint = new TMapPoint(location.getLatitude(), location.getLongitude());

        if (isCompassModeOn) {
            compassModeMarker.setTMapPoint(currentPoint);
        } else {
            currentLocationMarker.setTMapPoint(currentPoint);
        }

        updateCurrentLocationMarker();

        // 추가: 초기 위치 설정
        if (!initialLocationSet) {
            tMapView.setCenterPoint(location.getLongitude(), location.getLatitude());
            tMapView.setZoomLevel(15);
            initialLocationSet = true;
        }

        if (isNavigating) {
            tMapView.setCenterPoint(location.getLongitude(), location.getLatitude());
        }

        // 디버깅을 위한 로그 추가
        Log.d("MainActivity", "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
        Log.d("MainActivity", "Is Navigating: " + isNavigating);
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

    private void showSearchView() {
        cardViewSearch.setVisibility(View.VISIBLE);
        cardViewDistanceTime.setVisibility(View.GONE);
    }

    private void showRouteInfoView() {
        cardViewSearch.setVisibility(View.GONE);
        cardViewDistanceTime.setVisibility(View.VISIBLE);
    }

    private void startLocationUpdates() {
        if (checkLocationPermission()) {
            tMapGPS.OpenGps();
        }
    }

    private void stopLocationUpdates() {
        tMapGPS.CloseGps();
    }
}