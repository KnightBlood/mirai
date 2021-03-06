package net.mamoe.mirai.plugin

import net.mamoe.mirai.utils.DefaultLogger
import net.mamoe.mirai.utils.io.encodeToString
import java.io.File
import java.net.URL
import java.util.jar.JarFile


abstract class PluginBase constructor() {
    val dataFolder by lazy {
        File(PluginManager.pluginsPath + pluginDescription.name).also { it.mkdir() }
    }

    open fun onLoad() {

    }

    open fun onEnable() {

    }

    open fun onDisable() {

    }

    private lateinit var pluginDescription: PluginDescription

    internal fun init(pluginDescription: PluginDescription) {
        this.pluginDescription = pluginDescription
        this.onLoad()
    }
}

class PluginDescription(
    val name: String,
    val author: String,
    val basePath: String,
    val version: String,
    val info: String,
    val depends: List<String>,//插件的依赖
    internal var loaded: Boolean = false,
    internal var noCircularDepend: Boolean = true
) {

    override fun toString(): String {
        return "name: $name\nauthor: $author\npath: $basePath\nver: $version\ninfo: $info\ndepends: $depends"
    }

    companion object {
        fun readFromContent(content_: String): PluginDescription {
            val content = content_.split("\n")

            var name = "Plugin"
            var author = "Unknown"
            var basePath = "net.mamoe.mirai.PluginMain"
            var info = "Unknown"
            var version = "1.0.0"
            val depends = mutableListOf<String>();

            content.forEach {
                val line = it.trim()
                val lowercaseLine = line.toLowerCase()
                if (it.contains(":")) {
                    when {
                        lowercaseLine.startsWith("name") -> {
                            name = line.substringAfter(":").trim()
                        }
                        lowercaseLine.startsWith("author") -> {
                            author = line.substringAfter(":").trim()
                        }
                        lowercaseLine.startsWith("info") || lowercaseLine.startsWith("information") -> {
                            info = line.substringAfter(":").trim()
                        }
                        lowercaseLine.startsWith("main") ||
                                lowercaseLine.startsWith("path") ||
                                lowercaseLine.startsWith("basepath") -> {
                            basePath = line.substringAfter(":").trim()
                        }
                        lowercaseLine.startsWith("version") || lowercaseLine.startsWith("ver") -> {
                            version = line.substringAfter(":").trim()
                        }
                    }
                } else if (line.startsWith("-")) {
                    depends.add(line.substringAfter("-").trim())
                }
            }
            return PluginDescription(name, author, basePath, version, info, depends)
        }
    }
}


object PluginManager {
    internal val pluginsPath = System.getProperty("user.dir") + "/plugins/".replace("//", "/").also {
        File(it).mkdirs()
    }

    private val logger = DefaultLogger("Mirai Plugin Manager")

    //已完成加载的
    private val nameToPluginBaseMap: MutableMap<String, PluginBase> = mutableMapOf()


    /**
     * 尝试加载全部插件
     */
    fun loadPlugins() {
        val pluginsFound: MutableMap<String, PluginDescription> = mutableMapOf()
        val pluginsLocation: MutableMap<String, JarFile> = mutableMapOf()

        File(pluginsPath).listFiles()?.forEach { file ->
            if (file != null && file.extension == "jar") {
                val jar = JarFile(file)
                val pluginYml =
                    jar.entries().asSequence().filter { it.name.toLowerCase().contains("plugin.yml") }.firstOrNull()
                if (pluginYml == null) {
                    logger.info("plugin.yml not found in jar " + jar.name + ", it will not be considered as a Plugin")
                } else {
                    val description =
                        PluginDescription.readFromContent(URL("jar:file:" + file.absoluteFile + "!/" + pluginYml.name).openConnection().inputStream.readAllBytes().encodeToString())
                    println(description)
                    pluginsFound[description.name] = description
                    pluginsLocation[description.name] = jar
                }
            }
        }

        fun checkNoCircularDepends(target: PluginDescription, needDepends: List<String>, existDepends: MutableList<String>) {

            if (!target.noCircularDepend) {
                return
            }

            existDepends.add(target.name)

            if (needDepends.any { existDepends.contains(it) }) {
                target.noCircularDepend = false
            }

            existDepends.addAll(needDepends)

            needDepends.forEach {
                if (pluginsFound.containsKey(it)) {
                    checkNoCircularDepends(pluginsFound[it]!!, pluginsFound[it]!!.depends, existDepends)
                }
            }
        }


        pluginsFound.values.forEach {
            checkNoCircularDepends(it, it.depends, mutableListOf())
        }

        //load


        fun loadPlugin(description: PluginDescription): Boolean {
            if (!description.noCircularDepend) {
                logger.error("Failed to load plugin " + description.name + " because it has circular dependency")
                return false
            }

            //load depends first
            description.depends.forEach { dependentName ->
                val dependent = pluginsFound[dependentName]
                if (dependent == null) {
                    logger.error("Failed to load plugin " + description.name + " because it need " + dependentName + " as dependency")
                    return false
                }
                //还没有加载
                if (!dependent.loaded && !loadPlugin(dependent)) {
                    logger.error("Failed to load plugin " + description.name + " because " + dependentName + " as dependency failed to load")
                    return false
                }
            }
            //在这里所有的depends都已经加载了


            //real load
            logger.info("loading plugin " + description.name)

            try {
                this.javaClass.classLoader.loadClass(description.basePath)
                return try {
                    val subClass = javaClass.asSubclass(PluginBase::class.java)
                    val plugin: PluginBase = subClass.getDeclaredConstructor().newInstance()
                    description.loaded = true
                    logger.info("successfully loaded plugin " + description.name)
                    logger.info(description.info)

                    nameToPluginBaseMap[description.name] = plugin
                    plugin.init(description)

                    true
                } catch (e: ClassCastException) {
                    logger.error("failed to load plugin " + description.name + " , Main class does not extends PluginBase ")
                    false
                }
            } catch (e: ClassNotFoundException) {
                e.printStackTrace()
                logger.error("failed to load plugin " + description.name + " , Main class not found under " + description.basePath)
                return false
            }
        }

        pluginsFound.values.forEach {
            loadPlugin(it)
        }
    }


}



