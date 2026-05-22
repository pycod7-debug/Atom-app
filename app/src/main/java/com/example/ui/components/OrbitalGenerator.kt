package com.example.ui.components

import kotlin.math.*

data class OrbitalPoint(
    val x: Float,
    val y: Float,
    val z: Float,
    val probability: Float // Determines alpha transparency
)

object OrbitalGenerator {

    /**
     * Generates a list of 3D point cloud coordinates representing electron density
     * according to quantum mechanical wavefunctions (Schrödinger equations).
     */
    fun generateOrbital(type: String, count: Int = 1200): List<OrbitalPoint> {
        val points = mutableListOf<OrbitalPoint>()
        var generated = 0
        var attempts = 0
        val maxRadius = 160f

        while (generated < count && attempts < count * 20) {
            attempts++
            // Generate a point in spherical space
            val r = (Math.random() * maxRadius).toFloat()
            val theta = (Math.random() * PI).toFloat()
            val phi = (Math.random() * 2 * PI).toFloat()

            // Calculate wavefunction amplitude Psi(r, theta, phi)
            // Values are scaled for aesthetic rendering
            val psi = when (type.lowercase()) {
                "s" -> {
                    // 1s orbital: Psi ~ e^(-r / 30)
                    exp(-r / 35f)
                }
                "p_z" -> {
                    // 2pz orbital: Psi ~ r * cos(theta) * e^(-r / 50)
                    val cosTheta = cos(theta)
                    (r / 25f) * cosTheta * exp(-r / 45f)
                }
                "p_x" -> {
                    // 2px orbital: Psi ~ r * sin(theta) * cos(phi) * e^(-r/50)
                    val sinTheta = sin(theta)
                    val cosPhi = cos(phi)
                    (r / 25f) * sinTheta * cosPhi * exp(-r / 45f)
                }
                "p_y" -> {
                    // 2py orbital: Psi ~ r * sin(theta) * sin(phi) * e^(-r/50)
                    val sinTheta = sin(theta)
                    val sinPhi = sin(phi)
                    (r / 25f) * sinTheta * sinPhi * exp(-r / 45f)
                }
                "d" -> {
                    // 3dz2 orbital: Psi ~ r^2 * (3cos^2(theta) - 1) * e^(-r / 60)
                    val cosTheta = cos(theta)
                    val term = 3 * cosTheta * cosTheta - 1f
                    (r * r / 1200f) * term * exp(-r / 50f)
                }
                else -> exp(-r / 35f) // Default to s
            }

            // Probability density is |Psi|^2
            val prob = psi * psi
            
            // Acceptance-rejection sampling
            if (prob > Math.random() * 0.95 / (if (type == "d") 1.8 else 1.0)) {
                // Convert spherical to Cartesians
                val px = r * sin(theta) * cos(phi)
                val py = r * sin(theta) * sin(phi)
                val pz = r * cos(theta)
                
                points.add(OrbitalPoint(px, py, pz, prob.coerceIn(0.08f, 1.0f)))
                generated++
            }
        }
        return points
    }
}
