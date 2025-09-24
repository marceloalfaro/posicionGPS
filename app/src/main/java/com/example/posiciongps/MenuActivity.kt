package com.example.posiciongps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

data class UserLocation(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = 0L,
    val address: String = ""
)

class MenuActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            MaterialTheme {
                MenuScreen(
                    auth = auth,
                    database = database,
                    fusedLocationClient = fusedLocationClient,
                    onLogout = {
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    auth: FirebaseAuth,
    database: DatabaseReference,
    fusedLocationClient: FusedLocationProviderClient,
    onLogout: () -> Unit
) {
    val currentUser = auth.currentUser
    var userLocations by remember { mutableStateOf<List<UserLocation>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isGettingLocation by remember { mutableStateOf(false) }

    val context = LocalContext.current


    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            getCurrentLocation(fusedLocationClient, database, currentUser?.uid) { success ->
                isGettingLocation = false
                if (success) {
                    android.widget.Toast.makeText(context, "Ubicación guardada exitosamente", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "Error al obtener ubicación", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            isGettingLocation = false
            android.widget.Toast.makeText(context, "Permiso de ubicación denegado", android.widget.Toast.LENGTH_SHORT).show()
        }
    }


    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { userId ->
            database.child("user_locations").child(userId)
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val locations = mutableListOf<UserLocation>()
                        for (locationSnapshot in snapshot.children) {
                            val location = locationSnapshot.getValue(UserLocation::class.java)
                            location?.let { locations.add(it) }
                        }
                        userLocations = locations.sortedByDescending { it.timestamp }
                        isLoading = false
                    }

                    override fun onCancelled(error: DatabaseError) {
                        isLoading = false
                    }
                })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Bienvenido",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentUser?.email ?: "Usuario",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onLogout) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = "Cerrar Sesión",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {

                    val fineLocationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val coarseLocationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (fineLocationPermission || coarseLocationPermission) {
                        isGettingLocation = true
                        getCurrentLocation(fusedLocationClient, database, currentUser?.uid) { success ->
                            isGettingLocation = false
                            if (success) {
                                android.widget.Toast.makeText(context, "Ubicación guardada", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, "Error al obtener ubicación", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        locationPermissionLauncher.launch(arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isGettingLocation
            ) {
                if (isGettingLocation) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isGettingLocation) "Obteniendo..." else "Guardar GPS")
            }

            OutlinedButton(
                onClick = {

                    currentUser?.uid?.let { userId ->
                        database.child("user_locations").child(userId).removeValue()
                            .addOnSuccessListener {
                                android.widget.Toast.makeText(context, "Todas las ubicaciones borradas", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                android.widget.Toast.makeText(context, "Error al borrar ubicaciones", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Borrar Todo")
            }
        }


        Text(
            text = "Historial de Ubicaciones",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (userLocations.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Text(
                    text = "No hay ubicaciones guardadas",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn {
                items(userLocations.size) { index ->
                    LocationItem(location = userLocations[index])
                    if (index < userLocations.size - 1) {
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LocationItem(location: UserLocation) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ubicación GPS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = formatTimestamp(location.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Lat: ${String.format("%.6f", location.latitude)}",
                fontSize = 14.sp
            )
            Text(
                text = "Lng: ${String.format("%.6f", location.longitude)}",
                fontSize = 14.sp
            )

            if (location.address.isNotEmpty()) {
                Text(
                    text = location.address,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}


fun getCurrentLocation(
    fusedLocationClient: FusedLocationProviderClient,
    database: DatabaseReference,
    userId: String?,
    onComplete: (Boolean) -> Unit
) {
    if (userId == null) {
        onComplete(false)
        return
    }

    try {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    val userLocation = UserLocation(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestamp = System.currentTimeMillis(),
                        address = "Lat: ${String.format("%.6f", location.latitude)}, Lng: ${String.format("%.6f", location.longitude)}"
                    )

                    val locationId = database.child("user_locations").child(userId).push().key
                    locationId?.let {
                        database.child("user_locations").child(userId).child(it).setValue(userLocation)
                            .addOnSuccessListener { onComplete(true) }
                            .addOnFailureListener { onComplete(false) }
                    } ?: onComplete(false)
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                onComplete(false)
            }
    } catch (e: SecurityException) {
        onComplete(false)
    }
}


fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
    return format.format(date)
}