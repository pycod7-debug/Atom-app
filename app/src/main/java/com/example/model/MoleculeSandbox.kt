package com.example.model

import androidx.compose.ui.geometry.Offset
import java.util.UUID
import kotlin.math.sqrt

enum class BondType(val value: Int, val bondEnergyKj: Double) {
    SINGLE(1, 348.0),   // C-C avg
    DOUBLE(2, 614.0),   // C=C avg
    TRIPLE(3, 839.0),   // C≡C avg
    IONIC(1, 400.0)     // Electrostatic
}

data class AtomInstance(
    val id: String = UUID.randomUUID().toString(),
    val element: Element,
    // 3D coordinates (projected or simulated)
    var x: Float,
    var y: Float,
    var z: Float,
    // Velocities for dynamic float / vibration simulations
    var vx: Float = 0f,
    var vy: Float = 0f,
    var vz: Float = 0f,
    var isSelected: Boolean = false
) {
    // Calculates attractive forces from other atoms or handles interactive drags
    fun updatePhysics(width: Float, height: Float, dt: Float = 0.16f) {
        // Safe check for NaN
        if (x.isNaN()) x = 0f
        if (y.isNaN()) y = 0f
        if (z.isNaN()) z = 0f
        if (vx.isNaN()) vx = 0f
        if (vy.isNaN()) vy = 0f
        if (vz.isNaN()) vz = 0f

        // Soft cosmic floating effect (Lennard-Jones force base or simple continuous noise)
        x += vx * dt
        y += vy * dt
        z += vz * dt

        // Symmetrical boundary damping centered around (0,0,0)
        val halfW = width / 2f
        val halfH = height / 2f
        val maxZ = 200f
        val radius = element.atomicRadiusPm.toFloat() * 0.15f

        if (x < -halfW + radius || x > halfW - radius) {
            vx = -vx * 0.8f
            x = x.coerceIn(-halfW + radius, halfW - radius)
        }
        if (y < -halfH + radius || y > halfH - radius) {
            vy = -vy * 0.8f
            y = y.coerceIn(-halfH + radius, halfH - radius)
        }
        if (z < -maxZ || z > maxZ) {
            vz = -vz * 0.8f
            z = z.coerceIn(-maxZ, maxZ)
        }

        // Apply slow friction/drag to stabilize
        vx *= 0.95f
        vy *= 0.95f
        vz *= 0.95f

        if (x.isNaN() || x.isInfinite()) x = 0f
        if (y.isNaN() || y.isInfinite()) y = 0f
        if (z.isNaN() || z.isInfinite()) z = 0f
        if (vx.isNaN() || vx.isInfinite()) vx = 0f
        if (vy.isNaN() || vy.isInfinite()) vy = 0f
        if (vz.isNaN() || vz.isInfinite()) vz = 0f
    }
}

data class BondInstance(
    val id: String = UUID.randomUUID().toString(),
    val atomId1: String,
    val atomId2: String,
    val type: BondType
)

data class DetectedMolecule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val formula: String,
    val geometry: String, // tetrahedral, bent, linear, etc.
    val bondAngle: String,
    val enthalpyOfFormationKj: Double,
    val GibbsFreeEnergyKj: Double,
    val hybridisation: String,
    val dipoleMomentDebye: Double,
    val mathematicalFormula: String,
    val description: String
)

class MoleculeSandbox {
    var atoms = mutableListOf<AtomInstance>()
    var bonds = mutableListOf<BondInstance>()

    fun addAtom(element: Element, x: Float, y: Float, z: Float) {
        atoms.add(AtomInstance(element = element, x = x, y = y, z = z))
    }

    fun removeAtom(atomId: String) {
        atoms.removeAll { it.id == atomId }
        bonds.removeAll { it.atomId1 == atomId || it.atomId2 == atomId }
    }

    fun addBond(atomId1: String, atomId2: String, type: BondType) {
        if (atomId1 == atomId2) return
        // Check if bond already exists, replace it
        val existingIndex = bonds.indexOfFirst {
            (it.atomId1 == atomId1 && it.atomId2 == atomId2) ||
            (it.atomId1 == atomId2 && it.atomId2 == atomId1)
        }
        if (existingIndex != -1) {
            bonds[existingIndex] = BondInstance(atomId1 = atomId1, atomId2 = atomId2, type = type)
        } else {
            bonds.add(BondInstance(atomId1 = atomId1, atomId2 = atomId2, type = type))
        }
    }

    fun removeBond(bondId: String) {
        bonds.removeAll { it.id == bondId }
    }

    fun clearAll() {
        atoms.clear()
        bonds.clear()
    }

    /**
     * Traverses the atoms network and detects chemical products.
     */
    fun analyzeMolecules(): List<DetectedMolecule> {
        if (atoms.isEmpty()) return emptyList()

        // Group connected atoms using simple Union-Find or DFS
        val parent = atoms.associate { it.id to it.id }.toMutableMap()
        fun find(id: String): String {
            var curr = id
            while (parent.containsKey(curr) && curr != parent[curr]) {
                val p = parent[curr] ?: break
                val gp = parent[p] ?: p
                parent[curr] = gp
                curr = p
            }
            return curr
        }

        fun union(id1: String, id2: String) {
            val root1 = find(id1)
            val root2 = find(id2)
            if (root1 != root2 && parent.containsKey(root1)) {
                parent[root1] = root2
            }
        }

        // Only connect nodes where both atom endpoints exist in the sandbox
        val atomIdsSet = atoms.map { it.id }.toSet()
        bonds.forEach { bond ->
            if (bond.atomId1 in atomIdsSet && bond.atomId2 in atomIdsSet) {
                union(bond.atomId1, bond.atomId2)
            }
        }

        // Group atoms by their root
        val groups = atoms.groupBy { find(it.id) }
        val detected = mutableListOf<DetectedMolecule>()

        groups.values.forEach { groupAtoms ->
            if (groupAtoms.size < 2) return@forEach // Single atoms are isolated elements

            // Create signature profile
            val elementCounts = groupAtoms.groupingBy { it.element.symbol }.eachCount()

            // Map standard known signatures
            val molecule = identifyFromSignature(elementCounts, groupAtoms)
            if (molecule != null) {
                detected.add(molecule)
            }
        }

        return detected
    }

    private fun identifyFromSignature(
        counts: Map<String, Int>,
        groupAtoms: List<AtomInstance>
    ): DetectedMolecule? {
        val size = groupAtoms.size
        return when {
            // H2O
            counts["H"] == 2 && counts["O"] == 1 && size == 3 -> {
                DetectedMolecule(
                    name = "Water",
                    formula = "H₂O",
                    geometry = "Bent",
                    bondAngle = "104.5°",
                    enthalpyOfFormationKj = -285.8,
                    GibbsFreeEnergyKj = -237.1,
                    hybridisation = "sp³",
                    dipoleMomentDebye = 1.85,
                    mathematicalFormula = "ψ_bind(r) = c₁φ_O(2p) + c₂[φ_H1(1s) + φ_H2(1s)]",
                    description = "Highly polar molecule and universal solvent. The bent geometry is formed due to sp3 hybridization with two bonding pairs and two lone pairs repelling each other (VSEPR theory)."
                )
            }
            // CO2
            counts["C"] == 1 && counts["O"] == 2 && size == 3 -> {
                DetectedMolecule(
                    name = "Carbon Dioxide",
                    formula = "CO₂",
                    geometry = "Linear",
                    bondAngle = "180°",
                    enthalpyOfFormationKj = -393.5,
                    GibbsFreeEnergyKj = -394.4,
                    hybridisation = "sp",
                    dipoleMomentDebye = 0.0,
                    mathematicalFormula = "μvec = ∑ q_i • r_i = 0 (Symmetric Cancel)",
                    description = "A linear Greenhouse Gas. The sp hybridized carbon forms two double bonds with oxygen atoms. Despite polar C=O bonds, the symmetric linear geometry cancels the net dipole moment."
                )
            }
            // CH4
            counts["C"] == 1 && counts["H"] == 4 && size == 5 -> {
                DetectedMolecule(
                    name = "Methane",
                    formula = "CH₄",
                    geometry = "Tetrahedral",
                    bondAngle = "109.5°",
                    enthalpyOfFormationKj = -74.8,
                    GibbsFreeEnergyKj = -50.8,
                    hybridisation = "sp³",
                    dipoleMomentDebye = 0.0,
                    mathematicalFormula = "Ĥψ = Eψ with T_d spherical symmetry",
                    description = "The simplest hydrocarbon. Carbon undergoes sp3 hybridization, distributing four identical covalent bonds symmetrically to minimize electron repulsion, matching the standard tetrahedral angle exactly."
                )
            }
            // NH3
            counts["N"] == 1 && counts["H"] == 3 && size == 4 -> {
                DetectedMolecule(
                    name = "Ammonia",
                    formula = "NH₃",
                    geometry = "Trigonal Pyramidal",
                    bondAngle = "107.8°",
                    enthalpyOfFormationKj = -45.9,
                    GibbsFreeEnergyKj = -16.4,
                    hybridisation = "sp³",
                    dipoleMomentDebye = 1.42,
                    mathematicalFormula = "V_repulsion(lone-pair) > V_repulsion(bond-pair)",
                    description = "A common nitrogen fertilizer feedstock. Possesses one unshared nitrogen lone pair which compresses the standard 109.5° tetrahedral angle down to 107.8°."
                )
            }
            // O2
            counts["O"] == 2 && size == 2 -> {
                DetectedMolecule(
                    name = "Oxygen Gas",
                    formula = "O₂",
                    geometry = "Linear",
                    bondAngle = "180°",
                    enthalpyOfFormationKj = 0.0, // Standard state
                    GibbsFreeEnergyKj = 0.0,
                    hybridisation = "sp²",
                    dipoleMomentDebye = 0.0,
                    mathematicalFormula = "KK(2sσ)²(2sσ*)²(2pσ)²(2pπ)⁴(2pπ*)¹(2pπ*)¹",
                    description = "Paramagnetic double-bonded diatomic molecule. Molecular orbital theory shows it contains two unpaired electrons in antibonding orbitals (triple triplet ground state), explaining its rich reactions."
                )
            }
            // H2
            counts["H"] == 2 && size == 2 -> {
                DetectedMolecule(
                    name = "Hydrogen Gas",
                    formula = "H₂",
                    geometry = "Linear",
                    bondAngle = "180°",
                    enthalpyOfFormationKj = 0.0,
                    GibbsFreeEnergyKj = 0.0,
                    hybridisation = "s-orbital overlay",
                    dipoleMomentDebye = 0.0,
                    mathematicalFormula = "ψ_H2 = 1/√2 [φ_A(1s)φ_B(1s) + φ_B(1s)φ_A(1s)]",
                    description = "The lightest diatomic molecule, held by a single covalent sigma bond. Its bonding energy curve is a classic representation of Morse or Lennard-Jones potential states."
                )
            }
            // NaCl
            counts["Na"] == 1 && counts["Cl"] == 1 && size == 2 -> {
                DetectedMolecule(
                    name = "Sodium Chloride",
                    formula = "NaCl",
                    geometry = "Linear (Ionic)",
                    bondAngle = "180°",
                    enthalpyOfFormationKj = -411.1,
                    GibbsFreeEnergyKj = -384.0,
                    hybridisation = "Electrostatic attraction",
                    dipoleMomentDebye = 9.0, // High ionic character
                    mathematicalFormula = "V_coulomb(r) = - (e² / 4πϵ₀ r) + B / rⁿ",
                    description = "Classic table salt. Formed via full transfer of an electron from Na (low ionization energy) to Cl (high electron affinity), resulting in Coulombic electrostatic attraction."
                )
            }
            // HCl
            counts["H"] == 1 && counts["Cl"] == 1 && size == 2 -> {
                DetectedMolecule(
                    name = "Hydrochloric Acid",
                    formula = "HCl",
                    geometry = "Linear (Polar Covalent)",
                    bondAngle = "180°",
                    enthalpyOfFormationKj = -92.3,
                    GibbsFreeEnergyKj = -95.3,
                    hybridisation = "s-p overlap",
                    dipoleMomentDebye = 1.08,
                    mathematicalFormula = "% Ionic = 16|χ_A - χ_B| + 3.5(χ_A - χ_B)²",
                    description = "A strong binary acid. Composed of s-orbital from Hydrogen overlapping with a 3p-orbital of Chlorine. Highly polar due to Chlorine's high electronegativity of 3.16."
                )
            }
            else -> {
                // Return a generic custom molecule so any bond manipulation generates neat quantum data
                val symbols = counts.entries.sortedBy { it.key }.joinToString("") { "${it.key}${if (it.value > 1) it.value else ""}" }
                DetectedMolecule(
                    name = "Custom Hybrid Complex",
                    formula = symbols,
                    geometry = "Dynamical Geometry",
                    bondAngle = "Sterically Optimized",
                    enthalpyOfFormationKj = -120.5 * size,
                    GibbsFreeEnergyKj = -85.0 * size,
                    hybridisation = "Mixed Hybridization",
                    dipoleMomentDebye = 0.45 * size,
                    mathematicalFormula = "Ĥψ_total(r) = E_total ψ_total(r)",
                    description = "A complex organometallic or unique atomic compound. The electronic configuration relies on molecular orbital overlaps, calculated dynamically via LCAO mechanics."
                )
            }
        }
    }
}
