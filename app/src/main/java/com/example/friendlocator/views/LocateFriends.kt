package com.example.friendlocator.views


import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.api.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

//@Composable
//fun LocateFriends(navController: NavController) {
//    val db = FirebaseFirestore.getInstance()
//    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
//
//    var userLocation by remember { mutableStateOf<LatLng?>(null) }
//    var friendsLocations by remember { mutableStateOf<List<LatLng>>(emptyList()) }
//    var shortestPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
//    val cameraPositionState = rememberCameraPositionState()
//
//    // Fetch user's saved location
//    LaunchedEffect(Unit) {
//        db.collection("users").document(userId).get()
//            .addOnSuccessListener { document ->
//                val latitude = document.getDouble("latitude")
//                val longitude = document.getDouble("longitude")
//                if (latitude != null && longitude != null) {
//                    val location = LatLng(latitude, longitude)
//                    userLocation = location
//                    cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 12f)
//                }
//            }
//            .addOnFailureListener { Log.e("Firestore", "Error fetching user location") }
//
//        // Fetch friends' locations
//        getFriendsLocations(userId) { locations ->
//            friendsLocations = locations
//
//            // 🚀 Compute shortest path if user & friends exist
//            if (userLocation != null && locations.isNotEmpty()) {
//                fetchShortestPath(userLocation!!, locations.first()) { path ->
//                    shortestPath = path
//                }
//            }
//        }
//    }
//
//    GoogleMap(
//        modifier = Modifier.fillMaxSize(),
//        cameraPositionState = cameraPositionState
//    ) {
//        // User's Location Marker
//        userLocation?.let {
//            Marker(
//                state = MarkerState(position = it),
//                title = "Your Location",
//                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
//            )
//        }
//
//        // Friends' Locations Markers
//        friendsLocations.forEach { friendLocation ->
//            Marker(
//                state = MarkerState(position = friendLocation),
//                title = "Friend",
//                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
//            )
//        }
//
//        // 🚗 Draw Shortest Path
//        if (shortestPath.isNotEmpty()) {
//            Polyline(
//                points = shortestPath,
//                color = Color.Blue,
//                width = 8f
//            )
//        }else{
//            Log.d("Shortest Path is Empty","Tag")
//        }
//    }
//}

@Composable
fun LocateFriends(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val context = LocalContext.current

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var friendsLocations by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var shortestPath by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    val cameraPositionState = rememberCameraPositionState()

    // ✅ Start Listening for Location Updates
    LaunchedEffect(Unit) {
        startLocationUpdates(context)
        // Listen to user's real-time location
        db.collection("users").document(userId)
            .addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    if (latitude != null && longitude != null) {
                        val location = LatLng(latitude, longitude)
                        userLocation = location
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(location, 15f)
                    }
                }
            }

        // ✅ Listen for Friends' Real-Time Locations
        getFriendsLocations(userId) { locations ->
            friendsLocations = locations

            // 🚀 Compute shortest path if user & friends exist
            if (userLocation != null && locations.isNotEmpty()) {
                fetchShortestPath(userLocation!!, locations.first()) { path ->
                    shortestPath = path
                }
            }
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {
        // ✅ User's Location Marker (Real-time)
        userLocation?.let {
            Marker(
                state = MarkerState(position = it),
                title = "Your Location",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)
            )
        }

        // ✅ Friends' Locations Markers (Real-time)
        friendsLocations.forEach { friendLocation ->
            Marker(
                state = MarkerState(position = friendLocation),
                title = "Friend",
                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }

        // 🚗 Draw Shortest Path
        if (shortestPath.isNotEmpty()) {
            Polyline(
                points = shortestPath,
                color = Color.Blue,
                width = 8f
            )
        }
    }
}



fun fetchShortestPath(start: LatLng, end: LatLng, onPathFetched: (List<LatLng>) -> Unit) {
    Log.d("Fetching Started ........................","yes")
    val apiKey = "AlzaSyPyTCLVIGnpyy0ZD5AV_8OyJPjet3JkdyP" // Your GoMap API Key
    val url = "https://maps.gomaps.pro/maps/api/directions/json?" +
            "origin=${start.latitude},${start.longitude}" +
            "&destination=${end.latitude},${end.longitude}" +
            "&key=$apiKey"

    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: okhttp3.Call, e: IOException) {
            Log.e("GoMapAPI", "Error fetching path", e)
        }

        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.body?.string()?.let { responseData ->
                val path = parseDirections(responseData) // Parse response
                onPathFetched(path)
            }
        }
    })
}

// ✅ Parse Google Directions API response
fun parseDirections(response: String): List<LatLng> {
    val path = mutableListOf<LatLng>()
    try {
        val json = JSONObject(response)
        val routes = json.getJSONArray("routes")
        if (routes.length() > 0) {
            val legs = routes.getJSONObject(0).getJSONArray("legs")
            val steps = legs.getJSONObject(0).getJSONArray("steps")

            for (i in 0 until steps.length()) {
                val points = steps.getJSONObject(i).getJSONObject("polyline").getString("points")
                path.addAll(decodePolyline(points))
            }
        }
    } catch (e: JSONException) {
        Log.e("DirectionsAPI", "Error parsing JSON", e)
    }
    return path
}

// ✅ Decode polyline from Google Maps API
fun decodePolyline(encoded: String): List<LatLng> {
    val poly = ArrayList<LatLng>()
    var index = 0
    val len = encoded.length
    var lat = 0
    var lng = 0

    while (index < len) {
        var b: Int
        var shift = 0
        var result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1F shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lat += dlat

        shift = 0
        result = 0
        do {
            b = encoded[index++].code - 63
            result = result or (b and 0x1F shl shift)
            shift += 5
        } while (b >= 0x20)
        val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        lng += dlng

        val point = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
        poly.add(point)
    }

    return poly
}

//
//fun getFriendsLocations(userId: String, onLocationsFetched: (List<LatLng>) -> Unit) {
//    val db = FirebaseFirestore.getInstance()
//
//    db.collection("users").get()
//        .addOnSuccessListener { result ->
//            val locations = mutableListOf<LatLng>()
//
//            for (document in result) {
//                if (document.id != userId) { // Exclude logged-in user
//                    val latitude = document.getDouble("latitude")
//                    val longitude = document.getDouble("longitude")
//
//                    if (latitude != null && longitude != null) {
//                        locations.add(LatLng(latitude, longitude))
//                    }
//                }
//            }
//
//            onLocationsFetched(locations)
//        }
//        .addOnFailureListener { e ->
//            Log.e("Firestore", "Error fetching friends' locations", e)
//        }
//}


fun startLocationUpdates(context: android.content.Context) {
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val locationRequest = LocationRequest.create().apply {
        interval = 5000 // Update every 5 seconds
        fastestInterval = 2000
        priority = Priority.PRIORITY_HIGH_ACCURACY
    }

    val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            for (location in locationResult.locations) {
                val userLocation = mapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                db.collection("users").document(userId)
                    .set(userLocation, SetOptions.merge())
                    .addOnSuccessListener { Log.d("LocationUpdate", "Location updated!") }
                    .addOnFailureListener { e -> Log.e("LocationUpdate", "Error updating location", e) }
            }
        }
    }

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
}


fun getFriendsLocations(userId: String, onLocationsFetched: (List<LatLng>) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    db.collection("users").addSnapshotListener { snapshot, e ->
        if (e != null) {
            Log.e("Firestore", "Error listening to location updates", e)
            return@addSnapshotListener
        }

        if (snapshot != null) {
            val locations = mutableListOf<LatLng>()

            for (document in snapshot.documents) {
                if (document.id != userId) { // Exclude logged-in user
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")

                    if (latitude != null && longitude != null) {
                        locations.add(LatLng(latitude, longitude))
                    }
                }
            }

            onLocationsFetched(locations) // Update real-time friend locations
        }
    }
}



