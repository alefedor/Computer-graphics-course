package util

import assimp.Importer
import uno.kotlin.uri
import java.lang.RuntimeException

class Model() {
   private val meshes = ArrayList<Mesh>()

    constructor(path: String) : this() {
        val scene = Importer().readFile(path.uri, 0) ?: throw RuntimeException("Scene not loaded!")
        scene.meshes.forEach { meshes += ModelMesh(it, scene) }
    }

    constructor(mesh: Mesh) : this() {
        meshes += mesh
    }

    fun draw() = meshes.forEach { it.draw() }

    fun dispose() = meshes.forEach(Mesh::dispose)
}
