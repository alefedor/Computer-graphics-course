import builders.shader
import glm_.vec2.Vec2
import glm_.vec2.Vec2d
import glm_.vec2.Vec2i
import glm_.vec4.Vec4
import gln.buffer.glBindBuffer
import gln.buffer.glBufferData
import gln.texture.glBindTexture
import gln.draw.glDrawArrays
import gln.glClearColor
import gln.glViewport
import gln.glf.semantic
import gln.vertexArray.glBindVertexArray
import imgui.ImGui
import imgui.WindowFlags
import imgui.functionalProgramming.withWindow
import imgui.impl.LwjglGL3
import imgui.or
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.glDeleteVertexArrays
import org.lwjgl.opengl.GL30.glGenVertexArrays
import uno.buffer.destroyBuf
import uno.buffer.intBufferBig
import uno.glfw.GlfwWindow
import uno.glfw.glfw
import org.lwjgl.opengl.GL20.glUniform1i



fun main() {
    with(MandelbrotSet()) {
        run()
        end()
    }
}

private val windowSize = Vec2i(1280, 720)

private class MandelbrotSet {
    companion object {
        private val zoomScale = 1.1f
    }

    val window = initWindow("Mandelbrot Set")

    val vertexShaderSource = shader {
        input("vec2", "point", 0)

        main {
            set("gl_Position", "point.x", "point.y", "0.0", "1.0")
        }
    }

    val fragmentShaderSource = shader {
        uniform("int", "iterations")
        uniform("float", "threshold")
        uniform("float", "zoom")
        uniform("vec2", "offset")
        uniform("vec2", "window_size")
        uniform("sampler1D", "colorTexture")
        uniform("int", "texture_width")
        out("vec4", "frag_color")

        + """
            int mandelbrot_iterations(vec2 c) {
                vec2 z = vec2(0.0, 0.0);
                int i;
                for (i = 0; i < iterations; i++) {
                    z = vec2(z.x * z.x - z.y * z.y + c.x, 2.0 * z.x * z.y + c.y);
                    if (z.x * z.x + z.y * z.y > threshold) 
                        break;
                }
                
                return i;
            }""".trimIndent()

        main {
            + """
                vec2 point = vec2(gl_FragCoord.x, gl_FragCoord.y) - window_size / 2;
                point = point / zoom + offset;
                int needed_iterations = mandelbrot_iterations(point);
                float texture_position = needed_iterations / float(iterations);
                texture_position = 0.5 / texture_width + texture_position * (1 - 1.0 / texture_width);
                frag_color = texture(colorTexture, texture_position);
            """.trimIndent()
        }
    }

    val shaderProgram: Int

    val vbo = intBufferBig(1)
    val vao = intBufferBig(1)
    val texture = intBufferBig(1)
    val colorTexture = floatArrayOf(
        1.0f, 1.0f, 204 / 255.0f,
        161 / 255.0f, 218 / 255.0f, 180 / 255.0f,
        65 / 255.0f, 182 / 255.0f, 196 / 255.0f,
        44 / 255.0f, 127 / 255.0f, 184 / 255.0f,
        37 / 255.0f, 52 / 255.0f, 148 / 255.0f
    )

    val textureWidth: Int
        get() = colorTexture.size / 3

    val vertices = floatArrayOf(
        1.0f, 1.0f,
        -1.0f, 1.0f,
        -1.0f, -1.0f,

        1.0f, 1.0f,
        -1.0f, -1.0f,
        1.0f, -1.0f
    ) // just all square

    init {
        val vertexShader = glCreateShader(GL_VERTEX_SHADER)
        glShaderSource(vertexShader, vertexShaderSource)
        glCompileShader(vertexShader)

        if (glGetShaderi(vertexShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val infoLog = glGetShaderInfoLog(vertexShader)
            System.err.println("ERROR::SHADER::VERTEX::COMPILATION_FAILED\n$infoLog")
        }

        val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
        glShaderSource(fragmentShader, fragmentShaderSource)
        glCompileShader(fragmentShader)

        if (glGetShaderi(fragmentShader, GL_COMPILE_STATUS) == GL_FALSE) {
            val infoLog = glGetShaderInfoLog(fragmentShader)
            System.err.print("ERROR::SHADER::FRAGMENT::COMPILATION_FAILED\n$infoLog")
        }

        shaderProgram = glCreateProgram()
        glAttachShader(shaderProgram, vertexShader)
        glAttachShader(shaderProgram, fragmentShader)
        glLinkProgram(shaderProgram)

        if (glGetProgrami(shaderProgram, GL_LINK_STATUS) == GL_FALSE) {
            val infoLog = glGetProgramInfoLog(shaderProgram)
            System.err.print("ERROR::SHADER::PROGRAM::LINKING_FAILED\n$infoLog")
        }

        glDeleteShader(vertexShader)
        glDeleteShader(fragmentShader)

        glGenVertexArrays(vao)
        glGenBuffers(vbo)
        glBindVertexArray(vao)

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW)

        glGenTextures(texture)
        glBindTexture(GL_TEXTURE_1D, texture)
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexImage1D(GL_TEXTURE_1D, 0, GL_RGB, textureWidth, 0, GL_RGB, GL_FLOAT, colorTexture)

        glVertexAttribPointer(semantic.attr.POSITION, Vec2.length, GL_FLOAT, false, 0, 0)
        glEnableVertexAttribArray(semantic.attr.POSITION)
        glBindBuffer(GL_ARRAY_BUFFER)
        glBindVertexArray()
        LwjglGL3.init(window, true)
    }

    var showOverlay = true
    var maxIterations = 15
    var threshold = 10.0f
    var zoom = 200f
    var offset = Vec2(0.0, 0.0)
    private var inDrag = false
    private var dragPos = Vec2d(0.0, 0.0)
    private var inChildWindow: (Vec2d) -> Boolean = { false }

    fun run() {
        with(window) {
            cursorPosCallBack = { x, y ->
                if (inDrag) {
                    val cursorPos = Vec2d(x, y)
                    offset.x -= ((cursorPos.x - dragPos.x) / zoom).toFloat() // inverted x
                    offset.y += ((cursorPos.y - dragPos.y) / zoom).toFloat()

                    dragPos = cursorPos // just consider that we are now in a new drag event

                    glUniform2f(glGetUniformLocation(shaderProgram, "offset"), offset.x, offset.y)
                }
            }

            framebufferSizeCallback = { size -> glViewport(size) }

            scrollCallBack = { _, offsetY ->
                if (offsetY != 0.0) {
                    val cursorPos = Vec2d(window.cursorPos)
                    cursorPos.x = windowSize.x - cursorPos.x // invert x
                    val halfWindowSize = Vec2d(windowSize.x / 2.0, windowSize.y / 2.0)
                    val shift = (cursorPos - halfWindowSize) / zoom - offset

                    if (offsetY < 0.0)
                        zoom /= zoomScale
                    else
                        zoom *= zoomScale

                    val newShift = (cursorPos - halfWindowSize) / zoom

                    offset.x = (newShift.x - shift.x).toFloat()
                    offset.y = (newShift.y - shift.y).toFloat()

                    glUniform1f(glGetUniformLocation(shaderProgram, "zoom"), zoom)
                    glUniform2f(glGetUniformLocation(shaderProgram, "offset"), offset.x, offset.y)
                }
            }

            mouseButtonCallback = { button, action, mods ->
                if (button == GLFW_MOUSE_BUTTON_LEFT && !inChildWindow(window.cursorPos)) {
                    when (action) {
                        GLFW_PRESS -> {
                            if (!inDrag) {
                                inDrag = true
                                dragPos = Vec2d(window.cursorPos)
                            }
                        }
                        GLFW_RELEASE -> inDrag = false
                    }
                }
            }
        }

        while (window.open) {
            window.processInput()
            LwjglGL3.newFrame()

            with(ImGui) {
                setNextWindowPos(Vec2(10))
                withWindow("Overlay", ::showOverlay, WindowFlags.NoTitleBar or WindowFlags.NoResize or WindowFlags.AlwaysAutoResize or WindowFlags.NoMove or WindowFlags.NoSavedSettings) {
                    sliderInt("iterations", ::maxIterations, 1, 500)
                    sliderFloat("threshold", ::threshold, 1.0f, 100f)
                }
                val child = findWindowByName("Overlay")!!
                inChildWindow = {
                    it.x >= child.pos.x && it.y >= child.pos.y &&
                    it.x < child.pos.x + child.size.x && it.y < child.pos.y + child.size.y
                }
            }

            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL)

            glClearColor(Vec4(1.0, 1.0, 1.0, 1.0))
            glClear(GL_COLOR_BUFFER_BIT)
            glUseProgram(shaderProgram)
            glUniform2f(glGetUniformLocation(shaderProgram, "window_size"), windowSize.x.toFloat(), windowSize.y.toFloat())
            glUniform2f(glGetUniformLocation(shaderProgram, "offset"), offset.x, offset.y)
            glUniform1f(glGetUniformLocation(shaderProgram, "threshold"), threshold)
            glUniform1f(glGetUniformLocation(shaderProgram, "zoom"), zoom)
            glUniform1i(glGetUniformLocation(shaderProgram, "texture_width"), textureWidth)
            glUniform1i(glGetUniformLocation(shaderProgram, "iterations"), maxIterations)

            glBindTexture(GL_TEXTURE_1D, texture);
            glBindVertexArray(vao)
            glDrawArrays(GL_TRIANGLES, vertices.size)

            ImGui.render()
            window.swapBuffers()
            glfw.pollEvents()
        }
    }

    fun end() {

        LwjglGL3.shutdown()

        glDeleteProgram(shaderProgram)
        glDeleteVertexArrays(vao)
        glDeleteBuffers(vbo)

        destroyBuf(vao, vbo)

        window.end()
    }

    private fun GlfwWindow.processInput() {
        if (pressed(GLFW_KEY_ESCAPE)) close = true
    }
}

fun GlfwWindow.end() {
    destroy()
    glfw.terminate()
}

fun initWindow(title: String): GlfwWindow {
    with(glfw) {
        init()
        windowHint {
            context.version = "4.5"
            profile = "core"
            forwardComp = true
        }
    }
    return GlfwWindow(windowSize, title).apply {
        makeContextCurrent()
        show()
    }.also {
        GL.createCapabilities()
    }
}