package util


import assimp.AiMaterial
import assimp.AiMesh
import assimp.AiScene
import assimp.AiTexture
import glm_.set
import glm_.vec3.Vec3
import gln.draw.glDrawElements
import gln.get
import gln.glf.glf
import gln.glf.semantic
import gln.texture.glBindTexture
import gln.texture.glTexImage2D
import gln.texture.initTexture2d
import gln.vertexArray.glBindVertexArray
import gln.vertexArray.glVertexAttribPointer
import gln.vertexArray.withVertexArray
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.glEnableVertexAttribArray
import org.lwjgl.opengl.GL30.*
import uno.buffer.*
import java.nio.IntBuffer

abstract class Mesh(meshVertices: List<Vec3>, meshNormals: List<Vec3>, meshTextureCoords: List<FloatArray>, meshFaces: List<List<Int>>) {
    private val vao = intBufferBig(1)
    enum class Buffer { Vertex, Element }

    private val buffers = intBufferBig<Buffer>()
    private val indexCount: Int
    protected var diffuseMap: IntBuffer? = null
    protected var specularMap: IntBuffer? = null

    init {
        glGenVertexArrays(vao)
        glGenBuffers(buffers)
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, buffers[Buffer.Vertex])
        val vertexSize = 3 + 3 + 2 // vertex, normal, texture coords
        val vertices = floatBufferBig(vertexSize * meshVertices.size)
        meshVertices.forEachIndexed { i, v ->
            val n = meshNormals[i]
            v.to(vertices, i * vertexSize)
            n.to(vertices, i * vertexSize + 3)
            val tc = meshTextureCoords[i]
            vertices[i * vertexSize + 3 + 3] = tc[0]
            vertices[i * vertexSize + 3 + 3 + 1] = tc[1]
        }
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers[Buffer.Element])
        indexCount = meshFaces.size * 3
        val indices = intBufferBig(indexCount)
        repeat(indexCount) { indices[it] = meshFaces[it / 3][it % 3] }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)
        glEnableVertexAttribArray(semantic.attr.POSITION)
        glVertexAttribPointer(glf.pos3_nor3_tc2)
        glEnableVertexAttribArray(semantic.attr.NORMAL)
        glVertexAttribPointer(glf.pos3_nor3_tc2[1])
        glEnableVertexAttribArray(semantic.attr.TEX_COORD)
        glVertexAttribPointer(glf.pos3_nor3_tc2[2])
        glBindVertexArray()
    }

    fun draw() {
        diffuseMap?.let {
            glActiveTexture(GL_TEXTURE0 + semantic.sampler.DIFFUSE)
            glBindTexture(GL_TEXTURE_2D, it)
        }
        specularMap?.let {
            glActiveTexture(GL_TEXTURE0 + semantic.sampler.SPECULAR)
            glBindTexture(GL_TEXTURE_2D, it)
        }
        withVertexArray(vao) {
            glDrawElements(indexCount)
        }
    }

    fun dispose() {
        glDeleteVertexArrays(vao)
        glDeleteBuffers(buffers)
        diffuseMap?.let {
            glDeleteTextures(it)
            it.destroy()
        }
        specularMap?.let {
            glDeleteTextures(it)
            it.destroy()
        }
    }
}

class ModelMesh(mesh: AiMesh, scene: AiScene) : Mesh(
    mesh.vertices,
    mesh.normals,
    calculateTextureCoords(mesh.textureCoords, mesh.vertices.size),
    mesh.faces
) {
    init {
        with(scene.materials[mesh.materialIndex]) {
            textures.firstOrNull { it.type == AiTexture.Type.diffuse }?.let {
                diffuseMap = intBufferOf(loadMaterialTexture(it, scene))
            }
            textures.firstOrNull { it.type == AiTexture.Type.specular }?.let {
                specularMap = intBufferOf(loadMaterialTexture(it, scene))
            }
        }
    }

    private fun loadMaterialTexture(texture: AiMaterial.Texture, scene: AiScene) = initTexture2d {
        val gliTexture = scene.textures[texture.file]!!
        image(gliTexture)
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    }

    companion object {
        fun calculateTextureCoords(coords: List<List<FloatArray>>, vertices: Int): List<FloatArray> {
            if (coords.isNotEmpty()) return coords[0]
            return List(vertices) { FloatArray(2) { 0.0f } }
        }
    }
}

class GroundMesh(scale: Float) : Mesh(
    listOf(Vec3(-1f, -0.0005f, -1f) * scale, Vec3(-1f, -0.0005f, 1f) * scale, Vec3(1f, -0.0005f, -1f) * scale, Vec3(1f, -0.0005f, 1f) * scale),
    listOf(Vec3(0f, 1f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, 1f, 0f)),
    listOf(floatArrayOf(0.0f, 0.0f), floatArrayOf(0.0f, 1.0f), floatArrayOf(1.0f, 0.0f), floatArrayOf(1.0f, 1.0f)),
    listOf(
        listOf(0, 1, 2),
        listOf(1, 3, 2)
    )
) {
    init {
        diffuseMap = intBufferBig(1)
        specularMap = intBufferBig(1)

        glGenTextures(diffuseMap)
        glBindTexture(GL_TEXTURE_2D, diffuseMap!!)
        glTexImage2D(GL_RGB, 1, 1, GL_RGB, GL_UNSIGNED_BYTE, byteArrayOf(75, 105, 60, 75, 105, 60, 75, 105, 60, 75, 105, 60).toBuf())
        glGenerateMipmap(GL_TEXTURE_2D)

        glGenTextures(specularMap)
        glBindTexture(GL_TEXTURE_2D, specularMap!!)
        glTexImage2D(GL_RGB, 1, 1, GL_RGB, GL_UNSIGNED_BYTE, byteArrayOf(0, 0, 0).toBuf())
        glGenerateMipmap(GL_TEXTURE_2D)
    }
}

class CubeMesh() : Mesh(
    vertices,
    normals,
    textureCoords,
    faces
) {
    init {
        diffuseMap = intBufferBig(1)
        specularMap = intBufferBig(1)

        glGenTextures(diffuseMap)
        glBindTexture(GL_TEXTURE_2D, diffuseMap!!)
        glTexImage2D(GL_RGB, 1, 1, GL_RGB, GL_UNSIGNED_BYTE, byteArrayOf(189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte(), 189.toByte()).toBuf())
        glGenerateMipmap(GL_TEXTURE_2D)

        glGenTextures(specularMap)
        glBindTexture(GL_TEXTURE_2D, specularMap!!)
        glTexImage2D(GL_RGB, 1, 1, GL_RGB, GL_UNSIGNED_BYTE, byteArrayOf(0, 0, 0).toBuf())
        glGenerateMipmap(GL_TEXTURE_2D)
    }

    companion object {
        val vertices: MutableList<Vec3> = mutableListOf()
        val normals: MutableList<Vec3> = mutableListOf()

        val faces: MutableList<List<Int>> = mutableListOf()
        val textureCoords: MutableList<FloatArray> = mutableListOf()

        val data = floatArrayOf(
            -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f,
            0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f,
            0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f,
            0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f,
            -0.5f, -0.5f, -0.5f, 0.0f, 0.0f, -1.0f,
            -0.5f, 0.5f, -0.5f, 0.0f, 0.0f, -1.0f,

            -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f,
            0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f,
            0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f,
            0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f,
            -0.5f, 0.5f, 0.5f, 0.0f, 0.0f, 1.0f,
            -0.5f, -0.5f, 0.5f, 0.0f, 0.0f, 1.0f,

            -0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, 0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, -0.5f, 0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, 0.5f, 0.5f, -1.0f, 0.0f, 0.0f,

            0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f,
            0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f,
            0.5f, 0.5f, -0.5f, 1.0f, 0.0f, 0.0f,
            0.5f, -0.5f, -0.5f, 1.0f, 0.0f, 0.0f,
            0.5f, 0.5f, 0.5f, 1.0f, 0.0f, 0.0f,
            0.5f, -0.5f, 0.5f, 1.0f, 0.0f, 0.0f,

            -0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f,
            0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f,
            0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f,
            0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f,
            -0.5f, -0.5f, 0.5f, 0.0f, -1.0f, 0.0f,
            -0.5f, -0.5f, -0.5f, 0.0f, -1.0f, 0.0f,

            -0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f,
            0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f,
            0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f,
            0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f,
            -0.5f, 0.5f, -0.5f, 0.0f, 1.0f, 0.0f,
            -0.5f, 0.5f, 0.5f, 0.0f, 1.0f, 0.0f
        )

        init {
            for (i in 0 until data.size step 6) {
                vertices.add(Vec3(data[i], data[i + 1], data[i + 2]))
                normals.add(Vec3(data[i + 3], data[i + 4], data[i + 5]))
                textureCoords.add(floatArrayOf(0.0f, 0.0f))
            }
            for (i in 0 until data.size / 18)
                faces.add(listOf(3 * i, 3 * i + 1, 3 * i + 2))
        }
    }
}