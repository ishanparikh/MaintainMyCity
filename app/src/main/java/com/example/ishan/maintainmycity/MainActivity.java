package com.example.ishan.maintainmycity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.amazonaws.mobile.client.AWSStartupHandler;
import com.amazonaws.mobile.client.AWSStartupResult;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;

import com.amazonaws.mobile.client.AWSMobileClient;


import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, LocationEngineListener, PermissionsListener, MapboxMap.OnMapClickListener{

    private String tag = "MainActivity";
    private MapView mapView;
    private MapboxMap map;
    private PermissionsManager permissionsManager;
    private LocationEngine locationEngine;
    private Location originLocation;
    private LocationLayerPlugin locationLayerPlugin;
    private Marker destinationMarker;
    private Button reportButton;

    private String downloadDate = ""; // Format: YYYY/MM/DD
    private final String preferencesFile = "MyPrefsFile"; // for storing preferences

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getString(R.string.access_token));
//        MapboxAccountManager.start(this, getString(R.string.access_token));
        setContentView(R.layout.activity_main);
        reportButton = findViewById(R.id.reportButton);

        mapView = (MapView) findViewById((R.id.mapView));
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        //mapView.getMapAsync(new OnMapReadyCallback() {
        //    @Override
        //    public void onMapReady(MapboxMap mapboxMap) {
        //        map = mapboxMap;
        //        enableLocation();
        //    // Customize map with markers, polylines, etc.

        //    }
        //});

        reportButton.setEnabled(true);
        reportButton.setBackgroundResource(R.color.mapbox_blue);

        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("YourMainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
            }
        }).execute();

        reportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LatLng point = new LatLng(originLocation.getLatitude(), originLocation.getLongitude());
                destinationMarker = map.addMarker(new MarkerOptions().position(point));
                put(point);
            }
        });
        AWSMobileClient.getInstance().initialize(this, new AWSStartupHandler() {
            @Override
            public void onComplete(AWSStartupResult awsStartupResult) {
                Log.d("YourMainActivity", "AWSMobileClient is instantiated and you are connected to AWS!");
            }
            }).execute();


        //Uncomment below and add the req URL to download GeoJSON file.
        // new DownloadFileTask().execute("URL");
    }



    public static void put(LatLng point) {
        double lat = round(point.getLatitude(),4);
        double lng = round(point.getLongitude(), 4);

        // TODO
        // send these to the lambda functions
        String user = "Anthony";
        String latLng = Double.toString(lat) + '_' + Double.toString(lng);
        long date = new Date().getTime()/1000;
        String command = "python ../../../../../../../../lambda.py";
        String param = " put " + user + " " + latLng + " " + date;
        String s = command + param;
        try {
            Process p = Runtime.getRuntime().exec(s);
        } catch (Exception e) {

        }
    }

    public static void updateMap() {
        // TODO
        // pull points from database, convert to geoJson, and update map with new markers
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(places, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        if(mapboxMap == null)
        {
            Log.d(tag, "[onMapReady] mapBox is null");
        } else {
            map = mapboxMap;
            map.getUiSettings().setCompassEnabled(true);
            map.getUiSettings().setZoomControlsEnabled(true);

            // Make location information available
            enableLocation();
        }
    }


    private void enableLocation() {
        if(PermissionsManager.areLocationPermissionsGranted(this)) {

            Log.d(tag, "Permissions are granted");
            initializeLocationEngine();
            initializeLocationLayer();
        } else {
            Log.d(tag, "Permissions are not granted");
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this );
        }
    }

    @SuppressLint("MissingPermission")
    private void initializeLocationEngine() {
        locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
        locationEngine.setInterval(5000); // preferably every 5 seconds
        locationEngine.setFastestInterval(1000); // at most every second
        locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
        //locationEngine.addLocationEngineListener(this);
        locationEngine.activate();


        Location lastLocation = locationEngine.getLastLocation();
        if (lastLocation != null) {
            originLocation = lastLocation;
            setCameraPosition(lastLocation);
        } else
            locationEngine.addLocationEngineListener(this);
    }

    @SuppressLint("MissingPermission")
    private void initializeLocationLayer() {

        if(mapView == null){
            Log.d(tag, "mapView is null");
        }else {
            if(map == null){
                Log.d(tag, "map is null");
            }else {
                locationLayerPlugin = new LocationLayerPlugin(mapView,map,locationEngine);
                locationLayerPlugin.setLocationLayerEnabled(true);
                locationLayerPlugin.setCameraMode(CameraMode.TRACKING);
                locationLayerPlugin.setRenderMode(RenderMode.NORMAL);
            }
        }

    }

    private void setCameraPosition(Location location) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                location.getLongitude()),13.0));
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnected() {
        Log.d(tag, "[onConnected] requesting location updates");
        locationEngine.requestLocationUpdates();
    }

    @Override
    public void onLocationChanged(Location location) {
        if(location != null){
            Log.d(tag, "[onLocationChanged] location is not null");
            originLocation = location;
            setCameraPosition(location);
        }else {
            Log.d(tag, "[onLocationChanged] location is null");
        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        //Present Toast/Dialogue to user to enable Location services
        Context context = getApplicationContext();
        CharSequence text = "Enable Location!";
        Log.d(tag, "Permissions: " + permissionsToExplain.toString());
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        Log.d(tag, "[onPermissionResult] granted == " + granted);
        if (granted) {
            enableLocation();
        } else {
            // Open a dialogue with the user
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }




    @SuppressLint("MissingPermission")
    @Override
    protected void onStart() {
        super.onStart();

        SharedPreferences settings = getSharedPreferences(preferencesFile,
                Context.MODE_PRIVATE);
        // use ”” as the default value (this might be the first time the app is run)
        downloadDate = settings.getString("lastDownloadDate", "");
        Log.d(tag, "[onStart] Recalled lastDownloadDate is ’" + downloadDate + "’");


        if(locationEngine != null) {
            locationEngine.requestLocationUpdates();
        } if(locationLayerPlugin != null)
            locationLayerPlugin.onStart();


        mapView.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.d(tag, "[onStop] Storing lastDownloadDate of " + downloadDate);
        // All objects are from android.context.Context
        SharedPreferences settings = getSharedPreferences(preferencesFile,
                Context.MODE_PRIVATE);
        // We need an Editor object to make preference changes.
        SharedPreferences.Editor editor = settings.edit();
        editor.putString("lastDownloadDate", downloadDate);
        // Apply the edits!
        editor.apply();

        if(locationEngine != null) {
            locationEngine.removeLocationUpdates();
        } if(locationLayerPlugin != null)
            locationLayerPlugin.onStop();

        mapView.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (locationEngine != null)
            locationEngine.deactivate();

        mapView.onDestroy();
    }


    @Override
    public void onMapClick(@NonNull LatLng point) {
    //    destinationMarker = map.addMarker(new MarkerOptions().position(point));
    //    destPos = Point.fromLngLat(point.getLongitude(),point.getLatitude());
    //    origPos = Point.fromLngLat(originLocation.getLongitude(),originLocation.getLatitude());

    }


    
    public class DownloadCompleteRunner {
         String result;
        public void downloadComplete(String result) {
            this.result = result;
        }
    }

    @SuppressLint("StaticFieldLeak")
    public class DownloadFileTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls){
            try {
                return loadFileFromNetwork(urls[0]);
            } catch (IOException e) {
                return "Unable to load content. Check your network connection";
            }
        }


        private String loadFileFromNetwork(String urlString) throws IOException {
            return readStream(downloadUrl(new URL(urlString)));

        }


        // Given a string representation of a URL, sets up a connection and gets an input stream.
        private InputStream downloadUrl(URL url) throws IOException {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(10000); // milliseconds
            conn.setConnectTimeout(15000); // milliseconds
            conn.setRequestMethod("GET");
            conn.setDoInput(true);
            conn.connect();
            return conn.getInputStream();
        }

        @NonNull
        private String readStream(InputStream stream)
                throws IOException {
        // Read input from stream, build result as a string
            return stream.toString();
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            new DownloadCompleteRunner().downloadComplete(result);
        }
    } // end class DownloadFileTask




}

