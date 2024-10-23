package binderservice.compiler

import binderservice.BinderService
import com.google.auto.service.AutoService
import org.json.JSONObject
import java.io.IOException
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Processor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic
import javax.tools.StandardLocation


@AutoService(Processor::class)
class BinderServiceCompiler : AbstractProcessor() {

    companion object {
        private const val OUTPUT_PATH = "assets/application-binder-services.json"
    }

    private val mServices = HashSet<TypeElement>()

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(BinderService::class.java.name)
    }

    override fun process(
        annotations: MutableSet<out TypeElement>,
        roundEnv: RoundEnvironment
    ): Boolean {
        if (roundEnv.processingOver()) {
            generateConfigFiles()
        } else {
            processAnnotations(roundEnv)
        }
        return true
    }

    private fun format(message: String): String {
        return StringBuilder(DEFAULT_BUFFER_SIZE)
            .appendLine("==${BinderServiceCompiler::class.java.name}==")
            .appendLine(message)
            .toString()
    }

    private fun error(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, format(message))
    }

    private fun log(message: String) {
        processingEnv.messager.printMessage(Diagnostic.Kind.NOTE, format(message))
    }

    private fun generateConfigFiles() {
        var targetFile = processingEnv.filer.getResource(
            StandardLocation.CLASS_OUTPUT, "",
            OUTPUT_PATH
        )
        val configs = try {
            JSONObject(targetFile.openReader(false).use { it.readText() })
        } catch (e: IOException) {
            log("Resource file did not already exist.")
            JSONObject()
        }
        for (service in mServices) {
            val annotation = service.getAnnotation(BinderService::class.java)
            if (configs.has(annotation.name)) {
                error("A service named '${annotation.name}' has already been registered")
                return
            } else {
                val config = JSONObject()
                config.put("process", annotation.process)
                config.put("class", getBinaryName(service))
                configs.put(annotation.name, config)
            }
        }
        val text = configs.toString(2)
        targetFile = processingEnv.filer.createResource(
            StandardLocation.CLASS_OUTPUT, "", OUTPUT_PATH
        )
        targetFile.openWriter().use { it.write(text) }
        log(text)
        log("Wrote to: " + targetFile.toUri())
    }

    private fun getBinaryName(element: TypeElement): String {
        return getBinaryNameImpl(element, element.simpleName.toString())
    }

    private fun getBinaryNameImpl(element: TypeElement, className: String): String {
        val enclosingElement = element.enclosingElement
        if (enclosingElement is PackageElement) {
            return if (enclosingElement.isUnnamed) {
                className
            } else enclosingElement.qualifiedName.toString() + "." + className
        }
        val typeElement = enclosingElement as TypeElement
        return getBinaryNameImpl(typeElement, typeElement.simpleName.toString() + "$" + className)
    }

    private fun processAnnotations(roundEnv: RoundEnvironment) {
        val newList = roundEnv.getElementsAnnotatedWith(BinderService::class.java).asSequence()
            .filter {
                it.kind == ElementKind.CLASS
            }.mapNotNull {
                it as? TypeElement
            }.toList()
        mServices.addAll(newList)
    }
}