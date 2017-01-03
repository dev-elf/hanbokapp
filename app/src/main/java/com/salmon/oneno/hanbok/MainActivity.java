package com.salmon.oneno.hanbok;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.salmon.oneno.hanbok.search.Item;
import com.salmon.oneno.hanbok.search.OnFinishSearchListener;
import com.salmon.oneno.hanbok.search.Searcher;

import net.daum.mf.map.api.CalloutBalloonAdapter;
import net.daum.mf.map.api.CameraUpdateFactory;
import net.daum.mf.map.api.MapLayout;
import net.daum.mf.map.api.MapPOIItem;
import net.daum.mf.map.api.MapPoint;
import net.daum.mf.map.api.MapPointBounds;
import net.daum.mf.map.api.MapReverseGeoCoder;
import net.daum.mf.map.api.MapView;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

import static com.salmon.oneno.hanbok.MapApiConst.DAUM_MAPS_ANDROID_APP_API_KEY;
import static com.salmon.oneno.hanbok.R.id.buttonSearch;
import static com.salmon.oneno.hanbok.R.id.hanbokSearch;

public class MainActivity extends AppCompatActivity implements MapView.OpenAPIKeyAuthenticationResultListener, MapView.MapViewEventListener, MapView.CurrentLocationEventListener, MapReverseGeoCoder.ReverseGeoCodingResultListener, MapView.POIItemEventListener {
    private static final String LOG_TAG = "MainActivity";

    private MapView mMapView;
    private HashMap<Integer, Item> mTagItemMap = new HashMap<Integer, Item>();

    private final int MENU_LOCATION = Menu.FIRST;
    private static final int MENU_MAP_TYPE = Menu.FIRST + 1;
    private static final int MENU_MAP_MOVE = Menu.FIRST + 2;

    private MapReverseGeoCoder mReverseGeoCoder = null;
    private boolean isUsingCustomLocationMarker = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        //custom
        MapLayout mapLayout = new MapLayout(this);
        mMapView = mapLayout.getMapView();
        mMapView.setDaumMapApiKey(DAUM_MAPS_ANDROID_APP_API_KEY);
        mMapView.setOpenAPIKeyAuthenticationResultListener(this);
        mMapView.setMapViewEventListener(this);
        mMapView.setCurrentLocationEventListener(this);
        mMapView.setMapType(MapView.MapType.Standard);

        ViewGroup mapViewContainer = (ViewGroup) findViewById(R.id.map_view);
        mapViewContainer.addView(mapLayout);

        //검색
        mMapView.setPOIItemEventListener(this);
        mMapView.setCalloutBalloonAdapter(new CustomCalloutBalloonAdapter());

        Button button = (Button) findViewById(buttonSearch);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
            }
        });
        Button button2 = (Button) findViewById(hanbokSearch);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String query = "한복대여점";
                MapPoint.GeoCoordinate geoCoordinate = mMapView.getMapCenterPoint().getMapPointGeoCoord();
                double latitude = geoCoordinate.latitude; // 위도
                double longitude = geoCoordinate.longitude; // 경도
                int radius = 1000; // 중심 좌표부터의 반경거리. 특정 지역을 중심으로 검색하려고 할 경우 사용. meter 단위 (0 ~ 10000)
                int page = 1; // 페이지 번호 (1 ~ 3). 한페이지에 15개
                Toast.makeText(getApplicationContext(), String.format("위치는 (%f,%f)", latitude, longitude), Toast.LENGTH_SHORT).show();
                String apikey = DAUM_MAPS_ANDROID_APP_API_KEY;

                Searcher searcher = new Searcher(); // net.daum.android.map.openapi.search.Searcher
                searcher.searchKeyword(getApplicationContext(), query, latitude, longitude, radius, page, apikey, new OnFinishSearchListener() {
                    @Override
                    public void onSuccess(List<Item> itemList) {
                        mMapView.removeAllPOIItems(); // 기존 검색 결과 삭제
                        showResult(itemList); // 검색 결과 보여줌
                    }

                    @Override
                    public void onFail() {
                        showToast("API_KEY의 제한 트래픽이 초과되었습니다.");
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
        mMapView.setShowCurrentLocationMarker(false);
    }

    @Override
    public void onCurrentLocationUpdate(MapView mapView, MapPoint currentLocation, float accuracyInMeters) {
        MapPoint.GeoCoordinate mapPointGeo = currentLocation.getMapPointGeoCoord();

        Toast.makeText(getApplicationContext(), String.format("현재 위치는 (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude), Toast.LENGTH_SHORT).show();

        Log.i(LOG_TAG, String.format("MapView onCurrentLocationUpdate (%f,%f) accuracy (%f)", mapPointGeo.latitude, mapPointGeo.longitude, accuracyInMeters));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_MAP_TYPE, Menu.NONE, "MapType");
        menu.add(0, MENU_MAP_MOVE, Menu.NONE, "Move");
        menu.add(0, MENU_LOCATION, Menu.NONE, "Location");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final int itemId = item.getItemId();

        switch (itemId) {
            case MENU_MAP_TYPE: {

                //String hdMapTile = mMapView.isHDMapTileEnabled()? "HD Map Tile Off" : "HD Map Tile On";

                String hdMapTile;

                if (mMapView.getMapTileMode() == MapView.MapTileMode.HD2X) {
                    hdMapTile = "Set to Standard Mode";
                } else if (mMapView.getMapTileMode() == MapView.MapTileMode.HD) {
                    hdMapTile = "Set to HD 2X Mode";
                } else {
                    hdMapTile = "Set to HD Mode";
                }

                String[] mapTypeMenuItems = {"Standard", "Satellite", "Hybrid", hdMapTile, "Clear Map Tile Cache"};

                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setTitle("MapType");
                dialog.setItems(mapTypeMenuItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        controlMapTile(which);
                    }
                });
                dialog.show();


                return true;
            }

            case MENU_MAP_MOVE: {
                String rotateMapMenu = mMapView.getMapRotationAngle() == 0.0f ? "Rotate Map 60" : "Unrotate Map";
                String[] mapMoveMenuItems = {"Move to", "Zoom to", "Move and Zoom to", "Zoom In", "Zoom Out", rotateMapMenu};

                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setTitle("Move");
                dialog.setItems(mapMoveMenuItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        controlMapMove(which);
                    }

                });
                dialog.show();

                return true;
            }
            case MENU_LOCATION: {
                String[] mapMoveMenuItems = {
                        "User Location On",
                        "User Location On, MapMoving Off",
                        "User Location+Heading On",
                        "User Location+Heading On, MapMoving Off",
                        "Off",
                        (isUsingCustomLocationMarker ? "Default" : "Custom") + " Location Marker",
                        "Show Location Marker",
                        "Hide Location Marker"
                };
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setTitle("Location");
                dialog.setItems(mapMoveMenuItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0: // User Location On
                            {
                                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
                            }
                            break;
                            case 1: // User Location On, MapMoving Off
                            {
                                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeadingWithoutMapMoving);
                            }
                            break;
                            case 2: // User Location+Heading On
                            {
                                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeading);
                            }
                            break;
                            case 3: // User Location+Heading On, MapMoving Off
                            {
                                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithHeadingWithoutMapMoving);
                            }
                            break;
                            case 4: // Off
                            {
                                mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOff);
                                mMapView.setShowCurrentLocationMarker(false);
                            }
                            break;
                            case 5: // Default/Custom Location Marker
                            {
                                if (isUsingCustomLocationMarker) {
                                    mMapView.setCurrentLocationRadius(0);
                                    mMapView.setDefaultCurrentLocationMarker();
                                } else {
                                    mMapView.setCurrentLocationRadius(100); // meter
                                    mMapView.setCurrentLocationRadiusFillColor(Color.argb(77, 255, 255, 0));
                                    mMapView.setCurrentLocationRadiusStrokeColor(Color.argb(77, 255, 165, 0));

                                    MapPOIItem.ImageOffset trackingImageAnchorPointOffset = new MapPOIItem.ImageOffset(16, 16); // 좌하단(0,0) 기준 앵커포인트 오프셋
                                    MapPOIItem.ImageOffset directionImageAnchorPointOffset = new MapPOIItem.ImageOffset(65, 65);
                                    MapPOIItem.ImageOffset offImageAnchorPointOffset = new MapPOIItem.ImageOffset(15, 15);
                                    mMapView.setCustomCurrentLocationMarkerTrackingImage(R.drawable.custom_map_present_tracking, trackingImageAnchorPointOffset);
                                    mMapView.setCustomCurrentLocationMarkerDirectionImage(R.drawable.custom_map_present_direction, directionImageAnchorPointOffset);
                                    mMapView.setCustomCurrentLocationMarkerImage(R.drawable.custom_map_present, offImageAnchorPointOffset);
                                }
                                isUsingCustomLocationMarker = !isUsingCustomLocationMarker;
                            }
                            break;
                            case 6: // Show Location Marker
                            {
                                mMapView.setShowCurrentLocationMarker(true);
                            }
                            break;
                            case 7: // Hide Location Marker
                            {
                                if (mMapView.isShowingCurrentLocationMarker()) {
                                    mMapView.setShowCurrentLocationMarker(false);
                                }
                            }
                            break;
                        }
                    }

                });
                dialog.show();
                return true;
            }
        }


        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCurrentLocationDeviceHeadingUpdate(MapView mapView, float v) {

    }

    @Override
    public void onCurrentLocationUpdateFailed(MapView mapView) {

    }

    @Override
    public void onCurrentLocationUpdateCancelled(MapView mapView) {

    }


    private void onFinishReverseGeoCoding(String result) {
        Toast.makeText(MainActivity.this, "Reverse Geo-coding : " + result, Toast.LENGTH_SHORT).show();
    }

    private void controlMapMove(int which) {
        switch (which) {
            case 0: // Move to
            {
                mMapView.setMapCenterPoint(MapPoint.mapPointWithGeoCoord(37.579617, 126.974847), true);
            }
            break;
            case 1: // Zoom to
            {
                mMapView.setZoomLevel(7, true);
            }
            break;
            case 2: // Move and Zoom to
            {
                mMapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(33.41, 126.52), 9, true);
            }
            break;
            case 3: // Zoom In
            {
                mMapView.zoomIn(true);
            }
            break;
            case 4: // Zoom Out
            {
                mMapView.zoomOut(true);
            }
            break;
            case 5: // Rotate Map 60, Unrotate Map
            {
                if (mMapView.getMapRotationAngle() == 0.0f) {
                    mMapView.setMapRotationAngle(60.0f, true);
                } else {
                    mMapView.setMapRotationAngle(0.0f, true);
                }
            }
            break;
        }
    }

    /**
     * 지도 타일 컨트롤.
     */
    private void controlMapTile(int which) {
        switch (which) {
            case 0: // Standard
            {
                mMapView.setMapType(MapView.MapType.Standard);
            }
            break;
            case 1: // Satellite
            {
                mMapView.setMapType(MapView.MapType.Satellite);
            }
            break;
            case 2: // Hybrid
            {
                mMapView.setMapType(MapView.MapType.Hybrid);
            }
            break;
            case 3: // HD Map Tile On/Off
            {
                if (mMapView.getMapTileMode() == MapView.MapTileMode.HD2X) {
                    //Set to Standard Mode
                    mMapView.setMapTileMode(MapView.MapTileMode.Standard);
                } else if (mMapView.getMapTileMode() == MapView.MapTileMode.HD) {
                    //Set to HD 2X Mode
                    mMapView.setMapTileMode(MapView.MapTileMode.HD2X);
                } else {
                    //Set to HD Mode
                    mMapView.setMapTileMode(MapView.MapTileMode.HD);
                }
            }
            break;
            case 4: // Clear Map Tile Cache
            {
                MapView.clearMapTilePersistentCache();
            }
            break;
        }
    }

    //	/////////////////////////////////////////////////////////////////////////////////////////////////
    // net.daum.mf.map.api.MapView.OpenAPIKeyAuthenticationResultListener

    @Override
    public void onDaumMapOpenAPIKeyAuthenticationResult(MapView mapView, int resultCode, String resultMessage) {
        Log.i(LOG_TAG, String.format("Open API Key Authentication Result : code=%d, message=%s", resultCode, resultMessage));
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // net.daum.mf.map.api.MapView.MapViewEventListener

//    public void onMapViewInitialized(MapView mapView) {
//        Log.i(LOG_TAG, "MapView had loaded. Now, MapView APIs could be called safely");
//        //mMapView.setCurrentLocationTrackingMode(MapView.CurrentLocationTrackingMode.TrackingModeOnWithoutHeading);
//        mapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(37.537229, 127.005515), 2, true);
//    }

    @Override
    public void onMapViewCenterPointMoved(MapView mapView, MapPoint mapCenterPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapCenterPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onMapViewCenterPointMoved (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
    }

    @Override
    public void onMapViewDoubleTapped(MapView mapView, MapPoint mapPoint) {

        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("DaumMapLibrarySample");
        alertDialog.setMessage(String.format("Double-Tap on (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
        alertDialog.setPositiveButton("OK", null);
        alertDialog.show();
    }

    @Override
    public void onMapViewLongPressed(MapView mapView, MapPoint mapPoint) {

        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setTitle("DaumMapLibrarySample");
        alertDialog.setMessage(String.format("Long-Press on (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
        alertDialog.setPositiveButton("OK", null);
        alertDialog.show();
    }

    @Override
    public void onMapViewSingleTapped(MapView mapView, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onMapViewSingleTapped (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
    }

    @Override
    public void onMapViewDragStarted(MapView mapView, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onMapViewDragStarted (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
    }

    @Override
    public void onMapViewDragEnded(MapView mapView, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onMapViewDragEnded (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
    }

    @Override
    public void onMapViewMoveFinished(MapView mapView, MapPoint mapPoint) {
        MapPoint.GeoCoordinate mapPointGeo = mapPoint.getMapPointGeoCoord();
        Log.i(LOG_TAG, String.format("MapView onMapViewMoveFinished (%f,%f)", mapPointGeo.latitude, mapPointGeo.longitude));
    }

    @Override
    public void onMapViewZoomLevelChanged(MapView mapView, int zoomLevel) {
        Log.i(LOG_TAG, String.format("MapView onMapViewZoomLevelChanged (%d)", zoomLevel));
    }
    //custom

    @Override
    public void onReverseGeoCoderFoundAddress(MapReverseGeoCoder mapReverseGeoCoder, String s) {
        mapReverseGeoCoder.toString();
        onFinishReverseGeoCoding(s);
    }

    @Override
    public void onReverseGeoCoderFailedToFindAddress(MapReverseGeoCoder mapReverseGeoCoder) {
        onFinishReverseGeoCoding("Fail");
    }


    //검색기능 추가
    class CustomCalloutBalloonAdapter implements CalloutBalloonAdapter {

        private final View mCalloutBalloon;

        public CustomCalloutBalloonAdapter() {
            mCalloutBalloon = getLayoutInflater().inflate(R.layout.custom_callout_balloon, null);
        }

        @Override
        public View getCalloutBalloon(MapPOIItem poiItem) {
            if (poiItem == null) return null;
            Item item = mTagItemMap.get(poiItem.getTag());
            if (item == null) return null;
            ImageView imageViewBadge = (ImageView) mCalloutBalloon.findViewById(R.id.badge);
            TextView textViewTitle = (TextView) mCalloutBalloon.findViewById(R.id.title);
            textViewTitle.setText(item.title);
            TextView textViewDesc = (TextView) mCalloutBalloon.findViewById(R.id.desc);
            textViewDesc.setText(item.address);
            imageViewBadge.setImageDrawable(createDrawableFromUrl(item.imageUrl));
            return mCalloutBalloon;
        }

        @Override
        public View getPressedCalloutBalloon(MapPOIItem poiItem) {
            return null;
        }

    }
//    검색
//    private void hideSoftKeyboard() {
//        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.hideSoftInputFromWindow(mEditTextQuery.getWindowToken(), 0);
//    }

    public void onMapViewInitialized(MapView mapView) {
        Log.i(LOG_TAG, "MapView had loaded. Now, MapView APIs could be called safely");

        mMapView.setMapCenterPointAndZoomLevel(MapPoint.mapPointWithGeoCoord(37.537229, 127.005515), 2, true);

        Searcher searcher = new Searcher();
        String query = "한복대여점";
        double latitude = 37.537229;
        double longitude = 127.005515;
        int radius = 200; // 중심 좌표부터의 반경거리. 특정 지역을 중심으로 검색하려고 할 경우 사용. meter 단위 (0 ~ 10000)
        int page = 1;
        String apikey = MapApiConst.DAUM_MAPS_ANDROID_APP_API_KEY;

        searcher.searchKeyword(getApplicationContext(), query, latitude, longitude, radius, page, apikey, new OnFinishSearchListener() {
            @Override
            public void onSuccess(final List<Item> itemList) {
                showResult(itemList);
            }

            @Override
            public void onFail() {
                showToast("API_KEY의 제한 트래픽이 초과되었습니다.");
            }
        });
    }

    private void showToast(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showResult(List<Item> itemList) {
        MapPointBounds mapPointBounds = new MapPointBounds();

        for (int i = 0; i < itemList.size(); i++) {
            Item item = itemList.get(i);

            MapPOIItem poiItem = new MapPOIItem();
            poiItem.setItemName(item.title);
            poiItem.setTag(i);
            MapPoint mapPoint = MapPoint.mapPointWithGeoCoord(item.latitude, item.longitude);
            poiItem.setMapPoint(mapPoint);
            mapPointBounds.add(mapPoint);
            poiItem.setMarkerType(MapPOIItem.MarkerType.CustomImage);
            poiItem.setCustomImageResourceId(R.drawable.map_pin_blue);
            poiItem.setSelectedMarkerType(MapPOIItem.MarkerType.CustomImage);
            poiItem.setCustomSelectedImageResourceId(R.drawable.map_pin_red);
            poiItem.setCustomImageAutoscale(false);
            poiItem.setCustomImageAnchor(0.5f, 1.0f);

            mMapView.addPOIItem(poiItem);
            mTagItemMap.put(poiItem.getTag(), item);
        }

        mMapView.moveCamera(CameraUpdateFactory.newMapPointBounds(mapPointBounds));

        MapPOIItem[] poiItems = mMapView.getPOIItems();
        if (poiItems.length > 0) {
            mMapView.selectPOIItem(poiItems[0], false);
        }
    }

    private Drawable createDrawableFromUrl(String url) {
        try {
            InputStream is = (InputStream) this.fetch(url);
            Drawable d = Drawable.createFromStream(is, "src");
            return d;
        } catch (MalformedURLException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object fetch(String address) throws MalformedURLException, IOException {
        URL url = new URL(address);
        Object content = url.getContent();
        return content;
    }

    @Override
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem, MapPOIItem.CalloutBalloonButtonType calloutBalloonButtonType) {
        Item item = mTagItemMap.get(mapPOIItem.getTag());
        StringBuilder sb = new StringBuilder();
        sb.append("title=").append(item.title).append("\n");
        sb.append("imageUrl=").append(item.imageUrl).append("\n");
        sb.append("address=").append(item.address).append("\n");
        sb.append("newAddress=").append(item.newAddress).append("\n");
        sb.append("zipcode=").append(item.zipcode).append("\n");
        sb.append("phone=").append(item.phone).append("\n");
        sb.append("category=").append(item.category).append("\n");
        sb.append("longitude=").append(item.longitude).append("\n");
        sb.append("latitude=").append(item.latitude).append("\n");
        sb.append("distance=").append(item.distance).append("\n");
        sb.append("direction=").append(item.direction).append("\n");
        Toast.makeText(this, sb.toString(), Toast.LENGTH_SHORT).show();
    }

    @Override
    @Deprecated
    public void onCalloutBalloonOfPOIItemTouched(MapView mapView, MapPOIItem mapPOIItem) {
    }

    @Override
    public void onDraggablePOIItemMoved(MapView mapView, MapPOIItem mapPOIItem, MapPoint mapPoint) {
    }

    @Override
    public void onPOIItemSelected(MapView mapView, MapPOIItem mapPOIItem) {
    }


}
