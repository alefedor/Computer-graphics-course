import glm_.f
import glm_.vec2.Vec2i
import glm_.vec4.Vec4
import gln.glClearColor
import gln.glViewport
import org.lwjgl.opengl.GL11.*
import uno.glfw.GlfwWindow
import uno.glfw.glfw
import uno.glsl.Program
import uno.glsl.glUseProgram
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec3.Vec3
import gln.glf.semantic
import gln.program.ProgramUse.to
import gln.program.usingProgram
import gln.texture.glTexImage2D
import imgui.ImGui
import imgui.WindowFlags
import imgui.functionalProgramming.withWindow
import imgui.impl.LwjglGL3
import imgui.or
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL20.*
import uno.buffer.intBufferBig
import uno.glsl.glDeleteProgram
import util.Model
import kotlin.math.*
import gln.texture.glBindTexture
import org.lwjgl.opengl.*
import org.lwjgl.opengl.ARBDebugOutput.*
import org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap
import org.lwjgl.opengl.GL13.*
import uno.buffer.toBuf
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import javax.imageio.ImageIO

fun main() {
    with(ModelDrawing()) {
        run()
        end()
    }
}

val windowSize = Vec2i(1280, 720)
val clearColor = Vec4(208 / 255f, 209 / 255f, 230 / 255f, 1f)

private class ModelDrawing {
    val window: GlfwWindow = initWindow("Model 3D")

    val program = ModelDrawingProgram()

    var yaw = -90f
    var pitch = 0f
    var fov = 30f

    var cameraFront = Vec3(
        cos(yaw.rad) * cos(pitch.rad),
        sin(pitch.rad),
        sin(yaw.rad) * cos(pitch.rad)
    ).normalizeAssign()
    val cameraUp = Vec3(0f, 1f, 0f)
    val model: Model = Model("cyborg/cyborg.obj")

    private var dragPosition = Vec2d()
    private var inDrag = false

    private var lightAngle = 0f
    private val lightRadius = 3f

    private var showOverlay = true
    private var inChildWindow: (Vec2d) -> Boolean = { false }

    private val dissolveTexture = intBufferBig(1)
    private var dissolve = 0.0f

    init {
        GLUtil.setupDebugMessageCallback()

        with(window) {
            apply {
                cursorPosCallback = { pos ->
                    if (inDrag) {
                        val offset = Vec2d(
                            pos.x - dragPosition.x,
                            dragPosition.y - pos.y
                        )
                        dragPosition = pos
                        offset *= 0.2f
                        yaw += offset.x.f
                        pitch += offset.y.f
                        pitch = glm.clamp(pitch, -85f, 85f)
                        val front = Vec3(
                            cos(yaw.rad) * cos(pitch.rad),
                            sin(pitch.rad),
                            sin(yaw.rad) * cos(pitch.rad)
                        )
                        cameraFront = front.normalizeAssign()
                    }
                }
                scrollCallback = { offset ->
                    fov -= offset.y.f
                    fov = glm.clamp(fov, 2f, 70f)
                }

                mouseButtonCallback = { button, action, _ ->
                    if (button == GLFW_MOUSE_BUTTON_LEFT && !inChildWindow(window.cursorPos)) {
                        when (action) {
                            GLFW_PRESS -> {
                                if (!inDrag) {
                                    inDrag = true
                                    dragPosition = Vec2d(window.cursorPos)
                                }
                            }
                            GLFW_RELEASE -> inDrag = false
                        }
                    }
                }
            }
        }

        glGenTextures(dissolveTexture)
        val image = readImage("textures/random-noise.png")
        glBindTexture(GL_TEXTURE_2D, dissolveTexture)
        glTexImage2D(GL_RGB, image.width, image.height, GL_LUMINANCE, GL_UNSIGNED_BYTE, image.toBuffer())
        glGenerateMipmap(GL_TEXTURE_2D)
        LwjglGL3.init(window, false)
    }

    fun run() {
        glEnable(GL_DEPTH_TEST)

        while (window.open) {
            window.processInput()

            LwjglGL3.newFrame()

            with(ImGui) {
                setNextWindowPos(Vec2(10))
                withWindow("Overlay", ::showOverlay, WindowFlags.NoTitleBar or WindowFlags.NoResize or WindowFlags.AlwaysAutoResize or WindowFlags.NoMove or WindowFlags.NoSavedSettings) {
                    sliderFloat("light position", ::lightAngle, 0.0f, 360f)
                    sliderFloat("dissolve status", ::dissolve, 0.0f, 1.0f)
                }
                val child = findWindowByName("Overlay")!!
                inChildWindow = {
                    it.x >= child.pos.x && it.y >= child.pos.y &&
                            it.x < child.pos.x + child.size.x && it.y < child.pos.y + child.size.y
                }
            }
            glClearColor(clearColor)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

            glActiveTexture(GL_TEXTURE2)
            glBindTexture(GL_TEXTURE_2D, dissolveTexture)

            glUseProgram(program)
            glm.perspective(fov.rad, window.aspect, 0.1f, 100f) to program.projection
            glm.lookAt(-cameraFront, Vec3(), cameraUp) to program.view
            (-cameraFront) to program.cameraPosition
            val modelMatrix = Mat4()
                .translate(0f, -0.5f, 0f)
                .scale(0.2f)
            modelMatrix to program.model

            // circle, 45 degrees
            Vec3(lightRadius * cos( lightAngle.rad), lightRadius, lightRadius * sin(lightAngle.rad)) to program.lightPosition

            Vec3(0.2f, 0.2f, 0.2f) to program.lightAmbient
            Vec3(0.6f, 0.6f, 0.6f) to program.lightDiffuse
            Vec3(1.0f, 1.0f, 1.0f) to program.lightSpecular

            dissolve to program.dissolve

            model.draw()
            ImGui.render()
            window.swapBuffers()
            glfw.pollEvents()
        }
    }

    fun end() {
        LwjglGL3.shutdown()
        glDeleteProgram(program)
        glDeleteTextures(dissolveTexture)
        model.dispose()
        window.destroy()
        glfw.terminate()
    }

    private inner class ModelDrawingProgram : Program("shaders/", "model.vert", "model.frag") {

        val model = glGetUniformLocation(name, "model")
        val view = glGetUniformLocation(name, "view")
        val projection = glGetUniformLocation(name, "projection")
        val cameraPosition = glGetUniformLocation(name, "cameraPosition")

        val lightPosition = glGetUniformLocation(name, "lightPosition")
        val lightAmbient = glGetUniformLocation(name, "ambient")
        val lightDiffuse = glGetUniformLocation(name, "diffuse")
        val lightSpecular = glGetUniformLocation(name, "specular")
        val dissolve = glGetUniformLocation(name, "dissolve")

        init {
            usingProgram(name) {
                "texture_diffuse".unit = semantic.sampler.DIFFUSE
                "texture_specular".unit = semantic.sampler.SPECULAR
                "texture_dissolve".unit = 2
            }
        }
    }
}

fun initWindow(title: String): GlfwWindow {
    with(glfw) {
        init()
        windowHint {
            context.version = "3.3"
            profile = "compat"
            forwardComp = true
            debug = true
        }
    }
    return GlfwWindow(windowSize, title).apply {
        makeContextCurrent()
        show()
        framebufferSizeCallback = { size -> glViewport(size) }
    }.also {
        GL.createCapabilities(true)
    }
}

fun GlfwWindow.processInput() {
    if (pressed(GLFW_KEY_ESCAPE)) close = true
}

fun readImage(filePath: String): BufferedImage {

    val url = ClassLoader.getSystemResource(filePath)
    val file = File(url.toURI())

    return ImageIO.read(file)
}

fun BufferedImage.toBuffer() = (raster.dataBuffer as DataBufferByte).data.toBuf()
