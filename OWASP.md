# Ringkasan Perubahan Kode Berdasarkan OWASP

## Daftar Isi

- [HomeScreen.kt](#homescreenkt)
- [LibraryScreen.kt](#libraryscreenkt)
- [MusicScreen.kt](#musicscreenkt)
- [ProfileScreen.kt](#profilescreenkt)
- [LoginViewModel.kt](#loginviewmodelkt)
- [MusicBehaviorViewModel.kt](#musicbehaviorviewmodelkt)
- [MusicDbViewModel.kt](#musicdbviewmodelkt)

---

## HomeScreen.kt

- Validasi URI musik  
  Sebelum:

  ```
  val retriever = MediaMetadataRetriever()
    try {
        if (song?.artworkUri == "Metadata") {
            retriever.setDataSource(context, Uri.parse(song.uri))
            val art = retriever.embeddedPicture
            if (art != null) {
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                imageBitmapState.value = bitmap.asImageBitmap()
  ```

  Sesudah:

  ```
  if (song.artworkUri.isNotEmpty()) {
      val retriever = MediaMetadataRetriever()
      try {
          val uri = Uri.parse(song.uri)
          if (!isValidUri(uri, context.contentResolver)) {
              return@LaunchedEffect
  ```

- Validasi URI foto album dan limit ukuran file foto album menjadi 5 MB  
  Sebelum:

  ```
  } else if (!song?.artworkUri.isNullOrEmpty()) {
      val fileBitmap = BitmapFactory.decodeFile(song?.artworkUri)
      if (fileBitmap != null) {
          imageBitmapState.value = fileBitmap.asImageBitmap()
  ```

  Sesudah:

  ```
  if (song.artworkUri == "Metadata") {
      retriever.setDataSource(context, uri)
      val art = retriever.embeddedPicture
      if (art != null && art.size <= 5 * 1024 * 1024) { // batas ukuran 5mb
          val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
          imageBitmapState.value = bitmap?.asImageBitmap()
      }
  } else {
      val file = File(song.artworkUri)
      if (file.exists() && isSafeFilePath(file.absolutePath) && file.length() <= 5 * 1024 * 1024) {
          val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
          imageBitmapState.value = fileBitmap?.asImageBitmap()
      }
  ```

## LibraryScreen.kt

- Validasi URI foto album dan limit ukuran file foto album menjadi 5 MB  
  Sebelum:

  ```
  val file = File(song.artworkUri)
  if (file.exists()) {
      val bitmap = BitmapFactory.decodeFile(file.absolutePath)
      imageBitmap = bitmap?.asImageBitmap()
  ```

  Sesudah:

  ```
  imageBitmap = null
    if (song.artworkUri.isNotEmpty() && isSafeFilePath(song.artworkUri)) {
        val file = File(song.artworkUri)
        if (file.exists() && file.length() <= 5 * 1024 * 1024) { // batas ukuran file 5mb
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                imageBitmap = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                Log.e("SongItem", "Error loading artwork", e)
            }
        }
  ```

## MusicScreen.kt

- Validasi URI foto album dan limit ukuran file foto album menjadi 5 MB  
  Sebelum:

  ```
  val retriever = MediaMetadataRetriever()
  try {
      if (song?.artworkUri == "Metadata") {
          retriever.setDataSource(context, Uri.parse(song.uri))
          val art = retriever.embeddedPicture
          if (art != null) {
              val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
              imageBitmapState.value = bitmap.asImageBitmap()
          }
      } else if (!song?.artworkUri.isNullOrEmpty()) {
          val fileBitmap = BitmapFactory.decodeFile(song?.artworkUri)
          if (fileBitmap != null) {
              imageBitmapState.value = fileBitmap.asImageBitmap()
  ```

  Sesudah:

  ```
  if (song?.artworkUri?.isNotEmpty() == true) {
      val retriever = MediaMetadataRetriever()
      try {
          if (song.artworkUri == "Metadata") {
              val uri = Uri.parse(song.uri)
              if (!isValidUri(uri, context.contentResolver)) {
                  return@LaunchedEffect
              }
              retriever.setDataSource(context, uri)
              val art = retriever.embeddedPicture
              if (art != null && art.size <= 5 * 1024 * 1024) {
                  val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size)
                  imageBitmapState.value = bitmap?.asImageBitmap()
              }
          } else {
              val file = File(song.artworkUri)
              if (file.exists() && isSafeFilePath(file.absolutePath) && file.length() <= 5 * 1024 * 1024) {
                  val fileBitmap = BitmapFactory.decodeFile(song.artworkUri)
                  imageBitmapState.value = fileBitmap?.asImageBitmap()
              }
  ```

## ProfileScreen.kt

- Memastikan id musik yang dimuat dari database tidak `null`  
  Sebelum:

  ```
  .filter { it.id !in timestampMap }
  ```

  Sesudah:

  ```
  .filter { it.id != null && it.id !in timestampMap }
  ```

- Memastikan agar aplikasi terkoneksi ke jaringan sebelum profil dimuat  
  Sebelum:

  ```
  viewModel.loadProfile()
  ```

  Sesudah:

  ```
  if (isConnected) {
      viewModel.loadProfile()
  }
  ```

- Sanitasi URL foto profil  
  Sebelum:

  ```
  val profilePhotoUrl = "$baseUrl/uploads/profile-picture/${profile.profilePhoto}"
  ```

  Sesudah:

  ```
  val sanitizedPhoto = sanitizeFileName(profile.profilePhoto)
  val profilePhotoUrl = "$baseUrl/uploads/profile-picture/$sanitizedPhoto"
  ```

- Sanitasi username, email, dan lokasi di profil  
  Sebelum:

  ```
  // USERNAME
  Text(
      text = profile.username,
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground
  )

  // EMAIL
  Text(
      text = profile.email,
      fontSize = 16.sp,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
  )

  // LOCATION
  Text(
      text = profile.location,
      fontSize = 16.sp,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
  )
  ```

  Sesudah:

  ```
    // USERNAME
  Text(
      text = sanitizeText(profile.username),
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.onBackground
  )

  // EMAIL
  Text(
      text = sanitizeText(profile.email),
      fontSize = 16.sp,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
  )

  // LOCATION
  Text(
      text = sanitizeText(profile.location),
      fontSize = 16.sp,
      color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
  )
  ```

## LoginViewModel.kt

- Validasi email dan password sebelum menyimpan

  ```
  if (!isValidEmail(email)) {
      _loginState.value = LoginState.Error("Invalid email format")
      return
  }
  if (password.length < 8) {
      _loginState.value = LoginState.Error("Password must be at least 8 characters")
      return
  }
  ```

- Sanitasi error message

  ```
      } else {
          val errorMessage = sanitizeText(
              result.exceptionOrNull()?.message ?: "Unknown error"
          )
          LoginState.Error(errorMessage)
      }
  } catch (e: Exception) {
      _loginState.value = LoginState.Error("Failed to connect to server")
  ```

## MusicBehaviorViewModel.kt

- Validasi URI musik

  ```
  val uri = Uri.parse(song.uri)
  if (!isValidUri(uri, context.contentResolver)) {
      Log.e("MusicBehavior", "Invalid URI: ${song.uri}")
      return
  }
  ```

- Validasi lagu sebelum ditambahkan ke queue

  ```
  if (isValidSong(song)) {
      _queue.add(song)
      Log.i("MusicBehavior", "Added to queue: ${song.title}, new queue size: ${_queue.size}")
  } else {
      Log.w("MusicBehavior", "Invalid song not added to queue: ${song.title}")
  }
  ```

## MusicDbViewModel.kt

- Sanitasi email sebelum mengambil lagu dari database  
  Sebelum:

  ```
  songDao.getSongsByUser(DataKeeper.email.toString()).map { entities ->
  ```

  Sesudah:

  ```
  songDao.getSongsByUser(sanitizeText(DataKeeper.email ?: "")).map { entities ->
  ```

- Sanitasi judul lagu dan nama artis lagu  
  Sebelum:

  ```
  title = entity.title,
  artist = entity.artist,
  ```

  Sesudah:

  ```
  title = sanitizeText(entity.title),
  artist = sanitizeText(entity.artist),
  ```
