package com.example.friendlocator.views

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.example.friendlocator.secure.Config
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = Firebase.firestore

    val googleSignInClient = remember { GoogleSignIn.getClient(context, getGoogleSignInOptions()) }

    // 🔹 State to control dialog visibility
    var showDialog by remember { mutableStateOf(false) }
    var userId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)

        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)

            auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                if (authTask.isSuccessful) {
                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val userEmail = auth.currentUser?.email ?: "No Email"
                    val userName = auth.currentUser?.displayName ?: userEmail.substring(0,4)

                    // 🔹 Check Firestore for phone number
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { document ->
                            if (document.exists() && document.getString("phoneNumber") != null) {
                                // ✅ Phone number exists, go to home
                                navController.navigate("home") {
                                    popUpTo("login") { inclusive = true }
                                }
                            } else {
                                // ❌ No phone number, show dialog
                                userId = uid
                                email = userEmail
                                name = userName
                                showDialog = true
                            }
                        }
                        .addOnFailureListener {
                            Log.e("Firestore", "Error checking user data", it)
                        }
                } else {
                    Log.e("Login", "Error: ${authTask.exception?.message}")
                }
            }
        } catch (e: ApiException) {
            Log.e("Login", "Google Sign-In failed: ${e.message}")
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize().systemBarsPadding()
    ) {
        Button(
            onClick = {
                val signInIntent = googleSignInClient.signInIntent
                launcher.launch(signInIntent)
            }
        ) {
            Text("Sign in with Google")
        }
    }

    // 🔹 Show dialog when needed
    if (showDialog) {
        PhoneNumberDialog(
            userId = userId,
            email = email,
            name = name,
            navController = navController,
            fusedLocationClient = fusedLocationClient,
            onDismiss = { showDialog = false }
        )
    }
}


fun getGoogleSignInOptions(): GoogleSignInOptions {
    return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(Config.WEB_CLIENT_ID) // Replace with your Firebase Web Client ID
        .requestEmail()
        .build()
}



@Composable
fun PhoneNumberDialog(
    userId: String,
    email: String,
    name: String,
    navController: NavController,
    fusedLocationClient: FusedLocationProviderClient, // Add FusedLocationProviderClient
    onDismiss: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    val context = LocalContext.current
    val db = Firebase.firestore

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Your Phone Number") },
        text = {
            TextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        },
        confirmButton = {
            Button(onClick = {
                if (phoneNumber.isNotEmpty()) {
                    // Fetch last known location
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED &&
                        ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Toast.makeText(context, "Location permission required!", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        if (location != null) {
                            val user = hashMapOf(
                                "name" to name,
                                "email" to email,
                                "phoneNumber" to phoneNumber,
                                "latitude" to location.latitude,
                                "longitude" to location.longitude
                            )

                            db.collection("users").document(userId).set(user, SetOptions.merge())
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Phone & location saved!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(context, "Failed to get location!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }) {
                Text("Save")
            }
        }
    )
}

