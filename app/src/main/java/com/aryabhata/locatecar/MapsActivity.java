package com.aryabhata.locatecar;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.animation.FastOutSlowInInterpolator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.internal.MDTintHelper;
import com.afollestad.materialdialogs.internal.ThemeSingleton;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

import uk.co.samuelwall.materialtaptargetprompt.MaterialTapTargetPrompt;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,OnMarkerDragListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    LocationRequest mLocationRequest;
    SharedPreferences sharedpreferences;
    final String TAG = "LocateCar";
    FloatingActionButton shareLocation;
    private EditText locationUrl;
    private View findAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkLocationPermission();
        }

        final MaterialTapTargetPrompt mFabPrompt = new MaterialTapTargetPrompt.Builder(MapsActivity.this)
                .setTarget(findViewById(R.id.capture))
                .setPrimaryText("A smart way to locate your vehicle")
                .setSecondaryText("Share or Find you Vehicle\nTap the button for capture location")
                .setAnimationInterpolator(new FastOutSlowInInterpolator())
                .setOnHidePromptListener(new MaterialTapTargetPrompt.OnHidePromptListener() {
                    @Override
                    public void onHidePrompt(MotionEvent event, boolean tappedTarget) {
                        //Do something such as storing a value so that this prompt is never shown again
                    }

                    @Override
                    public void onHidePromptComplete() {

                    }
                })
                .create();
        mFabPrompt.show();


        // GPS Check
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if ( !manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
            buildAlertMessageNoGps();
        }
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // code for long clicking the map displays a floating instruction menu
        //final FloatingActionsMenu menuCar = (FloatingActionsMenu) findViewById(R.id.multiple_actions);

        //menuCar.setOnLongClickListener(new View.OnLongClickListener() {
        //  @Override
        //public boolean onLongClick(View view) {
        //  mFabPrompt.show();
        //return true;
        //}
        //});


        // Floating Menu
        final FloatingActionButton captureCar = (FloatingActionButton) findViewById(R.id.capture);
        captureCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureCar.setTitle("Capture Car Location");
                captureCarLocation();
            }
        });

        shareLocation = (FloatingActionButton) findViewById(R.id.share);
        shareLocation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareLocation.setTitle("Share Car Location");
                shareIt();
            }
        });

        shareLocation.setVisibility(View.GONE);
        sharedpreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        if((sharedpreferences.getString("shorturl","default") != null) && (sharedpreferences.getString("shorturl","default") != "default")) {
            shareLocation.setVisibility(View.VISIBLE);
        }

        final FloatingActionButton findCar = (FloatingActionButton) findViewById(R.id.find);
        findCar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                findCar.setTitle("Find the Car Location");
                findCarLocation();
            }
        });


    }

    public void captureCarLocation() {
        sharedpreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedpreferences.edit();
        String lat=String.valueOf(mLastLocation.getLatitude());
        String lng=String.valueOf(mLastLocation.getLongitude());
        editor.putString("Latitude",lat);
        editor.putString("Longitude", lng);
        String locationid=lat+","+lng;
        Log.e(TAG,locationid);
        editor.putString("locationkey", locationid);
        editor.apply();
        Log.e(TAG,"Latitude id is " + lat);
        Log.e(TAG,"Longitude id is " + lng);
        String l= sharedpreferences.getString("locationkey","default");
        Log.e(TAG,"locationkey from db is " +l);
        Toast.makeText(this, locationid, Toast.LENGTH_LONG).show();

        // code for generating shorten url
        newShortAsync task = new newShortAsync();
        task.execute();
    }

    public void findCarLocation() {
        MaterialDialog dialog = new MaterialDialog.Builder(this)
                .title(R.string.FindCarTitle)
                .customView(R.layout.dialog_findcar, true)
                .positiveText(R.string.FindButton)
                .negativeText(android.R.string.cancel)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        final Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://goo.gl/" + locationUrl.getText().toString()));
                        startActivity(intent);

                    }
                }).build();

        findAction = dialog.getActionButton(DialogAction.POSITIVE);
        //noinspection ConstantConditions
        locationUrl = (EditText) dialog.getCustomView().findViewById(R.id.locationurl);
        sharedpreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        if((sharedpreferences.getString("shorturl","default") != null) &&
                (sharedpreferences.getString("shorturl","default") != "default")) {
            locationUrl.setText((sharedpreferences.getString("shorturl", "default")).split("gl/")[1]);
            locationUrl.setSelection(locationUrl.getText().length());
        }
        locationUrl.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                locationUrl.setFocusable(true);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });


        int widgetColor = ThemeSingleton.get().widgetColor;
        MDTintHelper.setTint(locationUrl,
                widgetColor == 0 ? ContextCompat.getColor(this, R.color.pink) : widgetColor);

        dialog.show();
    }

    private void shareIt()  {
        String s;
        sharedpreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        if((sharedpreferences.getString("shorturl","default") != null) && (sharedpreferences.getString("shorturl","default") != "default")) {
            s = sharedpreferences.getString("shorturl","default");
            System.out.println("ID:" + s);
            Log.e(TAG,"shortened value is " + s);
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            String[] key=s.split("gl/");
            System.out.println("code in maps activity : " +  key[1]);
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "AndroidSolved");
            sharingIntent.putExtra(Intent.EXTRA_TEXT, key[1]);
            startActivity(Intent.createChooser(sharingIntent, "Share via"));
        } else {
            MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                    .title("Capture")
                    .content("Your Car location is not captured, do you want to capture it?")
                    .positiveText("Capture")
                    .negativeText("Dismiss");

            builder.onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    captureCarLocation();
                }
            });

            builder.onNegative(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    dialog.dismiss();
                }
            });

            MaterialDialog dialog = builder.build();
            dialog.show();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
                mMap.setOnMarkerDragListener(this);
            }
        }
        else {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
            mMap.setOnMarkerDragListener(this);
        }
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onLocationChanged(Location location) {
        mLastLocation = location;
        if (mCurrLocationMarker != null) {
            mCurrLocationMarker.remove();
        }
        //Place current location marker
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        markerOptions.title("Current Position");
        markerOptions.draggable(true);
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
        mCurrLocationMarker = mMap.addMarker(markerOptions);
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(17));
        //stop location updates
        if (mGoogleApiClient != null) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public boolean checkLocationPermission(){
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Asking user if explanation is needed
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

                //Prompt the user once explanation has been shown
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);


            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {

            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_LOCATION: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (ContextCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_FINE_LOCATION)
                            == PackageManager.PERMISSION_GRANTED) {

                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }

                } else {

                    // Permission denied, Disable the functionality that depends on this permission.
                    MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                            .title("Permissions Required!")
                            .content("Permissions are not enabled, do you want to enable it?")
                            .positiveText("Enable")
                            .negativeText("Dismiss");

                    builder.onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            checkLocationPermission();
                        }
                    });

                    builder.onNegative(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            dialog.dismiss();
                            finish();
                        }
                    });

                    MaterialDialog dialog = builder.build();
                    dialog.show();
                }
                return;
            }
        }
    }

    @Override
    public void onMarkerDragStart(Marker marker) {

    }

    @Override
    public void onMarkerDrag(Marker marker) {

    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        //Getting the coordinates
        double latitude = marker.getPosition().latitude;
        double longitude = marker.getPosition().longitude;
        Log.e(TAG, "Latitude id is " + latitude);
        Log.e(TAG,"Longitude id is " + longitude);
        mLastLocation.setLongitude(longitude);
        mLastLocation.setLatitude(latitude);
        LatLng latLong = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLong));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(17));
    }

    // code for shorten url implementation
    public class newShortAsync extends AsyncTask<Void, Void, String> {

        String db_loc= sharedpreferences.getString("locationkey","deepa");
        String longUrl = "http://maps.google.com/maps?saddr=My+Location&daddr="+db_loc;
        String idurl=null;

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            System.out.println("JSON RESP:" + s);
            String response = s;
            try {
                JSONObject jsonObject = new JSONObject(response);
                idurl = jsonObject.getString("id");
                sharedpreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedpreferences.edit();
                editor.putString("shorturl",idurl);
                editor.apply();
                System.out.println("ID:" + idurl);
                shareLocation.setVisibility(View.VISIBLE);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected String doInBackground(Void... params) {
            BufferedReader reader;
            StringBuffer buffer;
            String res = null;
            String json = "{\"longUrl\": \"" + longUrl + "\"}";
            try {
                URL url = new URL("https://www.googleapis.com/urlshortener/v1/url?key=AIzaSyDLel-zMRXtYYu-jHWP-JJogzUH04cR_CM");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setReadTimeout(40000);
                con.setConnectTimeout(40000);
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type", "application/json");
                OutputStream os = con.getOutputStream();
                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(os, "UTF-8"));

                writer.write(json);
                writer.flush();
                writer.close();
                os.close();

                int status = con.getResponseCode();
                InputStream inputStream;
                if (status == HttpURLConnection.HTTP_OK)
                    inputStream = con.getInputStream();
                else
                    inputStream = con.getErrorStream();

                reader = new BufferedReader(new InputStreamReader(inputStream));

                buffer = new StringBuffer();

                String line = "";
                while ((line = reader.readLine()) != null) {
                    buffer.append(line);
                }

                res = buffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (ProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return res;
        }
    }

    private void buildAlertMessageNoGps() {

        MaterialDialog.Builder builder = new MaterialDialog.Builder(this)
                .title("GPS Check!")
                .content("Your GPS is disabled, do you want to enable it?")
                .positiveText("Enable")
                .negativeText("Dismiss");

        builder.onPositive(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            }
        });

        builder.onNegative(new MaterialDialog.SingleButtonCallback() {
            @Override
            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                dialog.dismiss();
                finish();
            }
        });

        MaterialDialog dialog = builder.build();
        dialog.show();
    }
}