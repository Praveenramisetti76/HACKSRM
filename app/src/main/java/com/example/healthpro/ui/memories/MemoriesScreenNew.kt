package com.example.healthpro.ui.memories

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.healthpro.photos.PhotosManager
import com.example.healthpro.ui.theme.*

// ═══════════════════════════════════════════════════════════════
//  Premium color palette for Memories
// ═══════════════════════════════════════════════════════════════
private val MemoriesPurple = Color(0xFF8B5CF6)
private val MemoriesAmber = Color(0xFFF59E0B)
private val MemoriesPink = Color(0xFFEC4899)
private val MemoriesEmerald = Color(0xFF10B981)
private val MemoriesSky = Color(0xFF0EA5E9)

private val AlbumColors = listOf(
    MemoriesPurple, MemoriesAmber, MemoriesPink,
    MemoriesEmerald, MemoriesSky, TealAccent
)

// ═══════════════════════════════════════════════════════════════
//  Main Entry Point
// ═══════════════════════════════════════════════════════════════
@Composable
fun MemoriesScreenNew(navController: NavController) {
    val context = LocalContext.current
    val viewModel: MemoriesViewModel = viewModel()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    // Permission request
    val photosPermission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_IMAGES
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.hasPhotosPermission = granted
        if (granted) viewModel.loadPhotos()
    }

    when {
        viewModel.isSlideShowActive -> {
            val photos = viewModel.selectedAlbum?.photos ?: viewModel.allPhotos
            SlideShowScreen(
                photos = photos,
                currentIndex = viewModel.slideShowIndex,
                onNext = { viewModel.nextSlide(photos.size) },
                onPrev = { viewModel.prevSlide() },
                onClose = { viewModel.stopSlideShow() }
            )
        }
        viewModel.selectedAlbum != null -> {
            AlbumDetailScreen(
                album = viewModel.selectedAlbum!!,
                onBack = { viewModel.closeAlbum() },
                onPhotoClick = { idx ->
                    viewModel.startSlideShow(viewModel.selectedAlbum!!.photos, idx)
                }
            )
        }
        !viewModel.hasPhotosPermission -> {
            PhotosPermissionScreen(
                onRequestPermission = { permissionLauncher.launch(photosPermission) }
            )
        }
        else -> {
            MainMemoriesContent(
                viewModel = viewModel,
                navController = navController,
                onPhotoClick = { idx -> viewModel.startSlideShow(viewModel.allPhotos, idx) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Permission Screen
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PhotosPermissionScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MemoriesPurple.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = MemoriesPurple,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Access Your Memories",
                style = MaterialTheme.typography.headlineMedium,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Allow access to your photos so we can\norganize them by places you've visited",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MemoriesPurple)
            ) {
                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Allow Photo Access", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Main Content Screen
// ═══════════════════════════════════════════════════════════════
@Composable
private fun MainMemoriesContent(
    viewModel: MemoriesViewModel,
    navController: NavController,
    onPhotoClick: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ── Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "MEMORIES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MemoriesPurple,
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Your Places",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "& Moments",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MemoriesPurple,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(CardDark)
                    .clickable { viewModel.loadPhotos() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Refresh, "Refresh", tint = MemoriesPurple, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Loading State ──
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MemoriesPurple, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading your memories...", color = TextMuted, fontSize = 16.sp)
                }
            }
        }

        // ── Stats Bar ──
        if (!viewModel.isLoading && viewModel.allPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardDark)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("Photos", "${viewModel.allPhotos.size}", MemoriesPurple)
                StatItem("Albums", "${viewModel.albums.size}", MemoriesAmber)
                StatItem("Places", "${viewModel.albums.count { it.placeName != "Other Memories" }}", MemoriesEmerald)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // ── Recent Photos ──
        if (viewModel.recentPhotos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Photos", fontSize = 20.sp, color = TextWhite, fontWeight = FontWeight.Bold)
                Text(
                    "${viewModel.recentPhotos.size} photos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MemoriesPurple,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(viewModel.recentPhotos.take(15)) { photo ->
                    RecentPhotoCard(photo = photo, onClick = {
                        val idx = viewModel.allPhotos.indexOf(photo)
                        if (idx >= 0) onPhotoClick(idx)
                    })
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // ── Place Albums ──
        if (viewModel.albums.isNotEmpty()) {
            Text("Place Albums", fontSize = 20.sp, color = TextWhite, fontWeight = FontWeight.Bold)
            Text(
                "Automatically grouped by location",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(16.dp))

            viewModel.albums.forEachIndexed { index, album ->
                AlbumCard(
                    album = album,
                    color = AlbumColors[index % AlbumColors.size],
                    onClick = { viewModel.openAlbum(album) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── Empty State ──
        if (!viewModel.isLoading && viewModel.allPhotos.isEmpty()) {
            Spacer(modifier = Modifier.height(60.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.PhotoLibrary,
                    null,
                    tint = TextMuted.copy(alpha = 0.3f),
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("No photos found", fontSize = 22.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                Text("Take some photos to see your\nmemories organized here",
                    color = TextMuted.copy(alpha = 0.6f), textAlign = TextAlign.Center, fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ═══════════════════════════════════════════════════════════════
//  Stat Item
// ═══════════════════════════════════════════════════════════════
@Composable
private fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 26.sp, color = color, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 13.sp, color = TextMuted)
    }
}

// ═══════════════════════════════════════════════════════════════
//  Recent Photo Card
// ═══════════════════════════════════════════════════════════════
@Composable
private fun RecentPhotoCard(photo: PhotosManager.PhotoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(180.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            // Photo name
            Text(
                text = photo.displayName.substringBeforeLast('.'),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                fontSize = 12.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Album Card (Location-based)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AlbumCard(
    album: PhotosManager.PhotoAlbum,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover photo
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .fillMaxHeight()
            ) {
                if (album.coverPhotoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(album.coverPhotoUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = album.placeName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Image, null, tint = color, modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Album info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        tint = color,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        album.placeName,
                        fontSize = 18.sp,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "${album.photoCount} photos",
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }

            // Arrow
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = TextMuted,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(28.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  Album Detail Screen
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AlbumDetailScreen(
    album: PhotosManager.PhotoAlbum,
    onBack: () -> Unit,
    onPhotoClick: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    album.placeName,
                    fontSize = 22.sp,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${album.photoCount} photos",
                    fontSize = 14.sp,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Slideshow button
            IconButton(onClick = {
                if (album.photos.isNotEmpty()) onPhotoClick(0)
            }) {
                Icon(Icons.Default.Slideshow, "Slideshow", tint = MemoriesPurple, modifier = Modifier.size(28.dp))
            }
        }

        // Photo Grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 110.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(album.photos) { photo ->
                val idx = album.photos.indexOf(photo)
                PhotoGridItem(photo = photo, onClick = { onPhotoClick(idx) })
            }
        }
    }
}

@Composable
private fun PhotoGridItem(photo: PhotosManager.PhotoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  Fullscreen Slideshow
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SlideShowScreen(
    photos: List<PhotosManager.PhotoItem>,
    currentIndex: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit
) {
    if (photos.isEmpty()) {
        onClose()
        return
    }

    val photo = photos.getOrNull(currentIndex) ?: photos.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -50) onNext()
                    else if (dragAmount > 50) onPrev()
                }
            }
    ) {
        // Full photo
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text(
                "${currentIndex + 1} / ${photos.size}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Bottom info
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(20.dp)
        ) {
            Text(
                photo.displayName.substringBeforeLast('.'),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (photo.placeName != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = MemoriesPurple, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(photo.placeName!!, color = TextMuted, fontSize = 14.sp)
                }
            }
        }

        // Navigation arrows
        if (currentIndex > 0) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(8.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ChevronLeft, "Previous", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        if (currentIndex < photos.size - 1) {
            IconButton(
                onClick = onNext,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(8.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(Icons.Default.ChevronRight, "Next", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}
