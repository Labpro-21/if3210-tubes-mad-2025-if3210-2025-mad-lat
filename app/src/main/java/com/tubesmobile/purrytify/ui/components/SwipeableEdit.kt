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
// import com.google.android.libraries.places.api.model.Place // For Google Places
// import com.google.android.libraries.places.widget.Autocomplete // For Google Places
// import com.google.android.libraries.places.widget.model.AutocompleteActivityMode // For Google Places
import androidx.compose.material.icons.outlined.PhotoLibrary // For Gallery
import androidx.compose.material.icons.outlined.PhotoCamera  // For Camera
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Edit

data class ProfileData(
    val currentUsername: String,
    val currentLocation: String?,
    val currentProfilePhotoUrl: String?
)

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

    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
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

    val mapsActivityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // TODO: Process Google Places SDK result
            // val place = Autocomplete.getPlaceFromIntent(result.data!!)
            // val countryComponent = place.addressComponents?.asList()
            //    ?.find { component -> component.types.contains("country") }
            // if (countryComponent != null && countryComponent.shortName != null) {
            //    location = countryComponent.shortName!!
            // } else {
            //    errorMessage = "Could not determine country code from selected location."
            //    showErrorDialog = true
            // }
            // Log.i("ProfileEditDialog", "Places SDK result received. Processing needed.")
            errorMessage = "Manual map selection: Google Places SDK integration needed for result processing."
            showErrorDialog = true
        } else {
            // Log.w("ProfileEditDialog", "Places SDK activity cancelled or failed.")
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false)
        val coarseLocationGranted = permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)

        if (fineLocationGranted || coarseLocationGranted) {
            // Log.i("ProfileEditDialog", "Location permission granted. Implement location fetching.")
            // TODO: Implement FusedLocationProviderClient logic to get current location,
            //  then use Geocoder to get country code from LatLng.
            errorMessage = "Auto-detect location: Location fetching and Geocoding needed."
            showErrorDialog = true
        } else {
            errorMessage = "Location permission denied for auto-detection."
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
                        OutlinedButton( // Changed to OutlinedButton
                            onClick = {
                                // TODO: Setup and launch Google Places Autocomplete Intent
                                // Example:
                                // val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS_COMPONENTS)
                                // val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                                //    .setCountries(listOf("ID", "US", "GB")) // Optional: Filter by specific countries
                                //    .setHint("Search for a country") // Optional: Provide a hint text
                                //    .build(context)
                                // mapsActivityLauncher.launch(intent)
                                errorMessage = "Manual map selection: Google Places SDK setup required."
                                showErrorDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.EditLocation, contentDescription = "Select map icon", modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Select Map")
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
}