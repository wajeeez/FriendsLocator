package com.example.friendlocator.views

import android.Manifest
import android.content.Intent
import android.location.Location
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore

import com.google.firebase.firestore.firestore

@Composable
fun HeaderTitle() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val bounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Text(
        text = "📍 Friends Locator 🌍",
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.offset(y = bounce.dp).padding(16.dp),
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val permissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var friendsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var contactsList by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        if (permissionState.status.isGranted) {
            val contacts = getContacts(context)
            checkUsersInFirestore(contacts) { usersOnApp, contactsNotOnApp ->
                friendsList = usersOnApp
                contactsList = contactsNotOnApp
            }
        } else {
            permissionState.launchPermissionRequest()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        HeaderTitle()

        Spacer(modifier = Modifier.height(16.dp))

        Text("Friends List", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(friendsList) { contact ->

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text(contact, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        navController.navigate("mapScreen")
                    }) {
                        Text("Track Location")
                    }
                }

            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text("Contacts Not Using App", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyColumn {
            items(contactsList) { contact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                ) {
                    Text(contact, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_SEND)

                        intent.type = "text/plain"
                        intent.putExtra(Intent.EXTRA_TEXT, "Hey! Join this amazing app: [Download Link]")
                        intent.setPackage("com.whatsapp")
                        context.startActivity(intent)
                    }) {
                        Text("Invite")
                    }
                }
            }
        }
    }
}


fun checkUsersInFirestore(contacts: List<String>, onResult: (List<String>, List<String>) -> Unit) {
    val db = Firebase.firestore
    val usersCollection = db.collection("users")

    usersCollection.get().addOnSuccessListener { result ->
        val usersOnApp = mutableListOf<String>()
        val contactsNotOnApp = contacts.toMutableList()

        for (document in result.documents) {
            val phoneNumber = document.getString("phoneNumber") ?: continue
            if (contacts.contains(phoneNumber)) {
                usersOnApp.add(phoneNumber)
                contactsNotOnApp.remove(phoneNumber)
            }
        }

        onResult(usersOnApp, contactsNotOnApp)
    }
}


