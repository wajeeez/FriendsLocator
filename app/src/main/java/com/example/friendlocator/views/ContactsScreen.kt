package com.example.friendlocator.views

import android.Manifest
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun RequestContactPermission(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onPermissionGranted()
        } else {
            Toast.makeText(context, "Permission denied!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }
}

fun getContacts(context: Context): List<String> {
    val contactsList = mutableListOf<String>()
    val contentResolver = context.contentResolver
    val cursor: Cursor? = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null
    )

    cursor?.use {
        while (it.moveToNext()) {
            val phoneNumber = it.getString(0).replace(" ", "")
            contactsList.add(phoneNumber)
        }
    }

    return contactsList
}

