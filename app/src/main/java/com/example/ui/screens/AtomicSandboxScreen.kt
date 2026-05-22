package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AtomInstance
import com.example.model.BondInstance
import com.example.model.BondType
import com.example.ui.components.MathLabsSidebar
import com.example.ui.components.OrbitalGenerator
import com.example.ui.components.OrbitalPoint
import com.example.ui.components.PeriodicTableDrawer
import com.example.viewmodel.AtomicViewModel
import kotlinx.coroutines.delay
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AtomicSandboxScreen(
    viewModel: AtomicViewModel,
    modifier: Modifier = Modifier
) {
    val atoms by viewModel.atomsList.collectAsState()
    val bonds by viewModel.bondsList.collectAsState()
    val molecules by viewModel.detectedMolecules.collectAsState()

    // Drag tracking to support camera rotational orbital states
    val rx by viewModel.cameraRotX
    val ry by viewModel.cameraRotY
    val zoom by viewModel.cameraZoom

    val isMathSideExpanded by viewModel.isMathLabsExpanded
    val selectedAtomId by viewModel.selectedAtomId
    val selectedBondId by viewModel.selectedBondId
    val representationMode by viewModel.representationMode

    // Real-time animation loop for orbital electrons
    var animationFrame by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            animationFrame += 0.05f
            if (viewModel.autoVibrateBonds.value) {
                viewModel.applyThermalJitter()
            }
            delay(16) // Smooth 60 frames approx
        }
    }

    // Selected atom reference
    val selectedAtom = remember(atoms, selectedAtomId) {
        atoms.find { it.id == selectedAtomId }
    }
    // Quantum cloud cached points for the selected atom to speed up rendering
    val quantumPoints = remember(selectedAtomId, viewModel.orbitalTypeToShow.value, representationMode) {
        if (selectedAtom != null && representationMode == "Quantum Probability Cloud") {
            OrbitalGenerator.generateOrbital(viewModel.orbitalTypeToShow.value, count = 1000)
        } else {
            emptyList()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10)) // Immersive deep dark ground
    ) {
        // Space canvas rendering stars, 3D orbits, atoms, orbitals, and bonds using painters algorithm
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Rotate 3D camera
                        viewModel.cameraRotY.value = (viewModel.cameraRotY.value + dragAmount.x * 0.4f) % 360f
                        viewModel.cameraRotX.value = (viewModel.cameraRotX.value - dragAmount.y * 0.4f).coerceIn(-85f, 85f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val centerX = canvasWidth / 2f
                val centerY = canvasHeight / 2f

                // Render background star particles
                drawStarsBackground(this, canvasWidth, canvasHeight)

                // Render glowing central ambient nebulous field from Immersive UI
                if (canvasWidth > 0f && canvasHeight > 0f) {
                    val nebRadius = (min(canvasWidth, canvasHeight) * 0.45f).coerceAtLeast(1f)
                    val drawRadius = (min(canvasWidth, canvasHeight) * 1.1f).coerceAtLeast(1f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x142563EB), Color.Transparent),
                            center = Offset(centerX, centerY),
                            radius = nebRadius
                        ),
                        radius = drawRadius,
                        center = Offset(centerX, centerY)
                    )
                }

                // 3D Matrix Rotations
                val angleX = (rx * PI / 180.0)
                val angleY = (ry * PI / 180.0)

                // To render overlapping correctly, we project all entities and sort by projected Z coordinate (Painters algorithm)
                // We create drawable items wrapper
                val drawables = mutableListOf<ProjectedItem>()

                // 1. Project bonds
                bonds.forEach { bond ->
                    val a1 = atoms.find { it.id == bond.atomId1 }
                    val a2 = atoms.find { it.id == bond.atomId2 }
                    if (a1 != null && a2 != null) {
                        // Projected coordinate points
                        val p1 = project3D(a1.x, a1.y, a1.z, angleX, angleY, zoom, centerX, centerY)
                        val p2 = project3D(a2.x, a2.y, a2.z, angleX, angleY, zoom, centerX, centerY)
                        
                        // Add link representation
                        val avgZ = (p1.projZ + p2.projZ) / 2
                        drawables.add(ProjectedItem.BondItem(bond, p1, p2, avgZ))
                    }
                }

                // 2. Project atoms, shell orbitals and electrons
                atoms.forEach { atom ->
                    val p = project3D(atom.x, atom.y, atom.z, angleX, angleY, zoom, centerX, centerY)
                    drawables.add(ProjectedItem.AtomItem(atom, p, p.projZ))
                }



                // Sort everything back-to-front (descending in Z means further away, so list starts with furthest, drawn first!)
                drawables.sortByDescending { it.sortZ }

                // Draw items!
                drawables.forEach { item ->
                    when (item) {
                        is ProjectedItem.BondItem -> {
                            val color = when (item.bond.type) {
                                BondType.SINGLE -> Color(0xFF81D4FA)
                                BondType.DOUBLE -> Color(0xFF4FC3F7)
                                BondType.TRIPLE -> Color(0xFF02BBFF)
                                BondType.IONIC -> Color(0xFFFFD54F)
                            }
                            
                            if (item.bond.type == BondType.IONIC) {
                                // Draw pulsating dash lines
                                drawLine(
                                    color = color,
                                    start = Offset(item.p1.projX, item.p1.projY),
                                    end = Offset(item.p2.projX, item.p2.projY),
                                    strokeWidth = 3f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), (animationFrame * 20f) % 30f)
                                )
                            } else {
                                // Double or Triple Covalent bonds
                                when (item.bond.type.value) {
                                    1 -> {
                                        drawLine(
                                            color = color,
                                            start = Offset(item.p1.projX, item.p1.projY),
                                            end = Offset(item.p2.projX, item.p2.projY),
                                            strokeWidth = 4f * zoom
                                        )
                                    }
                                    2 -> {
                                        // Draw parallel lines
                                        val dx = item.p2.projX - item.p1.projX
                                        val dy = item.p2.projY - item.p1.projY
                                        val length = sqrt(dx*dx + dy*dy)
                                        if (length > 0) {
                                            val nx = -dy / length * 6f
                                            val ny = dx / length * 6f
                                            drawLine(
                                                color = color,
                                                start = Offset(item.p1.projX + nx, item.p1.projY + ny),
                                                end = Offset(item.p2.projX + nx, item.p2.projY + ny),
                                                strokeWidth = 3f * zoom
                                            )
                                            drawLine(
                                                color = color,
                                                start = Offset(item.p1.projX - nx, item.p1.projY - ny),
                                                end = Offset(item.p2.projX - nx, item.p2.projY - ny),
                                                strokeWidth = 3f * zoom
                                            )
                                        }
                                    }
                                    3 -> {
                                        val dx = item.p2.projX - item.p1.projX
                                        val dy = item.p2.projY - item.p1.projY
                                        val length = sqrt(dx*dx + dy*dy)
                                        if (length > 0) {
                                            val nx = -dy / length * 10f
                                            val ny = dx / length * 10f
                                            drawLine(
                                                color = color,
                                                start = Offset(item.p1.projX + nx, item.p1.projY + ny),
                                                end = Offset(item.p2.projX + nx, item.p2.projY + ny),
                                                strokeWidth = 2.5f * zoom
                                            )
                                            drawLine(
                                                color = color,
                                                start = Offset(item.p1.projX, item.p1.projY),
                                                end = Offset(item.p2.projX, item.p2.projY),
                                                strokeWidth = 2.5f * zoom
                                            )
                                            drawLine(
                                                color = color,
                                                start = Offset(item.p1.projX - nx, item.p1.projY - ny),
                                                end = Offset(item.p2.projX - nx, item.p2.projY - ny),
                                                strokeWidth = 2.5f * zoom
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        is ProjectedItem.AtomItem -> {
                            val atom = item.atom
                            val px = item.projection.projX
                            val py = item.projection.projY
                            val baseRadius = atom.element.atomicRadiusPm.toFloat() * 0.16f * zoom
                            val isSelected = atom.isSelected

                            // Shell paths / Classical wireframe
                            if (representationMode == "Classical Bohr" || representationMode == "Wireframe Orbitals") {
                                atom.element.shellConfiguration.forEachIndexed { sIdx, count ->
                                    val shellRadius = baseRadius * 1.6f + sIdx * 25f * zoom
                                    // Draw shell orbital path
                                    drawCircle(
                                        color = Color(0x3381D4FA),
                                        radius = shellRadius,
                                        center = Offset(px, py),
                                        style = Stroke(width = 1f)
                                    )

                                    // Rotate electrons on the path depending on index
                                    val speed = 1.0f / (sIdx + 1.2f)
                                    for (eIdx in 0 until count) {
                                        val angle = (animationFrame * speed + (eIdx * 2 * PI / count)).toFloat()
                                        val ex = px + shellRadius * cos(angle)
                                        val ey = py + shellRadius * sin(angle)

                                        // Glow halo for electron
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(Color(0xFF00FFCC), Color.Transparent),
                                                center = Offset(ex, ey),
                                                radius = 6f
                                            ),
                                            radius = 6f,
                                            center = Offset(ex, ey)
                                        )
                                        // Solid electron core
                                        drawCircle(
                                            color = Color(0xFF00FFCC),
                                            radius = 2.5f,
                                            center = Offset(ex, ey)
                                        )
                                    }
                                }
                            }

                            // Radial shadow and CPK colored 3D lighting gradient sphere
                            val brush = Brush.radialGradient(
                                colors = listOf(Color.White, atom.element.cpkColor, atom.element.cpkColor.copy(alpha = 0.4f), Color.Transparent),
                                center = Offset(px - baseRadius * 0.25f, py - baseRadius * 0.25f), // highlight upper-left sources
                                radius = (baseRadius * 1.1f).coerceAtLeast(1f)
                            )

                            // Outer selection pulse glow
                            if (isSelected) {
                                drawCircle(
                                    color = Color(0x8800E5FF),
                                    radius = baseRadius + 8f + sin(animationFrame * 3f) * 4f,
                                    center = Offset(px, py),
                                    style = Stroke(width = 2f)
                                )
                            }

                            // Draw quantum points from direct cache if this is the selected atom and mode is Quantum Cloud
                            if (atom.id == selectedAtomId && representationMode == "Quantum Probability Cloud") {
                                quantumPoints.forEach { qp ->
                                    val qpProj = project3D(atom.x + qp.x, atom.y + qp.y, atom.z + qp.z, angleX, angleY, zoom, centerX, centerY)
                                    drawCircle(
                                        color = Color(0x7300E5FF),
                                        radius = 2f * zoom,
                                        center = Offset(qpProj.projX, qpProj.projY),
                                        alpha = qp.probability
                                    )
                                }
                            }

                            // Main atomic core
                            drawCircle(
                                brush = brush,
                                radius = baseRadius,
                                center = Offset(px, py)
                            )

                            // Draw text symbol inside core
                            val symbolPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.WHITE
                                textSize = 13f * density * zoom
                                isFakeBoldText = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            val bounds = android.graphics.Rect()
                            symbolPaint.getTextBounds(atom.element.symbol, 0, atom.element.symbol.length, bounds)
                            val textHeightOffset = bounds.height() / 2f
                            drawContext.canvas.nativeCanvas.drawText(
                                atom.element.symbol,
                                px,
                                py + textHeightOffset,
                                symbolPaint
                            )
                        }
                    }
                }
            }

            // Real-time molecular reaction alert overlay or stats panel
            if (molecules.isNotEmpty()) {
                Surface(
                    color = Color(0xE60D1E2D),
                    border = BorderStroke(1.dp, Color(0xFF00C853)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                        .widthIn(max = 420.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF00C853).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Reaction Match",
                                tint = Color(0xFF00C853)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "STABLE REACTION SYNTHESIS: MATCHED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00C853),
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = molecules.joinToString(" + ") { "${it.name} (${it.formula})" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Immersive Theme Header
        val currentElement = remember(viewModel.activeElementId.value) {
            com.example.model.Element.elementsMap[viewModel.activeElementId.value]!!
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .padding(top = 16.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Circular active element badge with dynamic chemical symbol & glow effect
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(currentElement.cpkColor, currentElement.cpkColor.copy(alpha = 0.4f)),
                            ),
                            shape = CircleShape
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentElement.symbol,
                        color = if (currentElement.cpkColor == Color(0xFFECEFF1)) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Column {
                    Text(
                        text = "Carbon Lattice Labs",
                        color = Color(0xFFF1F5F9),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.25.sp
                    )
                    Text(
                        text = "Diamond Structure simulation".uppercase(),
                        color = Color(0xFF4184F3),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Quick simulation controls/reset button on header right
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggles live temperature vibration
                IconButton(
                    onClick = { viewModel.autoVibrateBonds.value = !viewModel.autoVibrateBonds.value },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (viewModel.autoVibrateBonds.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Thermal Vibration Toggle",
                        tint = if (viewModel.autoVibrateBonds.value) Color(0xFF00FFCC) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Preset selection indicator
                IconButton(
                    onClick = { viewModel.resetSandbox() },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Left sidebar toolbar to load presets, control zoom and toggle orbital/Bohr parameters
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 84.dp, bottom = 48.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Preset Compounds Picker
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xB30A0C10)),
                border = BorderStroke(1.dp, Color(0x1BFFFFFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "PRESETS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    listOf(
                        "Water" to { viewModel.loadPresetWater() },
                        "CO₂" to { viewModel.loadPresetCarbonDioxide() },
                        "Methane" to { viewModel.loadPresetMethane() },
                        "Salt" to { viewModel.loadPresetSalt() }
                    ).forEach { (label, loadFn) ->
                        TextButton(
                            onClick = loadFn,
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(label, fontSize = 12.sp, color = Color(0xFF00E5FF), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Orbital Mode Toggles
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xB30A0C10)),
                border = BorderStroke(1.dp, Color(0x1BFFFFFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        "REPRESENTATION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    listOf(
                        "Quantum Cloud" to "Quantum Probability Cloud",
                        "Classical Bohr" to "Classical Bohr",
                        "Structure only" to "Wireframe Orbitals"
                    ).forEach { (title, mode) ->
                        TextButton(
                            onClick = { viewModel.representationMode.value = mode },
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (representationMode == mode) Color(0x2600E5FF) else Color.Transparent
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(title, fontSize = 11.sp, color = if (representationMode == mode) Color(0xFF00E5FF) else Color.White)
                        }
                    }

                    // Shell selection / Subshell if quantum mode is active
                    if (representationMode == "Quantum Probability Cloud" && selectedAtom != null) {
                        HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 6.dp))
                        Text(
                            "ORBITAL LOADS",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("s", "p_x", "p_y", "p_z", "d").forEach { orb ->
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(
                                            if (viewModel.orbitalTypeToShow.value == orb) Color(0xFF00E5FF) else Color(0x1FFFFFFF),
                                            RoundedCornerShape(3.dp)
                                        )
                                        .clickable { viewModel.orbitalTypeToShow.value = orb },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        orb.substringAfter("_").uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (viewModel.orbitalTypeToShow.value == orb) Color.Black else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Sandbox General settings
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xB30A0C10)),
                border = BorderStroke(1.dp, Color(0x1BFFFFFF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ZOOM", fontSize = 8.sp, color = Color.Gray)
                    Slider(
                        value = zoom,
                        onValueChange = { viewModel.cameraZoom.value = it },
                        valueRange = 0.5f..2.5f,
                        modifier = Modifier.width(80.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                    
                    Text("TEMP (K)", fontSize = 8.sp, color = Color.White)
                    Text(
                        "${viewModel.simulationTemperatureK.value.toInt()} K",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB74D)
                    )
                    Slider(
                        value = viewModel.simulationTemperatureK.value,
                        onValueChange = { viewModel.simulationTemperatureK.value = it },
                        valueRange = 100f..1500f,
                        modifier = Modifier.width(80.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFB74D),
                            activeTrackColor = Color(0xFFFFB74D),
                            inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Right Expandable "Math Labs" sidebar toggle
        if (!isMathSideExpanded) {
            Button(
                onClick = { viewModel.isMathLabsExpanded.value = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0A111E)),
                border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(64.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.Science, contentDescription = "Math Labs", tint = Color(0xFF00E5FF))
                    Text("MATHS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                }
            }
        }

        // Math Labs Expandable Sidebar Layout
        MathLabsSidebar(
            isExpanded = isMathSideExpanded,
            onToggle = { viewModel.isMathLabsExpanded.value = false },
            selectedAtom = selectedAtom,
            selectedBond = null, // Bond calculation detail inside sidebar general
            atoms = atoms,
            moleculesCount = molecules.size,
            detectedMolecules = molecules,
            temperatureK = viewModel.simulationTemperatureK.value,
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        // Lower Interactive Dock / Selection controls
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Column {
                // Interactive atom manipulation utilities (Only show if atom or multi-selection points exist!)
                if (selectedAtom != null || atoms.count { it.isSelected } >= 1) {
                    Surface(
                        color = Color(0xDD0F172A),
                        border = BorderStroke(1.dp, Color(0x66FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Atom Utility:",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )

                            // Position pad (Precise 3D alignment arrow controllers)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.moveSelectedAtom(-15f, 0f, 0f) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    IconButton(onClick = { viewModel.moveSelectedAtom(0f, -15f, 0f) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(onClick = { viewModel.moveSelectedAtom(0f, 15f, 0f) }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { viewModel.moveSelectedAtom(15f, 0f, 0f) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Z depth axis offset
                                IconButton(onClick = { viewModel.moveSelectedAtom(0f, 0f, 15f) }, modifier = Modifier.size(32.dp)) {
                                    Text("+Z", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                IconButton(onClick = { viewModel.moveSelectedAtom(0f, 0f, -15f) }, modifier = Modifier.size(32.dp)) {
                                    Text("-Z", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            VerticalDivider(color = Color(0x33FFFFFF), thickness = 1.dp, modifier = Modifier.height(28.dp))

                            // Bonding Actions: Enable when multiple atoms are selected
                            val countSelected = atoms.count { it.isSelected }
                            if (countSelected == 2) {
                                Button(
                                    onClick = { viewModel.establishBondBetweenSelected(BondType.SINGLE) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Single Covalent", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.establishBondBetweenSelected(BondType.DOUBLE) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02BBFF)),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Double Bond", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { viewModel.establishBondBetweenSelected(BondType.IONIC) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD54F)),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Ionic", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Box(modifier = Modifier.padding(horizontal = 4.dp)) {
                                    Text(
                                        "Select exactly 2 atoms on screen to link bonds",
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            VerticalDivider(color = Color(0x33FFFFFF), thickness = 1.dp, modifier = Modifier.height(28.dp))

                            // Delete selection
                            IconButton(onClick = { viewModel.deleteSelectedAtom() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }

                // Sandbox clearing & setup floating options
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { viewModel.resetSandbox() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear All", tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR SANDBOX", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    if (selectedAtomId != null || atoms.any { it.isSelected }) {
                        Button(
                            onClick = { viewModel.clearSelection() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("DESELECT ALL", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "Orbit rotation: Drag canvas  |  Add elements using Periodic grid below",
                            color = Color.Gray,
                            fontSize = 10.sp,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }

                // Periodic table selector
                PeriodicTableDrawer(
                    selectedElementId = viewModel.activeElementId.value,
                    onElementSelect = { viewModel.activeElementId.value = it },
                    onAddAtomToSandbox = { viewModel.addNewAtom() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// 3D Projection coordinate mapping
private fun project3D(
    x: Float, y: Float, z: Float,
    angleX: Double, angleY: Double,
    zoom: Float,
    centerX: Float, centerY: Float
): ProjectionResult {
    // 1. Rotate around Y axis
    val rotatedX = x * cos(angleY) - z * sin(angleY)
    val tempZ = x * sin(angleY) + z * cos(angleY)

    // 2. Rotate around X axis
    val rotatedY = y * cos(angleX) - tempZ * sin(angleX)
    val rotatedZ = y * sin(angleX) + tempZ * cos(angleX)

    // 3. Perspective zoom scale
    // Constant view distance
    val d = 600f
    val denom = d + rotatedZ.toFloat()
    val safeDenom = if (denom < 50f) 50f else denom
    val scale = d / safeDenom * zoom

    var projX = rotatedX.toFloat() * scale * 1.5f + centerX
    var projY = rotatedY.toFloat() * scale * 1.5f + centerY

    if (projX.isNaN() || projX.isInfinite()) projX = centerX
    if (projY.isNaN() || projY.isInfinite()) projY = centerY

    return ProjectionResult(projX, projY, rotatedZ.toFloat())
}

private data class ProjectionResult(
    val projX: Float,
    val projY: Float,
    val projZ: Float // Depth used for painter's algorithm sorting
)

private sealed class ProjectedItem {
    abstract val sortZ: Float

    data class AtomItem(
        val atom: AtomInstance,
        val projection: ProjectionResult,
        override val sortZ: Float
    ) : ProjectedItem()

    data class BondItem(
        val bond: BondInstance,
        val p1: ProjectionResult,
        val p2: ProjectionResult,
        override val sortZ: Float
    ) : ProjectedItem()


}

// Draw static backdrop star coordinates
private fun drawStarsBackground(drawScope: androidx.compose.ui.graphics.drawscope.DrawScope, width: Float, height: Float) {
    if (width <= 0f || height <= 0f) return
    val starCount = 80
    for (i in 0 until starCount) {
        val sx = (i * 7919) % width
        val sy = (i * 3571) % height
        val alpha = 0.15f + 0.6f * sin(i.toFloat() + 1.2f).absoluteValue
        drawScope.drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = 1.3f,
            center = Offset(sx, sy)
        )
    }
}
