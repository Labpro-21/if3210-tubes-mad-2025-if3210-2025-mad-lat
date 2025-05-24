package com.tubesmobile.purrytify.ui.components

import android.Manifest
import android.R
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.material.icons.filled.Check
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.IOException // For Geocoder
import java.util.Locale // For Geocoder
import com.tubesmobile.purrytify.service.DataKeeper
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

data class ProfileData(
    val currentUsername: String,
    val currentLocation: String?,
    val currentProfilePhotoUrl: String?
)

@Composable
fun OsmCountryPickerDialog(
    onDismissRequest: () -> Unit,
    onLocationSelected: (geoPoint: GeoPoint) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }

    LaunchedEffect(Unit) {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(3.0)
        mapView.controller.setCenter(GeoPoint(20.0, 0.0)) // Default center
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Center Marker",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Button(
                    onClick = {
                        val centerGeoPoint = mapView.mapCenter as GeoPoint
                        onLocationSelected(centerGeoPoint)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Confirm")
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Confirm This Location")
                }
            }
        }
    }
}

@Composable
fun ProfilePhotoBox(
    label: String,
    imageBitmap: ImageBitmap?, // For newly selected local image
    imageUrl: String?, // For existing image URL from network
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box( // This is the main container for the photo and the edit icon
        modifier = modifier
            .size(120.dp) // Main size of the profile photo area
            .clickable(onClick = onClick) // The entire area is clickable
    ) {
        // Box for the circular photo itself (image or placeholder)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize() // Takes the size of the parent Box (120.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Selected Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .placeholder(com.tubesmobile.purrytify.R.drawable.ic_launcher_foreground)
                        .error(com.tubesmobile.purrytify.R.drawable.ic_launcher_foreground)
                        .build(),
                    contentDescription = "Current Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = label,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Edit Icon Overlay Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd) // Position at the bottom-right of the parent 120.dp Box
                .padding(4.dp) // Optional small offset from the very edge
                .size(32.dp)   // Size of the circular badge for the icon
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary), // Background color of the badge
            contentAlignment = Alignment.Center // Center the Icon within this badge
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit Photo",
                tint = MaterialTheme.colorScheme.onPrimary, // Color of the edit icon itself
                modifier = Modifier.size(18.dp) // Size of the actual icon vector
            )
        }
    }
}

@Composable
fun ProfileActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = true // Added to distinguish primary/secondary
) {
    val buttonColors = if (isPrimary) {
        ButtonDefaults.buttonColors() // Default primary colors
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 120.dp),
        colors = buttonColors,
        shape = RoundedCornerShape(24.dp)
    ) {
        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SwipeableProfileEditDialog(
    onDismiss: () -> Unit,
    existingProfile: ProfileData?,
    onSaveProfile: (location: String?, profilePhotoUri: Uri?, onSaveComplete: () -> Unit, onError: (String) -> Unit) -> Unit
) {
    var isProfilePhotoChanged by remember(existingProfile) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }

    var location by remember(existingProfile?.currentLocation) { mutableStateOf(existingProfile?.currentLocation ?: "") }
    var selectedProfilePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var displayedProfilePhotoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showOsmMapDialog by remember { mutableStateOf(false) }

    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var cameraImageUriForPhoto by remember { mutableStateOf<Uri?>(null) }
    var actualCameraOutputFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(selectedProfilePhotoUri) {
        isProfilePhotoChanged = selectedProfilePhotoUri != null // Photo is considered changed if a new URI is set
        if (selectedProfilePhotoUri != null) {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(selectedProfilePhotoUri!!)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        withContext(Dispatchers.Main) {
                            displayedProfilePhotoBitmap = bitmap?.asImageBitmap()
                        }
                    }
                } catch (e: Exception) {
                    // Log.e("ProfileEditDialog", "Error loading selected photo URI: $selectedProfilePhotoUri", e)
                    withContext(Dispatchers.Main) {
                        displayedProfilePhotoBitmap = null // Clear preview on error
                        errorMessage = "Could not load selected photo: ${e.localizedMessage}"
                        showErrorDialog = true
                    }
                }
            }
        } else {
            displayedProfilePhotoBitmap = null // Clear local bitmap preview if URI is null
        }
    }

    fun createProfilePhotoImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")

        // STORE THE FILE OBJECT HERE:
        actualCameraOutputFile = imageFile

        android.util.Log.d("ProfileEditDialog_Diag", "createProfilePhotoImageUri: Expecting camera to write to path: ${imageFile.absolutePath}")
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
    }

    val galleryProfilePhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedProfilePhotoUri = uri
    }

    val cameraProfilePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            Log.d("ProfileEditDialog_Diag", "Camera returned success=true.")
            val fileToCheck = actualCameraOutputFile
            if (fileToCheck != null && fileToCheck.exists() && fileToCheck.length() > 0) {
                Log.i("ProfileEditDialog_Diag", "SUCCESS: Camera photo file found at: ${fileToCheck.absolutePath}, Size: ${fileToCheck.length()}")
                // File exists and has content, proceed as normal
                selectedProfilePhotoUri = cameraImageUriForPhoto
            } else {
                Log.e("ProfileEditDialog_Diag", "ERROR: Camera returned success, but file NOT FOUND or IS EMPTY at: ${fileToCheck?.absolutePath}. Length: ${fileToCheck?.length()}")
                errorMessage = "Failed to save photo. Camera didn't create the file correctly. Please try again or use the gallery."
                showErrorDialog = true
                selectedProfilePhotoUri = null // Ensure no invalid URI is used
                fileToCheck?.delete() // Attempt to delete if an empty/corrupt file was created
            }
        } else {
            Log.w("ProfileEditDialog_Diag", "Camera activity did not return success (e.g., user cancelled).")
            actualCameraOutputFile?.delete()
        }
        actualCameraOutputFile = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val newUri = createProfilePhotoImageUri(context)
            cameraImageUriForPhoto = newUri
            cameraProfilePhotoLauncher.launch(newUri)
        } else {
            errorMessage = "Camera permission denied."
            showErrorDialog = true
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        val coarseLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

        if (fineLocationGranted || coarseLocationGranted) {
            Log.i("ProfileEditDialog", "Location permission granted. Fetching current location...")

            // Double-check permission for lint and safety before making the call
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc: android.location.Location? ->
                        if (loc != null) {
                            Log.d("ProfileEditDialog", "Location fetched: Lat ${loc.latitude}, Lon ${loc.longitude}")
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())

                                // Use modern API for Android 13 and above
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                                        override fun onGeocode(addresses: MutableList<Address>) {
                                            if (addresses.isNotEmpty()) {
                                                val countryCode = addresses[0].countryCode // ISO 3166-1 alpha-2
                                                if (!countryCode.isNullOrEmpty()) {
                                                    location = countryCode.uppercase()
                                                    DataKeeper.location = countryCode
                                                    Log.i("ProfileEditDialog", "Country code (API 33+): $countryCode")
                                                } else {
                                                    Log.w("ProfileEditDialog", "Country code not found in address (API 33+).")
                                                    errorMessage = "Could not determine country code from current location."
                                                    showErrorDialog = true
                                                }
                                            } else {
                                                Log.w("ProfileEditDialog", "No address found for the current location (API 33+).")
                                                errorMessage = "Could not find address details for current location."
                                                showErrorDialog = true
                                            }
                                        }

                                        override fun onError(errorMsgFromGeocoder: String?) {
                                            super.onError(errorMsgFromGeocoder)
                                            Log.e("ProfileEditDialog", "Geocoder error (API 33+): $errorMsgFromGeocoder")
                                            errorMessage = "Geocoder service error: ${errorMsgFromGeocoder ?: "Unknown geocoder error"}"
                                            showErrorDialog = true
                                        }
                                    })
                                } else { // For versions older than Android 13
                                    @Suppress("DEPRECATION")
                                    val addresses: List<Address>? = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        val countryCode = addresses[0].countryCode
                                        if (!countryCode.isNullOrEmpty()) {
                                            location = countryCode.uppercase()
                                            DataKeeper.location = countryCode
                                            Log.i("ProfileEditDialog", "Country code (pre-API 33): $countryCode")
                                        } else {
                                            Log.w("ProfileEditDialog", "Country code not found in address (pre-API 33).")
                                            errorMessage = "Could not determine country code from current location."
                                            showErrorDialog = true
                                        }
                                    } else {
                                        Log.w("ProfileEditDialog", "No address found for the current location (pre-API 33).")
                                        errorMessage = "Could not find address details for current location."
                                        showErrorDialog = true
                                    }
                                }
                            } catch (e: IOException) { // Geocoder can throw IOException
                                Log.e("ProfileEditDialog", "Geocoder IOException (likely network or service unavailable)", e)
                                errorMessage = "Unable to connect to Geocoder service. Check network."
                                showErrorDialog = true
                            } catch (e: IllegalArgumentException) {
                                Log.e("ProfileEditDialog", "Geocoder IllegalArgumentException (invalid lat/lon)", e)
                                errorMessage = "Invalid coordinates for geocoding."
                                showErrorDialog = true
                            }
                        } else {
                            Log.w("ProfileEditDialog", "FusedLocationProviderClient.getCurrentLocation returned null.")
                            errorMessage = "Could not get current location. Please ensure GPS/Location Services are enabled and try again."
                            showErrorDialog = true
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileEditDialog", "FusedLocationProviderClient.getCurrentLocation task failed", e)
                        errorMessage = "Failed to get location: ${e.localizedMessage ?: "Unknown error"}"
                        showErrorDialog = true
                    }
            } else {
                Log.w("ProfileEditDialog", "Location permission check failed immediately after launcher callback.")
                errorMessage = "Location permission error. Please try again."
                showErrorDialog = true
            }
        } else {
            Log.w("ProfileEditDialog", "Location permission denied by user.")
            errorMessage = "Location permission denied. Cannot auto-detect location."
            showErrorDialog = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) { // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss
                    )
            )
            Box( // Draggable Sheet Content
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 400.dp, max = (configuration.screenHeightDp * 0.65).dp)
                    .offset { IntOffset(0, offsetY.value.toInt()) }
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
                    .clickable(enabled = false) {}
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            scope.launch { offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f)) }
                        },
                        onDragStopped = {
                            scope.launch {
                                if (offsetY.value > screenHeightPx * 0.25f) {
                                    offsetY.animateTo(screenHeightPx, tween(300))
                                    onDismiss()
                                } else {
                                    offsetY.animateTo(0f, tween(300))
                                }
                            }
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box( // Drag Handle
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    ProfilePhotoBox(
                        label = "Change Photo",
                        imageBitmap = displayedProfilePhotoBitmap,
                        imageUrl = if (isProfilePhotoChanged) null else existingProfile?.currentProfilePhotoUrl,
                        onClick = { showPhotoSourceDialog = true },
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    OutlinedTextField(
                        value = location,
                        onValueChange = { if (it.length <= 2) location = it.uppercase().filter { char -> char.isLetter() } },
                        label = { Text("Location") },
                        placeholder = { Text("e.g., US, ID") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Done,
                            autoCorrect = false,
                            capitalization = KeyboardCapitalization.Characters
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.EditLocation, "Location Icon") },
                        supportingText = { Text("ISO 3166-1 alpha-2 country code") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton( // Changed to OutlinedButton
                            onClick = {
                                locationPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Auto-detect icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Auto-detect")
                        }
                        OutlinedButton(
                            onClick = {
                                showOsmMapDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.EditLocation, contentDescription = "Pick on map icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Pick on Map") // You can adjust the button text if you like
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = true)) // Pushes buttons to bottom

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileActionButton(
                            "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            isPrimary = false // Style as secondary
                        )
                        ProfileActionButton(
                            "Save",
                            onClick = {
                                val finalLocationToSave = if (location.isNotBlank() && location.length == 2 && location != (existingProfile?.currentLocation ?: "")) location else null
                                val finalPhotoUriToSave = if (isProfilePhotoChanged) selectedProfilePhotoUri else null

                                if (finalLocationToSave == null && finalPhotoUriToSave == null) {
                                    errorMessage = "No changes to save."
                                    showErrorDialog = true
                                } else {
                                    onSaveProfile(
                                        finalLocationToSave,
                                        finalPhotoUriToSave,
                                        { onDismiss() }, // onSuccess: dismisses the dialog
                                        { errorMsg -> // onError
                                            errorMessage = errorMsg
                                            showErrorDialog = true
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isProfilePhotoChanged || (location.isNotBlank() && location != (existingProfile?.currentLocation ?: "")),
                            isPrimary = true // Style as primary
                        )
                    }
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            confirmButton = { TextButton(onClick = { showErrorDialog = false }) { Text("OK") } },
            title = { Text("Edit Profile") },
            text = { Text(errorMessage) }
        )
    }

    if (showPhotoSourceDialog) {
        Dialog(onDismissRequest = { showPhotoSourceDialog = false }) { // Photo source selection dialog
            Surface(
                shape = MaterialTheme.shapes.extraLarge, // M3 Dialog shape
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .width(IntrinsicSize.Max), // Or your preferred width logic
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Change Profile Photo",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between buttons
                    ) {
                        TextButton(
                            onClick = {
                                showPhotoSourceDialog = false
                                galleryProfilePhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 80.dp) // Increased height for icon + text + padding
                                .padding(vertical = 8.dp) // Padding within the button
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoLibrary,
                                    contentDescription = "Choose from Gallery",
                                    modifier = Modifier.size(48.dp),
                                )
                                Spacer(Modifier.height(4.dp)) // Space between icon and text
                                Text(
                                    text = "Choose from Gallery",
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp, // Slightly smaller font can help
                                    color = Color.LightGray
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                showPhotoSourceDialog = false
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    val newUri = createProfilePhotoImageUri(context)
                                    cameraImageUriForPhoto = newUri
                                    cameraProfilePhotoLauncher.launch(newUri)
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 80.dp) // Increased height
                                .padding(vertical = 8.dp) // Padding within the button
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = "Take Photo with Camera",
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Take Photo with Camera",
                                    textAlign = TextAlign.Center,
                                    fontSize = 12.sp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    TextButton(
                        onClick = { showPhotoSourceDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Cancel",
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
    if (showOsmMapDialog) {
        OsmCountryPickerDialog(
            onDismissRequest = { showOsmMapDialog = false },
            onLocationSelected = { geoPoint ->
                showOsmMapDialog = false // Dismiss map dialog after selection
                Log.i("ProfileEditDialog", "OSM Map selected: Lat ${geoPoint.latitude}, Lon ${geoPoint.longitude}")
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    // Handle Geocoder for different API levels
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (addresses.isNotEmpty()) {
                                        addresses[0].countryCode?.let { code ->
                                            if (code.isNotBlank()) {
                                                location = code.uppercase()
                                                DataKeeper.location = location
                                            }
                                            else {
                                                errorMessage = "Country code not found for selected map point."
                                                showErrorDialog = true
                                            }
                                        } ?: run {
                                            errorMessage = "Country code unavailable for selected map point."
                                            showErrorDialog = true
                                        }
                                    } else {
                                        errorMessage = "No address details found for selected map point."
                                        showErrorDialog = true
                                    }
                                }
                                override fun onError(errorMsgFromGeocoder: String?) {
                                    errorMessage = "Geocoder error: ${errorMsgFromGeocoder ?: "Unknown"}"
                                    showErrorDialog = true
                                }
                            }
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            addresses[0].countryCode?.let { code ->
                                if (code.isNotBlank()) location = code.uppercase()
                                else {
                                    errorMessage = "Country code not found for selected map point."
                                    showErrorDialog = true
                                }
                            } ?: run {
                                errorMessage = "Country code unavailable for selected map point."
                                showErrorDialog = true
                            }
                        } else {
                            errorMessage = "No address details found for selected map point."
                            showErrorDialog = true
                        }
                    }
                } catch (e: IOException) {
                    errorMessage = "Geocoder service not available. Check network connection."
                    showErrorDialog = true
                    Log.e("ProfileEditDialog", "Geocoder failed for OSM point", e)
                }
            }
        )
    }
}