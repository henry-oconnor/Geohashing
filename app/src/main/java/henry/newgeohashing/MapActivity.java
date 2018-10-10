package henry.newgeohashing;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.media.Image;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.android.telemetry.location.LostLocationEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;

import static henry.newgeohashing.MD5.computeMD5;
import static henry.newgeohashing.MD5.toHexString;

public class MapActivity extends AppCompatActivity implements HomemadeCallbackInterface {

    public final int REQUEST_CODE_GRANTED = 1;
    public final int REQUEST_CODE_DENIED = 0;

    private static volatile boolean usingLocation = true;
    private static final String TAG = "MapActivity";
    private MapView mMapView;
    private LatLng mLocation;
    private LatLng mDestination;
    private Button mHashBtn;
    private Button nNavigateBtn;
    private ImageButton mLocationBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        Mapbox.getInstance(this, "pk.eyJ1IjoiYmFsc2FmcmVzaCIsImEiOiJjamVibXlraXMwYzFuMndvMmc3ZmlvZ3h2In0.9n_cDsxQyjoO_dvqjwN9eQ");

        mMapView = (MapView) findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);

        nNavigateBtn = (Button) findViewById(R.id.navigate_button);
        nNavigateBtn.setOnClickListener(e -> {
            startNavigation(mLocation, mDestination);
        });

        // gets an async MapboxMap object
        mMapView.getMapAsync((MapboxMap mapboxMap) -> {
            try {
                Location lastLocation = getCurrentLocation(this);
                LatLng point = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                mapboxMap.addMarker(new MarkerOptions().position(point));
                mapboxMap.addPolyline(makeRectangle(point));
                snapToRectangle(mapboxMap, point);
                mLocation = point;
            } catch (NullPointerException ex) {
                Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT);
            }

            mapboxMap.addOnMapClickListener((LatLng point) -> {
                if (!usingLocation) {
                    mapboxMap.clear();
                    mapboxMap.addMarker(new MarkerOptions().position(point));
                    mapboxMap.addPolyline(MapActivity.this.makeRectangle(point));
                    MapActivity.this.snapToRectangle(mapboxMap, point);
                    mLocation = point;
                }
            });

            mLocationBtn = (ImageButton) findViewById(R.id.my_location_button);
            mLocationBtn.setOnClickListener(view -> {
                usingLocation = !usingLocation;
                if(usingLocation){
                    try {
                        mLocationBtn.setImageResource(android.R.drawable.ic_menu_mylocation);
                        Location lastLocation = getCurrentLocation(this);
                        LatLng point = new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude());
                        mapboxMap.addMarker(new MarkerOptions().position(point));
                        mapboxMap.addPolyline(makeRectangle(point));
                        snapToRectangle(mapboxMap, point);
                        mLocation = point;
                    } catch (NullPointerException ex) {
                        Toast.makeText(this, "Location unavailable", Toast.LENGTH_SHORT);
                    }
                }else{
                    mapboxMap.clear();
                    mLocationBtn.setImageResource(android.R.drawable.ic_menu_compass);
                }
            });

            mHashBtn = (Button) findViewById(R.id.lil_button);
            mHashBtn.setOnClickListener(view -> new GetHashTask().execute());
        });
    }

    /**
     * returns user's location (or null if not found)
     */
    protected Location getCurrentLocation(Context context) {
        LocationEngine locationEngine = new LostLocationEngine(context);
        locationEngine.setPriority(LocationEnginePriority.LOW_POWER);
        locationEngine.setInterval(5000);
        locationEngine.activate();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // request location access
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_CODE_GRANTED);
        } else {
            return locationEngine.getLastLocation();
        }
        return null;
    }

    /**
     * builds rectangle enclosing user's starting point
     */
    public PolylineOptions makeRectangle(LatLng point) {
        // get sign of latitude and longitude
        double lat = (int) point.getLatitude();
        int latSign = (int) Math.signum(point.getLatitude());
        double lng = (int) point.getLongitude();
        int lngSign = (int) Math.signum(point.getLongitude());

        PolylineOptions rectangle = new PolylineOptions();
        rectangle.add(new LatLng(lat, lng));
        rectangle.add(new LatLng(lat + latSign, lng));
        rectangle.add(new LatLng(lat + latSign, lng + lngSign));
        rectangle.add(new LatLng(lat, lng + lngSign));
        rectangle.add(new LatLng(lat, lng));
        return rectangle.width(4);
    }

    /**
     *     returns point in center of rectangle, used in snapToRectangle
     */
    public LatLng getCenterOfRectangle(LatLng point) {
        double lat = (int) point.getLatitude();
        int latSign = (int) Math.signum(point.getLatitude());
        double lng = (int) point.getLongitude();
        int lngSign = (int) Math.signum(point.getLongitude());

        return new LatLng(lat + (0.5 * latSign), lng + (0.5 * lngSign));
    }

    public JSONObject getAAPLOpening() {
        JSONObject ob = new JSONObject();

        try {
            String url = Uri.parse("https://api.iextrading.com/1.0/stock/aapl/ohlc")
                    .buildUpon().build().toString();
            ob = new JSONObject(new String(getUrlBytes(url)));
        } catch (IOException ioe) {
            Log.e(TAG, "getAAPLOpening: Failed to fetch items", ioe);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return ob;
    }

    private class GetHashTask extends AsyncTask<Void, Void, String> {
        String date = new SimpleDateFormat("dd/MM/YYYY").toString();
        String price = null;
        String hash = null;
        String latHash = null;
        String lngHash = null;

        @Override
        protected String doInBackground(Void... voids) {
            JSONObject ob = getAAPLOpening();
            JSONObject open = null;
            try {
                if (ob.has("open")) {
                    open = ob.getJSONObject("open");
                    price = open.getString("price");
                    return price;
                }
            } catch (JSONException e) {
                Log.e(TAG, "doInBackground: ", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String price) {
            if (!price.equals(null)) {
                hash = toHexString(computeMD5((date + price).getBytes()));
                new Toast(getApplicationContext()).makeText(getApplicationContext(),
                        String.format("AAPL opening: %s", price), Toast.LENGTH_LONG).show();
                new Toast(getApplicationContext()).makeText(getApplicationContext(),
                        String.format("Hash: %s", hash), Toast.LENGTH_LONG).show();
                callback(hash);
            }
        }
    }

    public byte[] getUrlBytes(String urlSpec) throws IOException {
        URL url = new URL(urlSpec);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = connection.getInputStream();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException(connection.getResponseMessage() +
                        ": with " +
                        urlSpec);
            }
            int bytesRead = 0;
            byte[] buffer = new byte[1024];
            while ((bytesRead = in.read(buffer)) > 0) {
                out.write(buffer, 0, bytesRead);
            }
            out.close();
            return out.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void callback(String hash) {

        String latString = Integer.toString((int) mLocation.getLatitude()) + ".";
        String substr = hash.substring(0, hash.length() / 2);
        BigInteger longSubstr = new BigInteger(substr, 16);
        latString += longSubstr.toString();

        String lngString = Integer.toString((int) mLocation.getLongitude()) + ".";
        substr = hash.substring(hash.length() / 2, hash.length());
        longSubstr = new BigInteger(substr, 16);
        lngString += longSubstr.toString();

        placeDestinationMarker(Double.parseDouble(latString), Double.parseDouble(lngString));
    }

    public void placeDestinationMarker(Double lat, Double lng) {
        mDestination = new LatLng(lat, lng);
        mMapView.getMapAsync(mapboxMap -> {
            mapboxMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lng))
                    .title("Adventure spot\nLat " + Double.toString(lat) + "\nLng " + Double.toString(lng))
            );
        });
    }

    public void snapToRectangle(MapboxMap mapboxMap, LatLng point) {
        mapboxMap.clear();
        mapboxMap.addMarker(new MarkerOptions().position(point));
        mapboxMap.addPolyline(makeRectangle(point));

        // set camera position and zoom

        CameraPosition.Builder camBuilder = new CameraPosition.Builder();
        camBuilder.target(new LatLng(getCenterOfRectangle(point)));
        camBuilder.zoom(8);

//                new Toast(getApplicationContext()).makeText(getApplicationContext(),
//                        String.format("Lat: %s\nLng: %s", point.getLatitude(), point.getLongitude()), Toast.LENGTH_SHORT).show();

        mapboxMap.easeCamera(m -> camBuilder.build());
    }

    public void startNavigation(LatLng start, LatLng end) {
        if (start != null && end != null) {
            String startStr = start.getLatitude() + "," + start.getLongitude();
            String endStr = end.getLatitude() + "," + end.getLongitude();
            String url = new Uri.Builder()
                    .scheme("https")
                    .authority("maps.google.com")
                    .appendPath("maps")
                    .appendQueryParameter("saddr", startStr)
                    .appendQueryParameter("daddr", endStr)
                    .build().toString();
            Intent intent = new Intent(android.content.Intent.ACTION_VIEW,
                    Uri.parse(url));
            startActivity(intent);
        } else
            new Toast(getApplicationContext()).makeText(getApplicationContext(),
                    "Generate your destination first", Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onStart() {
        super.onStart();
        mMapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mMapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mMapView.onSaveInstanceState(outState);
    }
}

