import glm_.f
import glm_.func.rad
import glm_.glm
import glm_.mat4x4.Mat4
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec2.Vec2i
import glm_.vec3.Vec3
import glm_.vec4.Vec4
import gln.glClearColor
import gln.glViewport
import gln.glf.semantic
import gln.program.ProgramUse.to
import gln.program.usingProgram
import imgui.ImGui
import imgui.WindowFlags
import imgui.functionalProgramming.withWindow
import imgui.impl.LwjglGL3
import imgui.or
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL20.glGetUniformLocation
import org.lwjgl.opengl.GL30.*
import uno.buffer.intBufferBig
import uno.glfw.GlfwWindow
import uno.glfw.glfw
import uno.glsl.Program
import uno.glsl.glDeleteProgram
import uno.glsl.glUseProgram
import util.CubeMesh
import util.GroundMesh
import util.Model
import kotlin.math.cos
import kotlin.math.sin

fun main() {
    with(ModelDrawing()) {
        run()
        end()
    }
}

val windowSize = Vec2i(1280, 720)
val shadowMapSize = Vec2i(1024, 1024)
val clearColor = Vec4(8 / 255f, 29 / 255f, 88 / 255f, 1f)

private class ModelDrawing {
    val window: GlfwWindow = initWindow("Model 3D")

    val mainProgram = ModelDrawingProgram()
    val shadowProgram = ShadowDepthProgram()

    var yaw = -90f
    var pitch = 0f
    var fov = 50f

    var cameraFront = Vec3(
        cos(yaw.rad) * cos(pitch.rad),
        sin(pitch.rad),
        sin(yaw.rad) * cos(pitch.rad)
    ).normalizeAssign()
    val cameraUp = Vec3(0f, 1f, 0f)
    val cyborgModel: Model = Model("cyborg/cyborg.obj")
    val groundModel: Model = Model(GroundMesh(5.0f))
    val cubeModel: Model = Model(CubeMesh())

    private var dragPosition = Vec2d()
    private var inDrag = false

    private var lightAngle = 0f

    private var showOverlay = true
    private var inChildWindow: (Vec2d) -> Boolean = { false }

    private var depthMapBuffer = intBufferBig(1)
    private var depthMap = intBufferBig(1)

    private var biasType: Int = 2
    private var cullFrontFaces: Boolean = true
    private var tuneIndividually: Boolean = true
    private var averageShadows: Boolean = true

    init {
        // uncomment this line to add debug output
        //GLUtil.setupDebugMessageCallback()

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
                    fov = glm.clamp(fov, 2f, 90f)
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

        LwjglGL3.init(window, false)

        glGenFramebuffers(depthMapBuffer)
        glGenTextures(depthMap)
        glBindTexture(GL_TEXTURE_2D, depthMap[0])
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT, shadowMapSize.x, shadowMapSize.y, 0, GL_DEPTH_COMPONENT, GL_FLOAT, 0)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_BORDER)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_BORDER)
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f))

        glBindFramebuffer(GL_FRAMEBUFFER, depthMapBuffer[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthMap[0], 0)
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
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
                    combo("Depth bias", ::biasType, listOf("No", "Const", "Robust"))
                    checkbox("Cull front faces for shadows", ::cullFrontFaces)
                    checkbox("Tune individually", ::tuneIndividually)
                    checkbox("Soften shadows", ::averageShadows)
                }
                val child = findWindowByName("Overlay")!!
                inChildWindow = {
                    it.x >= child.pos.x && it.y >= child.pos.y &&
                            it.x < child.pos.x + child.size.x && it.y < child.pos.y + child.size.y
                }
            }
            glClearColor(clearColor)

            glUseProgram(shadowProgram)
            initializeProgramBase(shadowProgram)

            glViewport(0, 0, shadowMapSize.x, shadowMapSize.y)
            glBindFramebuffer(GL_FRAMEBUFFER, depthMapBuffer[0])
            glClear( GL_DEPTH_BUFFER_BIT)
            if (cullFrontFaces) {
                glCullFace(GL_FRONT)
            } else {
                glCullFace(GL_BACK)
            }
            renderScene(shadowProgram)
            glBindFramebuffer(GL_FRAMEBUFFER, 0)

            glUseProgram(mainProgram)
            initializeMainProgram()

            glViewport(window.size)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            glCullFace(GL_BACK)
            glActiveTexture(GL_TEXTURE0 + 2)
            glBindTexture(GL_TEXTURE_2D, depthMap[0])
            renderScene(mainProgram)

            ImGui.render()
            window.swapBuffers()
            glfw.pollEvents()
        }
    }

    fun renderScene(program: ProgramBase) {
        glEnable(GL_CULL_FACE)

        if (tuneIndividually && program == mainProgram)
            0 to mainProgram.biasType

        Mat4()
            .translate(-0.5f, -0.3f, -0.1f)
            .rotate(45f.rad, 0f, 1f, 0f)
            .scale(0.2f) to program.model

        cubeModel.draw()

        Mat4()
            .translate(0.4f, -0.15f, 0.3f)
            .rotate(-70f.rad, 0.5f, 1f, 1f)
            .scale(0.1f) to program.model

        cubeModel.draw()

        if (tuneIndividually) {
            glDisable(GL_CULL_FACE)
            if (program == mainProgram)
                biasType to mainProgram.biasType
        }

        Mat4()
            .translate(0f, -0.4f, 0f)
            .scale(0.2f) to program.model

        cyborgModel.draw()

        if (tuneIndividually) {
            glEnable(GL_CULL_FACE)
            if (program == mainProgram)
                0 to mainProgram.biasType
        }

        if (program == mainProgram)
            glDisable(GL_CULL_FACE)

        Mat4()
            .translate(0f, -0.4f, 0f)
            .scale(0.2f) to program.model
        groundModel.draw()
    }

    fun initializeMainProgram() {
        initializeProgramBase(mainProgram)
        glm.perspective(fov.rad, window.aspect, 0.1f, 100f) to mainProgram.projection
        glm.lookAt(-cameraFront, Vec3(), cameraUp) to mainProgram.view
        (-cameraFront) to mainProgram.cameraPosition

        Vec3(cos( lightAngle.rad), 1, sin(lightAngle.rad)) to mainProgram.lightPosition

        Vec3(0.2f, 0.2f, 0.2f) to mainProgram.lightAmbient
        Vec3(0.6f, 0.6f, 0.6f) to mainProgram.lightDiffuse
        Vec3(1.0f, 1.0f, 1.0f) to mainProgram.lightSpecular
        biasType to mainProgram.biasType
        (if (averageShadows) 1 else 0) to mainProgram.averageShadows
    }

    fun initializeProgramBase(program: ProgramBase) {
        val lightPos = Vec3(cos( lightAngle.rad), 1, sin(lightAngle.rad))
        glm.ortho(-1.3f, 1.3f, -1.3f, 1.3f, 0.5f, 6.0f)to program.lightProjection
        glm.lookAt(lightPos, Vec3(0.0f), cameraUp) to program.lightView
    }

    fun end() {
        LwjglGL3.shutdown()
        glDeleteProgram(mainProgram)
        cyborgModel.dispose()
        groundModel.dispose()
        window.destroy()
        glfw.terminate()
    }

    private abstract inner class ProgramBase(path: String, vert: String, frag: String) : Program(path, vert, frag) {
        val model = glGetUniformLocation(name, "model")
        val lightView = glGetUniformLocation(name, "lightView")
        val lightProjection = glGetUniformLocation(name, "lightProjection")
    }

    private inner class ModelDrawingProgram : ProgramBase("shaders/", "model.vert", "model.frag") {
        val view = glGetUniformLocation(name, "view")
        val projection = glGetUniformLocation(name, "projection")

        val cameraPosition = glGetUniformLocation(name, "cameraPosition")

        val lightPosition = glGetUniformLocation(name, "lightPosition")
        val lightAmbient = glGetUniformLocation(name, "ambient")
        val lightDiffuse = glGetUniformLocation(name, "diffuse")
        val lightSpecular = glGetUniformLocation(name, "specular")
        val biasType = glGetUniformLocation(name, "biasType")
        val averageShadows = glGetUniformLocation(name, "averageShadows")

        init {
            usingProgram(name) {
                "texture_diffuse".unit = semantic.sampler.DIFFUSE
                "texture_specular".unit = semantic.sampler.SPECULAR
                "depth_map".unit = 2
            }
        }
    }

    private inner class ShadowDepthProgram : ProgramBase("shaders/", "shadow.vert", "shadow.frag")
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