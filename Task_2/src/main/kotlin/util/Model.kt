package util

import assimp.Importer
import uno.kotlin.uri
import java.lang.RuntimeException

class Model(path: String) {
   private val meshes = ArrayList<Mesh>()

    init {
        val scene = Importer().readFile(path.uri, 0) ?: throw RuntimeException("Scene not loaded!")
        scene.meshes.forEach { meshes += Mesh(it, scene) }
    }

    fun draw() = meshes.forEach { it.draw() }

    fun dispose() = meshes.forEach(Mesh::dispose)
}
