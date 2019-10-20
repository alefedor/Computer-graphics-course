package builders

fun shader(body: ShaderBuilder.() -> Unit): String = ShaderBuilder().apply(body).build()

@DslMarker
annotation class ShaderMarker

@ShaderMarker
interface ShaderElement

class MainShaderBuilder : ShaderElement {
    private val output = StringBuilder().apply {
        appendln("void main()")
        appendln("{")
    }

    operator fun String.unaryPlus() {
        output.appendln(this)
    }

    fun set(name: String, x: String, y: String, z: String, w: String) {
        output.appendln("$name = vec4($x, $y, $z, $w);")
    }

    fun build() = output.appendln("}")
}

class ShaderBuilder : ShaderElement {
    companion object {
        private const val USED_VERSION ="#version 450 core"
    }

    private val output = StringBuilder().apply { appendln(USED_VERSION) }

    fun input(type: String, name: String, location: Int? = null) {
        define("in", type, name, location)
    }

    fun out(type: String, name: String, location: Int? = null) {
        define("out", type, name, location)
    }

    fun uniform(type: String, name: String, location: Int? = null) {
        define("uniform", type, name, location)
    }

    fun main(body: MainShaderBuilder.() -> Unit) = output.appendln(MainShaderBuilder().apply(body).build())

    fun build(): String = output.toString()

    operator fun String.unaryPlus() {
        output.appendln(this)
    }

    private fun define(tag: String, type: String, name: String, location: Int? = null) {
        val prefix = if (location != null) "layout (location = $location) " else ""
        output.appendln("$prefix$tag $type $name;")
    }
}