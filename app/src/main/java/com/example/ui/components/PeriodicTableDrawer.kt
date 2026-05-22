package com.example.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Element
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add

@Composable
fun PeriodicTableDrawer(
    selectedElementId: Int,
    onElementSelect: (Int) -> Unit,
    onAddAtomToSandbox: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("Classic Grid") }
    val elementDetails = remember(selectedElementId) { Element.elementsMap[selectedElementId]!! }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFA0A0C10)) // Immersive slate dark glass backing
            .padding(16.dp)
    ) {
        // Heading & Info panel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ELEMENT CATALOGUE",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            // Tabs to toggle representation
            Row(
                modifier = Modifier
                    .background(Color(0x40000000), RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                listOf("Classic Grid", "Classified List").forEach { tab ->
                    Text(
                        text = tab,
                        modifier = Modifier
                            .clickable { activeTab = tab }
                            .background(
                                if (activeTab == tab) Color(0xFF00E5FF) else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == tab) Color.Black else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected Element HUD details
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x660A0C10)),
            border = BorderStroke(1.2.dp, elementDetails.cpkColor.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Giant element visual
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(elementDetails.cpkColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .border(2.dp, elementDetails.cpkColor, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${elementDetails.atomicNumber}",
                            fontSize = 10.sp,
                            color = elementDetails.cpkColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start).padding(start = 4.dp, top = 2.dp)
                        )
                        Text(
                            text = elementDetails.symbol,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.2f", elementDetails.atomicMass),
                            fontSize = 8.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Detail specifications
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = elementDetails.name.uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${elementDetails.category}  |  State: ${elementDetails.stateAtStp}",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Radius: ${elementDetails.atomicRadiusPm} pm  |  Electronegativity: ${elementDetails.electronegativity}  |  Valence: ${elementDetails.valenceElectrons}e⁻",
                        fontSize = 10.sp,
                        color = Color(0xFF00E5FF),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Place Atom Button
                Button(
                    onClick = onAddAtomToSandbox,
                    colors = ButtonDefaults.buttonColors(containerColor = elementDetails.cpkColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", tint = if (elementDetails.cpkColor == Color(0xFFECEFF1)) Color.Black else Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "ADD",
                        fontWeight = FontWeight.Bold,
                        color = if (elementDetails.cpkColor == Color(0xFFECEFF1)) Color.Black else Color.White,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Element selection grid
        if (activeTab == "Classic Grid") {
            // Render beautiful authentic horizontally-scrolling Periodic Grid!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                // Grid layout based on Periods (Z rows) and Groups (Z columns)
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (periodIndex in 1..9) { // 1 to 7 main, 8-9 for lanthanides/actinides
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (groupIndex in 1..18) {
                                val atomNum = getAtomicNumberForGrid(periodIndex, groupIndex)
                                if (atomNum != null) {
                                    val element = Element.elementsMap[atomNum]!!
                                    val isSelected = element.atomicNumber == selectedElementId
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(width = 32.dp, height = 36.dp)
                                            .background(
                                                if (isSelected) element.cpkColor else element.cpkColor.copy(alpha = 0.15f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else element.cpkColor.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onElementSelect(element.atomicNumber) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${element.atomicNumber}",
                                                fontSize = 7.sp,
                                                color = if (isSelected) Color.Black else element.cpkColor,
                                                modifier = Modifier.align(Alignment.Start).padding(start = 2.dp)
                                            )
                                            Text(
                                                text = element.symbol,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) Color.Black else Color.White
                                            )
                                        }
                                    }
                                } else {
                                    // Spacing gap in standard periodic table layout
                                    Spacer(modifier = Modifier.size(width = 32.dp, height = 36.dp))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // List sorted and categorized programmatically
            LazyVerticalGrid(
                columns = GridCells.Adaptive(85.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(Element.elementsMap.values.toList()) { element ->
                    val isSelected = element.atomicNumber == selectedElementId
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) element.cpkColor else Color(0x331E293B)
                        ),
                        border = BorderStroke(1.dp, element.cpkColor.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onElementSelect(element.atomicNumber) }
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${element.atomicNumber} ${element.symbol}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(element.cpkColor, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

// Maps standard chemistry layout row (Period) and col (Group) into elements list atomic numbers
private fun getAtomicNumberForGrid(period: Int, group: Int): Int? {
    return when (period) {
        1 -> when (group) {
            1 -> 1   // H
            18 -> 2  // He
            else -> null
        }
        2 -> when (group) {
            1 -> 3    // Li
            2 -> 4    // Be
            in 13..18 -> group - 8 // B(5) to Ne(10)
            else -> null
        }
        3 -> when (group) {
            1 -> 11   // Na
            2 -> 12   // Mg
            in 13..18 -> group // Al(13) to Ar(18)
            else -> null
        }
        4 -> group + 18 // K(19) to Kr(36)
        5 -> group + 36 // Rb(37) to Xe(54)
        6 -> when (group) {
            1 -> 55  // Cs
            2 -> 56  // Ba
            3 -> null // Lanthanide placeholder gap (drawn in table bottom row 8)
            in 4..18 -> group + 68 // Hf(72) to Rn(86)
            else -> null
        }
        7 -> when (group) {
            1 -> 87  // Fr
            2 -> 88  // Ra
            3 -> null // Actinide placeholder gap (drawn in table bottom row 9)
            in 4..18 -> group + 100 // Rf(104) to Og(118)
            else -> null
        }
        // Lanthanide segment at bottom offsets
        8 -> when (group) {
            in 4..18 -> group + 53 // La(57) to Lu(71)
            else -> null
        }
        // Actinide segment at bottom offsets
        9 -> when (group) {
            in 4..18 -> group + 85 // Ac(89) to Lr(103)
            else -> null
        }
        else -> null
    }
}
