package com.tubesmobile.purrytify.ui.components

import android.Manifest
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tubesmobile.purrytify.service.DataKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.io.IOException
import java.util.Locale
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.content.res.Configuration as AndroidConfiguration


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
        mapView.controller.setCenter(GeoPoint(20.0, 0.0))
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
    imageBitmap: ImageBitmap?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val boxSize = if (isLandscape) 100.dp else 120.dp
    val iconBadgeSize = if (isLandscape) 28.dp else 32.dp
    val iconSize = if (isLandscape) 16.dp else 18.dp

    Box(
        modifier = modifier
            .size(boxSize)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
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
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = label,
                        modifier = Modifier.size(if (isLandscape) 30.dp else 40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = label,
                        style = if (isLandscape) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(if (isLandscape) 2.dp else 4.dp)
                .size(iconBadgeSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "Edit Photo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(iconSize)
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
    isPrimary: Boolean = true
) {
    val buttonColors = if (isPrimary) {
        ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 100.dp),
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

    val isLandscape = configuration.orientation == AndroidConfiguration.ORIENTATION_LANDSCAPE

    LaunchedEffect(selectedProfilePhotoUri) {
        isProfilePhotoChanged = selectedProfilePhotoUri != null
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
                    withContext(Dispatchers.Main) {
                        displayedProfilePhotoBitmap = null
                        errorMessage = "Could not load selected photo: ${e.localizedMessage}"
                        showErrorDialog = true
                    }
                }
            }
        } else {
            displayedProfilePhotoBitmap = null
        }
    }

    fun createProfilePhotoImageUri(context: Context): Uri {
        val imageFile = File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
        actualCameraOutputFile = imageFile
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
            val fileToCheck = actualCameraOutputFile
            if (fileToCheck != null && fileToCheck.exists() && fileToCheck.length() > 0) {
                selectedProfilePhotoUri = cameraImageUriForPhoto
            } else {
                errorMessage = "Failed to save photo. Camera didn't create the file correctly."
                showErrorDialog = true
                selectedProfilePhotoUri = null
                fileToCheck?.delete()
            }
        } else {
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
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { loc: android.location.Location? ->
                        if (loc != null) {
                            try {
                                val geocoder = Geocoder(context, Locale.getDefault())
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    geocoder.getFromLocation(loc.latitude, loc.longitude, 1, object : Geocoder.GeocodeListener {
                                        override fun onGeocode(addresses: MutableList<Address>) {
                                            if (addresses.isNotEmpty()) {
                                                addresses[0].countryCode?.takeIf { it.isNotBlank() }?.let {
                                                    location = it.uppercase()
                                                    DataKeeper.location = it
                                                } ?: run {
                                                    errorMessage = "Could not determine country code from current location."
                                                    showErrorDialog = true
                                                }
                                            } else {
                                                errorMessage = "Could not find address details for current location."
                                                showErrorDialog = true
                                            }
                                        }
                                        override fun onError(errorMsgFromGeocoder: String?) {
                                            errorMessage = "Geocoder service error: ${errorMsgFromGeocoder ?: "Unknown geocoder error"}"
                                            showErrorDialog = true
                                        }
                                    })
                                } else {
                                    @Suppress("DEPRECATION")
                                    val addresses: List<Address>? = geocoder.getFromLocation(loc.latitude, loc.longitude, 1)
                                    if (!addresses.isNullOrEmpty()) {
                                        addresses[0].countryCode?.takeIf { it.isNotBlank() }?.let {
                                            location = it.uppercase()
                                            DataKeeper.location = it
                                        } ?: run {
                                            errorMessage = "Could not determine country code from current location."
                                            showErrorDialog = true
                                        }
                                    } else {
                                        errorMessage = "Could not find address details for current location."
                                        showErrorDialog = true
                                    }
                                }
                            } catch (e: Exception) {
                                errorMessage = "Error processing location: ${e.localizedMessage}"
                                showErrorDialog = true
                            }
                        } else {
                            errorMessage = "Could not get current location."
                            showErrorDialog = true
                        }
                    }
                    .addOnFailureListener { e ->
                        errorMessage = "Failed to get location: ${e.localizedMessage ?: "Unknown error"}"
                        showErrorDialog = true
                    }
            } else {
                errorMessage = "Location permission error."
                showErrorDialog = true
            }
        } else {
            errorMessage = "Location permission denied."
            showErrorDialog = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .heightIn(min = 300.dp, max = (configuration.screenHeightDp * (if (isLandscape) 0.9 else 0.7)).dp)
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
                        .fillMaxSize()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .width(32.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = if (isLandscape) 16.dp else 24.dp)
                    )

                    if (isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ProfilePhotoBox(
                                label = "Change Photo",
                                imageBitmap = displayedProfilePhotoBitmap,
                                imageUrl = if (isProfilePhotoChanged) null else existingProfile?.currentProfilePhotoUrl,
                                onClick = { showPhotoSourceDialog = true },
                                modifier = Modifier.padding(end = 8.dp),
                                isLandscape = true
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                LocationFields(
                                    location = location,
                                    onLocationChange = { if (it.length <= 2) location = it.uppercase().filter { char -> char.isLetter() } },
                                    keyboardController = keyboardController,
                                    onAutoDetectClick = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                                    onPickOnMapClick = { showOsmMapDialog = true }
                                )
                            }
                        }
                    } else {
                        ProfilePhotoBox(
                            label = "Change Photo",
                            imageBitmap = displayedProfilePhotoBitmap,
                            imageUrl = if (isProfilePhotoChanged) null else existingProfile?.currentProfilePhotoUrl,
                            onClick = { showPhotoSourceDialog = true },
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        LocationFields(
                            location = location,
                            onLocationChange = { if (it.length <= 2) location = it.uppercase().filter { char -> char.isLetter() } },
                            keyboardController = keyboardController,
                            onAutoDetectClick = { locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
                            onPickOnMapClick = { showOsmMapDialog = true }
                        )
                    }


                    Spacer(modifier = Modifier.weight(1f, fill = true))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = if (isLandscape) 16.dp else 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProfileActionButton(
                            "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            isPrimary = false
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
                                        { onDismiss() },
                                        { errorMsg ->
                                            errorMessage = errorMsg
                                            showErrorDialog = true
                                        }
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = isProfilePhotoChanged || (location.isNotBlank() && location.length == 2 && location != (existingProfile?.currentLocation ?: "")),
                            isPrimary = true
                        )
                    }
                    if (isLandscape) Spacer(modifier = Modifier.height(16.dp))
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
        Dialog(onDismissRequest = { showPhotoSourceDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .width(IntrinsicSize.Min),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Change Profile Photo",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    PhotoSourceButton(
                        icon = Icons.Outlined.PhotoLibrary,
                        text = "Choose from Gallery",
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryProfilePhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    PhotoSourceButton(
                        icon = Icons.Outlined.PhotoCamera,
                        text = "Take Photo with Camera",
                        onClick = {
                            showPhotoSourceDialog = false
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                val newUri = createProfilePhotoImageUri(context)
                                cameraImageUriForPhoto = newUri
                                cameraProfilePhotoLauncher.launch(newUri)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = { showPhotoSourceDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (showOsmMapDialog) {
        OsmCountryPickerDialog(
            onDismissRequest = { showOsmMapDialog = false },
            onLocationSelected = { geoPoint ->
                showOsmMapDialog = false
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (addresses.isNotEmpty()) {
                                        addresses[0].countryCode?.takeIf { it.isNotBlank() }?.let { code ->
                                            location = code.uppercase()
                                            DataKeeper.location = location
                                        } ?: run {
                                            errorMessage = "Country code not found for selected map point."
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
                            addresses[0].countryCode?.takeIf { it.isNotBlank() }?.let { code ->
                                location = code.uppercase()
                                DataKeeper.location = location
                            } ?: run {
                                errorMessage = "Country code not found for selected map point."
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
                }
            }
        )
    }
}

@Composable
private fun LocationFields(
    location: String,
    onLocationChange: (String) -> Unit,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?,
    onAutoDetectClick: () -> Unit,
    onPickOnMapClick: () -> Unit,
) {
    OutlinedTextField(
        value = location,
        onValueChange = onLocationChange,
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
        leadingIcon = { Icon(Icons.Filled.Place, "Location Icon") },
        supportingText = { Text("ISO 3166-1 alpha-2 country code") }
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onAutoDetectClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = "Auto-detect icon", modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Auto-detect", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        OutlinedButton(
            onClick = onPickOnMapClick,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.EditLocation, contentDescription = "Pick on map icon", modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text("Pick on Map", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PhotoSourceButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
