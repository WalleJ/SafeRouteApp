package com.example.saferouteapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.core.content.ContextCompat;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.Polygon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
    private MapView map;
    private EditText originEditText, destinationEditText;
    private Button routeButton, vehicleRouteButton;
    private ImageButton backButton;
    private Marker originMarker, destinationMarker;
    private FloatingActionButton menuButton, zoomInButton, zoomOutButton, dangerZonesButton,
            streetCrimeFilterButton, vehicleCrimeFilterButton;
    private Button reportCrimeButton;

    private final String MAPBOX_ACCESS_TOKEN = "pk.eyJ1IjoibHVjYXNhZzA1IiwiYSI6ImNtZ3poaTdxMDAwOGcyaXBxYWRvYzJkanIifQ.2tJ3eYfxB8W5NbeQTKNHwA";

    private LinearLayout routeInfoLayout, searchLayout;
    private LinearLayout safeRouteOption, fastRouteOption;
    private TextView safeRouteInfo, fastRouteInfo;
    private ImageView fastRouteRadio;

    private final List<Polyline> routeOverlays = new ArrayList<>();
    private final String GRAPHHOPPER_API_KEY = "34f7e5c8-bf47-4cb5-99e4-24d04d61ef0f";
    
    // Variables para almacenar ambas rutas
    private List<RouteInfo> safeRoutes = new ArrayList<>();
    private List<RouteInfo> fastRoutes = new ArrayList<>();
    private boolean safeRouteSelected = true;
    
    // Variables para filtros de crimen
    private boolean showStreetCrime = true;
    private boolean showVehicleCrime = true;
    private boolean showAllCrimes = true;
    private boolean vehicleMode = false; // Modo vehículo para rutas específicas

    private static class SafePoint {
        final String name;
        final GeoPoint location;
        final String type;

        SafePoint(String name, GeoPoint location, String type) {
            this.name = name;
            this.location = location;
            this.type = type;
        }
    }

    private static class CrimeAlert {
        final String title;
        final String description;
        final String address; // Dirección que será geocodificada
        final String timeAgo;
        final String crimeType; // Tipo de crimen
        GeoPoint location; // Se establecerá después de la geocodificación

        CrimeAlert(String title, String description, String address, String timeAgo, String crimeType) {
            this.title = title;
            this.description = description;
            this.address = address;
            this.timeAgo = timeAgo;
            this.crimeType = crimeType;
            this.location = null; // Se establecerá más tarde
        }
    }

    private final List<SafePoint> safePoints = new ArrayList<>();
    private final List<Marker> safePointMarkers = new ArrayList<>();
    private final List<CrimeAlert> crimeAlerts = new ArrayList<>();
    private final List<Marker> crimeAlertMarkers = new ArrayList<>();
    private final List<Polygon> dangerZones = new ArrayList<>();
    private final List<Polygon> dangerZoneOverlays = new ArrayList<>();
    private boolean showDangerZones = false;


    private static class RouteInfo {
        final List<GeoPoint> points;
        final long timeInMillis;
        final double distanceInMeters;

        RouteInfo(List<GeoPoint> points, long timeInMillis, double distanceInMeters) {
            this.points = points;
            this.timeInMillis = timeInMillis;
            this.distanceInMeters = distanceInMeters;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(), getSharedPreferences("OSM", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_main);

        map = findViewById(R.id.map);

        final XYTileSource mapboxTileSource = new XYTileSource("Mapbox", 0, 22, 256, ".png",
                new String[] { "https://api.mapbox.com/styles/v1/mapbox/streets-v11/tiles/" }) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl() + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "?access_token=" + MAPBOX_ACCESS_TOKEN;
            }
        };
        map.setTileSource(mapboxTileSource);

        map.setMultiTouchControls(true);

        IMapController mapController = map.getController();
        mapController.setZoom(15.0); // Zoom inicial más amplio, se ajustará automáticamente
        // Centro inicial en Buenos Aires, se recentrará cuando se carguen las alertas
        mapController.setCenter(new GeoPoint(-34.6037, -58.3816));

        originEditText = findViewById(R.id.origin_text);
        destinationEditText = findViewById(R.id.destination_text);
        routeButton = findViewById(R.id.route_button);
        vehicleRouteButton = findViewById(R.id.vehicle_route_button);
        routeInfoLayout = findViewById(R.id.route_info_layout);
        searchLayout = findViewById(R.id.search_layout);
        safeRouteOption = findViewById(R.id.safe_route_option);
        fastRouteOption = findViewById(R.id.fast_route_option);
        safeRouteInfo = findViewById(R.id.safe_route_info);
        fastRouteInfo = findViewById(R.id.fast_route_info);
        fastRouteRadio = findViewById(R.id.fast_route_radio);
        menuButton = findViewById(R.id.menu_button);
        reportCrimeButton = findViewById(R.id.report_crime_button);
        backButton = findViewById(R.id.back_button);
        zoomInButton = findViewById(R.id.zoom_in_button);
        zoomOutButton = findViewById(R.id.zoom_out_button);
        dangerZonesButton = findViewById(R.id.danger_zones_button);
        streetCrimeFilterButton = findViewById(R.id.street_crime_filter_button);
        vehicleCrimeFilterButton = findViewById(R.id.vehicle_crime_filter_button);


        routeButton.setOnClickListener(v -> {
            String originAddress = originEditText.getText().toString();
            String destinationAddress = destinationEditText.getText().toString();

            if (originAddress.isEmpty() || destinationAddress.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresa un origen y un destino", Toast.LENGTH_SHORT).show();
                return;
            }
            vehicleMode = false;
            hideKeyboard();
            calculateBothRoutes(originAddress, destinationAddress);
        });
        
        vehicleRouteButton.setOnClickListener(v -> {
            String originAddress = originEditText.getText().toString();
            String destinationAddress = destinationEditText.getText().toString();

            if (originAddress.isEmpty() || destinationAddress.isEmpty()) {
                Toast.makeText(this, "Por favor, ingresa un origen y un destino", Toast.LENGTH_SHORT).show();
                return;
            }
            vehicleMode = true;
            hideKeyboard();
            calculateBothRoutes(originAddress, destinationAddress);
        });

        // Configurar listeners para las opciones de ruta
        safeRouteOption.setOnClickListener(v -> selectRouteType(true));
        fastRouteOption.setOnClickListener(v -> selectRouteType(false));

        menuButton.setOnClickListener(v -> Toast.makeText(this, "Botón de Menú presionado", Toast.LENGTH_SHORT).show());
        reportCrimeButton.setOnClickListener(v -> showReportCrimeDialog());

        backButton.setOnClickListener(v -> clearRoute());

        zoomInButton.setOnClickListener(v -> map.getController().zoomIn());
        zoomOutButton.setOnClickListener(v -> map.getController().zoomOut());

        dangerZonesButton.setOnClickListener(v -> toggleDangerZones());
        
        streetCrimeFilterButton.setOnClickListener(v -> toggleStreetCrimeFilter());
        vehicleCrimeFilterButton.setOnClickListener(v -> toggleVehicleCrimeFilter());

        setupSafePoints();
        addSafePointsToMap();
        setupCrimeAlerts();
        addCrimeAlertsToMap();
        
        // Inicializar filtros y botones
        showAllCrimes = true;
        showStreetCrime = true;
        showVehicleCrime = true;
        updateCrimeFilterButtons();
        
        // Crear zonas de peligro automáticamente para que el algoritmo funcione correctamente
        showDangerZones = true;
        createDangerZones();

        // Configurar manejo moderno del botón atrás
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Si hay una ruta activa (panel de información visible), limpiar la ruta
                if (routeInfoLayout.getVisibility() == View.VISIBLE) {
                    clearRoute();
                } else {
                    // Si no hay ruta activa, comportamiento normal (cerrar app)
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void setupSafePoints() {
        // Hospitales Públicos de CABA
        safePoints.add(new SafePoint("Hospital Durand", new GeoPoint(-34.6095, -58.4411), "hospital"));
        safePoints.add(new SafePoint("Hospital Fernández", new GeoPoint(-34.5828, -58.4203), "hospital"));
        safePoints.add(new SafePoint("Hospital Rivadavia", new GeoPoint(-34.5900, -58.4042), "hospital"));
        safePoints.add(new SafePoint("Hospital de Clínicas", new GeoPoint(-34.6011, -58.4052), "hospital"));
        safePoints.add(new SafePoint("Hospital Santojanni", new GeoPoint(-34.6508, -58.5028), "hospital"));
        safePoints.add(new SafePoint("Hospital Piñero", new GeoPoint(-34.6397, -58.4503), "hospital"));
        safePoints.add(new SafePoint("Hospital Pirovano", new GeoPoint(-34.5578, -58.4844), "hospital"));
        safePoints.add(new SafePoint("Hospital Zubizarreta", new GeoPoint(-34.6063, -58.5144), "hospital"));
        safePoints.add(new SafePoint("Hospital Penna", new GeoPoint(-34.6461, -58.4069), "hospital"));
        safePoints.add(new SafePoint("Hospital Elizalde", new GeoPoint(-34.6225, -58.3803), "hospital"));
        safePoints.add(new SafePoint("Hospital Garrahan", new GeoPoint(-34.6289, -58.3908), "hospital"));

        // Comisarías Vecinales de CABA (una por comuna como ejemplo)
        safePoints.add(new SafePoint("Comisaría Vecinal 1A", new GeoPoint(-34.6111, -58.3741), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 2B", new GeoPoint(-34.5941, -58.4098), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 3A", new GeoPoint(-34.6073, -58.4124), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 4D", new GeoPoint(-34.6391, -58.3712), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 5A", new GeoPoint(-34.6118, -58.4215), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 6B", new GeoPoint(-34.6186, -58.4533), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 7A", new GeoPoint(-34.6341, -58.4608), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 8A", new GeoPoint(-34.6738, -58.4674), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 9A", new GeoPoint(-34.6645, -58.5147), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 10A", new GeoPoint(-34.6288, -58.5042), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 11A", new GeoPoint(-34.6173, -58.4841), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 12A", new GeoPoint(-34.5614, -58.4981), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 13A", new GeoPoint(-34.5623, -58.4557), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 14A", new GeoPoint(-34.5775, -58.4312), "police"));
        safePoints.add(new SafePoint("Comisaría Vecinal 15A", new GeoPoint(-34.5901, -58.4439), "police"));
    }

    private void addSafePointsToMap() {
        Drawable policeIcon = ContextCompat.getDrawable(this, R.drawable.ic_police_station);
        Drawable hospitalIcon = ContextCompat.getDrawable(this, R.drawable.ic_hospital);

        for (SafePoint point : safePoints) {
            Marker marker = new Marker(map);
            marker.setPosition(point.location);
            marker.setTitle(point.name);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

            if ("police".equals(point.type)) {
                marker.setIcon(policeIcon);
            } else {
                marker.setIcon(hospitalIcon);
            }
            map.getOverlays().add(marker);
            safePointMarkers.add(marker);
        }
        map.invalidate();
    }

    private void clearRoute() {
        routeInfoLayout.setVisibility(View.GONE);
        searchLayout.setVisibility(View.VISIBLE);
        originEditText.setText("");
        destinationEditText.setText("");
        map.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();
        
        // Limpiar las rutas almacenadas
        safeRoutes.clear();
        fastRoutes.clear();
        safeRouteSelected = true;
        
        if (originMarker != null) {
            map.getOverlays().remove(originMarker);
            originMarker = null;
        }
        if (destinationMarker != null) {
            map.getOverlays().remove(destinationMarker);
            destinationMarker = null;
        }
        
        // Limpiar marcadores de alertas para recrearlos
        map.getOverlays().removeAll(crimeAlertMarkers);
        crimeAlertMarkers.clear();
        
        // Volver a agregar los marcadores de puntos seguros y alertas de crimen
        addSafePointsToMap();
        addCrimeAlertsToMap(); // Esto volverá a geocodificar y agregar las alertas
        
        map.invalidate();
    }

    private void calculateBothRoutes(String originAddress, String destinationAddress) {
        new Thread(() -> {
            try {
                GeoPoint originPoint = getGeoPointFromAddress(originAddress);
                GeoPoint destinationPoint = getGeoPointFromAddress(destinationAddress);
                if (originPoint == null || destinationPoint == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se pudo encontrar una de las direcciones", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Calcular ruta rápida (directa)
                fastRoutes = getRoutes(originPoint, destinationPoint);
                if (fastRoutes == null || fastRoutes.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se pudo calcular ninguna ruta", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Calcular ruta segura usando waypoints que eviten zonas peligrosas
                safeRoutes = getSafeRoute(originPoint, destinationPoint);
                if (safeRoutes.isEmpty()) {
                    // Fallback: filtrar las rutas normales por seguridad
                    List<RouteInfo> filteredSafeRoutes = filterSafeRoutes(fastRoutes);
                    safeRoutes = filteredSafeRoutes.isEmpty() ? fastRoutes : filteredSafeRoutes;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Usando ruta con menor riesgo disponible.", Toast.LENGTH_LONG).show());
                }

                // Debug: Verificar que las rutas son diferentes
                if (!fastRoutes.isEmpty() && !safeRoutes.isEmpty()) {
                    RouteInfo fastRoute = fastRoutes.get(0);
                    RouteInfo safeRoute = safeRoutes.get(0);
                    
                    System.out.println("=== DEBUG RUTAS ===");
                    System.out.println("Ruta rápida - Tiempo: " + fastRoute.timeInMillis/1000 + "s, Distancia: " + fastRoute.distanceInMeters + "m");
                    System.out.println("Ruta segura - Tiempo: " + safeRoute.timeInMillis/1000 + "s, Distancia: " + safeRoute.distanceInMeters + "m");
                    System.out.println("¿Son la misma ruta? " + (fastRoute == safeRoute));
                    System.out.println("Riesgo ruta rápida: " + calculateRouteSafetyScore(fastRoute));
                    System.out.println("Riesgo ruta segura: " + calculateRouteSafetyScore(safeRoute));
                }

                runOnUiThread(() -> {
                    drawBothRoutes(originPoint, destinationPoint);
                    showRouteOptions();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error al calcular las rutas", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void selectRouteType(boolean selectSafe) {
        safeRouteSelected = selectSafe;
        
        // Actualizar UI
        if (selectSafe) {
            safeRouteOption.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E3F2FD")));
            fastRouteOption.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            fastRouteRadio.setImageResource(android.R.drawable.radiobutton_off_background);
        } else {
            safeRouteOption.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            fastRouteOption.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFEBEE")));
            fastRouteRadio.setImageResource(android.R.drawable.radiobutton_on_background);
        }

        // Redibujar rutas con la selección actualizada
        drawSelectedRoute();
    }

    private void drawBothRoutes(GeoPoint originPoint, GeoPoint destinationPoint) {
        // Limpiar rutas anteriores
        map.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();

        // Agregar marcadores de origen y destino
        addRouteMarkers(originPoint, destinationPoint);

        // Dibujar ruta rápida con color según el modo
        if (!fastRoutes.isEmpty()) {
            RouteInfo fastRoute = fastRoutes.get(0);
            int fastColor = vehicleMode ? Color.parseColor("#FF6600") : Color.RED; // Naranja para vehículo, rojo para peatón
            drawSingleRoute(fastRoute.points, fastColor, 6.0f);
        }

        // Dibujar ruta segura con color según el modo
        if (!safeRoutes.isEmpty()) {
            RouteInfo safeRoute = safeRoutes.get(0);
            int safeColor = vehicleMode ? Color.parseColor("#00AA00") : Color.BLUE; // Verde para vehículo, azul para peatón
            drawSingleRoute(safeRoute.points, safeColor, 8.0f);
        }

        map.invalidate();
    }

    private void drawSelectedRoute() {
        // Limpiar rutas anteriores
        map.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();

        List<RouteInfo> selectedRoutes = safeRouteSelected ? safeRoutes : fastRoutes;
        int routeColor;
        
        if (safeRouteSelected) {
            routeColor = vehicleMode ? Color.parseColor("#00AA00") : Color.BLUE; // Verde para vehículo seguro, azul para peatón seguro
        } else {
            routeColor = vehicleMode ? Color.parseColor("#FF6600") : Color.RED; // Naranja para vehículo rápido, rojo para peatón rápido
        }

        if (!selectedRoutes.isEmpty()) {
            RouteInfo selectedRoute = selectedRoutes.get(0);
            drawSingleRoute(selectedRoute.points, routeColor, 8.0f);
        }

        map.invalidate();
    }

    private void drawSingleRoute(List<GeoPoint> routePoints, int color, float width) {
        Polyline routeLine = new Polyline();
        routeLine.setPoints(routePoints);
        routeLine.setColor(color);
        routeLine.setWidth(width);
        routeOverlays.add(routeLine);
        map.getOverlays().add(routeLine);
    }

    private void showRouteOptions() {
        // Actualizar información de las rutas
        if (!safeRoutes.isEmpty()) {
            RouteInfo safeRoute = safeRoutes.get(0);
            String safeLabel = vehicleMode ? "Ruta Segura (Vehículo)" : "Ruta Segura (Peatón)";
            String safeInfo = String.format(Locale.getDefault(), "%s - %s (%s)",
                    safeLabel,
                    formatDuration(safeRoute.timeInMillis),
                    formatDistance(safeRoute.distanceInMeters));
            safeRouteInfo.setText(safeInfo);
        }

        if (!fastRoutes.isEmpty()) {
            RouteInfo fastRoute = fastRoutes.get(0);
            String fastLabel = vehicleMode ? "Ruta Directa (Vehículo)" : "Ruta Directa (Peatón)";
            String fastInfo = String.format(Locale.getDefault(), "%s - %s (%s)",
                    fastLabel,
                    formatDuration(fastRoute.timeInMillis),
                    formatDistance(fastRoute.distanceInMeters));
            fastRouteInfo.setText(fastInfo);
        }

        // Mostrar panel de opciones
        searchLayout.setVisibility(View.GONE);
        routeInfoLayout.setVisibility(View.VISIBLE);

        // Seleccionar ruta segura por defecto
        selectRouteType(true);
        
        // Mostrar mensaje específico para modo vehículo
        if (vehicleMode) {
            Toast.makeText(this, "Rutas optimizadas para vehículo - Evitando robos de vehículos", Toast.LENGTH_LONG).show();
        }
    }

    private void addRouteMarkers(GeoPoint originPoint, GeoPoint destinationPoint) {
        // Limpiar marcadores anteriores
        if (originMarker != null) {
            map.getOverlays().remove(originMarker);
            originMarker = null;
        }
        if (destinationMarker != null) {
            map.getOverlays().remove(destinationMarker);
            destinationMarker = null;
        }

        // Crear marcador de origen
        originMarker = new Marker(map);
        originMarker.setPosition(originPoint);
        originMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        originMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_origin_marker));
        originMarker.setTitle("Inicio");
        map.getOverlays().add(originMarker);

        // Crear marcador de destino
        destinationMarker = new Marker(map);
        destinationMarker.setPosition(destinationPoint);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_destination_marker));
        destinationMarker.setTitle("Destino");
        map.getOverlays().add(destinationMarker);
    }

    private void findAndDrawRoute(String originAddress, String destinationAddress, boolean isSafeRoute) {
        String routeType = isSafeRoute ? "ruta segura" : "rutas";
        Toast.makeText(this, "Calculando " + routeType + "...", Toast.LENGTH_SHORT).show();
        routeInfoLayout.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                GeoPoint originPoint = getGeoPointFromAddress(originAddress);
                GeoPoint destinationPoint = getGeoPointFromAddress(destinationAddress);
                if (originPoint == null || destinationPoint == null) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se pudo encontrar una de las direcciones", Toast.LENGTH_SHORT).show());
                    return;
                }
                List<RouteInfo> routes = getRoutes(originPoint, destinationPoint);
                if (routes == null || routes.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se pudo calcular ninguna ruta", Toast.LENGTH_SHORT).show());
                    return;
                }
                
                final List<RouteInfo> finalRoutes;
                if (isSafeRoute) {
                    // Filtrar y ordenar rutas por seguridad
                    List<RouteInfo> safeRoutes = filterSafeRoutes(routes);
                    if (safeRoutes.isEmpty()) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "No se encontraron rutas seguras. Mostrando ruta más segura disponible.", Toast.LENGTH_LONG).show());
                        // Si no hay rutas seguras, usar la ruta original pero marcarla como la más segura disponible
                        finalRoutes = routes;
                    } else {
                        finalRoutes = safeRoutes;
                    }
                } else {
                    finalRoutes = routes;
                }
                
                runOnUiThread(() -> drawRoutes(finalRoutes, originPoint, destinationPoint, isSafeRoute));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error al calcular la ruta", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private GeoPoint getGeoPointFromAddress(String address) throws IOException, JSONException {
        String encodedAddress = URLEncoder.encode(address, "UTF-8");
        URL url = new URL("https://nominatim.openstreetmap.org/search?q=" + encodedAddress + "&format=json&limit=1&countrycodes=ar");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", getPackageName());
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        JSONArray jsonArray = new JSONArray(result.toString());
        if (jsonArray.length() > 0) {
            JSONObject obj = jsonArray.getJSONObject(0);
            return new GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"));
        }
        return null;
    }

    private List<RouteInfo> getRoutes(GeoPoint start, GeoPoint end) throws IOException, JSONException {
        String vehicle = vehicleMode ? "car" : "foot";
        String urlString = "https://graphhopper.com/api/1/route?point=" + start.getLatitude() + "," + start.getLongitude() +
                "&point=" + end.getLatitude() + "," + end.getLongitude() +
                "&vehicle=" + vehicle + "&key=" + GRAPHHOPPER_API_KEY + 
                "&alternative_route.max_paths=3&alternative_route.max_weight_factor=1.4&alternative_route.max_share_factor=0.6&points_encoded=true";
        URL url = new URL(urlString);
        System.out.println("GraphHopper URL (" + (vehicleMode ? "vehicle" : "walking") + "): " + urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        JSONObject jsonObject = new JSONObject(result.toString());
        JSONArray paths = jsonObject.getJSONArray("paths");
        List<RouteInfo> routes = new ArrayList<>();
        
        System.out.println("=== GraphHopper Response ===");
        System.out.println("Número de rutas devueltas: " + paths.length());
        
        for (int i = 0; i < paths.length(); i++) {
            JSONObject path = paths.getJSONObject(i);
            String encodedPolyline = path.getString("points");
            List<GeoPoint> points = decodePolyline(encodedPolyline);
            long time = path.getLong("time");
            double distance = path.getDouble("distance");
            routes.add(new RouteInfo(points, time, distance));
            
            System.out.println("Ruta " + (i+1) + " - Tiempo: " + time/1000 + "s, Distancia: " + distance + "m, Puntos: " + points.size());
        }
        
        return routes;
    }

    private List<RouteInfo> getSafeRoute(GeoPoint start, GeoPoint end) throws IOException, JSONException {
        // Generar waypoints intermedios que eviten zonas peligrosas
        List<GeoPoint> safeWaypoints = generateSafeWaypoints(start, end);
        
        if (safeWaypoints.isEmpty()) {
            // Si no se pueden generar waypoints seguros, usar la ruta directa
            return getRoutes(start, end);
        }
        
        // Construir URL con waypoints intermedios
        StringBuilder urlBuilder = new StringBuilder();
        urlBuilder.append("https://graphhopper.com/api/1/route?");
        
        // Punto de origen
        urlBuilder.append("point=").append(start.getLatitude()).append(",").append(start.getLongitude());
        
        // Waypoints intermedios
        for (GeoPoint waypoint : safeWaypoints) {
            urlBuilder.append("&point=").append(waypoint.getLatitude()).append(",").append(waypoint.getLongitude());
        }
        
        // Punto de destino
        urlBuilder.append("&point=").append(end.getLatitude()).append(",").append(end.getLongitude());
        
        // Parámetros adicionales - usar vehículo según el modo
        String vehicle = vehicleMode ? "car" : "foot";
        urlBuilder.append("&vehicle=").append(vehicle).append("&key=").append(GRAPHHOPPER_API_KEY)
                  .append("&points_encoded=true");
        
        String urlString = urlBuilder.toString();
        System.out.println("Safe Route URL (" + (vehicleMode ? "vehicle" : "walking") + "): " + urlString);
        
        // Verificar longitud de URL para evitar errores
        if (urlString.length() > 2000) {
            System.out.println("URL demasiado larga (" + urlString.length() + " chars), usando ruta directa");
            return getRoutes(start, end);
        }
        
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            // Verificar código de respuesta
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.out.println("Error en respuesta GraphHopper: " + responseCode + ", usando ruta directa");
                return getRoutes(start, end);
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            reader.close();
            
            JSONObject jsonObject = new JSONObject(result.toString());
            JSONArray paths = jsonObject.getJSONArray("paths");
            List<RouteInfo> routes = new ArrayList<>();
            
            System.out.println("=== Safe Route Response ===");
            System.out.println("Número de rutas seguras devueltas: " + paths.length());
            
            for (int i = 0; i < paths.length(); i++) {
                JSONObject path = paths.getJSONObject(i);
                String encodedPolyline = path.getString("points");
                List<GeoPoint> points = decodePolyline(encodedPolyline);
                long time = path.getLong("time");
                double distance = path.getDouble("distance");
                routes.add(new RouteInfo(points, time, distance));
                
                System.out.println("Ruta segura " + (i+1) + " - Tiempo: " + time/1000 + "s, Distancia: " + distance + "m, Puntos: " + points.size());
            }
            
            return routes;
            
        } catch (Exception e) {
            System.out.println("Error al calcular ruta segura: " + e.getMessage());
            e.printStackTrace();
            // Fallback: usar ruta directa
            return getRoutes(start, end);
        }
    }

    private List<GeoPoint> generateSafeWaypoints(GeoPoint start, GeoPoint end) {
        List<GeoPoint> waypoints = new ArrayList<>();
        
        // Calcular la línea directa entre origen y destino
        double startLat = start.getLatitude();
        double startLon = start.getLongitude();
        double endLat = end.getLatitude();
        double endLon = end.getLongitude();
        
        // Crear más puntos de verificación para mejor detección de peligros
        int numSegments = vehicleMode ? 3 : 5; // Más segmentos para peatones para mejor detección
        
        for (int i = 1; i < numSegments; i++) {
            double ratio = (double) i / numSegments;
            double intermediateLat = startLat + (endLat - startLat) * ratio;
            double intermediateLon = startLon + (endLon - startLon) * ratio;
            
            GeoPoint intermediatePoint = new GeoPoint(intermediateLat, intermediateLon);
            
            // Verificar si este punto está cerca de una zona peligrosa
            if (isPointNearDanger(intermediatePoint)) {
                // Mover el punto a una ubicación más segura
                GeoPoint safePoint = findSaferNearbyPoint(intermediatePoint);
                if (safePoint != null) {
                    waypoints.add(safePoint);
                    System.out.println("Waypoint seguro agregado para " + (vehicleMode ? "vehículo" : "peatón") + ": " + safePoint.getLatitude() + "," + safePoint.getLongitude());
                }
            }
        }
        
        // Para peatones, agregar waypoints adicionales si la ruta pasa cerca de crímenes en vía pública
        if (!vehicleMode) {
            addPedestrianSpecificWaypoints(start, end, waypoints);
        }
        
        // Limitar waypoints adicionales para vehículos
        if (vehicleMode && waypoints.size() < 2) {
            addVehicleSpecificWaypoints(start, end, waypoints);
        }
        
        // Limitar a máximo 3 waypoints para evitar URLs demasiado largas
        if (waypoints.size() > 3) {
            waypoints = waypoints.subList(0, 3);
        }
        
        System.out.println("Total waypoints generados para " + (vehicleMode ? "vehículo" : "peatón") + ": " + waypoints.size());
        
        return waypoints;
    }

    private boolean isPointNearDanger(GeoPoint point) {
        // Verificar proximidad a alertas de crimen
        for (CrimeAlert crime : crimeAlerts) {
            if (crime.location != null) {
                // En modo vehículo, dar más peso a robos de vehículos
                double riskRadius = 200; // Radio base
                double riskMultiplier = 1.0;
                
                if (vehicleMode && "Robo de vehículos".equals(crime.crimeType)) {
                    riskMultiplier = 2.0; // Doblar el radio de riesgo para vehículos
                } else if (!vehicleMode && "Crimen en vía pública".equals(crime.crimeType)) {
                    riskMultiplier = 2.0; // Doblar el radio para peatones con crímenes callejeros
                } else if (!vehicleMode) {
                    riskMultiplier = 1.2; // Incrementar ligeramente para todos los crímenes en modo peatón
                }
                
                double adjustedRadius = riskRadius * riskMultiplier;
                double distance = calculateDistance(point, crime.location);
                
                if (distance <= adjustedRadius) {
                    System.out.println("Peligro detectado para " + (vehicleMode ? "vehículo" : "peatón") + 
                        " - Tipo: " + crime.crimeType + ", Distancia: " + distance + "m, Radio: " + adjustedRadius + "m");
                    return true;
                }
            }
        }
        
        // Verificar si está dentro de zonas de peligro (polígonos)
        for (Polygon dangerZone : dangerZones) {
            if (isPointInPolygon(point, dangerZone)) {
                return true;
            }
        }
        
        return false;
    }

    private GeoPoint findSaferNearbyPoint(GeoPoint dangerousPoint) {
        // Buscar en un radio más amplio para peatones
        double[] offsets = vehicleMode ? 
            new double[]{0.002, -0.002} : // 200m para vehículos
            new double[]{0.003, -0.003, 0.004, -0.004}; // 300-400m para peatones
        
        for (double latOffset : offsets) {
            for (double lonOffset : offsets) {
                GeoPoint candidate = new GeoPoint(
                    dangerousPoint.getLatitude() + latOffset,
                    dangerousPoint.getLongitude() + lonOffset
                );
                
                if (!isPointNearDanger(candidate)) {
                    System.out.println("Punto seguro encontrado para " + (vehicleMode ? "vehículo" : "peatón") + 
                        ": " + candidate.getLatitude() + "," + candidate.getLongitude());
                    return candidate;
                }
            }
        }
        
        // Si no se encuentra un punto seguro, intentar con offsets más pequeños
        double[] smallOffsets = {0.001, -0.001};
        for (double latOffset : smallOffsets) {
            for (double lonOffset : smallOffsets) {
                GeoPoint candidate = new GeoPoint(
                    dangerousPoint.getLatitude() + latOffset,
                    dangerousPoint.getLongitude() + lonOffset
                );
                
                if (!isPointNearDanger(candidate)) {
                    System.out.println("Punto seguro alternativo encontrado: " + candidate.getLatitude() + "," + candidate.getLongitude());
                    return candidate;
                }
            }
        }
        
        // Si no se encuentra un punto seguro, devolver null
        return null;
    }

    private List<RouteInfo> filterSafeRoutes(List<RouteInfo> routes) {
        // Evaluar cada ruta basado en su proximidad a zonas de crimen
        List<RouteWithSafety> routesWithSafety = new ArrayList<>();
        
        for (RouteInfo route : routes) {
            double safetyScore = calculateRouteSafetyScore(route);
            routesWithSafety.add(new RouteWithSafety(route, safetyScore));
            // Debug
            System.out.println("filterSafeRoutes - Ruta con riesgo: " + safetyScore + ", Distancia: " + route.distanceInMeters + "m");
        }
        
        // Ordenar por puntuación de seguridad (menor puntuación = más seguro)
        routesWithSafety.sort((r1, r2) -> Double.compare(r1.safetyScore, r2.safetyScore));
        
        // Calcular umbral dinámico basado en las rutas disponibles
        List<RouteInfo> safeRoutes = new ArrayList<>();
        
        if (!routesWithSafety.isEmpty()) {
            double minRisk = routesWithSafety.get(0).safetyScore;
            double maxRisk = routesWithSafety.get(routesWithSafety.size() - 1).safetyScore;
            
            // Si hay diferencia significativa entre la más segura y la más peligrosa
            if (maxRisk - minRisk > 20.0) {
                // Usar umbral dinámico: riesgo mínimo + 30% de la diferencia
                double dynamicThreshold = minRisk + (maxRisk - minRisk) * 0.3;
                
                for (RouteWithSafety routeWithSafety : routesWithSafety) {
                    if (routeWithSafety.safetyScore <= dynamicThreshold) {
                        safeRoutes.add(routeWithSafety.route);
                    }
                }
                
                System.out.println("Umbral dinámico: " + dynamicThreshold + " (min: " + minRisk + ", max: " + maxRisk + ")");
            }
            
            // Si no hay rutas que cumplan el umbral dinámico, o la diferencia es pequeña,
            // devolver solo la más segura
            if (safeRoutes.isEmpty()) {
                safeRoutes.add(routesWithSafety.get(0).route);
                System.out.println("Usando solo la ruta más segura disponible con riesgo: " + routesWithSafety.get(0).safetyScore);
            }
        }
        
        return safeRoutes;
    }

    private List<RouteInfo> findSafestRoute(List<RouteInfo> allRoutes) {
        if (allRoutes.isEmpty()) {
            return new ArrayList<>();
        }

        RouteInfo safestRoute = null;
        double lowestRiskScore = Double.MAX_VALUE;

        // Evaluar cada ruta y encontrar la que tenga el menor riesgo
        for (RouteInfo route : allRoutes) {
            double riskScore = calculateRouteSafetyScore(route);
            
            // Log para debug
            System.out.println("Ruta evaluada - Riesgo: " + riskScore + ", Distancia: " + route.distanceInMeters + "m, Tiempo: " + route.timeInMillis/1000 + "s");
            
            if (riskScore < lowestRiskScore) {
                lowestRiskScore = riskScore;
                safestRoute = route;
            }
        }

        List<RouteInfo> result = new ArrayList<>();
        if (safestRoute != null) {
            result.add(safestRoute);
            System.out.println("Ruta más segura seleccionada con riesgo: " + lowestRiskScore);
        }
        
        return result;
    }

    private double calculateRouteSafetyScore(RouteInfo route) {
        double totalRisk = 0.0;
        int sampledPoints = 0;
        
        // Evaluar cada 10 puntos de la ruta para eficiencia
        for (int i = 0; i < route.points.size(); i += 10) {
            GeoPoint point = route.points.get(i);
            double pointRisk = calculatePointRisk(point);
            totalRisk += pointRisk;
            sampledPoints++;
        }
        
        // También evaluar el punto final
        if (!route.points.isEmpty()) {
            GeoPoint lastPoint = route.points.get(route.points.size() - 1);
            totalRisk += calculatePointRisk(lastPoint);
            sampledPoints++;
        }
        
        return sampledPoints > 0 ? totalRisk / sampledPoints : 0.0;
    }

    private double calculatePointRisk(GeoPoint point) {
        double totalRisk = 0.0;
        
        // Riesgo por alertas de crimen individuales
        for (CrimeAlert crime : crimeAlerts) {
            if (crime.location != null) {
                double distance = calculateDistance(point, crime.location);
                
                // Riesgo decae exponencialmente con la distancia
                if (distance <= 500) { // 500 metros de radio de influencia (aumentado)
                    double riskFactor;
                    if (distance <= 100) {
                        // Muy alto riesgo si está muy cerca
                        riskFactor = 50.0;
                    } else if (distance <= 200) {
                        // Alto riesgo
                        riskFactor = 30.0 * Math.exp(-distance / 50.0);
                    } else {
                        // Riesgo moderado que decae exponencialmente
                        riskFactor = 15.0 * Math.exp(-distance / 100.0);
                    }
                    totalRisk += riskFactor;
                }
            }
        }
        
        // Riesgo adicional por zonas de peligro (polígonos)
        for (Polygon dangerZone : dangerZones) {
            if (isPointInPolygon(point, dangerZone)) {
                // Si el punto está dentro de una zona de peligro, agregar riesgo alto
                totalRisk += 100.0; // Riesgo muy alto por estar en zona de peligro
            } else {
                // Calcular riesgo por proximidad a la zona de peligro
                double distanceToZone = getDistanceToPolygon(point, dangerZone);
                if (distanceToZone <= 200) { // 200 metros de proximidad a zona de peligro
                    double proximityRisk = 25.0 * Math.exp(-distanceToZone / 50.0);
                    totalRisk += proximityRisk;
                }
            }
        }
        
        return totalRisk;
    }

    private boolean isPointInPolygon(GeoPoint point, Polygon polygon) {
        // Usar el método contains del polígono de OSMDroid si está disponible
        // Como alternativa, implementar ray casting algorithm
        List<GeoPoint> points = polygon.getPoints();
        if (points.size() < 3) return false;
        
        boolean inside = false;
        int j = points.size() - 1;
        
        for (int i = 0; i < points.size(); i++) {
            double xi = points.get(i).getLatitude();
            double yi = points.get(i).getLongitude();
            double xj = points.get(j).getLatitude();
            double yj = points.get(j).getLongitude();
            
            if (((yi > point.getLongitude()) != (yj > point.getLongitude())) &&
                (point.getLatitude() < (xj - xi) * (point.getLongitude() - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
            j = i;
        }
        
        return inside;
    }

    private double getDistanceToPolygon(GeoPoint point, Polygon polygon) {
        double minDistance = Double.MAX_VALUE;
        List<GeoPoint> points = polygon.getPoints();
        
        // Calcular distancia al punto más cercano del polígono
        for (GeoPoint polygonPoint : points) {
            double distance = calculateDistance(point, polygonPoint);
            if (distance < minDistance) {
                minDistance = distance;
            }
        }
        
        return minDistance;
    }

    // Clase auxiliar para asociar rutas con su puntuación de seguridad
    private static class RouteWithSafety {
        final RouteInfo route;
        final double safetyScore;

        RouteWithSafety(RouteInfo route, double safetyScore) {
            this.route = route;
            this.safetyScore = safetyScore;
        }
    }

    private void drawRoutes(List<RouteInfo> routes, GeoPoint start, GeoPoint end, boolean isSafeRoute) {
        clearRoute();
        List<GeoPoint> allPoints = new ArrayList<>();

        // ★★★ CAMBIO IMPORTANTE: PREPARAMOS LAS LÍNEAS PERO NO LAS DIBUJAMOS AÚN ★★★
        for (int i = 0; i < routes.size(); i++) {
            RouteInfo routeInfo = routes.get(i);
            allPoints.addAll(routeInfo.points); // Juntamos todos los puntos para el zoom
        }

        // Llamamos a selectRoute para que se encargue del dibujado inicial
        if (!routes.isEmpty()) {
            selectRoute(0, routes, isSafeRoute);
        }

        originMarker = new Marker(map);
        originMarker.setPosition(start);
        originMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        originMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_origin_marker));
        originMarker.setTitle("Inicio");
        map.getOverlays().add(originMarker);

        destinationMarker = new Marker(map);
        destinationMarker.setPosition(end);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        destinationMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_destination_marker));
        destinationMarker.setTitle("Destino");
        map.getOverlays().add(destinationMarker);

        if (!allPoints.isEmpty()) {
            BoundingBox boundingBox = BoundingBox.fromGeoPoints(allPoints);
            map.zoomToBoundingBox(boundingBox, true, 150);
        }
        map.invalidate();
    }

    // ★★★ MÉTODO COMPLETAMENTE NUEVO PARA DIBUJAR CON BORDES ★★★
    private void selectRoute(int index, List<RouteInfo> routes, boolean isSafeRoute) {
        // Primero, borramos solo las rutas viejas para poder redibujarlas
        map.getOverlays().removeAll(routeOverlays);
        routeOverlays.clear();

        // Definir colores según el tipo de ruta
        String selectedMainColor, selectedBorderColor, alternativeColor;
        
        if (isSafeRoute) {
            selectedMainColor = "#4CAF50"; // Verde para rutas seguras
            selectedBorderColor = "#2E7D32"; // Verde oscuro
            alternativeColor = "#81C784"; // Verde claro para alternativas
        } else {
            selectedMainColor = "#4285F4"; // Azul para rutas normales
            selectedBorderColor = "#0D47A1"; // Azul oscuro
            alternativeColor = "#8A8A8A"; // Gris para alternativas
        }

        // Dibujamos las rutas alternativas (no seleccionadas) primero
        for (int i = 0; i < routes.size(); i++) {
            if (i != index) {
                Polyline routeOverlay = new Polyline();
                routeOverlay.setPoints(routes.get(i).points);
                routeOverlay.setColor(Color.parseColor(alternativeColor));
                routeOverlay.setWidth(14f);
                final int routeIndex = i;
                routeOverlay.setOnClickListener((polyline, mapView, eventPos) -> {
                    selectRoute(routeIndex, routes, isSafeRoute);
                    return true;
                });
                map.getOverlays().add(routeOverlay);
                routeOverlays.add(routeOverlay);
            }
        }

        // Ahora, dibujamos la ruta seleccionada AL FINAL para que quede por encima
        if (index < routes.size()) {
            // BORDE: Línea ancha y oscura por debajo
            Polyline routeBorder = new Polyline();
            routeBorder.setPoints(routes.get(index).points);
            routeBorder.setColor(Color.parseColor(selectedBorderColor));
            routeBorder.setWidth(22f);
            final int routeIndex = index;
            routeBorder.setOnClickListener((polyline, mapView, eventPos) -> {
                selectRoute(routeIndex, routes, isSafeRoute); // El borde también es clickeable
                return true;
            });
            map.getOverlays().add(routeBorder);
            routeOverlays.add(routeBorder);

            // RELLENO: Línea brillante por encima del borde
            Polyline routeFill = new Polyline();
            routeFill.setPoints(routes.get(index).points);
            routeFill.setColor(Color.parseColor(selectedMainColor));
            routeFill.setWidth(16f);
            routeFill.setOnClickListener((polyline, mapView, eventPos) -> {
                selectRoute(routeIndex, routes, isSafeRoute);
                return true;
            });
            map.getOverlays().add(routeFill);
            routeOverlays.add(routeFill);
        }

        // Actualizamos el panel de información
        RouteInfo selectedRoute = routes.get(index);
        String routeTypeIndicator = isSafeRoute ? "🛡️ " : "";
        String info = String.format(Locale.getDefault(), "%s%s (%s)",
                routeTypeIndicator,
                formatDuration(selectedRoute.timeInMillis),
                formatDistance(selectedRoute.distanceInMeters)
        );
        // routeInfoText.setText(info); // COMENTADO: Ya no se usa el texto simple
        searchLayout.setVisibility(View.GONE);
        routeInfoLayout.setVisibility(View.VISIBLE);
        map.invalidate();
    }

    // Sobrecarga del método para mantener compatibilidad con rutas normales
    private void selectRoute(int index, List<RouteInfo> routes) {
        selectRoute(index, routes, false);
    }


    private String formatDuration(long millis) {
        long minutes = (millis / 1000) / 60;
        return minutes + " min";
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.getDefault(), "%.0f m", meters);
        } else {
            return String.format(Locale.getDefault(), "%.1f km", meters / 1000.0);
        }
    }

    private List<GeoPoint> decodePolyline(String encoded) {
        List<GeoPoint> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;
            poly.add(new GeoPoint((double) lat / 1E5, (double) lng / 1E5));
        }
        return poly;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        View view = getCurrentFocus();
        if (view == null) view = new View(this);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void setupCrimeAlerts() {
        // ========== CRIMEN EN VÍA PÚBLICA ==========
        // Alertas de robos en la Av. Corrientes del 100 al 1000
        // Usando direcciones reales que serán geocodificadas por la API

        // Alerta 1: Av. Corrientes 300
        crimeAlerts.add(new CrimeAlert(
                "Robo a mano armada",
                "Se reportó un robo a mano armada en esta zona. El incidente ocurrió en horario nocturno cuando la víctima caminaba sola.",
                "Av. Corrientes 300, Buenos Aires, Argentina",
                "Hace 2 días",
                "Crimen en vía pública"
        ));

        // Alerta 2: Av. Corrientes 600
        crimeAlerts.add(new CrimeAlert(
                "Robo de pertenencias",
                "Robo de celular y billetera reportado por transeúntes. Los delincuentes escaparon en motocicleta.",
                "Av. Corrientes 600, Buenos Aires, Argentina",
                "Hace 1 semana",
                "Crimen en vía pública"
        ));

        // Alerta 3: Av. Corrientes 900
        crimeAlerts.add(new CrimeAlert(
                "Intento de robo",
                "Intento de robo frustrado gracias a la intervención de transeúntes. Se recomienda precaución en la zona.",
                "Av. Corrientes 900, Buenos Aires, Argentina",
                "Hace 4 días",
                "Crimen en vía pública"
        ));

        // Alerta 4: Florida y Lavalle (zona peatonal)
        crimeAlerts.add(new CrimeAlert(
                "Arrebato de cartera",
                "Arrebato de cartera en la zona peatonal durante el horario comercial. La víctima reportó que fueron dos personas en bicicleta.",
                "Florida 300, Buenos Aires, Argentina",
                "Hace 3 días",
                "Crimen en vía pública"
        ));

        // Alerta 5: Sarmiento cerca de Florida
        crimeAlerts.add(new CrimeAlert(
                "Robo en taxi",
                "Robo en el interior de un taxi. El conductor era cómplice del hecho. Se recomienda usar aplicaciones de transporte verificadas.",
                "Sarmiento 500, Buenos Aires, Argentina",
                "Hace 1 día",
                "Crimen en vía pública"
        ));

        // Alerta 6: Lavalle peatonal
        crimeAlerts.add(new CrimeAlert(
                "Hurto por distracción",
                "Hurto de billetera mediante distracción en zona comercial. Los delincuentes operaban en grupo fingiendo ser compradores.",
                "Lavalle 600, Buenos Aires, Argentina",
                "Hace 5 días",
                "Crimen en vía pública"
        ));

        // Alerta 7: Av. Santa Fe cerca de Florida
        crimeAlerts.add(new CrimeAlert(
                "Robo nocturno",
                "Robo con intimidación en parada de colectivo durante la madrugada. Se llevaron teléfono y documentos.",
                "Av. Santa Fe 800, Buenos Aires, Argentina",
                "Hace 1 semana",
                "Crimen en vía pública"
        ));

        // ========== ROBO DE VEHÍCULOS ==========
        // Nuevos casos de robo de vehículos en la misma zona

        // Robo de vehículo 1: Cerca de Av. Corrientes
        crimeAlerts.add(new CrimeAlert(
                "Robo de automóvil",
                "Robo de vehículo Toyota Corolla blanco en estacionamiento. Los delincuentes forzaron la cerradura y se llevaron el auto en menos de 3 minutos.",
                "Av. Corrientes 450, Buenos Aires, Argentina",
                "Hace 2 días",
                "Robo de vehículos"
        ));

        // Robo de vehículo 2: Florida
        crimeAlerts.add(new CrimeAlert(
                "Robo de motocicleta",
                "Sustracción de motocicleta Honda 125cc estacionada en la vía pública. La víctima había dejado el vehículo atado con cadena.",
                "Florida 250, Buenos Aires, Argentina",
                "Hace 4 días",
                "Robo de vehículos"
        ));

        // Robo de vehículo 3: Sarmiento
        crimeAlerts.add(new CrimeAlert(
                "Robo con intimidación",
                "Robo de vehículo Volkswagen Gol con arma de fuego. El conductor fue obligado a entregar las llaves en un semáforo.",
                "Sarmiento 350, Buenos Aires, Argentina",
                "Hace 1 día",
                "Robo de vehículos"
        ));

        // Robo de vehículo 4: Lavalle
        crimeAlerts.add(new CrimeAlert(
                "Robo de bicicleta",
                "Robo de bicicleta de alta gama en plena calle peatonal. Los delincuentes cortaron la cadena de seguridad con herramientas especializadas.",
                "Lavalle 500, Buenos Aires, Argentina",
                "Hace 3 días",
                "Robo de vehículos"
        ));

        // Robo de vehículo 5: Av. Santa Fe
        crimeAlerts.add(new CrimeAlert(
                "Robo de camioneta",
                "Sustracción de camioneta Ford EcoSport del estacionamiento de un centro comercial. Los ladrones utilizaron inhibidores de alarma.",
                "Av. Santa Fe 750, Buenos Aires, Argentina",
                "Hace 6 días",
                "Robo de vehículos"
        ));

        // Robo de vehículo 6: Cerca de Corrientes
        crimeAlerts.add(new CrimeAlert(
                "Robo de scooter",
                "Robo de scooter eléctrico mientras el propietario realizaba una entrega. Los delincuentes actuaron en grupo y huyeron rápidamente.",
                "Av. Corrientes 750, Buenos Aires, Argentina",
                "Hace 5 días",
                "Robo de vehículos"
        ));
    }

    private void addCrimeAlertsToMap() {
        // Geocodificar las direcciones de las alertas en un hilo separado
        new Thread(() -> {
            try {
                for (CrimeAlert alert : crimeAlerts) {
                    // Geocodificar la dirección usando la misma API que usamos para las rutas
                    GeoPoint location = getGeoPointFromAddress(alert.address);
                    if (location != null) {
                        alert.location = location;
                    }
                }
                
                // Una vez que tenemos todas las ubicaciones, agregar los marcadores en el hilo principal
                runOnUiThread(() -> {
                    for (CrimeAlert alert : crimeAlerts) {
                        if (alert.location != null) { // Solo agregar si se pudo geocodificar
                            Marker marker = new Marker(map);
                            marker.setPosition(alert.location);
                            marker.setTitle(alert.title);
                            marker.setSnippet(alert.timeAgo + " - " + alert.crimeType);
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                            
                            // Seleccionar ícono según el tipo de crimen
                            Drawable alertIcon;
                            if ("Robo de vehículos".equals(alert.crimeType)) {
                                // Usar ícono diferente para robos de vehículos
                                alertIcon = ContextCompat.getDrawable(this, R.drawable.ic_alert_warning);
                                // Para distinguirlos mejor, podríamos usar un color diferente
                                marker.setAlpha(0.8f); // Ligeramente transparente para diferenciar
                            } else {
                                // Crimen en vía pública
                                alertIcon = ContextCompat.getDrawable(this, R.drawable.ic_alert_warning);
                            }
                            marker.setIcon(alertIcon);
                            
                            // Configurar el click listener para mostrar el diálogo detallado
                            marker.setOnMarkerClickListener((marker1, mapView) -> {
                                showCrimeAlertDialog(alert);
                                return true; // Consumir el evento
                            });

                            map.getOverlays().add(marker);
                            crimeAlertMarkers.add(marker);
                        }
                    }
                    map.invalidate();
                    
                    // Centrar el mapa en la zona de las alertas si se geocodificaron correctamente
                    centerMapOnAlerts();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> 
                    Toast.makeText(MainActivity.this, "Error al geocodificar las alertas de seguridad", Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
    
    private void centerMapOnAlerts() {
        // Crear una lista con todas las ubicaciones de las alertas para centrar el mapa
        List<GeoPoint> alertLocations = new ArrayList<>();
        for (CrimeAlert alert : crimeAlerts) {
            if (alert.location != null) {
                alertLocations.add(alert.location);
            }
        }
        
        if (!alertLocations.isEmpty()) {
            // Si tenemos ubicaciones de alertas, centrar el mapa en ellas
            BoundingBox boundingBox = BoundingBox.fromGeoPoints(alertLocations);
            map.zoomToBoundingBox(boundingBox, true, 200); // 200 es el padding
        } else {
            // Si no se pudieron geocodificar, usar la ubicación por defecto en Corrientes
            IMapController mapController = map.getController();
            mapController.setZoom(17.0);
            mapController.setCenter(new GeoPoint(-34.6035, -58.3794)); // Av. Corrientes aprox.
        }
    }

    private void showCrimeAlertDialog(CrimeAlert alert) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Personalizar título según el tipo de crimen
        String titleIcon = "Robo de vehículos".equals(alert.crimeType) ? "🚗" : "⚠️";
        builder.setTitle(titleIcon + " " + alert.title);
        
        String message = "🏷️ Tipo: " + alert.crimeType + "\n\n" +
                        "📍 Ubicación: " + alert.address + "\n\n" +
                        "🕒 Cuándo: " + alert.timeAgo + "\n\n" +
                        "📝 Detalles: " + alert.description + "\n\n";
        
        // Mensaje de precaución específico según el tipo
        if ("Robo de vehículos".equals(alert.crimeType)) {
            message += "🚨 Recomendación: Evite estacionar vehículos en esta zona. " +
                      "Si debe hacerlo, use sistemas de seguridad adicionales y evite " +
                      "dejar objetos de valor a la vista.";
        } else {
            message += "⚠️ Recomendación: Se recomienda evitar esta zona o transitar con precaución, " +
                      "especialmente en horarios nocturnos. Manténgase alerta y evite mostrar objetos de valor.";
        }
        
        builder.setMessage(message);
        builder.setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss());
        builder.setIcon(R.drawable.ic_alert_warning);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void toggleDangerZones() {
        showAllCrimes = !showAllCrimes;
        if (showAllCrimes) {
            // Activar mostrar todos los crímenes
            showStreetCrime = true;
            showVehicleCrime = true;
        } else {
            // Desactivar todos los filtros
            showStreetCrime = false;
            showVehicleCrime = false;
        }
        
        updateCrimeFilterButtons();
        refreshCrimeDisplay();
        
        String status = showAllCrimes ? "Mostrando todos los crímenes" : "Ocultando zonas de peligro";
        Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
    }

    private void createDangerZones() {
        // Primero, limpiar zonas existentes
        hideDangerZones();
        
        // Crear zonas individuales para cada crimen
        for (CrimeAlert crime : crimeAlerts) {
            if (crime.location != null) {
                // Contar cuántos crímenes hay cerca de este
                int nearbyCount = countNearbyCrimes(crime.location, 200.0); // 200 metros de radio
                
                // Crear zona individual pero con color basado en densidad local
                createDangerZone(crime.location, nearbyCount);
            }
        }
        
        map.invalidate();
    }

    private int countNearbyCrimes(GeoPoint location, double radiusInMeters) {
        int count = 0;
        
        for (CrimeAlert crime : crimeAlerts) {
            if (crime.location != null) {
                double distance = calculateDistance(location, crime.location);
                if (distance <= radiusInMeters) {
                    count++;
                }
            }
        }
        
        return count;
    }

    private double calculateDistance(GeoPoint point1, GeoPoint point2) {
        // Fórmula de Haversine para calcular distancia entre dos puntos geográficos
        double R = 6371000; // Radio de la Tierra en metros
        double lat1Rad = Math.toRadians(point1.getLatitude());
        double lat2Rad = Math.toRadians(point2.getLatitude());
        double deltaLatRad = Math.toRadians(point2.getLatitude() - point1.getLatitude());
        double deltaLonRad = Math.toRadians(point2.getLongitude() - point1.getLongitude());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    private Map<String, List<CrimeAlert>> analyzeCrimeDensity() {
        Map<String, List<CrimeAlert>> crimesByArea = new HashMap<>();
        
        // Agrupar crímenes por áreas cercanas (aproximadamente cada 3-4 cuadras)
        for (CrimeAlert crime : crimeAlerts) {
            if (crime.location != null) {
                String areaKey = getAreaKey(crime.location);
                crimesByArea.computeIfAbsent(areaKey, k -> new ArrayList<>()).add(crime);
            }
        }
        
        return crimesByArea;
    }

    private String getAreaKey(GeoPoint location) {
        // Crear una clave de área basada en coordenadas redondeadas
        // Esto agrupa ubicaciones cercanas en la misma "zona"
        double latRounded = Math.round(location.getLatitude() * 1000.0) / 1000.0;
        double lonRounded = Math.round(location.getLongitude() * 1000.0) / 1000.0;
        return latRounded + "," + lonRounded;
    }

    private GeoPoint calculateAreaCenter(List<CrimeAlert> crimes) {
        double totalLat = 0;
        double totalLon = 0;
        int count = 0;
        
        for (CrimeAlert crime : crimes) {
            if (crime.location != null) {
                totalLat += crime.location.getLatitude();
                totalLon += crime.location.getLongitude();
                count++;
            }
        }
        
        if (count > 0) {
            return new GeoPoint(totalLat / count, totalLon / count);
        }
        return null;
    }

    private void createDangerZone(GeoPoint center, int dangerLevel) {
        if (center == null) return;
        
        // Determinar color y tamaño basado en el nivel de peligro
        int color;
        double radiusInMeters;
        int alpha;
        
        switch (dangerLevel) {
            case 1:
                color = Color.parseColor("#FFEB3B"); // Amarillo
                radiusInMeters = 120;
                alpha = 40; // Muy transparente para permitir superposición
                break;
            case 2:
                color = Color.parseColor("#FF9800"); // Naranja
                radiusInMeters = 140;
                alpha = 70; // Más visible cuando hay 2 crímenes cerca
                break;
            case 3:
                color = Color.parseColor("#FF5722"); // Naranja-rojo
                radiusInMeters = 160;
                alpha = 90;
                break;
            case 4:
                color = Color.parseColor("#F44336"); // Rojo
                radiusInMeters = 180;
                alpha = 110;
                break;
            default: // 5 o más
                color = Color.parseColor("#D32F2F"); // Rojo oscuro
                radiusInMeters = 200;
                alpha = 130; // Más opaco para zonas muy peligrosas
                break;
        }
        
        // Limitar el alpha máximo a 255
        alpha = Math.min(alpha, 180); // No demasiado opaco para mantener visibilidad
        
        // Crear círculo de peligro
        List<GeoPoint> circlePoints = createCirclePoints(center, radiusInMeters);
        
        Polygon dangerZone = new Polygon();
        dangerZone.setPoints(circlePoints);
        dangerZone.setFillColor(Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)));
        dangerZone.setStrokeColor(Color.argb(Math.min(alpha + 50, 255), Color.red(color), Color.green(color), Color.blue(color)));
        dangerZone.setStrokeWidth(2f);
        
        map.getOverlays().add(dangerZone);
        dangerZones.add(dangerZone);
    }

    private List<GeoPoint> createCirclePoints(GeoPoint center, double radiusInMeters) {
        List<GeoPoint> points = new ArrayList<>();
        int numPoints = 50; // Más puntos para un círculo más suave
        
        for (int i = 0; i < numPoints; i++) {
            double angle = 2 * Math.PI * i / numPoints;
            
            // Convertir metros a grados (aproximación)
            double deltaLat = radiusInMeters / 111000.0; // ~111km por grado de latitud
            double deltaLon = radiusInMeters / (111000.0 * Math.cos(Math.toRadians(center.getLatitude())));
            
            double lat = center.getLatitude() + deltaLat * Math.cos(angle);
            double lon = center.getLongitude() + deltaLon * Math.sin(angle);
            
            points.add(new GeoPoint(lat, lon));
        }
        
        return points;
    }

    private void hideDangerZones() {
        map.getOverlays().removeAll(dangerZones);
        dangerZones.clear();
        map.invalidate();
    }

    private void showReportCrimeDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚨 Reportar Crimen");
        
        // Inflar el layout del formulario
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report_crime, null);
        builder.setView(dialogView);
        
        // Referencias a los campos del formulario
        EditText addressInput = dialogView.findViewById(R.id.address_input);
        EditText incidentInput = dialogView.findViewById(R.id.incident_input);
        EditText timeInput = dialogView.findViewById(R.id.time_input);
        Button selectImageButton = dialogView.findViewById(R.id.select_image_button);
        TextView imageSelectedText = dialogView.findViewById(R.id.image_selected_text);
        
        // Configurar el botón de seleccionar imagen
        selectImageButton.setOnClickListener(v -> {
            // Simular selección de imagen (en una app real se abriría la galería)
            imageSelectedText.setText("✅ Imagen seleccionada: foto_evidencia.jpg");
            imageSelectedText.setTextColor(Color.parseColor("#4CAF50")); // Verde
            Toast.makeText(this, "Función de galería no implementada en la demo", Toast.LENGTH_SHORT).show();
        });
        
        builder.setPositiveButton("Enviar Reporte", null); // Lo configuraremos después
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Configurar el botón "Enviar Reporte" después de mostrar el diálogo
        Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            String address = addressInput.getText().toString().trim();
            String incident = incidentInput.getText().toString().trim();
            String time = timeInput.getText().toString().trim();
            
            // Validar campos obligatorios
            if (address.isEmpty()) {
                addressInput.setError("Por favor, ingresa la dirección");
                return;
            }
            
            if (incident.isEmpty()) {
                incidentInput.setError("Por favor, describe el incidente");
                return;
            }
            
            if (time.isEmpty()) {
                timeInput.setError("Por favor, indica cuándo ocurrió");
                return;
            }
            
            // Simular envío del reporte
            dialog.dismiss();
            showReportSuccessDialog();
        });
    }
    
    private void showReportSuccessDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✅ Reporte Enviado");
        builder.setMessage("Gracias por tu reporte. La información ha sido registrada y contribuirá a mejorar la seguridad en la zona.\n\n" +
                          "📊 Tu reporte ayudará a:\n" +
                          "• Identificar zonas de riesgo\n" +
                          "• Alertar a otros usuarios\n" +
                          "• Mejorar las rutas seguras\n\n" +
                          "🔒 Toda la información es tratada de forma confidencial.");
        builder.setPositiveButton("Entendido", (dialog, which) -> dialog.dismiss());
        builder.setIcon(R.drawable.ic_alert_warning);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    
    // ★★★ MÉTODO ESPECÍFICO PARA WAYPOINTS DE PEATONES ★★★
    
    private void addPedestrianSpecificWaypoints(GeoPoint start, GeoPoint end, List<GeoPoint> waypoints) {
        // Limitar a máximo 1 waypoint adicional para peatones
        if (waypoints.size() >= 2) {
            return; // Ya hay suficientes waypoints
        }
        
        // Buscar crímenes en vía pública específicamente
        List<CrimeAlert> streetCrimes = new ArrayList<>();
        for (CrimeAlert crime : crimeAlerts) {
            if ("Crimen en vía pública".equals(crime.crimeType) && crime.location != null) {
                streetCrimes.add(crime);
            }
        }
        
        if (streetCrimes.isEmpty()) {
            return; // No hay crímenes en vía pública que evitar
        }
        
        // Buscar el crimen más cercano a la ruta directa
        double startLat = start.getLatitude();
        double startLon = start.getLongitude();
        double endLat = end.getLatitude();
        double endLon = end.getLongitude();
        
        CrimeAlert closestCrime = null;
        double closestDistance = Double.MAX_VALUE;
        
        // Verificar múltiples puntos a lo largo de la ruta
        for (int i = 1; i <= 4; i++) {
            double ratio = (double) i / 5;
            double checkLat = startLat + (endLat - startLat) * ratio;
            double checkLon = startLon + (endLon - startLon) * ratio;
            GeoPoint checkPoint = new GeoPoint(checkLat, checkLon);
            
            for (CrimeAlert crime : streetCrimes) {
                double distance = calculateDistance(checkPoint, crime.location);
                if (distance < closestDistance && distance <= 300) { // Radio de 300m para peatones
                    closestDistance = distance;
                    closestCrime = crime;
                }
            }
        }
        
        // Si encontramos un crimen cercano, crear un waypoint de evasión
        if (closestCrime != null) {
            // Crear un waypoint que desvíe la ruta
            double avoidanceLat = closestCrime.location.getLatitude() + 0.002; // ~200m de desvío
            double avoidanceLon = closestCrime.location.getLongitude() + 0.002;
            
            // Verificar que el punto de evasión no esté cerca de otros peligros
            GeoPoint avoidancePoint = new GeoPoint(avoidanceLat, avoidanceLon);
            if (!isPointNearDanger(avoidancePoint)) {
                waypoints.add(avoidancePoint);
                System.out.println("Waypoint de evasión para peatón agregado: " + avoidanceLat + "," + avoidanceLon);
            } else {
                // Intentar en dirección opuesta
                avoidanceLat = closestCrime.location.getLatitude() - 0.002;
                avoidanceLon = closestCrime.location.getLongitude() - 0.002;
                avoidancePoint = new GeoPoint(avoidanceLat, avoidanceLon);
                if (!isPointNearDanger(avoidancePoint)) {
                    waypoints.add(avoidancePoint);
                    System.out.println("Waypoint de evasión alternativo para peatón agregado: " + avoidanceLat + "," + avoidanceLon);
                }
            }
        }
    }
    
    // ★★★ MÉTODO ESPECÍFICO PARA WAYPOINTS DE VEHÍCULOS ★★★
    
    private void addVehicleSpecificWaypoints(GeoPoint start, GeoPoint end, List<GeoPoint> waypoints) {
        // Limitar a máximo 1 waypoint adicional para vehículos
        if (waypoints.size() >= 2) {
            return; // Ya hay suficientes waypoints
        }
        
        // Buscar robos de vehículos específicamente
        List<CrimeAlert> vehicleThefts = new ArrayList<>();
        for (CrimeAlert crime : crimeAlerts) {
            if ("Robo de vehículos".equals(crime.crimeType) && crime.location != null) {
                vehicleThefts.add(crime);
            }
        }
        
        if (vehicleThefts.isEmpty()) {
            return; // No hay robos de vehículos que evitar
        }
        
        // Buscar solo el robo de vehículo más cercano a la ruta directa
        double startLat = start.getLatitude();
        double startLon = start.getLongitude();
        double endLat = end.getLatitude();
        double endLon = end.getLongitude();
        
        CrimeAlert closestTheft = null;
        double closestDistance = Double.MAX_VALUE;
        
        // Encontrar el robo más cercano al punto medio de la ruta
        double midLat = startLat + 0.5 * (endLat - startLat);
        double midLon = startLon + 0.5 * (endLon - startLon);
        GeoPoint midPoint = new GeoPoint(midLat, midLon);
        
        for (CrimeAlert theft : vehicleThefts) {
            double distance = calculateDistance(midPoint, theft.location);
            if (distance < closestDistance && distance <= 500) { // Solo si está dentro de 500m
                closestDistance = distance;
                closestTheft = theft;
            }
        }
        
        // Si encontramos un robo cercano, crear un waypoint de evasión
        if (closestTheft != null) {
            // Crear un waypoint que desvíe la ruta de manera más conservadora
            double avoidanceLat = closestTheft.location.getLatitude() + 0.003; // ~300m de desvío
            double avoidanceLon = closestTheft.location.getLongitude() + 0.003;
            
            // Verificar que el punto de evasión no esté cerca de otros peligros
            GeoPoint avoidancePoint = new GeoPoint(avoidanceLat, avoidanceLon);
            if (!isPointNearDanger(avoidancePoint)) {
                waypoints.add(avoidancePoint);
                System.out.println("Waypoint de evasión de robo de vehículo agregado: " + avoidanceLat + "," + avoidanceLon);
            }
        }
    }
    
    // ★★★ MÉTODOS PARA FILTROS DE CRIMEN ★★★
    
    private void toggleStreetCrimeFilter() {
        showStreetCrime = !showStreetCrime;
        showAllCrimes = false;
        updateCrimeFilterButtons();
        refreshCrimeDisplay();
        
        String status = showStreetCrime ? "mostrar" : "ocultar";
        Toast.makeText(this, "Filtro: " + status + " crímenes en vía pública", Toast.LENGTH_SHORT).show();
    }
    
    private void toggleVehicleCrimeFilter() {
        showVehicleCrime = !showVehicleCrime;
        showAllCrimes = false;
        updateCrimeFilterButtons();
        refreshCrimeDisplay();
        
        String status = showVehicleCrime ? "mostrar" : "ocultar";
        Toast.makeText(this, "Filtro: " + status + " robos de vehículos", Toast.LENGTH_SHORT).show();
    }
    
    private void updateCrimeFilterButtons() {
        // Actualizar el color de los botones según el estado activo
        streetCrimeFilterButton.setBackgroundTintList(ColorStateList.valueOf(
            showStreetCrime ? Color.parseColor("#FF5722") : Color.parseColor("#CCCCCC")));
        
        vehicleCrimeFilterButton.setBackgroundTintList(ColorStateList.valueOf(
            showVehicleCrime ? Color.parseColor("#9C27B0") : Color.parseColor("#CCCCCC")));
        
        dangerZonesButton.setBackgroundTintList(ColorStateList.valueOf(
            showAllCrimes ? Color.parseColor("#FF9800") : Color.parseColor("#CCCCCC")));
    }
    
    private void refreshCrimeDisplay() {
        // Limpiar overlays existentes de crímenes
        map.getOverlays().removeIf(overlay -> overlay instanceof Marker && 
            ((Marker) overlay).getTitle() != null && 
            (((Marker) overlay).getTitle().contains("ALERTA:") || 
             ((Marker) overlay).getTitle().contains("ROBO:")));
        
        // Limpiar zonas de peligro existentes
        map.getOverlays().removeIf(overlay -> overlay instanceof Polygon);
        
        // Volver a agregar según los filtros activos
        addFilteredCrimeAlertsToMap();
        if (showAllCrimes || showStreetCrime || showVehicleCrime) {
            createFilteredDangerZones();
        }
        
        map.invalidate(); // Refrescar el mapa
    }
    
    private void addFilteredCrimeAlertsToMap() {
        for (CrimeAlert alert : crimeAlerts) {
            if (alert.location == null) continue;
            
            boolean shouldShow = false;
            if (showAllCrimes) {
                shouldShow = true;
            } else {
                if ("Crimen en vía pública".equals(alert.crimeType) && showStreetCrime) {
                    shouldShow = true;
                } else if ("Robo de vehículos".equals(alert.crimeType) && showVehicleCrime) {
                    shouldShow = true;
                }
            }
            
            if (shouldShow) {
                Marker marker = new Marker(map);
                marker.setPosition(alert.location);
                
                if ("Crimen en vía pública".equals(alert.crimeType)) {
                    marker.setTitle("ALERTA: " + alert.title);
                    marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_alert_warning));
                } else if ("Robo de vehículos".equals(alert.crimeType)) {
                    marker.setTitle("ROBO: " + alert.title);
                    marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_alert_warning));
                }
                
                marker.setSnippet(alert.description + " - " + alert.timeAgo);
                marker.setOnMarkerClickListener((selectedMarker, mapView) -> {
                    showCrimeAlertDialog(alert);
                    return true;
                });
                
                map.getOverlays().add(marker);
            }
        }
    }
    
    private void createFilteredDangerZones() {
        for (CrimeAlert alert : crimeAlerts) {
            if (alert.location == null) continue;
            
            boolean shouldShow = false;
            if (showAllCrimes) {
                shouldShow = true;
            } else {
                if ("Crimen en vía pública".equals(alert.crimeType) && showStreetCrime) {
                    shouldShow = true;
                } else if ("Robo de vehículos".equals(alert.crimeType) && showVehicleCrime) {
                    shouldShow = true;
                }
            }
            
            if (shouldShow) {
                // Radio de zona de peligro según el tipo de crimen
                double dangerRadius = vehicleMode && "Robo de vehículos".equals(alert.crimeType) ? 
                    0.003 : 0.002; // Mayor radio para robos de vehículos en modo vehículo
                
                Polygon dangerZone = new Polygon();
                List<GeoPoint> circlePoints = new ArrayList<>();
                
                for (int i = 0; i <= 36; i++) {
                    double angle = i * 10 * Math.PI / 180;
                    double lat = alert.location.getLatitude() + dangerRadius * Math.cos(angle);
                    double lon = alert.location.getLongitude() + dangerRadius * Math.sin(angle);
                    circlePoints.add(new GeoPoint(lat, lon));
                }
                
                dangerZone.setPoints(circlePoints);
                
                if ("Crimen en vía pública".equals(alert.crimeType)) {
                    dangerZone.setFillColor(Color.argb(50, 255, 87, 34)); // Rojo transparente
                    dangerZone.setStrokeColor(Color.argb(100, 255, 87, 34));
                } else if ("Robo de vehículos".equals(alert.crimeType)) {
                    dangerZone.setFillColor(Color.argb(50, 156, 39, 176)); // Morado transparente
                    dangerZone.setStrokeColor(Color.argb(100, 156, 39, 176));
                }
                
                dangerZone.setStrokeWidth(2.0f);
                dangerZone.setTitle("Zona de peligro: " + alert.crimeType);
                
                map.getOverlays().add(dangerZone);
                dangerZoneOverlays.add(dangerZone);
            }
        }
    }

    @Override
    public void onBackPressed() {
        // Si hay una ruta activa (panel de información visible), limpiar la ruta
        if (routeInfoLayout.getVisibility() == View.VISIBLE) {
            clearRoute();
        } else {
            // Si no hay ruta activa, comportamiento normal (cerrar app)
            super.onBackPressed();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}

