package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.ChatMessage
import com.example.database.CommunityPost
import com.example.database.MeshDevice
import com.example.database.PostComment
import com.example.database.MeshSoundPlayer
import com.example.ui.theme.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MeshRootView(viewModel: MeshViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val callState by viewModel.callState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            "splash" -> SplashScreen(viewModel)
            "onboarding" -> OnboardingScreen(viewModel)
            "nearby", "chats", "calls", "people", "files", "groups", "community", "profile", "settings", "emergency" -> {
                MainLayoutScaffold(viewModel = viewModel, activeTab = currentScreen)
            }
            "personal_chat" -> {
                val activePeerMac by viewModel.selectedDeviceMac.collectAsStateWithLifecycle()
                PersonalChatScreen(viewModel = viewModel, peerMac = activePeerMac ?: "")
            }
        }

        // Overlay Call Screen
        if (callState != null) {
            CallOverlayScreen(viewModel = viewModel, session = callState!!)
        }
    }
}

@Composable
fun SplashScreen(viewModel: MeshViewModel) {
    val isSystemDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val backgroundBrush = if (isSystemDark) {
        Brush.verticalGradient(listOf(DarkSlateDeep, Color(0xFF07090F)))
    } else {
        Brush.verticalGradient(listOf(LightSlateBack, Color(0xFFE2E8F0)))
    }

    var startAnimation by remember { mutableStateOf(false) }
    val scaleIn by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.4f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    // Interactive draggable and attractive rotation states
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "logo_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isDragging) 2500 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val animatedOffsetX by animateFloatAsState(
        targetValue = if (isDragging) offsetX else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "offsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = if (isDragging) offsetY else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "offsetY"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        val activeCall = viewModel.repository.activeBackgroundCall
        if (activeCall != null) {
            viewModel.navigateTo("calls")
            return@LaunchedEffect
        }
        delay(3200) // Slightly longer to allow playing/dragging with the attractive logo!
        val prof = viewModel.repository.getProfile()
        if (prof != null && prof.isRegistered) {
            viewModel.navigateTo("nearby")
        } else {
            viewModel.navigateTo("onboarding")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scaleIn)
        ) {
            // Interactive glowing interconnect Mesh logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .offset(x = animatedOffsetX.dp, y = animatedOffsetY.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { 
                                isDragging = true 
                                offsetX = 0f
                                offsetY = 0f
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        )
                    }
                    .rotate(angle) // Continuous spinning effect for high-fidelity aesthetics
            ) {
                // Background radial glow effect
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(1.2f)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(ActionOrange.copy(alpha = 0.25f), Color.Transparent)
                            )
                        )
                )

                // Sub-nodes showing connection links (extremely professional aesthetic)
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .border(1.5.dp, ActionOrange.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Inner solid high-contrast corporate orange logo badge
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ActionOrange, Color(0xFFD84315))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "M",
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 48.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.sp
                            )
                        )
                    }
                }

                // Decorative satellites/mesh nodes
                Box(modifier = Modifier.size(8.dp).background(SignalGreen, CircleShape).align(Alignment.TopCenter).offset(y = (-15).dp))
                Box(modifier = Modifier.size(8.dp).background(SafeTeal, CircleShape).align(Alignment.BottomStart).offset(x = (-10).dp, y = (10).dp))
                Box(modifier = Modifier.size(8.dp).background(ActionOrange, CircleShape).align(Alignment.BottomEnd).offset(x = (10).dp, y = (10).dp))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "LinkMesh",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Serif
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "OFFLINE EMERGENCY NETWORK",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun LinkMeshLogo(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Canvas(modifier = Modifier.size(76.dp)) {
            val width = size.width
            val height = size.height
            
            // Vertices coordinates of the 'M' node network
            val p1 = Offset(width * 0.18f, height * 0.82f)
            val p2 = Offset(width * 0.18f, height * 0.25f)
            val p3 = Offset(width * 0.5f, height * 0.58f)
            val p4 = Offset(width * 0.82f, height * 0.25f)
            val p5 = Offset(width * 0.82f, height * 0.82f)
            
            val strokeWidth = 6f
            val linkColor = Color(0xFF00B0FF) // SafeTeal blue
            
            // Draw mesh link lines connecting nodes
            drawLine(linkColor, p1, p2, strokeWidth, alpha = 0.7f)
            drawLine(linkColor, p2, p3, strokeWidth, alpha = 0.7f)
            drawLine(linkColor, p3, p4, strokeWidth, alpha = 0.7f)
            drawLine(linkColor, p4, p5, strokeWidth, alpha = 0.7f)
            
            val nodeRadius = 16f
            val glowRadius = 28f
            val primaryBlue = Color(0xFF2962FF) // Deep brand blue
            
            // Draw Node glowing circles & center pins
            val points = listOf(p1, p2, p3, p4, p5)
            points.forEach { pt ->
                drawCircle(linkColor.copy(alpha = 0.2f), glowRadius, pt)
                drawCircle(primaryBlue, nodeRadius, pt)
                drawCircle(Color.White, nodeRadius * 0.4f, pt)
            }
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        Text(
            text = "LINKMESH",
            fontSize = 30.sp,
            fontWeight = FontWeight.Black,
            color = Color(0xFF2962FF),
            letterSpacing = 4.sp,
            fontFamily = FontFamily.SansSerif
        )
        
        Text(
            text = "Stay Connected. Anywhere.",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF5C6BC0),
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun OnboardingScreen(viewModel: MeshViewModel) {
    var currentStep by remember { mutableStateOf(0) } // 0 = Poster Welcome Screen, 1 = Set Profile Screen
    var usernameInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("English") }
    val languages = listOf("English", "Urdu", "Arabic")
    val profileData by viewModel.profile.collectAsStateWithLifecycle()

    var activeTabColor by remember { mutableStateOf(0xFF00B0FF.toInt()) }
    val availableColors = listOf(0xFF2962FF.toInt(), 0xFFF4511E.toInt(), 0xFF00B0FF.toInt(), 0xFF4CAF50.toInt(), 0xFF9C27B0.toInt())

    var customPhotoUri by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(profileData) {
        profileData?.let {
            if (usernameInput.isEmpty() && it.username.isNotEmpty()) {
                usernameInput = if (it.username == "nomi developer" || it.username == "Mesh User" || it.username == "Rescue-Node") "" else it.username
                selectedLanguage = it.appLanguage
                activeTabColor = it.avatarColor
                customPhotoUri = it.photoUri
            }
        }
    }

    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val backgroundColor = if (isDark) DarkSlateDeep else Color.White

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (currentStep == 0) {
            // STEP 0: Poster Welcome Screen (Matched "same to same" to the circled column)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Custom dynamic LINKMESH Header
                LinkMeshLogo(modifier = Modifier.padding(top = 16.dp))

                // Center Onboarding illustration card
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.img_welcome_illustration),
                        contentDescription = "LinkMesh Network Illustration",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) DarkSlateDeep else Color.White),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                }

                // Tagline & body text centered
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = "No Internet, No Limits.",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color.White else Color(0xFF1D192B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect with people nearby and chat, call or share files securely.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) TextSecondary else Color(0xFF5C6BC0),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Create Network Button (Premium Solid Blue)
                    Button(
                        onClick = {
                            val finalName = if (usernameInput.isBlank()) "User" else usernameInput
                            viewModel.updateProfile(finalName, selectedLanguage, false, activeTabColor, customPhotoUri)
                            viewModel.createMeshNetwork()
                            viewModel.navigateTo("nearby")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("create_network_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Network", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        }
                    }

                    // Join Network Button (Clean Outlined Blue)
                    OutlinedButton(
                        onClick = {
                            val finalName = if (usernameInput.isBlank()) "User" else usernameInput
                            viewModel.updateProfile(finalName, selectedLanguage, false, activeTabColor, customPhotoUri)
                            viewModel.joinMeshNetwork()
                            viewModel.navigateTo("nearby")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("join_network_button"),
                        border = BorderStroke(1.5.dp, Color(0xFF2962FF)),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF2962FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color(0xFF2962FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join Network", fontWeight = FontWeight.Bold, color = Color(0xFF2962FF), fontSize = 15.sp)
                        }
                    }

                    // Set Profile Button (Requested interactive addition)
                    TextButton(
                        onClick = { currentStep = 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("set_profile_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF2962FF),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (usernameInput.isNotEmpty()) "Edit Profile ($usernameInput)" else "Set Profile Name & Picture",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2962FF)
                            )
                        }
                    }
                }
            }
        } else {
            // STEP 1: Set Profile Screen (Interactive profile details editor)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Back Navigation Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentStep = 0 }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "Back to Welcome Screen",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { currentStep = 0 }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "APNA COMM-GRID PROFILE SET KAREIN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2962FF),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Set Up Your Profile",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Choose your username call-sign and add a profile picture so your team and nearby devices can discover your node directly.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Photo Picker Area
                    val context = LocalContext.current
                    val galleryLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent()
                    ) { uri ->
                        uri?.let {
                            try {
                                val contentResolver = context.contentResolver
                                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                contentResolver.takePersistableUriPermission(it, takeFlags)
                            } catch (e: Exception) {
                                // Ignore if permission binds are already established or not supported
                            }
                            customPhotoUri = it.toString()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(115.dp)
                            .clip(CircleShape)
                            .background(Color(activeTabColor).copy(alpha = 0.15f))
                            .border(2.5.dp, Color(activeTabColor), CircleShape)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (customPhotoUri != null) {
                            AsyncImage(
                                model = customPhotoUri,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "No photo selected",
                                tint = Color(activeTabColor),
                                modifier = Modifier.size(56.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(0xFF2962FF), CircleShape)
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Gallery",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "GALLERY SE PHOTO CHOOSE KAREIN",
                            fontSize = 11.sp,
                            color = Color(0xFF2962FF),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Theme Colors Row
                    Text(
                        text = "Choose Radar Node Color Accent",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        availableColors.forEach { colorVal ->
                            val isSelected = activeTabColor == colorVal
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorVal))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { activeTabColor = colorVal },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    // Username Text Input Field
                    OutlinedTextField(
                        value = usernameInput,
                        onValueChange = { if (it.length <= 18) usernameInput = it },
                        label = { Text("User Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    // Submit & Join Button
                    Button(
                        onClick = {
                            val finalName = if (usernameInput.isBlank()) "User" else usernameInput
                            viewModel.updateProfile(finalName, selectedLanguage, false, activeTabColor, customPhotoUri)
                            viewModel.navigateTo("nearby")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("submit_button"),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SAVE PROFILE & START COMM-MESH", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { currentStep = 0 },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Cancel & Go Back", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun MainLayoutScaffold(viewModel: MeshViewModel, activeTab: String) {
    val profileData by viewModel.profile.collectAsStateWithLifecycle()
    val appLanguage = profileData?.appLanguage ?: "English"

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MeshTopBar(viewModel, activeTab, appLanguage)
        },
        bottomBar = {
            MeshBottomBar(viewModel, activeTab, appLanguage)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (activeTab) {
                "nearby" -> DashboardScreen(viewModel)
                "chats" -> ChatsListScreen(viewModel)
                "calls" -> CallsHistoryScreen(viewModel)
                "people" -> PeopleNearbyScreen(viewModel)
                "files" -> SharedFilesScreen(viewModel)
                "groups" -> GroupsListScreen(viewModel)
                "community" -> CommunityFeedScreen(viewModel)
                "emergency" -> EmergencyScreen(viewModel)
                "profile" -> OnboardingScreen(viewModel) // Profile updates reuse this screen neatly
                "settings" -> SettingsScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshTopBar(viewModel: MeshViewModel, activeTab: String, language: String) {
    val titleText = when (activeTab) {
        "nearby" -> viewModel.getTranslation("nearby_tab", language)
        "chats" -> viewModel.getTranslation("chats_tab", language)
        "groups" -> viewModel.getTranslation("groups_tab", language)
        "community" -> viewModel.getTranslation("feed_tab", language)
        "emergency" -> viewModel.getTranslation("emergency_tab", language)
        "settings" -> viewModel.getTranslation("settings_tab", language)
        else -> "Profile"
    }

    val profileData by viewModel.profile.collectAsStateWithLifecycle()
    val isSOSActive = profileData?.rescueMode == true
    val devicesList by viewModel.devices.collectAsStateWithLifecycle()
    val connectedCount = if (devicesList.isEmpty()) 12 else devicesList.size
 
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val textColor = if (isSOSActive) Color.White else MaterialTheme.colorScheme.onBackground
    val menuIconTint = if (isSOSActive) Color.White else MaterialTheme.colorScheme.onBackground

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (isSOSActive) HighDensityEmergencyBg else MaterialTheme.colorScheme.background
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                var showMenu by remember { mutableStateOf(false) }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menu",
                            tint = menuIconTint
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = {
                                val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isDark) Icons.Default.WbSunny else Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isDark) "Light Mode" else "Dark Mode",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                viewModel.toggleTheme()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "My Profile",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                viewModel.navigateTo("profile")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Settings",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                viewModel.navigateTo("settings")
                                showMenu = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (activeTab == "nearby") "LinkMesh" else "LinkMesh ($titleText)",
                            style = androidx.compose.ui.text.TextStyle(
                                color = textColor,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            }
        },
        actions = {
            if (activeTab == "nearby") {
                // Pulse Radar active icon for beacon scanning status
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val rotateVal by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotate"
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Scanning Mesh Nodes",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(24.dp)
                        .rotate(rotateVal)
                )
            }
        }
    )
}

@Composable
fun MeshBottomBar(viewModel: MeshViewModel, activeTab: String, language: String) {
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val navContainerColor = if (isDark) HighDensityNavBg else MaterialTheme.colorScheme.surface
    val activeIndicatorColor = if (isDark) HighDensityPillBg else MaterialTheme.colorScheme.primaryContainer
    val activeIconColor = if (isDark) HighDensityOnPill else MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveColor = if (isDark) Color.White.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val selectedTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.primary

    NavigationBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
        containerColor = navContainerColor,
        tonalElevation = 4.dp
    ) {
        NavigationBarItem(
            selected = activeTab == "nearby",
            onClick = { viewModel.navigateTo("nearby") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = activeIconColor,
                selectedTextColor = selectedTextColor,
                indicatorColor = activeIndicatorColor,
                unselectedIconColor = inactiveColor,
                unselectedTextColor = inactiveColor
            ),
            icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
            label = { Text("Dashboard", overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 10.sp, fontWeight = if (activeTab == "nearby") FontWeight.Bold else FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = activeTab == "chats",
            onClick = { viewModel.navigateTo("chats") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = activeIconColor,
                selectedTextColor = selectedTextColor,
                indicatorColor = activeIndicatorColor,
                unselectedIconColor = inactiveColor,
                unselectedTextColor = inactiveColor
            ),
            icon = { Icon(Icons.Default.Chat, contentDescription = null) },
            label = { Text("Chats", overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 10.sp, fontWeight = if (activeTab == "chats") FontWeight.Bold else FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = activeTab == "calls",
            onClick = { viewModel.navigateTo("calls") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = activeIconColor,
                selectedTextColor = selectedTextColor,
                indicatorColor = activeIndicatorColor,
                unselectedIconColor = inactiveColor,
                unselectedTextColor = inactiveColor
            ),
            icon = { Icon(Icons.Default.Call, contentDescription = null) },
            label = { Text("Calls", overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 10.sp, fontWeight = if (activeTab == "calls") FontWeight.Bold else FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = activeTab == "groups",
            onClick = { viewModel.navigateTo("groups") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = activeIconColor,
                selectedTextColor = selectedTextColor,
                indicatorColor = activeIndicatorColor,
                unselectedIconColor = inactiveColor,
                unselectedTextColor = inactiveColor
            ),
            icon = { Icon(Icons.Default.Groups, contentDescription = null) },
            label = { Text("Groups", overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 10.sp, fontWeight = if (activeTab == "groups") FontWeight.Bold else FontWeight.Medium) }
        )
        NavigationBarItem(
            selected = activeTab == "files",
            onClick = { viewModel.navigateTo("files") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = activeIconColor,
                selectedTextColor = selectedTextColor,
                indicatorColor = activeIndicatorColor,
                unselectedIconColor = inactiveColor,
                unselectedTextColor = inactiveColor
            ),
            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
            label = { Text("Files", overflow = TextOverflow.Ellipsis, maxLines = 1, fontSize = 10.sp, fontWeight = if (activeTab == "files") FontWeight.Bold else FontWeight.Medium) }
        )
    }
}

// -------------------------------------------------------------
// SCREEN 1: NEARBY DEVICES & COMPRESSED TOPOLOGY CANVAS
// -------------------------------------------------------------
@Composable
fun NearbyNodesScreen(viewModel: MeshViewModel) {
    val deviceList by viewModel.devices.collectAsStateWithLifecycle()
    val logs by viewModel.systemAlertLog.collectAsStateWithLifecycle()
    val localProfile by viewModel.profile.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    var showTopologyMap by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Toggle Map Canvas view to save battery
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Offline Signal Radar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Visually traces active device linkages", fontSize = 12.sp, color = Color.Gray)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radar", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Switch(
                    checked = showTopologyMap,
                    onCheckedChange = { showTopologyMap = it },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        if (showTopologyMap) {
            // Interactive custom topology plotting
            NodeTopologyCanvas(deviceList, isDark, onNodeClicked = { mac ->
                viewModel.selectDirectChat(mac)
            })
        }

        // Discovered Devices List
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            item {
                // Mesh Link Control Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "OFFLINE CONNECTIVITY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set up your off-grid connection to link with nearby devices.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.createMeshNetwork() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ActionOrange)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Network", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            
                            Button(
                                onClick = { viewModel.joinMeshNetwork() },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Wifi,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Join Network", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            item {
                // Network Health Card (Design spec HTML)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkNavySurface),
                    border = BorderStroke(1.dp, HighDensityBorder)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "MESH STRENGTH",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextLightAccent,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Excellent",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ActionOrange
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF4F378B), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "V4.2 ACTIVE",
                                    color = Color(0xFFEADDFF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Metric bar chart simulation from HTML
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val barHeights = listOf(0.6f, 0.85f, 0.7f, 1.0f, 0.4f, 0.65f, 0.90f, 0.75f)
                            barHeights.forEach { heightMultiplier ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(heightMultiplier)
                                        .background(ActionOrange, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SignalGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Local Hop Latency: 14ms",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            item {
                // SOS Emergency Center active button from Design HTML
                val isSOSActiveLocal = localProfile?.rescueMode == true
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { viewModel.navigateTo("emergency") },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSOSActiveLocal) HighDensityEmergencyActive else HighDensityEmergencyBg
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "EMERGENCY CENTER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertTagSafety,
                                letterSpacing = 2.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (isSOSActiveLocal) "SOS ACTIVE" else "BROADCAST SOS",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "SOS Shortcut Trigger",
                                tint = HighDensityEmergencyBg,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "DISCOVERED PHYSICAL PEERS",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            items(deviceList) { device ->
                MeshDeviceItemCard(device = device, onToggle = { setOnline ->
                    viewModel.toggleDeviceRange(device.macAddress, setOnline)
                }, onChatClicked = {
                    viewModel.selectDirectChat(device.macAddress)
                })
            }
        }
    }
}

@Composable
fun NodeTopologyCanvas(devices: List<MeshDevice>, isDark: Boolean, onNodeClicked: (String) -> Unit) {
    val activePeers = devices.filter { it.isOnline }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
            .background(
                if (isDark) Color(0xFF111726) else Color(0xFFE6ECF5),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize().pointerInput(devices) {
            detectTapGestures { offset ->
                // Check if tapped near any node coordinates
                val centerX = size.width / 2f
                val centerY = size.height / 2f
                val radiusMax = size.height * 0.40f

                activePeers.forEachIndexed { index, peer ->
                    val angle = (index * 2 * Math.PI / activePeers.size)
                    val nodeDistanceFraction = 0.5f + (peer.signalStrength / 200f) // 0.5 to 1.0 based on signal strength
                    val nodeX = centerX + nodeDistanceFraction * radiusMax * cos(angle).toFloat()
                    val nodeY = centerY + nodeDistanceFraction * radiusMax * sin(angle).toFloat()

                    val distanceSquared = (offset.x - nodeX) * (offset.x - nodeX) + (offset.y - nodeY) * (offset.y - nodeY)
                    if (distanceSquared < 30 * 30 * 2) { // Tapped inside 30dp node area
                        onNodeClicked(peer.macAddress)
                    }
                }
            }
        }) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.height * 0.40f

            // 1. Draw range circles
            drawCircle(
                color = if (isDark) Color(0xFF1E283D) else Color(0xFFCBD5E1),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )
            drawCircle(
                color = if (isDark) Color(0xFF1E283D) else Color(0xFFCBD5E1),
                radius = radius * 0.5f,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )

            // 2. Draw active lines (Mesh routing links)
            activePeers.forEachIndexed { i, peer ->
                val angle = (i * 2 * Math.PI / activePeers.size)
                val peerDist = 0.5f + (peer.signalStrength / 200f)
                val px = cx + peerDist * radius * cos(angle).toFloat()
                val py = cy + peerDist * radius * sin(angle).toFloat()

                // Draw link from center (You) to nearby direct nodes
                drawLine(
                    color = if (peer.signalStrength > 70) ActionOrange.copy(alpha = 0.6f) else SafeTeal.copy(alpha = 0.4f),
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 2.dp.toPx()
                )

                // Inter-node secondary mesh links (simulation logic draws links between relays)
                if (peer.canRelay && i < activePeers.size - 1) {
                    val nextPeer = activePeers[i + 1]
                    val nextAngle = ((i + 1) * 2 * Math.PI / activePeers.size)
                    val nextPeerDist = 0.5f + (nextPeer.signalStrength / 200f)
                    val npx = cx + nextPeerDist * radius * cos(nextAngle).toFloat()
                    val npy = cy + nextPeerDist * radius * sin(nextAngle).toFloat()

                    drawLine(
                        color = Color(0xFF4CAF50).copy(alpha = 0.4f),
                        start = Offset(px, py),
                        end = Offset(npx, npy),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            // 3. Draw Center Node (User's Device)
            drawCircle(
                color = ActionOrange,
                radius = 12.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(cx, cy)
            )

            // 4. Draw Peer nodes
            activePeers.forEachIndexed { i, peer ->
                val angle = (i * 2 * Math.PI / activePeers.size)
                val peerDist = 0.5f + (peer.signalStrength / 200f)
                val px = cx + peerDist * radius * cos(angle).toFloat()
                val py = cy + peerDist * radius * sin(angle).toFloat()

                // Draw peer backing
                drawCircle(
                    color = if (peer.isSOSActive) AlertRed else SafeTeal,
                    radius = 10.dp.toPx(),
                    center = Offset(px, py)
                )

                // Draw central white dot
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        // Labels overlay
        Text(
            text = "YOU (RE-HUB)",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = ActionOrange,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 20.dp)
        )

        // Guide legends
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(SafeTeal, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Peer Node", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(AlertRed, CircleShape))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Distress SOS", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MeshDeviceItemCard(
    device: MeshDevice,
    onToggle: (Boolean) -> Unit,
    onChatClicked: () -> Unit
) {
    val leftBarColor = when {
        device.isSOSActive -> AlertRed
        device.canRelay -> ActionOrange
        else -> HighDensityBorder
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isSOSActive) AlertRed.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, HighDensityBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(leftBarColor)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Signal avatar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isOnline) SignalGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f)
                        )
                ) {
                    if (device.photoUri != null) {
                        AsyncImage(
                            model = device.photoUri,
                            contentDescription = "Device Profile Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (device.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (device.isOnline) SignalGreen else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            device.username,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (device.isSOSActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(AlertRed, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("SOS", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text("Mac: ${device.macAddress}", fontSize = 11.sp, color = Color.Gray)

                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("🔋 ${device.batteryLevel}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Text("🎚️ ${device.signalStrength}dBm", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        if (device.canRelay) {
                            Text("🔗 Multi-Hop Relay Node", fontSize = 10.sp, color = ActionOrange, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Connection Toggle Switch and Quick Actions
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (device.isOnline) "ONLINE" else "OUT OF RANGE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (device.isOnline) SignalGreen else Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = device.isOnline,
                            onCheckedChange = { onToggle(it) },
                            modifier = Modifier.scale(0.7f).testTag("device_toggle_${device.macAddress}")
                        )
                    }

                    if (device.isOnline) {
                        IconButton(
                            onClick = onChatClicked,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("chat_device_${device.macAddress}")
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Chat with node",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 2: PERSONAL CHATS THREAD LIST
// -------------------------------------------------------------
@Composable
fun ChatsListScreen(viewModel: MeshViewModel) {
    val convs by viewModel.conversations.collectAsStateWithLifecycle()
    val activeDevices by viewModel.devices.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (convs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Active Broadcast Private Key Sockets", fontSize = 14.sp)
                    Text("Select a node from direct range cards to spark direct encrypted mesh packet chats.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(convs) { msg ->
                    // Find matched node profile
                    val isIncoming = msg.isIncoming
                    val displayMac = if (isIncoming) msg.senderId else msg.receiverId
                    val matchedPeer = activeDevices.find { it.macAddress == displayMac }
                    val displayName = matchedPeer?.username ?: if (isIncoming) msg.senderName else "Offline Friend"
                    val isOnline = matchedPeer?.isOnline == true

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clickable { viewModel.selectDirectChat(displayMac) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val (avatarColor, avatarContent) = remember(displayName) {
                                val cleanName = displayName.lowercase()
                                when {
                                    cleanName.contains("rescue") || cleanName.contains("captain") || cleanName.contains("farhan") -> {
                                        Pair(ActionOrange, "🧑‍🚒")
                                    }
                                    cleanName.contains("paramedic") || cleanName.contains("doctor") || cleanName.contains("tariq") || cleanName.contains("sarah") -> {
                                        Pair(SafeTeal, "🩺")
                                    }
                                    cleanName.contains("volunteer") || cleanName.contains("amna") || cleanName.contains("malik") -> {
                                        Pair(Color(0xFF4CAF50), "🤝")
                                    }
                                    cleanName.contains("dev") || cleanName.contains("nouman") || cleanName.contains("choudry") -> {
                                        Pair(Color(0xFF9C27B0), "🧑‍💻")
                                    }
                                    else -> {
                                        val colors = listOf(ActionOrange, SafeTeal, Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00B0FF), Color(0xFFE91E63))
                                        val colorIndex = Math.abs(displayName.hashCode()) % colors.size
                                        val firstChar = if (displayName.isNotEmpty()) displayName.take(1).uppercase() else "👤"
                                        Pair(colors[colorIndex], firstChar)
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(avatarColor.copy(alpha = 0.25f))
                                    .border(1.5.dp, avatarColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (matchedPeer?.photoUri != null) {
                                    AsyncImage(
                                        model = matchedPeer.photoUri,
                                        contentDescription = "Profile Photo",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Text(avatarContent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(
                                    msg.textContent, 
                                    fontSize = 12.sp, 
                                    maxLines = 1, 
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            // Tick delivery indicators
                            Icon(
                                imageVector = when (msg.status) {
                                    "READ" -> Icons.Default.CheckCircle
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = if (msg.status == "READ") SignalGreen else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun getFileNameAndSize(context: android.content.Context, uri: android.net.Uri): Pair<String, String> {
    var name = "attachment_image.jpg"
    var sizeStr = "Unknown Size"
    try {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    val bytes = it.getLong(sizeIndex)
                    sizeStr = if (bytes > 1024 * 1024) {
                        String.format("%.1f MB", bytes.toDouble() / (1024 * 1024))
                    } else {
                        String.format("%d KB", bytes / 1024)
                    }
                }
            }
        }
    } catch (e: Exception) {
        // Fallback
    }
    if (name.isEmpty() || name == "Unknown") {
        name = uri.lastPathSegment ?: "file_attachment"
    }
    return Pair(name, sizeStr)
}

fun saveSelectedFileToInternalStorage(context: android.content.Context, uri: android.net.Uri, defaultName: String): java.io.File? {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val outputDir = java.io.File(context.filesDir, "attachments")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val file = java.io.File(outputDir, defaultName)
        java.io.FileOutputStream(file).use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()
        return file
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

// -------------------------------------------------------------
// SCREEN 3: HIGH-FIDELITY ACTIVE CHAT INTERFACE
// -------------------------------------------------------------
@Composable
fun PersonalChatScreen(viewModel: MeshViewModel, peerMac: String) {
    val context = LocalContext.current
    val activeChatCompanion = viewModel.devices.value.find { it.macAddress == peerMac }
    val companionName = activeChatCompanion?.username ?: "Mesh Relay Link"
    val isOnline = activeChatCompanion?.isOnline == true

    val messages by viewModel.activeDirectChat.collectAsStateWithLifecycle()
    val isRecordingVoice by viewModel.isRecordingVoice.collectAsStateWithLifecycle()
    val recordingDuration by viewModel.voiceRecordingDuration.collectAsStateWithLifecycle()

    var textInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isSirenPlaying by remember { mutableStateOf(false) }

    val fileTransferJob by viewModel.currentFileJobState.collectAsStateWithLifecycle()

    var showAttachmentsSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            MeshSoundPlayer.stopSound()
        }
    }

    val friendPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {}
            viewModel.updateDevicePhoto(peerMac, it.toString())
        }
    }

    val galleryImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val (name, size) = getFileNameAndSize(context, it)
                val savedFile = saveSelectedFileToInternalStorage(context, it, name)
                if (savedFile != null) {
                    viewModel.shareMeshFile(name, size, "IMAGE", savedFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val anyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val (name, size) = getFileNameAndSize(context, it)
                val savedFile = saveSelectedFileToInternalStorage(context, it, name)
                if (savedFile != null) {
                    viewModel.shareMeshFile(name, size, "FILE", savedFile.absolutePath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecordingVoice()
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required to record voice notes", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Scroll to latest message on receive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedMessage != null) {
                // WhatsApp-style message selection header (4th image)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedMessage = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "1 Message Selected",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(onClick = {
                        selectedMessage?.let {
                            viewModel.deleteMessage(it)
                            selectedMessage = null
                            android.widget.Toast.makeText(context, "Message Deleted", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Message", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            } else {
                // Standard Chat Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp)
                        .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.selectDirectChat(null) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Return")
                    }

                    val (avatarColor, avatarContent) = remember(companionName) {
                        val cleanName = companionName.lowercase()
                        when {
                            cleanName.contains("rescue") || cleanName.contains("captain") || cleanName.contains("farhan") -> {
                                Pair(ActionOrange, "🧑‍🚒")
                            }
                            cleanName.contains("paramedic") || cleanName.contains("doctor") || cleanName.contains("tariq") || cleanName.contains("sarah") -> {
                                Pair(SafeTeal, "🩺")
                            }
                            cleanName.contains("volunteer") || cleanName.contains("amna") || cleanName.contains("malik") -> {
                                Pair(Color(0xFF4CAF50), "🤝")
                            }
                            cleanName.contains("dev") || cleanName.contains("nouman") || cleanName.contains("choudry") -> {
                                Pair(Color(0xFF9C27B0), "🧑‍💻")
                            }
                            else -> {
                                val colors = listOf(ActionOrange, SafeTeal, Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00B0FF), Color(0xFFE91E63))
                                val colorIndex = Math.abs(companionName.hashCode()) % colors.size
                                val firstChar = if (companionName.isNotEmpty()) companionName.take(1).uppercase() else "👤"
                                Pair(colors[colorIndex], firstChar)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.25f))
                            .border(1.5.dp, avatarColor, CircleShape)
                            .clickable { friendPhotoLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeChatCompanion?.photoUri != null) {
                            AsyncImage(
                                model = activeChatCompanion.photoUri,
                                contentDescription = "Friend Profile Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Text(avatarContent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = companionName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isOnline) "ACTIVE P2P LINK" else "RELAY ACCESS ONLY / DISCONNECTED",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) SignalGreen else Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Voice / Video Caller simulation links
                    if (isOnline) {
                        IconButton(onClick = { viewModel.startCall(peerMac, video = false) }) {
                            Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.startCall(peerMac, video = true) }) {
                            Icon(Icons.Default.VideoCall, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }

                    // Three-dot options menu for Search, Siren, Block, and Clear Chat
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search chat") },
                                onClick = {
                                    isSearchActive = !isSearchActive
                                    showMenu = false
                                }
                            )
                            if (isOnline) {
                                DropdownMenuItem(
                                    text = { Text(if (isSirenPlaying) "Stop Siren Alert" else "Trigger Siren Alert") },
                                    onClick = {
                                        if (isSirenPlaying) {
                                            viewModel.sendSirenSignal("[STOP_SIREN]")
                                            isSirenPlaying = false
                                            android.widget.Toast.makeText(context, "Stopped Siren on companion device", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.sendSirenSignal("[SIREN_ALERT]")
                                            isSirenPlaying = true
                                            android.widget.Toast.makeText(context, "🚨 Triggered Siren Alert on companion device!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        showMenu = false
                                    }
                                )
                            }
                            val isBlocked = viewModel.isPeerBlocked(peerMac)
                            DropdownMenuItem(
                                text = { Text(if (isBlocked) "Unblock" else "Block") },
                                onClick = {
                                    viewModel.toggleBlockPeer(peerMac)
                                    showMenu = false
                                    android.widget.Toast.makeText(context, if (isBlocked) "User Unblocked" else "User Blocked", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear chat") },
                                onClick = {
                                    viewModel.clearChat(peerMac)
                                    showMenu = false
                                    android.widget.Toast.makeText(context, "Chat history cleared", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            // Search Header if active
            if (isSearchActive) {
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = { Text("Search messages index...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        IconButton(onClick = {
                            searchInput = ""
                            isSearchActive = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null)
                        }
                    }
                )
            }

            // Main Message Feed
            val filteredMessages = messages.filter {
                searchInput.isEmpty() || it.textContent.contains(searchInput, ignoreCase = true)
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp)
            ) {
                items(filteredMessages) { msg ->
                    BubbleChatMessage(
                        msg = msg, 
                        viewModel = viewModel,
                        onLongClick = { selectedMessage = msg }
                    )
                }
            }

            // Active transmission blocks (Progress bar for Wi-Fi direct document shares!)
            if (fileTransferJob != null && fileTransferJob!!.peerMac == peerMac) {
                val state = fileTransferJob!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SolidCardDark),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Attachment, contentDescription = null, tint = SafeTeal)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(state.fileName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Text(
                                "COMPACTING PACKETS: ${state.progress}%",
                                fontSize = 10.sp,
                                color = if (state.status == "INTERRUPTED") AlertRed else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { state.progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .padding(vertical = 4.dp),
                            color = if (state.status == "INTERRUPTED") AlertRed else ActionOrange
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (state.status == "SENDING") {
                                Button(
                                    onClick = { viewModel.interruptFileShare() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("PAUSE LINK", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (state.status == "INTERRUPTED") {
                                Button(
                                    onClick = { viewModel.resumeFileShare() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(4.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("RESUME MESH", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Bottom compose input bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showAttachmentsSheet = true }) {
                        Icon(Icons.Default.Attachment, contentDescription = "Options attachment")
                    }

                    // Input Field Text
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Private mesh envelope...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        trailingIcon = {
                            if (isRecordingVoice) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(AlertRed, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("${recordingDuration}s", color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    if (textInput.isNotBlank()) {
                        IconButton(onClick = {
                            viewModel.sendDirectText(textInput)
                            textInput = ""
                        }, modifier = Modifier.testTag("send_button")) {
                            Icon(Icons.Default.Send, contentDescription = "Send offline packet", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // Mic voice simulation trigger buttons
                        IconButton(
                            onClick = {
                                if (isRecordingVoice) {
                                    viewModel.stopRecordingVoice()
                                } else {
                                    val hasMicPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasMicPermission) {
                                        viewModel.startRecordingVoice()
                                    } else {
                                        micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(if (isRecordingVoice) AlertRed else MaterialTheme.colorScheme.secondary, CircleShape)
                                .size(40.dp)
                                .testTag("voice_button")
                        ) {
                            Icon(
                                imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = "Voice note creator",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Attachments Custom Sheet Modal
        if (showAttachmentsSheet) {
            AlertDialog(
                onDismissRequest = { showAttachmentsSheet = false },
                title = { Text("Select Attachment", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Choose an image from gallery or a document file from your device to send securely over the offline mesh network.", fontSize = 12.sp, color = Color.Gray)

                        Button(
                            onClick = {
                                galleryImageLauncher.launch("image/*")
                                showAttachmentsSheet = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Image from Gallery")
                            }
                        }

                        Button(
                            onClick = {
                                anyFileLauncher.launch("*/*")
                                showAttachmentsSheet = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Attachment, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Document / Archive")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAttachmentsSheet = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun BubbleChatMessage(
    msg: ChatMessage, 
    viewModel: MeshViewModel,
    onLongClick: () -> Unit = {}
) {
    val isSender = !msg.isIncoming
    val bubbleColor = if (isSender) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isSender) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    // Fetch own profile information for fallback/custom photo
    val myProfile by viewModel.profile.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val matchedPeer = remember(devices, msg.senderId) { devices.find { it.macAddress == msg.senderId } }

    val (avatarColor, avatarContent) = remember(isSender, msg.senderName, msg.senderId, myProfile) {
        if (isSender) {
            val myColor = myProfile?.avatarColor ?: 0xFFF4511E.toInt()
            val myEmoji = when (myColor) {
                0xFFF4511E.toInt() -> "🧑‍🚒"
                0xFF00B0FF.toInt() -> "🩺"
                0xFF4CAF50.toInt() -> "🤝"
                0xFF9C27B0.toInt() -> "🧑‍💻"
                else -> "👤"
            }
            Pair(Color(myColor), myEmoji)
        } else {
            val cleanName = msg.senderName.lowercase()
            when {
                cleanName.contains("rescue") || cleanName.contains("captain") || cleanName.contains("farhan") -> {
                    Pair(ActionOrange, "🧑‍🚒")
                }
                cleanName.contains("paramedic") || cleanName.contains("doctor") || cleanName.contains("tariq") || cleanName.contains("sarah") -> {
                    Pair(SafeTeal, "🩺")
                }
                cleanName.contains("volunteer") || cleanName.contains("amna") || cleanName.contains("malik") -> {
                    Pair(Color(0xFF4CAF50), "🤝")
                }
                cleanName.contains("dev") || cleanName.contains("nouman") || cleanName.contains("choudry") -> {
                    Pair(Color(0xFF9C27B0), "🧑‍💻")
                }
                else -> {
                    val colors = listOf(ActionOrange, SafeTeal, Color(0xFF4CAF50), Color(0xFF9C27B0), Color(0xFF00B0FF), Color(0xFFE91E63))
                    val colorIndex = Math.abs(msg.senderName.hashCode()) % colors.size
                    val firstChar = if (msg.senderName.isNotEmpty()) msg.senderName.take(1).uppercase() else "👤"
                    Pair(colors[colorIndex], firstChar)
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isSender) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isSender) {
            // Profile photo avatar on the left for incoming messages
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.25f))
                    .border(1.5.dp, avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (matchedPeer?.photoUri != null) {
                    AsyncImage(
                        model = matchedPeer.photoUri,
                        contentDescription = "Peer Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(avatarContent, fontSize = 14.sp)
                }
            }
        }

        Column(
            horizontalAlignment = if (isSender) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isSender) 16.dp else 0.dp,
                            bottomEnd = if (isSender) 0.dp else 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .pointerInput(msg.messageId) {
                        detectTapGestures(
                            onLongPress = {
                                onLongClick()
                            }
                        )
                    }
                    .padding(12.dp)
                    .widthIn(max = 240.dp)
            ) {
                Column {
                    if (msg.isGroup && !isSender) {
                        Text(
                            text = msg.senderName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SafeTeal,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    if (msg.attachmentType != "NONE") {
                        AttachmentPreviewItem(
                            type = msg.attachmentType,
                            path = msg.attachmentPath ?: "",
                            size = msg.attachmentSize ?: "",
                            durationSec = if (msg.voiceDurationSec > 0) msg.voiceDurationSec else 5,
                            isSender = isSender
                        )
                    }

                    val displayText = when (msg.textContent) {
                        "[SIREN_ALERT]" -> "🚨 Siren Alarm Triggered!"
                        "[STOP_SIREN]" -> "🛑 Siren Stopped"
                        else -> msg.textContent
                    }
                    Text(
                        text = displayText,
                        color = textColor,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${msg.hopsList}  |  " + java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(msg.timestamp),
                    fontSize = 8.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
                if (isSender) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = when (msg.status) {
                            "READ" -> Icons.Default.CheckCircle
                            else -> Icons.Default.Check
                        },
                        contentDescription = null,
                        tint = if (msg.status == "READ") SignalGreen else Color.Gray,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        if (isSender) {
            // Profile photo avatar on the right for outgoing messages
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, start = 8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(avatarColor.copy(alpha = 0.25f))
                    .border(1.5.dp, avatarColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (myProfile?.photoUri != null) {
                    AsyncImage(
                        model = myProfile?.photoUri,
                        contentDescription = "My Avatar",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(avatarContent, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun AttachmentPreviewItem(type: String, path: String, size: String, durationSec: Int = 5, isSender: Boolean = false) {
    var isPlaying by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Audio tape playback loop
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            MeshSoundPlayer.startVoicePlayback(path, durationSec, context)
            kotlinx.coroutines.delay(durationSec * 1000L)
            isPlaying = false
        } else {
            MeshSoundPlayer.stopSound()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isPlaying) {
                MeshSoundPlayer.stopSound()
            }
        }
    }

    // Dynamic contrast colors for light/dark mode and sender/receiver bubbles
    val dynamicTextColor = if (isSender) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val cardBgColor = if (isSender) Color.Black.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)

    if (type == "IMAGE") {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column {
                AsyncImage(
                    model = path,
                    contentDescription = "Shared Image Attachment",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = ActionOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = path.substringAfterLast('/'),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = size,
                        fontSize = 8.sp,
                        color = if (isSender) Color.White.copy(alpha = 0.7f) else Color.Gray
                    )
                }
            }
        }
        return
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clickable {
                if (type == "AUDIO") {
                    isPlaying = !isPlaying
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else cardBgColor
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (type) {
                    "AUDIO" -> if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Mic
                    else -> Icons.Default.Attachment
                },
                contentDescription = null,
                tint = if (isPlaying) SignalGreen else ActionOrange,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (type == "AUDIO") {
                        if (isPlaying) "🔊 Awaaz Chal Rahi Hai... Tap to Stop." else "🎤 Audio Message. Tap to Listen."
                    } else {
                        path.substringAfterLast('/')
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = dynamicTextColor
                )
                Text(
                    text = if (type == "AUDIO") "$durationSec Sec  |  $size" else size,
                    fontSize = 8.sp,
                    color = if (isSender) Color.White.copy(alpha = 0.7f) else Color.Gray
                )
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 4: DISASTER PUBLIC EMERGENCY GROUPS CHAT
// -------------------------------------------------------------
@Composable
fun GroupChatEmbedScreen(viewModel: MeshViewModel) {
    val messages by viewModel.activeGroupChat.collectAsStateWithLifecycle()
    var inputStr by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                .padding(12.dp)
        ) {
            Column {
                Text("Emergency Group: COMMUNITY_GROUP_M_01", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                Text("Public localized rescue band. All messages are gossiped over mesh to everyone. Use only for triage updates.", fontSize = 11.sp, color = Color.Gray)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp)
        ) {
            items(messages) { msg ->
                BubbleChatMessage(msg = msg, viewModel = viewModel)
            }
        }

        Surface(tonalElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputStr,
                    onValueChange = { inputStr = it },
                    placeholder = { Text("Broadcast group message...") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    viewModel.sendGroupText(inputStr)
                    inputStr = ""
                }) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 5: COMMUNITY BROADCAST FEED & COMMENT MODULE
// -------------------------------------------------------------
@Composable
fun CommunityFeedScreen(viewModel: MeshViewModel) {
    val posts by viewModel.communityPosts.collectAsStateWithLifecycle()
    val comments by viewModel.postComments.collectAsStateWithLifecycle()
    val openPostCommentsId by viewModel.selectedCommentPostId.collectAsStateWithLifecycle()

    var postInputText by remember { mutableStateOf("") }
    var locationInputText by remember { mutableStateOf("Disaster Sector 1") }
    var selectedType by remember { mutableStateOf("ALERT") }
    val types = listOf("ALERT", "MARKETPLACE", "ANNOUNCEMENT")

    if (openPostCommentsId != null) {
        // Detailed Comment dialog modal
        AlertDialog(
            onDismissRequest = { viewModel.selectPostComments(null) },
            title = { Text("Announcements Thread Comments", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(comments) { comment ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(comment.commenterName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ActionOrange)
                                Text(comment.commentContent, fontSize = 13.sp)
                                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }

                    var newCommentInput by remember { mutableStateOf("") }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = newCommentInput,
                            onValueChange = { newCommentInput = it },
                            placeholder = { Text("Add comment...") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        IconButton(onClick = {
                            viewModel.addCommentToPost(openPostCommentsId!!, newCommentInput)
                            newCommentInput = ""
                        }) {
                            Icon(Icons.Default.Send, contentDescription = null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.selectPostComments(null) }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Stop Alert sound bar / header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Community Broadcasts", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            TextButton(
                onClick = { com.example.database.MeshSoundPlayer.stopSound() },
                colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
            ) {
                Icon(Icons.Default.VolumeOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("SIREN BAND KAREIN / STOP SIREN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Create Announcement Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Broadcast Local Alert", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                
                OutlinedTextField(
                    value = postInputText,
                    onValueChange = { postInputText = it },
                    placeholder = { Text("What information is offline critical? Roads blocked, supplies, medical camps, water levels...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    types.forEach { t ->
                        val active = selectedType == t
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedType = t }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(t, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else Color.Gray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = locationInputText,
                        onValueChange = { locationInputText = it },
                        modifier = Modifier.width(150.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp),
                        shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Location") }
                    )

                    Button(
                        onClick = {
                            viewModel.addCommunityBroad(postInputText, selectedType, locationInputText)
                            postInputText = ""
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("POST BROAD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Posts List
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(posts) { post ->
                val borderRibbonColor = when (post.postType) {
                    "ALERT" -> AlertTagSafety
                    "MARKETPLACE" -> AlertTagUtility
                    else -> AlertTagGeneral
                }

                val isMissingPerson = post.messageContent.contains("MISSING")
                val cardBgColor = if (isMissingPerson) Color(0xFF0F172A) else MaterialTheme.colorScheme.surfaceVariant
                val cardBorderColor = if (isMissingPerson) AlertRed else HighDensityBorder
                val cardBorderWidth = if (isMissingPerson) 2.dp else 1.dp

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBgColor
                    ),
                    border = BorderStroke(cardBorderWidth, cardBorderColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    // Header Bar for Missing Person alerts
                    if (isMissingPerson) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(AlertRed)
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🚨 GUMSHUADA INSAAN / MISSING PERSON POSTER 🚨",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .background(if (isMissingPerson) AlertRed else borderRibbonColor)
                        )

                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(24.dp).background(ActionOrange, CircleShape), contentAlignment = Alignment.Center) {
                                        Text(post.authorUsername.take(1).uppercase(), color = HighDensityOnPill, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(post.authorUsername, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isMissingPerson) Color.White else Color.White)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(if (isMissingPerson) AlertRed.copy(alpha = 0.2f) else borderRibbonColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .border(1.dp, if (isMissingPerson) AlertRed else borderRibbonColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isMissingPerson) "MISSING poster" else post.postType,
                                        color = if (isMissingPerson) AlertRed else borderRibbonColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (isMissingPerson) {
                                // Extraction regex parsing to avoid low contrast plain text
                                val nameMatch = Regex("Help locate: (.*?)(?=\\()").find(post.messageContent)?.value?.replace("Help locate: ", "")?.trim() ?: ""
                                val ageMatch = Regex("\\((.*?)\\s*years").find(post.messageContent)?.groupValues?.getOrNull(1)?.trim() ?: ""
                                val lastSeenMatch = Regex("Last seen near (.*?)(?=wearing)").find(post.messageContent)?.value?.replace("Last seen near ", "")?.trim() ?: ""
                                val clothingMatch = Regex("wearing (.*?)(?=\\.)").find(post.messageContent)?.value?.replace("wearing ", "")?.trim() ?: ""

                                if (nameMatch.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                            .border(1.dp, AlertRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "ALERT SYNTAX / POSTER DETAILED SPECIFICATION:",
                                            color = ActionOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Name Label with Neon contrasts
                                        Row {
                                            Text("NAME / نام: ", color = ActionOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(nameMatch, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row {
                                            Text("AGE / عمر: ", color = ActionOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("$ageMatch Years Old", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row {
                                            Text("LAST SEEN / اخری مقام: ", color = ActionOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(lastSeenMatch, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row {
                                            Text("CLOTHING / لباس: ", color = ActionOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(clothingMatch, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Note: If seen or spotted, broadcast details using the nearby chat tab immediately. Offline synced database is active.",
                                            color = SafeTeal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 13.sp
                                        )
                                    }
                                } else {
                                    // High visibility fallback format
                                    Text(
                                        text = post.messageContent,
                                        fontSize = 13.sp,
                                        color = Color.Yellow,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    text = post.messageContent,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📍 ${post.locationName} | 🔋 ${post.batteryLevel}%",
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.upvotePostFlow(post) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (post.hasUpvoted) ActionOrange else Color.Gray.copy(alpha = 0.1f),
                                            contentColor = if (post.hasUpvoted) HighDensityOnPill else Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("👍 ${post.upvotesCount}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            viewModel.selectPostComments(post.postId)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.Gray.copy(alpha = 0.1f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("💬 Comments", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SCREEN 6: EMERGENCY CENTER (SOS TRIGGER & MISSING POSTERS)
// -------------------------------------------------------------
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EmergencyScreen(viewModel: MeshViewModel) {
    val prof by viewModel.profile.collectAsStateWithLifecycle()
    val isSOSActive = prof?.rescueMode == true

    var showMissingPersonModal by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "EMERGENCY CENTER BUTTON",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = AlertRed,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Glowing pulsating alarm canvas
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(220.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scalePulse by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isSOSActive) 1.5f else 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = EaseInOutBack),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scalePulse)
                    .background(
                        if (isSOSActive) AlertRed.copy(alpha = 0.25f) else ActionOrange.copy(alpha = 0.15f),
                        CircleShape
                    )
            )

            Button(
                onClick = {
                    if (isSOSActive) {
                        viewModel.turnOffSOS()
                    } else {
                        // Islamabad central coordinate simulation
                        viewModel.triggerSOSAlert(33.6844, 73.0479)
                    }
                },
                modifier = Modifier
                    .size(130.dp)
                    .testTag("sos_panic_button"),
                colors = ButtonDefaults.buttonColors(containerColor = if (isSOSActive) AlertRed else ActionOrange),
                shape = CircleShape
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(32.dp), tint = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (isSOSActive) "DEACTIVATE SOS" else "SOS\nPANIC",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Stop Siren Button as requested specifically
        Button(
            onClick = {
                viewModel.turnOffSOS()
                com.example.database.MeshSoundPlayer.stopSound()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 24.dp)
                .testTag("stop_siren_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = if (isSOSActive) AlertRed else Color.Gray.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeOff, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIREN BAND KAREIN / STOP SIREN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSOSActive) "Distress mesh signal is broadcasting. Click the button above to stop the siren instantly!" 
            else "Tapping the SOS circle will set off a loud alert siren and notify all connected nearby users.",
            fontSize = 12.sp,
            color = if (isSOSActive) AlertRed else Color.Gray,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // Missing Person Alerts Generator
        Button(
            onClick = { showMissingPersonModal = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Missing Person Alert Poster", fontWeight = FontWeight.Bold)
            }
        }

        // Show Missing Person Modal
        if (showMissingPersonModal) {
            var name by remember { mutableStateOf("") }
            var age by remember { mutableStateOf("") }
            var lastSeen by remember { mutableStateOf("") }
            var clothing by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showMissingPersonModal = false },
                title = { Text("Compile Missing Person Poster", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Poster gets distributed automatic through all client storage devices on nearby connections.", fontSize = 11.sp, color = Color.Gray)
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Person Full Name") })
                        OutlinedTextField(value = age, onValueChange = { age = it }, label = { Text("Approximate Age") })
                        OutlinedTextField(value = lastSeen, onValueChange = { lastSeen = it }, label = { Text("Last Seen Grid Coordinate") })
                        OutlinedTextField(value = clothing, onValueChange = { clothing = it }, label = { Text("Clothing / Distinct Details") })
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (name.isNotBlank()) {
                            viewModel.postMissingPersonAlert(name, age, lastSeen, clothing)
                            showMissingPersonModal = false
                        }
                    }) {
                        Text("Broadcast Alert")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMissingPersonModal = false }) {
                        Text("Dismiss")
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// SCREEN 7: LOW-LATENCY CALL OVERLAY SIMULATOR
// -------------------------------------------------------------
@Composable
fun CallOverlayScreen(viewModel: MeshViewModel, session: CallSession) {
    val isSystemDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val isRinging = session.status == "RINGING" || session.status == "DIALING"
    var isLoudspeakerActive by remember { mutableStateOf(false) }
    var showAddPeopleDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Dynamic runtime calling permissions request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            )
        )
    }

    // Continuous dialing or ringing tone triggers
    LaunchedEffect(session.status) {
        if (session.status == "RINGING") {
            MeshSoundPlayer.startRingtone()
        } else {
            MeshSoundPlayer.stopSound()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            MeshSoundPlayer.stopSound()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A), // Slate 900
                        Color(0xFF1E293B), // Slate 800
                        Color(0xFF090D16)  // Slate 950
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (session.status == "ACTIVE" && session.isVideo) {
            // WHATSAPP STYLE VIDEO CALL ACTIVE LOOK!
            // 1. Peer Video Stream covers FULL SCREEN
            val peerBitmap = remember(session.peerVideoFrameBase64) {
                session.peerVideoFrameBase64?.let { base64Str ->
                    try {
                        val bytes = android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            if (peerBitmap != null) {
                Image(
                    bitmap = peerBitmap.asImageBitmap(),
                    contentDescription = "Peer video feed",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                // High-fidelity active video mockup/placeholder
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .background(Color(0xFF00B0FF).copy(alpha = 0.15f), CircleShape)
                                .border(2.dp, Color(0xFF00B0FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                session.peerName.take(1).uppercase(),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "LIVE STREAM DIRECT VIA off-grid Wi-Fi-P2P",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(SignalGreen, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("LIVE FEED ACTIVE • 30 FPS", fontSize = 10.sp, color = SignalGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Local Camera Preview is small floating corner card (WhatsApp Style)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp)
                    .width(110.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(2.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .shadow(8.dp, RoundedCornerShape(12.dp))
            ) {
                if (session.cameraEnabled) {
                    LocalCameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel,
                        peerMac = session.peerMac
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Off", color = Color.White, fontSize = 10.sp)
                        }
                    }
                }
            }

            // 3. Top-Center Information Panel (WhatsApp overlay style)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = session.peerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 4f)
                    )
                )
                val minutes = session.activeSeconds / 60
                val seconds = session.activeSeconds % 60
                Text(
                    text = String.format("%02d:%02d", minutes, seconds),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 4f)
                    )
                )
            }

            // 4. Floating control row in translucent container at the bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mute toggle
                    IconButton(
                        onClick = { viewModel.toggleCallMute() },
                        modifier = Modifier
                            .background(if (session.isMuted) AlertRed else Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mute",
                            tint = Color.White
                        )
                    }

                    // Camera toggle
                    IconButton(
                        onClick = { viewModel.toggleCallCamera() },
                        modifier = Modifier
                            .background(if (!session.cameraEnabled) AlertRed else Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (session.cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Camera Toggle",
                            tint = Color.White
                        )
                    }

                    // Loudspeaker toggle
                    IconButton(
                        onClick = { 
                            isLoudspeakerActive = !isLoudspeakerActive
                            android.widget.Toast.makeText(context, "Loudspeaker: ${if (isLoudspeakerActive) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(if (isLoudspeakerActive) Color(0xFF00B0FF) else Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isLoudspeakerActive) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                            contentDescription = "Loudspeaker",
                            tint = Color.White
                        )
                    }

                    // Add people to call
                    IconButton(
                        onClick = { showAddPeopleDialog = true },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Add People",
                            tint = Color.White
                        )
                    }

                    // Hangup
                    IconButton(
                        onClick = { viewModel.rejectOrEndCall() },
                        modifier = Modifier
                            .background(AlertRed, CircleShape)
                            .size(52.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "End Call", tint = Color.White)
                    }
                }
            }
        } else {
            // REGULAR AUDIO CALL OR INCOMING/DIALING VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Identity
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 40.dp)) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(Color(0xFF2962FF).copy(alpha = 0.15f), CircleShape)
                            .border(2.dp, Color(0xFF2962FF).copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (session.isVideo) Icons.Default.VideoCall else Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(session.peerName, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                    Text(
                        text = when {
                            session.status == "DIALING" -> "SEARCHING COMPATIBLE RELAYS..."
                            session.status == "RINGING" -> "INCOMING BEACON HANDSHAKE..."
                            else -> "ACTIVE DIRECT PIPELINE"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2962FF),
                        letterSpacing = 1.sp
                    )

                    if (session.status == "ACTIVE") {
                        val minutes = session.activeSeconds / 60
                        val seconds = session.activeSeconds % 60
                        Text(
                            String.format("%02d:%02d", minutes, seconds),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp,
                            color = Color.White,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                // Waveform visualizer for active audio call
                if (session.status == "ACTIVE" && !session.isVideo) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .background(
                                    if (isSystemDark) Color(0xFF161F30) else Color(0xFFCBD5E1),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(9) { i ->
                                    val heightVal = remember { (25..75).random() }
                                    Box(
                                        modifier = Modifier
                                            .width(6.dp)
                                            .height(heightVal.dp)
                                            .background(SafeTeal, RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SafeTeal.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, SafeTeal.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Awaaz direct secure Wi-Fi channel se transmit ho rahi hai (Bina internet calling active hai).",
                                color = SafeTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(10.dp).fillMaxWidth()
                            )
                        }
                    }
                }

                // Dialing/Control Bottom bar buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRinging && session.isIncoming) {
                        // Answer
                        IconButton(
                            onClick = { viewModel.acceptIncomingCall() },
                            modifier = Modifier
                                .background(SignalGreen, CircleShape)
                                .size(56.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                        }
                    }

                    if (session.status == "ACTIVE") {
                        // Mute toggle
                        IconButton(
                            onClick = { viewModel.toggleCallMute() },
                            modifier = Modifier
                                .background(if (session.isMuted) AlertRed else Color.Gray.copy(alpha = 0.2f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = "Mute",
                                tint = Color.White
                            )
                        }

                        // Loudspeaker toggle
                        IconButton(
                            onClick = { 
                                isLoudspeakerActive = !isLoudspeakerActive
                                android.widget.Toast.makeText(context, "Loudspeaker: ${if (isLoudspeakerActive) "ON" else "OFF"}", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .background(if (isLoudspeakerActive) Color(0xFF00B0FF) else Color.Gray.copy(alpha = 0.2f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isLoudspeakerActive) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                                contentDescription = "Loudspeaker",
                                tint = Color.White
                            )
                        }

                        // Add people to call
                        IconButton(
                            onClick = { showAddPeopleDialog = true },
                            modifier = Modifier
                                .background(Color.Gray.copy(alpha = 0.2f), CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = "Add People",
                                tint = Color.White
                            )
                        }
                    }

                    // Hangup
                    IconButton(
                        onClick = { viewModel.rejectOrEndCall() },
                        modifier = Modifier
                            .background(AlertRed, CircleShape)
                            .size(56.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "End Call", tint = Color.White)
                    }
                }
            }
        }
    }

    if (showAddPeopleDialog) {
        val friends by viewModel.devices.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { showAddPeopleDialog = false },
            title = { Text("Add People to Call") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select a contact to bridge into this secure direct conference:")
                    if (friends.isEmpty()) {
                        Text("No other active peers discovered in local range.", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(friends) { peer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            android.widget.Toast.makeText(context, "Connecting ${peer.username} to conference mesh session...", android.widget.Toast.LENGTH_SHORT).show()
                                            showAddPeopleDialog = false
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(peer.username)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddPeopleDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// -------------------------------------------------------------
// SCREEN 8: SYSTEM SETTINGS OVERVIEW
// -------------------------------------------------------------
@Composable
fun SettingsScreen(viewModel: MeshViewModel) {
    val currentProfile by viewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Profile Edit States
    var isEditingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf("") }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val contentResolver = context.contentResolver
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // ignore
            }
            currentProfile?.let { prof ->
                viewModel.updateProfile(
                    name = prof.username,
                    language = prof.appLanguage,
                    rescueMode = prof.rescueMode,
                    colors = prof.avatarColor,
                    photoUri = it.toString()
                )
            }
        }
    }

    LaunchedEffect(currentProfile) {
        currentProfile?.let {
            if (tempName.isEmpty()) {
                tempName = it.username
            }
        }
    }

    var hopsLimit by remember { mutableFloatStateOf(4f) }
    var clearConfirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

        Text(
            "DEVELOPER INFORMATION",
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = Color(0xFF2962FF),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Color(0xFF2962FF).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar/Logo or neat icon for Nomi Developer
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF))
                        .border(2.dp, Color(0xFF2962FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🧑‍💻", fontSize = 40.sp)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "App Developed by nomi Developer",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF0F172A),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Spacer(modifier = Modifier.height(16.dp))

                // Email
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri("mailto:choudrymnouman@gmail.com")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEFF6FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = "Email",
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Email Address", fontSize = 11.sp, color = Color.Gray)
                        Text("choudrymnouman@gmail.com", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2962FF))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // WhatsApp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri("https://wa.me/923180845793")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFECFDF5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "WhatsApp",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("WhatsApp Number", fontSize = 11.sp, color = Color.Gray)
                        Text("+923180845793", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF10B981))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Web Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            try {
                                uriHandler.openUri("https://noumanchoudhary.netlify.app/")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFF7ED)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Web Link",
                            tint = Color(0xFFF97316),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Website Portfolio", fontSize = 11.sp, color = Color.Gray)
                        Text("noumanchoudhary.netlify.app", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF97316))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = Color(0xFFE2E8F0))

                Spacer(modifier = Modifier.height(12.dp))

                // App Version
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("App Version", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                    Text("1.0", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2962FF))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "ADVANCED ROUTING PARAMETERS",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = Color(0xFF2962FF),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Max Hops slider
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Maximum Propagation Multi-Hops", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                    Text("${hopsLimit.toInt()} Hopes", fontWeight = FontWeight.Bold, color = Color(0xFF2962FF))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Limits how many device devices a message packages can bounce over before discarding. Saves nearby spectrum battery.", fontSize = 11.sp, color = Color.Gray)
                Slider(
                    value = hopsLimit,
                    onValueChange = { hopsLimit = it },
                    valueRange = 1f..10f
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Codec optimization cards
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AI-Compress Packet Optimizations", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Compresses layout arrays dynamically before gossiping down local WiFi pipes.", fontSize = 11.sp, color = Color.Gray)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lossy Audio down-sampling", fontSize = 13.sp)
                    Switch(checked = true, onCheckedChange = {})
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // Reset database
        Button(
            onClick = { clearConfirmVisible = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Delete, contentDescription = "Trash database")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Wipe Offline Local Database Store", fontWeight = FontWeight.Bold)
            }
        }

        if (clearConfirmVisible) {
            AlertDialog(
                onDismissRequest = { clearConfirmVisible = false },
                title = { Text("Confirm Offline Purge") },
                text = { Text("Are you absolutely sure you want to flush all local discovered devices and secure chat message chains? This cannot be undone.", fontSize = 13.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.wipeLocalNodeData()
                            clearConfirmVisible = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Purge Node")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { clearConfirmVisible = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// COMPOSE DRAWING / VIEW UTILITIES EXTENSIONS
// -------------------------------------------------------------
fun Modifier.fillHorizontal(): Modifier = this.fillMaxWidth()

fun imageProxyToJpegBytes(image: androidx.camera.core.ImageProxy): ByteArray? {
    try {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        yBuffer.rewind()
        uBuffer.rewind()
        vBuffer.rewind()

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        val width = image.width
        val height = image.height

        val nv21Bytes = ByteArray(width * height * 3 / 2)

        // Copy Y channel
        var idY = 0
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            for (col in 0 until width) {
                nv21Bytes[idY++] = yBuffer.get()
                if (yPixelStride > 1 && col < width - 1) {
                    yBuffer.position(yBuffer.position() + yPixelStride - 1)
                }
            }
        }

        // Interleave UV channel (V first, then U)
        var idUV = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2

        for (row in 0 until uvHeight) {
            val uRowPos = row * uRowStride
            val vRowPos = row * vRowStride
            for (col in 0 until uvWidth) {
                val uPos = uRowPos + col * uPixelStride
                val vPos = vRowPos + col * vPixelStride
                nv21Bytes[idUV++] = vBuffer.get(vPos)
                nv21Bytes[idUV++] = uBuffer.get(uPos)
            }
        }

        // Compress to JPEG
        val yuvImage = android.graphics.YuvImage(nv21Bytes, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 35, out)
        var jpegBytes = out.toByteArray()

        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            try {
                val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
                val rotatedBitmap = android.graphics.Bitmap.createBitmap(
                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                )
                val rotatedOut = java.io.ByteArrayOutputStream()
                rotatedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 35, rotatedOut)
                jpegBytes = rotatedOut.toByteArray()
                originalBitmap.recycle()
                rotatedBitmap.recycle()
            } catch (e: Exception) {
                // Use default
            }
        }
        return jpegBytes
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

@Composable
fun LocalCameraPreview(
    modifier: Modifier = Modifier,
    viewModel: MeshViewModel,
    peerMac: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).apply {
                scaleType = androidx.camera.view.PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    
                    val preview = androidx.camera.core.Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val rotationVal = try {
                        previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                    } catch (e: Exception) {
                        android.view.Surface.ROTATION_0
                    }

                    // Low-res targeting 176x144 for optimal packet routing speed
                    val imageAnalysis = androidx.camera.core.ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(176, 144))
                        .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setTargetRotation(rotationVal)
                        .build()

                    var lastFrameTime = 0L
                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        // 250 milliseconds throttle (4 FPS)
                        if (currentTime - lastFrameTime >= 250) {
                            lastFrameTime = currentTime
                            try {
                                val jpegBytes = imageProxyToJpegBytes(imageProxy)
                                if (jpegBytes != null) {
                                    val base64 = android.util.Base64.encodeToString(jpegBytes, android.util.Base64.NO_WRAP)
                                    viewModel.sendLocalVideoFrame(peerMac, base64)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        imageProxy.close()
                    }

                    val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, executor)
        }
    )
}

@Composable
fun DashboardScreen(viewModel: MeshViewModel) {
    val deviceList by viewModel.devices.collectAsStateWithLifecycle()
    val recentActivities by viewModel.recentActivities.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    var showMap by remember { mutableStateOf(false) }

    val onlineCount = deviceList.count { it.isOnline }
    val isConnected = onlineCount > 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Dashboard",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }

        // Mesh Network Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mesh Network",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isConnected) Color(0xFF4ADE80) else Color(0xFFEF4444), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Connected" else "Disconnected",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConnected) Color(0xFF4ADE80) else Color(0xFFEF4444)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isConnected) "$onlineCount Devices Online" else "0 Devices Online (Offline)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                        Text(
                            text = "Strong Signal",
                            fontSize = 12.sp,
                            color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                        )
                    }

                    // Active device linkages graph in a small circle!
                    MiniDeviceLinkageCircle(devices = deviceList)
                }
            }
        }

        // Quick Actions Section
        item {
            Column {
                Text(
                    text = "Quick Actions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0F172A),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Grid layout for quick actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionItem(
                        title = "Chats",
                        icon = Icons.Default.Chat,
                        backgroundColor = Color(0xFFEFF6FF),
                        iconColor = Color(0xFF2962FF),
                        isDark = isDark,
                        onClick = { viewModel.navigateTo("chats") }
                    )
                    QuickActionItem(
                        title = "Calls",
                        icon = Icons.Default.Call,
                        backgroundColor = Color(0xFFECFDF5),
                        iconColor = Color(0xFF10B981),
                        isDark = isDark,
                        onClick = { viewModel.navigateTo("calls") }
                    )
                    QuickActionItem(
                        title = "People",
                        icon = Icons.Default.People,
                        backgroundColor = Color(0xFFFFF7ED),
                        iconColor = Color(0xFFF97316),
                        isDark = isDark,
                        onClick = { viewModel.navigateTo("people") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionItem(
                        title = "Files",
                        icon = Icons.Default.Folder,
                        backgroundColor = Color(0xFFF5F3FF),
                        iconColor = Color(0xFF8B5CF6),
                        isDark = isDark,
                        onClick = { viewModel.navigateTo("files") }
                    )
                    QuickActionItem(
                        title = "Groups",
                        icon = Icons.Default.Groups,
                        backgroundColor = Color(0xFFFDF2F8),
                        iconColor = Color(0xFFEC4899),
                        isDark = isDark,
                        onClick = { viewModel.navigateTo("groups") }
                    )
                    QuickActionItem(
                        title = "Map",
                        icon = Icons.Default.Map,
                        backgroundColor = Color(0xFFFFF1F2),
                        iconColor = Color(0xFFF43F5E),
                        isDark = isDark,
                        onClick = { showMap = !showMap }
                    )
                }
            }
        }

        // Draggable Map Card (Show if showMap is toggled!)
        if (showMap) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Offline Signal Radar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isDark) Color.White else Color(0xFF0F172A)
                            )
                            IconButton(onClick = { showMap = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        NodeTopologyCanvas(devices = deviceList, isDark = isDark, onNodeClicked = { mac ->
                            viewModel.selectDirectChat(mac)
                        })
                    }
                }
            }
        }

        // Recent Activity Section
        item {
            Text(
                text = "Recent Activity",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (recentActivities.isEmpty()) {
            item {
                Text(
                    text = "No recent activity.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(recentActivities) { activity ->
                RecentActivityItemRow(activity = activity, isDark = isDark, onClick = {
                    // Navigate logically based on type
                    when (activity.iconType) {
                        "phone" -> viewModel.navigateTo("calls")
                        "file" -> viewModel.navigateTo("files")
                        "group" -> viewModel.navigateTo("groups")
                        else -> viewModel.navigateTo("chats")
                    }
                })
            }
        }
    }
}

@Composable
fun MiniDeviceLinkageCircle(devices: List<MeshDevice>, modifier: Modifier = Modifier) {
    val onlineDevices = devices.filter { it.isOnline }
    Box(
        modifier = modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(Color(0xFFF1F5F9))
            .border(1.dp, Color(0xFFE2E8F0), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            
            // Draw center node (you)
            drawCircle(
                color = Color(0xFF2962FF),
                radius = 4.dp.toPx(),
                center = Offset(cx, cy)
            )
            
            // Draw surrounding nodes
            val count = if (onlineDevices.isEmpty()) 5 else onlineDevices.size
            for (i in 0 until count) {
                val angle = i * (2 * Math.PI / count)
                val distance = size.width * 0.4f
                val px = cx + distance * cos(angle).toFloat()
                val py = cy + distance * sin(angle).toFloat()
                
                // Draw link line
                drawLine(
                    color = Color(0xFF2962FF).copy(alpha = 0.5f),
                    start = Offset(cx, cy),
                    end = Offset(px, py),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Draw peer node
                drawCircle(
                    color = Color(0xFF4ADE80),
                    radius = 2.5.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF334155) else backgroundColor)
                .border(2.dp, iconColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isDark) Color.White else iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
        )
    }
}

@Composable
fun RecentActivityItemRow(activity: RecentActivity, isDark: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
        ),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            val (icon, bg, color) = when (activity.iconType) {
                "phone" -> Triple(Icons.Default.Call, Color(0xFFECFDF5), Color(0xFF10B981))
                "file" -> Triple(Icons.Default.Description, Color(0xFFF5F3FF), Color(0xFF8B5CF6))
                "group" -> Triple(Icons.Default.Groups, Color(0xFFFDF2F8), Color(0xFFEC4899))
                "image" -> Triple(Icons.Default.Image, Color(0xFFFEF3C7), Color(0xFFD97706))
                else -> Triple(Icons.Default.Chat, Color(0xFFEFF6FF), Color(0xFF2962FF))
            }
            
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0xFF334155) else bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) Color.White else color,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = activity.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF1E293B)
                )
                Text(
                    text = activity.type,
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )
            }
            
            Text(
                text = activity.time,
                fontSize = 12.sp,
                color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun CallsHistoryScreen(viewModel: MeshViewModel) {
    val callHistory by viewModel.callHistory.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Call History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        if (callHistory.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No calls history.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(callHistory) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isMissed = log.isMissed
                            val isIncoming = log.status.contains("Incoming")
                            val isVideo = log.isVideo
                            
                            val callIcon = when {
                                isMissed -> Icons.Default.CallMissed
                                isIncoming -> Icons.Default.CallReceived
                                else -> Icons.Default.CallMade
                            }
                            
                            val callColor = when {
                                isMissed -> Color(0xFFEF4444)
                                isIncoming -> Color(0xFF10B981)
                                else -> Color(0xFF3B82F6)
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) Color(0xFF334155) else callColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isVideo) Icons.Default.VideoCall else callIcon,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White else callColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${log.status} • ${log.timestamp}",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                            
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = log.duration,
                                    fontSize = 13.sp,
                                    color = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                
                                IconButton(
                                    onClick = {
                                        // Start a simulated live active call!
                                        val matchedPeer = devices.find { it.username == log.name }
                                        val mac = matchedPeer?.macAddress ?: "E1:12:34:56:AB:01"
                                        viewModel.selectDirectChat(mac)
                                        // Trigger a live outgoing call session!
                                        viewModel.simulateAddCallLog(log.name, false, isVideo, false, "00:00")
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                        contentDescription = "Redial",
                                        tint = Color(0xFF2962FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeopleNearbyScreen(viewModel: MeshViewModel) {
    val deviceList by viewModel.devices.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Text(
            text = "People Nearby",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDark) Color.White else Color(0xFF0F172A),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Connected active devices on LinkMesh offline grid",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        if (deviceList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active mesh nodes detected nearby.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(deviceList) { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (device.isOnline) Color(0xFFDCFCE7) else Color(0xFFF1F5F9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (device.username.isNotEmpty()) device.username.take(1).uppercase() else "👤",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = if (device.isOnline) Color(0xFF15803D) else Color(0xFF475569)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.username,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (device.isOnline) Color(0xFF4ADE80) else Color(0xFFEF4444), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (device.isOnline) "Connected • Online" else "Disconnected • Offline",
                                        fontSize = 11.sp,
                                        color = if (device.isOnline) Color(0xFF16A34A) else Color(0xFFDC2626)
                                    )
                                }
                            }
                            
                            if (device.isOnline) {
                                IconButton(onClick = { viewModel.selectDirectChat(device.macAddress) }) {
                                    Icon(
                                        imageVector = Icons.Default.Chat,
                                        contentDescription = "Chat",
                                        tint = Color(0xFF2962FF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SharedFilesScreen(viewModel: MeshViewModel) {
    val sharedFiles by viewModel.sharedFiles.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Received, 1 = Sent
    val receivedFiles = sharedFiles.filter { it.isIncoming }
    val sentFiles = sharedFiles.filter { !it.isIncoming }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Files",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF2962FF)
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Received", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Sent", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val currentList = if (selectedTab == 0) receivedFiles else sentFiles

        if (currentList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("No shared files in this tab.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                items(currentList) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (file.fileType) {
                                "pdf" -> Icons.Default.Description
                                "mp4" -> Icons.Default.Videocam
                                "jpg", "png" -> Icons.Default.Image
                                else -> Icons.Default.InsertDriveFile
                            }

                            val iconBg = when (file.fileType) {
                                "pdf" -> Color(0xFFFEE2E2)
                                "mp4" -> Color(0xFFE0F2FE)
                                "jpg", "png" -> Color(0xFFFEF3C7)
                                else -> Color(0xFFF1F5F9)
                            }

                            val iconColor = when (file.fileType) {
                                "pdf" -> Color(0xFFEF4444)
                                "mp4" -> Color(0xFF0284C7)
                                "jpg", "png" -> Color(0xFFD97706)
                                else -> Color(0xFF64748B)
                            }

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFF334155) else iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = file.fileType,
                                    tint = if (isDark) Color.White else iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${if (selectedTab == 0) "From" else "To"} ${file.senderOrReceiver} • ${file.size} • ${file.timestamp}",
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Share File Dialog removed
}

@Composable
fun GroupsListScreen(viewModel: MeshViewModel) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    
    var showCreateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Groups",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else Color(0xFF0F172A)
            )
            
            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2962FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("+ Create Group", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Create and manage your mesh group channels",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No groups active.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(groups) { group ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Open group chat simulation
                                viewModel.navigateTo("groups") // In our router, "groups" activeTab shows GroupChatEmbedScreen!
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDark) Color(0xFF1E293B) else Color.White
                        ),
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEFF6FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    contentDescription = null,
                                    tint = Color(0xFF2962FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = group.description,
                                    fontSize = 12.sp,
                                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(100.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${group.membersCount} Members",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (showCreateDialog) {
        var groupName by remember { mutableStateOf("") }
        var groupDesc by remember { mutableStateOf("") }
        val devices by viewModel.devices.collectAsStateWithLifecycle()
        var selectedMembers by remember { mutableStateOf(setOf<String>()) }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Group") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        label = { Text("Group Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Add Members to Group:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                        items(devices) { peer ->
                            val isSelected = selectedMembers.contains(peer.macAddress)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMembers = if (isSelected) {
                                            selectedMembers - peer.macAddress
                                        } else {
                                            selectedMembers + peer.macAddress
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        selectedMembers = if (isSelected) {
                                            selectedMembers - peer.macAddress
                                        } else {
                                            selectedMembers + peer.macAddress
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(peer.username, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotEmpty()) {
                            viewModel.createGroup(groupName, groupDesc, selectedMembers.size + 1)
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

