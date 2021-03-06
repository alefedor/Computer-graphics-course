package util


import assimp.*
import glm_.set
import gln.draw.glDrawElements
import gln.get
import gln.glf.glf
import gln.glf.semantic
import gln.texture.glBindTexture
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
import uno.buffer.destroy
import uno.buffer.floatBufferBig
import uno.buffer.intBufferBig
import uno.buffer.intBufferOf
import java.nio.IntBuffer

class Mesh(mesh: AiMesh, scene: AiScene) {
    private val vao = intBufferBig(1)
    enum class Buffer { Vertex, Element }

    private val buffers = intBufferBig<Buffer>()
    private val indexCount: Int
    private var diffuseMap: IntBuffer? = null
    private var specularMap: IntBuffer? = null

    init {
        glGenVertexArrays(vao)
        glGenBuffers(buffers)
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, buffers[Buffer.Vertex])
        val vertexSize = 3 + 3 + 2 // vertex, normal, texture coords
        val vertices = floatBufferBig(vertexSize * mesh.numVertices)
        mesh.vertices.forEachIndexed { i, v ->
            val n = mesh.normals[i]
            v.to(vertices, i * vertexSize)
            n.to(vertices, i * vertexSize + 3)
            if (mesh.textureCoords[0].isNotEmpty()) {
                val tc = mesh.textureCoords[0][i]
                vertices[i * vertexSize + 3 + 3] = tc[0]
                vertices[i * vertexSize + 3 + 3 + 1] = tc[1]
            }
        }
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, buffers[Buffer.Element])
        indexCount = mesh.numFaces * 3
        val indices = intBufferBig(indexCount)
        repeat(indexCount) { indices[it] = mesh.faces[it / 3][it % 3] }
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW)
        glEnableVertexAttribArray(semantic.attr.POSITION)
        glVertexAttribPointer(glf.pos3_nor3_tc2)
        glEnableVertexAttribArray(semantic.attr.NORMAL)
        glVertexAttribPointer(glf.pos3_nor3_tc2[1])
        glEnableVertexAttribArray(semantic.attr.TEX_COORD)
        glVertexAttribPointer(glf.pos3_nor3_tc2[2])
        glBindVertexArray()
        with(scene.materials[mesh.materialIndex]) {
            textures.firstOrNull { it.type == AiTexture.Type.diffuse }?.let {
                diffuseMap = intBufferOf(loadMaterialTexture(it, scene))
            }
            textures.firstOrNull { it.type == AiTexture.Type.specular }?.let {
                specularMap = intBufferOf(loadMaterialTexture(it, scene))
            }
        }
    }

    fun loadMaterialTexture(texture: AiMaterial.Texture, scene: AiScene) = initTexture2d {
        val gliTexture = scene.textures[texture.file]!!
        image(gliTexture)
        glGenerateMipmap(GL_TEXTURE_2D)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
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