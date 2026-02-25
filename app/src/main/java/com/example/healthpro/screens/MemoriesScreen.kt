package com.example.healthpro.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.healthpro.ui.theme.*

data class PhotoItem(val title: String, val subtitle: String, val color: Color)
data class AlbumItem(val title: String, val count: String, val icon: ImageVector, val color: Color)

@Composable
fun MemoriesScreen(navController: NavController) {
    val scrollState = rememberScrollState()

    val recentPhotos = listOf(
        PhotoItem("Sunday Park Visit", "2 days ago", Color(0xFF4A7C59)),
        PhotoItem("Birthday Party", "Last week", Color(0xFF7C6B4A)),
        PhotoItem("Garden Morning", "3 days ago", Color(0xFF4A6B7C))
    )

    val albums = listOf(
        AlbumItem("Grandkids", "10.4 Photos", Icons.Default.ChildCare, TealAccent),
        AlbumItem("Trips", "45 Photos", Icons.Default.Flight, BlueAccent),
        AlbumItem("Pets", "28 Photos", Icons.Default.Pets, FoodOrange),
        AlbumItem("Add New", "", Icons.Default.Add, TextMuted)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkNavy)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Memories",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Family",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Moments",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TealAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CardDark)
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Camera",
                    tint = TealAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Photos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Photos",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "View all",
                style = MaterialTheme.typography.labelMedium,
                color = HelpRed
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Recent photos row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recentPhotos.size) { index ->
                val photo = recentPhotos[index]
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(photo.color, photo.color.copy(alpha = 0.6f))
                                )
                            )
                    ) {
                        // Photo icon placeholder
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .size(48.dp)
                                .align(Alignment.Center)
                        )
                        // Labels at bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = photo.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = photo.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Family Albums
        Text(
            text = "Family Albums",
            style = MaterialTheme.typography.titleMedium,
            color = TextWhite,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Album grid (2x2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                albums.take(2).forEach { album ->
                    AlbumCard(
                        album = album,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                albums.drop(2).forEach { album ->
                    AlbumCard(
                        album = album,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AlbumCard(album: AlbumItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(album.color.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = album.icon,
                    contentDescription = album.title,
                    tint = album.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextWhite,
                    fontWeight = FontWeight.SemiBold
                )
                if (album.count.isNotEmpty()) {
                    Text(
                        text = album.count,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
