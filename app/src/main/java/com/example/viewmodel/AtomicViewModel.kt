package com.example.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.*

class AtomicViewModel : ViewModel() {

    private val sandbox = MoleculeSandbox()

    // Exposed States
    private val _atomsList = MutableStateFlow<List<AtomInstance>>(emptyList())
    val atomsList = _atomsList.asStateFlow()

    private val _bondsList = MutableStateFlow<List<BondInstance>>(emptyList())
    val bondsList = _bondsList.asStateFlow()

    private val _detectedMolecules = MutableStateFlow<List<DetectedMolecule>>(emptyList())
    val detectedMolecules = _detectedMolecules.asStateFlow()

    // 3D camera properties
    val cameraRotX = mutableStateOf(30f)
    val cameraRotY = mutableStateOf(-45f)
    val cameraZoom = mutableStateOf(1.0f)

    // Selection properties
    val selectedAtomId = mutableStateOf<String?>(null)
    val selectedBondId = mutableStateOf<String?>(null)
    val activeElementId = mutableStateOf(1) // Hydrogen default

    // Orbital configuration toggles
    val representationMode = mutableStateOf("Quantum Probability Cloud") // Classical Bohr, Quantum Probability Cloud, Wireframe Orbitals
    val orbitalTypeToShow = mutableStateOf("s") // "s", "p_x", "p_y", "p_z", "d"

    // Math Labs configs
    val isMathLabsExpanded = mutableStateOf(true)
    val simulationTemperatureK = mutableStateOf(298.15f) // RT default
    val autoVibrateBonds = mutableStateOf(true)

    init {
        // Pre-create some molecules so sandbox starts loaded with cool hyperrealistic orbitals & bonds!
        loadPresetWater()
    }

    fun loadPresetWater() {
        sandbox.clearAll()
        // Oxygen in the center
        sandbox.addAtom(Element.elementsMap[8]!!, 0f, 0f, 0f)
        // Two hydrogens with typical bent molecular positions
        val theta = 104.5 * PI / 180.0
        val bondLength = 96.0f * 1.5f // Scaled pm
        
        val h1x = bondLength * sin(theta / 2.0).toFloat()
        val h1y = bondLength * cos(theta / 2.0).toFloat()
        val h2x = bondLength * -sin(theta / 2.0).toFloat()
        val h2y = bondLength * cos(theta / 2.0).toFloat()

        sandbox.addAtom(Element.elementsMap[1]!!, h1x, h1y, 0f)
        sandbox.addAtom(Element.elementsMap[1]!!, h2x, h2y, 10f)

        // Find and link bonds
        val oxy = sandbox.atoms[0]
        val h1 = sandbox.atoms[1]
        val h2 = sandbox.atoms[2]
        
        sandbox.addBond(oxy.id, h1.id, BondType.SINGLE)
        sandbox.addBond(oxy.id, h2.id, BondType.SINGLE)

        syncState()
    }

    fun loadPresetCarbonDioxide() {
        sandbox.clearAll()
        // Carbon in the center
        sandbox.addAtom(Element.elementsMap[6]!!, 0f, 0f, 0f)
        val bondLength = 116.0f * 1.5f // Scaled C=O bond length
        
        // Two oxygens in linear configuration
        sandbox.addAtom(Element.elementsMap[8]!!, -bondLength, 0f, 0f)
        sandbox.addAtom(Element.elementsMap[8]!!, bondLength, 0f, 0f)

        val carbon = sandbox.atoms[0]
        val oxy1 = sandbox.atoms[1]
        val oxy2 = sandbox.atoms[2]

        sandbox.addBond(carbon.id, oxy1.id, BondType.DOUBLE)
        sandbox.addBond(carbon.id, oxy2.id, BondType.DOUBLE)

        syncState()
    }

    fun loadPresetSalt() {
        sandbox.clearAll()
        // Sodium and Chlorine ionic bond
        sandbox.addAtom(Element.elementsMap[11]!!, -120f, 0f, 0f)
        sandbox.addAtom(Element.elementsMap[17]!!, 120f, 0f, 0f)

        sandbox.addBond(sandbox.atoms[0].id, sandbox.atoms[1].id, BondType.IONIC)
        syncState()
    }

    fun loadPresetMethane() {
        sandbox.clearAll()
        // Carbon in center
        sandbox.addAtom(Element.elementsMap[6]!!, 0f, 0f, 0f)
        // 4 hydrogen in tetrahedral arrangement
        val d = 109.0f * 1.4f
        sandbox.addAtom(Element.elementsMap[1]!!, 0f, d, 0f)
        sandbox.addAtom(Element.elementsMap[1]!!, d * 0.94f, -d * 0.33f, 0f)
        sandbox.addAtom(Element.elementsMap[1]!!, -d * 0.47f, -d * 0.33f, d * 0.82f)
        sandbox.addAtom(Element.elementsMap[1]!!, -d * 0.47f, -d * 0.33f, -d * 0.82f)

        val carbon = sandbox.atoms[0]
        for (i in 1..4) {
            sandbox.addBond(carbon.id, sandbox.atoms[i].id, BondType.SINGLE)
        }
        syncState()
    }

    fun addNewAtom() {
        val element = Element.elementsMap[activeElementId.value] ?: return
        // Give a slightly randomized placement near the center to make it easily selectable
        val rx = (Math.random() * 120 - 60).toFloat()
        val ry = (Math.random() * 120 - 60).toFloat()
        val rz = (Math.random() * 120 - 60).toFloat()
        
        sandbox.addAtom(element, rx, ry, rz)
        syncState()
    }

    fun handleAtomTap(clickedId: String, multiSelect: Boolean = false) {
        if (multiSelect) {
            val idx = sandbox.atoms.indexOfFirst { it.id == clickedId }
            if (idx != -1) {
                sandbox.atoms[idx] = sandbox.atoms[idx].copy(isSelected = !sandbox.atoms[idx].isSelected)
            }
        } else {
            // Deselect all and select single
            sandbox.atoms.forEachIndexed { i, a ->
                sandbox.atoms[i] = a.copy(isSelected = a.id == clickedId)
            }
            selectedAtomId.value = clickedId
            selectedBondId.value = null
        }
        syncState()
    }

    fun clearSelection() {
        sandbox.atoms.forEachIndexed { i, a ->
            sandbox.atoms[i] = a.copy(isSelected = false)
        }
        selectedAtomId.value = null
        selectedBondId.value = null
        syncState()
    }

    fun moveSelectedAtom(dx: Float, dy: Float, dz: Float) {
        val selId = selectedAtomId.value ?: return
        val idx = sandbox.atoms.indexOfFirst { it.id == selId }
        if (idx != -1) {
            val a = sandbox.atoms[idx]
            a.x += dx
            a.y += dy
            a.z += dz
            syncState()
        }
    }

    fun establishBondBetweenSelected(type: BondType) {
        val selectedIds = sandbox.atoms.filter { it.isSelected }.map { it.id }
        if (selectedIds.size == 2) {
            sandbox.addBond(selectedIds[0], selectedIds[1], type)
            clearSelection()
        } else if (selectedAtomId.value != null && selectedIds.size < 2) {
            // If they only selected one primary atom, try to bind with any other selected
            // or we show a dialog
        }
    }

    fun deleteSelectedAtom() {
        val selId = selectedAtomId.value
        if (selId != null) {
            sandbox.removeAtom(selId)
            selectedAtomId.value = null
            syncState()
        }
    }

    fun deleteSelectedBond() {
        val bId = selectedBondId.value
        if (bId != null) {
            sandbox.removeBond(bId)
            selectedBondId.value = null
            syncState()
        }
    }

    fun resetSandbox() {
        sandbox.clearAll()
        clearSelection()
        syncState()
    }

    fun applyThermalJitter(dt: Float = 0.16f) {
        // Higher temperature causes atoms to vibrate and move in 3D faster!
        val tRatio = (simulationTemperatureK.value - 100f) / 1000f // Scale logic
        if (tRatio > 0.05f) {
            // Add tiny random movements
            sandbox.atoms.forEach { atom ->
                atom.vx += (Math.random() * 2 - 1).toFloat() * tRatio * 8f
                atom.vy += (Math.random() * 2 - 1).toFloat() * tRatio * 8f
                atom.vz += (Math.random() * 2 - 1).toFloat() * tRatio * 8f
            }
        }
        
        // Stabilize bonds
        // Attractive and repulsive mechanics to keep bonds solid (molecular mechanics forcefield)
        sandbox.bonds.forEach { bond ->
            val a1 = sandbox.atoms.find { it.id == bond.atomId1 }
            val a2 = sandbox.atoms.find { it.id == bond.atomId2 }
            if (a1 != null && a2 != null) {
                val dx = a2.x - a1.x
                val dy = a2.y - a1.y
                val dz = a2.z - a1.z
                val dist = sqrt(dx*dx + dy*dy + dz*dz)
                
                // Target length based on combined elements radius
                val target = (a1.element.atomicRadiusPm + a2.element.atomicRadiusPm).toFloat() * 1.2f
                if (dist > 5f && !dist.isNaN() && !dist.isInfinite()) {
                    val diff = dist - target
                    // Spring force: F = -k * x
                    val k = 0.12f
                    val fx = k * diff * (dx / dist)
                    val fy = k * diff * (dy / dist)
                    val fz = k * diff * (dz / dist)
                    
                    if (!fx.isNaN() && !fx.isInfinite() &&
                        !fy.isNaN() && !fy.isInfinite() &&
                        !fz.isNaN() && !fz.isInfinite()) {
                        // Simple mass-independent force distribution
                        a1.vx += fx
                        a1.vy += fy
                        a1.vz += fz
                        
                        a2.vx -= fx
                        a2.vy -= fy
                        a2.vz -= fz
                    }
                }
            }
        }

        // Apply boundary dampings and float
        sandbox.atoms.forEach { it.updatePhysics(600f, 600f, dt) }
        _atomsList.value = ArrayList(sandbox.atoms)
    }

    private fun syncState() {
        _atomsList.value = ArrayList(sandbox.atoms)
        _bondsList.value = ArrayList(sandbox.bonds)
        _detectedMolecules.value = sandbox.analyzeMolecules()
    }
}
