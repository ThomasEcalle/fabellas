package groupe_9.com.fabellas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.PlaceLikelihood;
import com.google.android.gms.location.places.PlaceLikelihoodBuffer;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterManager;

import java.util.ArrayList;
import java.util.Iterator;

import groupe_9.com.fabellas.bo.PlaceItem;
import groupe_9.com.fabellas.bo.PlaceTag;
import groupe_9.com.fabellas.cache.DatabaseHelper;
import groupe_9.com.fabellas.utils.FabellasSettingsRequest;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class MapActivity
        extends AppCompatActivity
        implements
        OnMapReadyCallback,
        GoogleApiClient.OnConnectionFailedListener,
        GoogleApiClient.ConnectionCallbacks, View.OnClickListener, ClusterManager.OnClusterItemInfoWindowClickListener<PlaceItem>, ClusterManager.OnClusterClickListener<PlaceItem>
{
    public static final int ZOOM = 18;
    private static final int REQUEST_APPLICATION_SETTINGS_CODE = 1000;
    private static final int REQUEST_LOCATION_ON_SETTINGS_CODE = 2000;
    public static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 3000;


    public static final String PLACE = "placeID";
    public static final String TAG = "thomasecalle";
    public static final String INTENT_FROM_MAP_ACTIVITY = "intentFromMapActivity";
    public static final String USER_PROFIL_ACTION = "userProfilAction";

    private GoogleMap googleMap;
    private MapFragment mapFragment;
    private FusedLocationProviderClient locationProviderClient;
    private Location lastLocation;
    private View mapContainer;
    private ClusterManager<PlaceItem> clusterManager;

    private GoogleApiClient googleApiClient;
    private boolean isUserBackFromSearch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        setUpToolbar();

        mapContainer = findViewById(R.id.map_container);

        locationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        final ImageView btProfil = findViewById(R.id.floating_button_profil);
        btProfil.setVisibility(FirebaseAuth.getInstance().getCurrentUser().isAnonymous() ? View.GONE : View.VISIBLE);
        btProfil.setOnClickListener(this);


        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.PLACE_DETECTION_API)
                .enableAutoManage(this, this)
                .addConnectionCallbacks(this)
                .build();

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if (null == FirebaseAuth.getInstance().getCurrentUser())
        {
            startActivity(new Intent(this, ConnectionActivity.class));
            finish();
        }

        if (googleApiClient != null)
        {
            googleApiClient.connect();
        }

        /*
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);

        if (permissionCheck == PackageManager.PERMISSION_GRANTED && mapFragment != null && !isUserBackFromSearch)
        {
            goToMyLocation(googleMap);
        }
        */
        //goToMyLocation(googleMap);
        //mapFragment.getMapAsync(this);
    }

    @Override
    protected void onPause()
    {
        super.onPause();

        if (googleApiClient != null && googleApiClient.isConnected())
        {
            googleApiClient.disconnect();
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        this.googleMap = googleMap;
        clusterManager = new ClusterManager<PlaceItem>(MapActivity.this, googleMap);

        //this.googleMap.setOnInfoWindowClickListener(this);
        MapActivityPermissionsDispatcher.goToMyLocationWithPermissionCheck(this, googleMap);
    }

    @SuppressLint("MissingPermission")
    @NeedsPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    void goToMyLocation(GoogleMap googleMap)
    {
        googleMap.setMyLocationEnabled(true);
        googleMap.setOnCameraIdleListener(clusterManager);
        googleMap.setOnInfoWindowClickListener(clusterManager);
        googleMap.setOnMarkerClickListener(clusterManager);


        clusterManager.setOnClusterItemInfoWindowClickListener(this);
        clusterManager.setOnClusterClickListener(this);

        locationProviderClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>()
                {
                    @Override
                    public void onComplete(@NonNull Task<Location> task)
                    {
                        if (task.isSuccessful() && task.getResult() != null)
                        {
                            if (!isUserBackFromSearch)
                            {
                                lastLocation = task.getResult();
                                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(),
                                        lastLocation.getLongitude()), MapActivity.ZOOM));
                            }
                        }
                        else
                        {
                            displaySnackBar(FabellasSettingsRequest.LOCATION_ON_REQUEST);
                        }
                    }
                });

        lookForPlaces();
    }

    @Override
    public void onClusterItemInfoWindowClick(PlaceItem item)
    {
        final Intent intent = new Intent(this, PlaceStoriesActivity.class);
        intent.setAction(INTENT_FROM_MAP_ACTIVITY);
        final Bundle bundle = new Bundle();
        bundle.putSerializable(MapActivity.PLACE, (PlaceTag) item.getPlaceTag());
        intent.putExtras(bundle);

        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        MapActivityPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @OnShowRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    void showRationaleForLocation(final PermissionRequest request)
    {
        new AlertDialog.Builder(this)
                .setMessage(R.string.permission_location_rationale)
                .setPositiveButton(R.string.button_allow, (dialog, button) -> request.proceed())
                .setNegativeButton(R.string.button_deny, (dialog, button) -> request.cancel())
                .show();
    }

    @OnPermissionDenied(Manifest.permission.ACCESS_FINE_LOCATION)
    void showDeniedForLocation()
    {
        displaySnackBar(FabellasSettingsRequest.PERMISSION_REQUEST);
    }

    @OnNeverAskAgain(Manifest.permission.ACCESS_FINE_LOCATION)
    public void onNeverAskAgain()
    {
        displaySnackBar(FabellasSettingsRequest.PERMISSION_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        switch (requestCode)
        {
            case REQUEST_APPLICATION_SETTINGS_CODE:
                MapActivityPermissionsDispatcher.goToMyLocationWithPermissionCheck(this, this.googleMap);
                break;

            case REQUEST_LOCATION_ON_SETTINGS_CODE:
                MapActivityPermissionsDispatcher.goToMyLocationWithPermissionCheck(this, this.googleMap);
                break;

            case PLACE_AUTOCOMPLETE_REQUEST_CODE:
                if (resultCode == RESULT_OK)
                {
                    isUserBackFromSearch = true;
                    final Place place = PlaceAutocomplete.getPlace(this, data);
                    this.googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), MapActivity.ZOOM));
                    putMarkerOnPlace(place);
                    //retrievingPlacesFromDatabase();

                    Log.i(MapActivity.TAG, "Place Searched : " + place.getName());
                }
                else if (resultCode == PlaceAutocomplete.RESULT_ERROR)
                {
                    Status status = PlaceAutocomplete.getStatus(this, data);
                    Log.i(MapActivity.TAG, status.getStatusMessage());
                }
                else if (resultCode == RESULT_CANCELED)
                {
                    Log.i(MapActivity.TAG, "Search cancelled by user");

                }
        }

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
        Toast.makeText(this, R.string.places_api_connection_failed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        //Toast.makeText(this, "on connected", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        //Toast.makeText(this, "on connection suspended", Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("MissingPermission")
    public void lookForPlaces()
    {
        final PendingResult<PlaceLikelihoodBuffer> currentPlaces = Places.PlaceDetectionApi.getCurrentPlace(googleApiClient, null);

        currentPlaces.setResultCallback(new ResultCallback<PlaceLikelihoodBuffer>()
        {
            @Override
            public void onResult(PlaceLikelihoodBuffer likelyPlaces)
            {
                final int statusCodes = likelyPlaces.getStatus().getStatusCode();

                if (statusCodes == CommonStatusCodes.NETWORK_ERROR)
                {
                    retrievingPlacesFromDatabase();
                }
                else
                {
                    for (PlaceLikelihood placeLikelihood : likelyPlaces)
                    {
                        final Place place = placeLikelihood.getPlace();

                        Log.i(MapActivity.TAG, String.format("Place '%s' found with id: '%s'", place.getName(), place.getId()));

                        putMarkerOnPlace(place);

                    }
                }

                likelyPlaces.release();

            }
        });


    }

    @Override
    public void onClick(View view)
    {
        switch (view.getId())
        {
            case R.id.icon:
                lookForUniquePlace();
                break;
            case R.id.icon_logout:
                handleLogout();

                break;
            case R.id.floating_button_profil:
                Log.i(MapActivity.TAG, String.format("BOUTTON PROFIL"));
                Intent intent = new Intent(this, UserStoriesActivity.class);
                intent.setAction(USER_PROFIL_ACTION);
                startActivity(intent);
                break;


        }
    }

    private void handleLogout()
    {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.deconnection_title);
        builder.setMessage(R.string.deconnection_message);
        builder.setCancelable(false);

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                FirebaseAuth.getInstance().signOut();
                if (user.isAnonymous())
                {
                    user.delete();
                }
                startActivity(new Intent(MapActivity.this, ConnectionActivity.class));
                finish();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialogInterface, int i)
            {
                dialogInterface.dismiss();
            }
        });

        builder.create().show();

    }

    private void putMarkerOnPlace(Place place)
    {
        final PlaceTag placeTag = new PlaceTag(place.getName().toString(), place.getId());
        final PlaceItem placeItem = new PlaceItem(placeTag, place.getLatLng().latitude, place.getLatLng().longitude);

        final DatabaseHelper databaseHelper = new DatabaseHelper(this, DatabaseHelper.DB_NAME, DatabaseHelper.DB_VERSION);

        databaseHelper.addPlace(placeItem);

        clusterManager.addItem(placeItem);
    }

    private void retrievingPlacesFromDatabase()
    {
        final DatabaseHelper databaseHelper = new DatabaseHelper(this, DatabaseHelper.DB_NAME, DatabaseHelper.DB_VERSION);
        final ArrayList<PlaceItem> allPlaces = databaseHelper.getAllPlaces();

        if (clusterManager != null)
        {
            clusterManager.clearItems();
        }
        for (PlaceItem placeItem : allPlaces)
        {
            clusterManager.addItem(placeItem);
        }
    }


    private void lookForUniquePlace()
    {
        try
        {
            final Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN)
                    .setFilter(null)
                    .build(this);
            startActivityForResult(intent, MapActivity.PLACE_AUTOCOMPLETE_REQUEST_CODE);
        }

        catch (GooglePlayServicesRepairableException e)
        {
            Toast.makeText(this, "An error occured", Toast.LENGTH_SHORT).show();

        }
        catch (GooglePlayServicesNotAvailableException e)
        {
            Toast.makeText(this, "Google services not availables", Toast.LENGTH_SHORT).show();
        }
    }

    private void setUpToolbar()
    {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        final TextView toolbarTitle = toolbar.findViewById(R.id.title);

        final ImageView iconImageView = findViewById(R.id.icon);
        iconImageView.setImageResource(R.drawable.ic_search);
        iconImageView.setOnClickListener(this);
        final ImageView logoutIconImageView = findViewById(R.id.icon_logout);
        logoutIconImageView.setImageResource(R.drawable.ic_log_out);
        logoutIconImageView.setOnClickListener(this);
        toolbarTitle.setText(R.string.app_name);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
    }

    private void displaySnackBar(FabellasSettingsRequest settingsRequest)
    {
        final Snackbar snackbar = Snackbar.make(mapContainer, R.string.error_permission_geolocation, Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.open_settings, new View.OnClickListener()
        {
            Intent intent = null;
            int requestCode = 0;

            @Override
            public void onClick(View view)
            {
                switch (settingsRequest)
                {
                    case PERMISSION_REQUEST:
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.fromParts("package", BuildConfig.APPLICATION_ID, null));
                        requestCode = MapActivity.REQUEST_APPLICATION_SETTINGS_CODE;
                        break;

                    case LOCATION_ON_REQUEST:
                        intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                        requestCode = MapActivity.REQUEST_LOCATION_ON_SETTINGS_CODE;
                        break;
                }
                startActivityForResult(intent, requestCode);

                snackbar.dismiss();
            }
        });

        snackbar.show();
    }


    @Override
    public boolean onClusterClick(Cluster<PlaceItem> cluster)
    {
        final Iterator<PlaceItem> iterator = cluster.getItems().iterator();
        if (!iterator.hasNext())
        {
            return false;
        }

        final PlaceItem item = iterator.next();
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom
                (new LatLng(item.getPosition().latitude,
                        item.getPosition().longitude), MapActivity.ZOOM));
        return true;
    }
}
