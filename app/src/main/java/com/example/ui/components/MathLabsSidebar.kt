package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AtomInstance
import com.example.model.BondInstance
import com.example.model.DetectedMolecule
import kotlin.math.exp
import kotlin.math.pow

@Composable
fun MathLabsSidebar(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    selectedAtom: AtomInstance?,
    selectedBond: BondInstance?,
    atoms: List<AtomInstance>,
    moleculesCount: Int,
    detectedMolecules: List<DetectedMolecule>,
    temperatureK: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isExpanded,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(340.dp)
                .border(1.dp, Color(0x2B4184F3), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)),
            color = Color(0xF20A0C10), // Immersive dark glass
            tonalElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = "Science",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MATH LABS",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp
                        )
                    }
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Sidebar",
                            tint = Color.White
                        )
                    }
                }

                HorizontalDivider(color = Color(0x3300E5FF), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // Subsection 1: Schrödinger Wave Equation
                Text(
                    text = "1. Quantum Mechanical Wavefunctions",
                    color = Color(0xFF81D4FA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Ĥψ = Eψ",
                            fontSize = 16.sp,
                            color = Color(0xFFFF8A65),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Hamiltonian Operator Expansion:",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                        Text(
                            text = "[ -ħ²/2m ∇² + V(r) ] ψ = E ψ",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color.Green,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = "Where V(r) = -Z·e² / (4π𝜖₀·r) represents Coulombic potential energy pulling the electron.",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Radial distribution visualization
                Text(
                    text = "Hydrogen Radial Probability Map",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(Color(0x2200E5FF), RoundedCornerShape(8.dp))
                ) {
                    val width = size.width
                    val height = size.height
                    
                    // Draw axis grid
                    drawLine(Color(0x22FFFFFF), Offset(0f, height/2), Offset(width, height/2))
                    
                    // Plotted radial wave probability (ex: P(r) = r^2 * e^(-2r) for 1s orbital)
                    val path = Path()
                    path.moveTo(10f, height - 10f)
                    val widthInt = width.toInt()
                    val denom = width - 20f
                    val safeDenom = if (denom <= 0f) 100f else denom
                    for (x in 10..widthInt step 2) {
                        val t = (x - 10) / safeDenom // normalize 0 to 1
                        val scaledR = t * 6.0f // up to 6 Bohr radii
                        val prob = (scaledR * scaledR) * exp(-2 * scaledR) * 5.5f // wave probability
                        val y = height - 10f - (prob * (height - 20f))
                        if (!y.isNaN() && !y.isInfinite()) {
                            path.lineTo(x.toFloat(), y)
                        }
                    }
                    drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 2.dp.toPx()))
                }
                Text(
                    text = "Bohr Radius scale: a₀ = 52.9 pm. Probability maximizes at r = 1 a₀ for 1s state.",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Subsection 2: Lennard-Jones Bond Potential
                Text(
                    text = "2. Molecular Bond Potential Energy",
                    color = Color(0xFF81D4FA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                    border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "V_LJ(r) = 4𝜖 [ (𝜎/r)¹² - (𝜎/r)⁶ ]",
                            fontSize = 13.sp,
                            color = Color(0xFFFFB74D),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• (𝜎/r)¹² : Pauli Born repulsive energy\n• (𝜎/r)⁶ : Long-range London dispersion attraction\n• 𝜖 : Potential well depth",
                            fontSize = 10.sp,
                            color = Color.LightGray,
                            lineHeight = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Interactive Potential Graph
                Text(
                    text = "Lennard-Jones Energy Well Curve",
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color(0x22FFA726), RoundedCornerShape(8.dp))
                ) {
                    val w = size.width
                    val h = size.height
                    
                    val zeroY = h * 0.3f // zero baseline energy
                    drawLine(Color(0x33FFFFFF), Offset(0f, zeroY), Offset(w, zeroY))
                    drawLine(Color(0x33FFFFFF), Offset(w * 0.25f, 0f), Offset(w * 0.25f, h))

                    val path = Path()
                    var first = true
                    val wInt = w.toInt()
                    val dW = w - 40f
                    val safeDW = if (dW <= 0f) 100f else dW
                    for (x in 20..wInt) {
                        val rVal = (x - 20) / safeDW * 4f + 0.9f // relative distance 0.9 to 4.9
                        if (rVal < 1.0f) continue
                        
                        // LJ Formula: V = 4 * ((1/r)^12 - (1/r)^6)
                        val r6 = rVal.pow(6)
                        val r12 = r6 * r6
                        val v = 3.5f * ((1f / r12) - (1f / r6)) // Scaled
                        
                        val yCoord = zeroY - v * (h * 0.35f)
                        if (yCoord in 0f..h && !yCoord.isNaN() && !yCoord.isInfinite()) {
                            if (first) {
                                path.moveTo(x.toFloat(), yCoord)
                                      first = false
                            } else {
                                path.lineTo(x.toFloat(), yCoord)
                            }
                        }
                    }
                    drawPath(path, Color(0xFFFFA726), style = Stroke(width = 2.dp.toPx()))

                    // Draw current state dot if hydrogen/oxygen bonding exists!
                    // Highlight optimal r_m = 1.122 * sigma
                    val minX = 20f + (1.122f - 0.9f) / 4f * safeDW
                    val minY = zeroY + 3.5f * 0.25f * (h * 0.35f) // Potential well minimum
                    if (!minX.isNaN() && !minY.isNaN() && !minX.isInfinite() && !minY.isInfinite()) {
                        drawCircle(Color.Red, radius = 4.dp.toPx(), center = Offset(minX, minY))
                    }
                }
                Text(
                    text = "Red dot shows the equilibrium bond length (r_m) where attractive/repulsive forces cancel matching net zero force.",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Subsection 3: Active State & Reaction Dynamics
                Text(
                    text = "3. Sandbox Reactions Thermodynamics",
                    color = Color(0xFF81D4FA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                // Show dynamic thermodynamic state parameters
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0x1A00E5FF)),
                    border = BorderStroke(1.dp, Color(0x4400E5FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("System Temp:", color = Color.LightGray, fontSize = 11.sp)
                            Text(String.format(java.util.Locale.US, "%.2f K", temperatureK), color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Atoms Count:", color = Color.LightGray, fontSize = 11.sp)
                            Text("${atoms.size}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Stable Networks:", color = Color.LightGray, fontSize = 11.sp)
                            Text("$moleculesCount", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }

                if (detectedMolecules.isNotEmpty()) {
                    Text(
                        text = "Detected Compounds & Energies:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    detectedMolecules.forEach { molecule ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x22101F30)),
                            border = BorderStroke(1.dp, Color(0x3300C853)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${molecule.name} (${molecule.formula})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF00C853)
                                    )
                                    Badge(containerColor = Color(0xFF00C853), contentColor = Color.Black) {
                                        Text(molecule.geometry, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Hybridization: ${molecule.hybridisation}  |  Angle: ${molecule.bondAngle}",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Gibbs Free Energy Equation:",
                                    fontSize = 9.sp,
                                    color = Color.Gray
                                )

                                // Real-time calculation of free energy change based on user temperature!
                                // ΔG = ΔH - T * ΔS
                                // Approximate ΔS based on general states
                                val sJoules = when (molecule.name) {
                                    "Water" -> -163.0 // J/(mol*K)
                                    "Carbon Dioxide" -> -2.0
                                    "Methane" -> -80.0
                                    "Ammonia" -> -198.0
                                    else -> -50.0
                                }
                                val deltaH = molecule.enthalpyOfFormationKj
                                val tSeconds = temperatureK
                                val deltaG = deltaH - (tSeconds * (sJoules / 1000.0))

                                Text(
                                    text = String.format(java.util.Locale.US, "ΔG = ΔH - TΔS\n= %.1f - %.1f • (%.3f kJ)\n= %.2f kJ/mol", deltaH, temperatureK, sJoules/1000.0, deltaG),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (deltaG < 0) Color(0xFF81C4FF) else Color(0xFFFFB74D),
                                    modifier = Modifier
                                        .background(Color(0x22000000), RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                        .fillMaxWidth()
                                )
                                Text(
                                    text = if (deltaG < 0) "Spontaneous Reaction (exergonic) at current T." else "Non-spontaneous state (endergonic). Needs active heat excitation.",
                                    fontSize = 8.sp,
                                    color = if (deltaG < 0) Color(0xAA81C4FF) else Color(0xAAFFB74D),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Establish bonds between complementary elements to simulate chemical products and view real-time thermodynamic Gibb's free energies.",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
