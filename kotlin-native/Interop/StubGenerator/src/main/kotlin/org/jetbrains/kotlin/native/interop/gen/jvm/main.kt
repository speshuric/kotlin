/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.native.interop.gen.jvm

import kotlinx.cinterop.usingJvmCInteropCallbacks
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.cli.required
import org.jetbrains.kotlin.konan.ForeignExceptionMode
import org.jetbrains.kotlin.konan.TempFiles
import org.jetbrains.kotlin.konan.exec.Command
import org.jetbrains.kotlin.konan.library.*
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.konan.target.Distribution
import org.jetbrains.kotlin.konan.target.KonanTarget
import org.jetbrains.kotlin.konan.util.DefFile
import org.jetbrains.kotlin.konan.util.KonanHomeProvider
import org.jetbrains.kotlin.konan.util.usingNativeMemoryAllocator
import org.jetbrains.kotlin.library.KotlinLibrary
import org.jetbrains.kotlin.library.metadata.resolver.TopologicalLibraryOrder
import org.jetbrains.kotlin.library.metadata.resolver.impl.KotlinLibraryResolverImpl
import org.jetbrains.kotlin.library.metadata.resolver.impl.libraryResolver
import org.jetbrains.kotlin.library.packageFqName
import org.jetbrains.kotlin.library.toUnresolvedLibraries
import org.jetbrains.kotlin.native.interop.gen.*
import org.jetbrains.kotlin.native.interop.gen.wasm.processIdlLib
import org.jetbrains.kotlin.native.interop.indexer.*
import org.jetbrains.kotlin.native.interop.tool.*
import org.jetbrains.kotlin.util.removeSuffixIfPresent
import org.jetbrains.kotlin.util.suffixIfNot
import java.io.File
import java.nio.file.*
import java.util.*
import kotlin.io.path.absolutePathString

data class InternalInteropOptions(val generated: String, val natives: String, val manifest: String? = null,
                                  val cstubsName: String? = null)

fun main(args: Array<String>) {
    // Adding flavor option for interop plugin.
    class FullCInteropArguments: CInteropArguments() {
        val flavor by argParser.option(ArgType.Choice<KotlinPlatform>(), description = "Interop target")
                .default(KotlinPlatform.JVM)
        val generated by argParser.option(ArgType.String, description = "place generated bindings to the directory")
                .required()
        val natives by argParser.option(ArgType.String, description = "where to put the built native files")
                .required()
    }
    val arguments = FullCInteropArguments()
    arguments.argParser.parse(args)
    val flavorName = arguments.flavor
    processCLibSafe(flavorName, arguments, InternalInteropOptions(arguments.generated, arguments.natives), runFromDaemon = false)
}

class Interop {
    /**
     * invoked via reflection from new test system: CompilationToolCallKt.invokeCInterop(),
     * `interop()` has issues to be invoked directly due to NoSuchMethodError, caused by presence of InternalInteropOptions argtype:
     * java.lang.IllegalArgumentException: argument type mismatch.
     * Also this method simplifies testing of [CInteropPrettyException] by wrapping the result in Any that acts like a "Result" class.
     * Using "Result" directly might be complicated due to signature mangle and different class loaders.
     */
    fun interopViaReflection(
            flavor: String, args: Array<String>,
            runFromDaemon: Boolean,
            generated: String, natives: String, manifest: String? = null, cstubsName: String? = null
    ): Any? {
        val internalInteropOptions = InternalInteropOptions(generated, natives, manifest, cstubsName)
        return try {
            interop(flavor, args, internalInteropOptions, runFromDaemon)
        } catch (prettyException: CInteropPrettyException) {
            prettyException
        }
    }

    fun interop(
            flavor: String, args: Array<String>,
            additionalArgs: InternalInteropOptions,
            runFromDaemon: Boolean
    ): Array<String>? = when (flavor) {
        "jvm", "native" -> {
            val cinteropArguments = CInteropArguments()
            cinteropArguments.argParser.parse(args)
            val platform = KotlinPlatform.values().single { it.name.equals(flavor, ignoreCase = true) }
            processCLibSafe(platform, cinteropArguments, additionalArgs, runFromDaemon)
        }
        "wasm" -> processIdlLib(args, additionalArgs)
        else -> error("Unexpected flavor")
    }
}
// Options, whose values are space-separated and can be escaped.
val escapedOptions = setOf("-compilerOpts", "-linkerOpts", "-compiler-options", "-linker-options")

private fun String.asArgList(key: String) =
        if (escapedOptions.contains(key))
            this.split(Regex("(?<!\\\\)\\Q \\E")).filter { it.isNotEmpty() }.map { it.replace("\\ ", " ") }
        else
            listOf(this)

private fun <T> Collection<T>.atMostOne(): T? {
    return when (this.size) {
        0 -> null
        1 -> this.iterator().next()
        else -> throw IllegalArgumentException("Collection has more than one element.")
    }
}

private fun List<String>?.isTrue(): Boolean {
    // The rightmost wins, null != "true".
    return this?.last() == "true"
}

private fun runCmd(command: Array<String>, verbose: Boolean = false, redirectInputFile: File? = null) {
    if (verbose) {
        val redirect = if (redirectInputFile == null) "" else " < ${redirectInputFile.path}"
        println("COMMAND: " + command.joinToString(" ") + redirect)
    }
    Command(command.toList(), redirectInputFile = redirectInputFile).getOutputLines(true).let { lines ->
        if (verbose) lines.forEach(::println)
    }
}

private fun Properties.storeProperties(file: File) {
    file.outputStream().use {
        this.store(it, null)
    }
}

private fun Properties.putAndRunOnReplace(key: Any, newValue: Any, beforeReplace: (Any, Any, Any) -> Unit) {
    val oldValue = this[key]
    if (oldValue != null && oldValue != newValue) {
        beforeReplace(key, oldValue, newValue)
    }
    this[key] = newValue
}

private fun selectNativeLanguage(config: DefFile.DefFileConfig): Language {
    val languages = mapOf(
            "C" to Language.C,
            "C++" to Language.CPP,
            "Objective-C" to Language.OBJECTIVE_C
    )

    // C++ is not publicly supported.
    val publicLanguages = languages.keys.minus("C++")

    val lang = config.language?.let {
        languages[it]
                ?: error("Unexpected language '${config.language}'. Possible values are: ${publicLanguages.joinToString { "'$it'" }}")
    } ?: Language.C

    return lang

}

private fun parseImports(dependencies: List<KotlinLibrary>): ImportsImpl =
        dependencies.filterIsInstance<KonanLibrary>().mapNotNull { library ->
            // TODO: handle missing properties?
            library.packageFqName?.let { packageFqName ->
                val headerIds = library.includedHeaders
                headerIds.map { HeaderId(it) to PackageInfo(packageFqName, library) }
            }
        }.reversed().flatten().toMap().let(::ImportsImpl)

fun getCompilerFlagsForVfsOverlay(headerFilterPrefix: Array<String>, def: DefFile): List<String> {
    val relativeToRoot = mutableMapOf<Path, Path>() // TODO: handle clashes

    val filteredIncludeDirs = headerFilterPrefix .map { Paths.get(it) }
    if (filteredIncludeDirs.isNotEmpty()) {
        val headerFilterGlobs = def.config.headerFilter
        val excludeFilterGlobs = def.config.excludeFilter
        if (headerFilterGlobs.isEmpty()) {
            error("'$HEADER_FILTER_ADDITIONAL_SEARCH_PREFIX' option requires " +
                    "'headerFilter' to be specified in .def file")
        }

        relativeToRoot += findFilesByGlobs(roots = filteredIncludeDirs, includeGlobs = headerFilterGlobs, excludeGlobs = excludeFilterGlobs)
    }

    if (relativeToRoot.isEmpty()) {
        return emptyList()
    }

    val virtualRoot = Paths.get(System.getProperty("java.io.tmpdir")).resolve("konanSystemInclude")

    val virtualPathToReal = relativeToRoot.map { (relativePath, realRoot) ->
        virtualRoot.resolve(relativePath) to realRoot.resolve(relativePath)
    }.toMap()

    val vfsOverlayFile = createVfsOverlayFile(virtualPathToReal)

    return listOf("-I${virtualRoot.toAbsolutePath()}", "-ivfsoverlay", vfsOverlayFile.toAbsolutePath().toString())
}

private fun findFilesByGlobs(roots: List<Path>, includeGlobs: List<String>, excludeGlobs: List<String>): Map<Path, Path> {
    val relativeToRoot = mutableMapOf<Path, Path>()

    val pathMatchers = includeGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }
    val excludePathMatchers = excludeGlobs.map { FileSystems.getDefault().getPathMatcher("glob:$it") }

    roots.reversed()
            .filter { path ->
                return@filter when {
                    path.toFile().exists() -> true
                    else -> { warn("$path doesn't exist"); false }
                }
            }
            .forEach { root ->
                // TODO: don't scan the entire tree, skip subdirectories according to globs.
                Files.walk(root, FileVisitOption.FOLLOW_LINKS).forEach { path ->
                    val relativePath = root.relativize(path)
                    val shouldInclude = !Files.isDirectory(path)
                            && excludePathMatchers.all { !it.matches(relativePath) }
                            && pathMatchers.any { it.matches(relativePath) }
                    if (shouldInclude) {
                        relativeToRoot[relativePath] = root
                    }
                }
            }
    return relativeToRoot
}

private fun processCLibSafe(flavor: KotlinPlatform, cinteropArguments: CInteropArguments,
                            additionalArgs: InternalInteropOptions, runFromDaemon: Boolean) =
        usingNativeMemoryAllocator {
            usingJvmCInteropCallbacks {
                processCLib(flavor, cinteropArguments, additionalArgs, runFromDaemon)
            }
        }

private fun processCLib(
        flavor: KotlinPlatform,
        cinteropArguments: CInteropArguments,
        additionalArgs: InternalInteropOptions,
        runFromDaemon: Boolean,
): Array<String>? = withExceptionPrettifier(cinteropArguments.disableExceptionPrettifier) {
    val ktGenRoot = additionalArgs.generated
    val nativeLibsDir = additionalArgs.natives
    val defFile = cinteropArguments.def?.let { File(it) }
    val manifestAddend = additionalArgs.manifest?.let { File(it) }

    if (defFile == null && cinteropArguments.pkg == null) {
        cinteropArguments.argParser.printError("-def or -pkg should be provided!")
    }

    val tool = prepareTool(cinteropArguments.target, flavor, runFromDaemon, parseKeyValuePairs(cinteropArguments.overrideKonanProperties))

    val def = DefFile(defFile, tool.substitutions)
    val isLinkerOptsSetByUser = (cinteropArguments.linkerOpts.valueOrigin == ArgParser.ValueOrigin.SET_BY_USER) ||
            (cinteropArguments.linkerOptions.valueOrigin == ArgParser.ValueOrigin.SET_BY_USER) ||
            (cinteropArguments.linkerOption.valueOrigin == ArgParser.ValueOrigin.SET_BY_USER)
    if (flavor == KotlinPlatform.NATIVE && isLinkerOptsSetByUser) {
        warn("-linker-option(s)/-linkerOpts option is not supported by cinterop. Please add linker options to .def file or binary compilation instead.")
    }

    val additionalLinkerOpts = cinteropArguments.linkerOpts.value.toTypedArray() + cinteropArguments.linkerOption.value.toTypedArray() +
            cinteropArguments.linkerOptions.value.toTypedArray()
    val verbose = cinteropArguments.verbose

    val entryPoint = def.config.entryPoints.atMostOne()
    val linkerName = cinteropArguments.linker ?: def.config.linker
    val linker = "${tool.llvmHome}/bin/$linkerName"
    val compiler = "${tool.llvmHome}/bin/clang"
    val excludedFunctions = def.config.excludedFunctions.toSet()
    val excludedMacros = def.config.excludedMacros.toSet()
    val staticLibraries = def.config.staticLibraries + cinteropArguments.staticLibrary.toTypedArray()
    val projectDir = cinteropArguments.projectDir
    val libraryPaths = (def.config.libraryPaths + cinteropArguments.libraryPath).map {
        if (projectDir == null || Paths.get(it).isAbsolute)
            it
        else Paths.get(projectDir, it).absolutePathString()
    }
    val fqParts = (cinteropArguments.pkg ?: def.config.packageName)?.split('.')
            ?: defFile!!.name.split('.').reversed().drop(1)

    val outKtPkg = fqParts.joinToString(".")

    val mode = run {
        val providedMode = cinteropArguments.mode

        if (providedMode == GenerationMode.METADATA && flavor == KotlinPlatform.JVM) {
            warn("Metadata mode isn't supported for Kotlin/JVM! Falling back to sourcecode.")
            GenerationMode.SOURCE_CODE
        } else {
            providedMode
        }
    }

    val resolver = getLibraryResolver(cinteropArguments, tool.target)

    val allLibraryDependencies = when (flavor) {
        KotlinPlatform.NATIVE -> resolveDependencies(resolver, cinteropArguments)
        else -> listOf()
    }

    val libName = additionalArgs.cstubsName ?: fqParts.joinToString("") + "stubs"

    val tempFiles = TempFiles(libName, cinteropArguments.tempDir)

    val imports = parseImports(allLibraryDependencies)

    val library = buildNativeLibrary(tool, def, cinteropArguments, imports)

    val plugin = Plugins.plugin(def.config.pluginName)

    val (nativeIndex, compilation) = plugin.buildNativeIndex(library, verbose)

    val target = tool.target

    val klibSuffix = CompilerOutputKind.LIBRARY.suffix(target)
    val moduleName = cinteropArguments.moduleName
            ?: File(cinteropArguments.output).name.removeSuffixIfPresent(klibSuffix)

    val configuration = InteropConfiguration(
            library = compilation,
            pkgName = outKtPkg,
            excludedFunctions = excludedFunctions,
            excludedMacros = excludedMacros,
            strictEnums = def.config.strictEnums.toSet(),
            nonStrictEnums = def.config.nonStrictEnums.toSet(),
            noStringConversion = def.config.noStringConversion.toSet(),
            exportForwardDeclarations = def.config.exportForwardDeclarations,
            disableDesignatedInitializerChecks = def.config.disableDesignatedInitializerChecks,
            target = target
    )


    File(nativeLibsDir).mkdirs()
    val outCFile = tempFiles.create(libName, ".${library.language.sourceFileExtension}")

    val logger = if (verbose) {
        { message: String -> println(message) }
    } else {
        {}
    }

    val stubIrContext = StubIrContext(logger, configuration, nativeIndex, imports, flavor, mode, libName, plugin)
    val stubIrOutput = run {
        val outKtFileCreator = {
            val outKtFileName = fqParts.last() + ".kt"
            val outKtFileRelative = (fqParts + outKtFileName).joinToString("/")
            val file = File(ktGenRoot, outKtFileRelative)
            file.parentFile.mkdirs()
            file
        }
        val driverOptions = StubIrDriver.DriverOptions(
                entryPoint,
                moduleName,
                File(outCFile.absolutePath),
                outKtFileCreator,
                cinteropArguments.dumpBridges ?: false
        )
        val stubIrDriver = StubIrDriver(stubIrContext, driverOptions)
        stubIrDriver.run()
    }

    // TODO: if a library has partially included headers, then it shouldn't be used as a dependency.
    def.manifestAddendProperties["includedHeaders"] = nativeIndex.includedHeaders.joinToString(" ") { it.value }

    def.manifestAddendProperties.putAndRunOnReplace("package", outKtPkg) {
        _, oldValue, newValue ->
            warn("The package value `$oldValue` specified in .def file is overridden with explicit $newValue")
    }
    def.manifestAddendProperties["interop"] = "true"
    if (stubIrOutput is StubIrDriver.Result.Metadata) {
        def.manifestAddendProperties["ir_provider"] = KLIB_INTEROP_IR_PROVIDER_IDENTIFIER
    }
    stubIrContext.addManifestProperties(def.manifestAddendProperties)
    // cinterop command line option overrides def file property
    val foreignExceptionMode = cinteropArguments.foreignExceptionMode?: def.config.foreignExceptionMode
    foreignExceptionMode?.let {
        def.manifestAddendProperties[ForeignExceptionMode.manifestKey] =
                ForeignExceptionMode.byValue(it).value   // may throw IllegalArgumentException
    }

    manifestAddend?.parentFile?.mkdirs()
    manifestAddend?.let { def.manifestAddendProperties.storeProperties(it) }

    val compilerArgs = stubIrContext.libraryForCStubs.compilerArgs.toTypedArray()
    val nativeOutputPath: String = when (flavor) {
        KotlinPlatform.JVM -> {
            val outOFile = tempFiles.create(libName,".o")
            val compilerCmd = arrayOf(compiler, *compilerArgs,
                    "-c", outCFile.absolutePath, "-o", outOFile.absolutePath)
            runCmd(compilerCmd, verbose)
            val linkerOpts =
                    def.config.linkerOpts.toTypedArray() +
                            tool.getDefaultCompilerOptsForLanguage(library.language) +
                            additionalLinkerOpts
            val outLib = File(nativeLibsDir, System.mapLibraryName(libName))
            val linkerCmd = arrayOf(linker,
                    outOFile.absolutePath, "-shared", "-o", outLib.absolutePath,
                    *linkerOpts)
            runCmd(linkerCmd, verbose)
            outOFile.absolutePath
        }
        KotlinPlatform.NATIVE -> {
            val outLib = File(nativeLibsDir, "$libName.bc")
            // Note that the output bitcode contains the source file path, which can lead to non-deterministc builds (see KT-54284).
            // The source file is passed in via stdin to ensure the output library is deterministic.
            val compilerCmd = arrayOf(compiler, *compilerArgs,
                    "-emit-llvm", "-x", library.language.clangLanguageName, "-c", "-", "-o", outLib.absolutePath, "-Xclang", "-detailed-preprocessing-record")
            runCmd(compilerCmd, verbose, redirectInputFile = File(outCFile.absolutePath))
            outLib.absolutePath
        }
    }

    val compiledFiles = compileSources(nativeLibsDir, tool, cinteropArguments)

    return when (stubIrOutput) {
        is StubIrDriver.Result.SourceCode -> {
            val bitcodePaths = compiledFiles.map {  listOf("-native-library", it) }.flatten()
            argsToCompiler(staticLibraries, libraryPaths) + bitcodePaths
        }
        is StubIrDriver.Result.Metadata -> {
            val stdlibDependency = resolver.resolveWithDependencies(
                    emptyList(),
                    noDefaultLibs = true,
                    noEndorsedLibs = true
            ).getFullList()

            val nopack = cinteropArguments.nopack
            val outputPath = cinteropArguments.output.let {
                val suffix = CompilerOutputKind.LIBRARY.suffix(tool.target)
                if (nopack) it.removeSuffixIfPresent(suffix) else it.suffixIfNot(suffix)
            }

            createInteropLibrary(
                    metadata = stubIrOutput.metadata,
                    nativeBitcodeFiles = compiledFiles + nativeOutputPath,
                    target = tool.target,
                    moduleName = moduleName,
                    libraryVersion = cinteropArguments.libraryVersion,
                    outputPath = outputPath,
                    manifest = def.manifestAddendProperties,
                    dependencies = stdlibDependency + imports.requiredLibraries.toList(),
                    nopack = nopack,
                    shortName = cinteropArguments.shortModuleName,
                    staticLibraries = resolveLibraries(staticLibraries, libraryPaths)
            )
            return null
        }
    }
}

private fun compileSources(
        nativeLibsDir: String,
        toolConfig: ToolConfig,
        cinteropArguments: CInteropArguments
): List<String> = cinteropArguments.compileSource.mapIndexed { index, source ->
    // Mangle file name to avoid collisions.
    val mangledFileName = "${index}_${File(source).nameWithoutExtension}"
    val outputFileName = "$nativeLibsDir/${mangledFileName}.bc"
    val compilerArgs = cinteropArguments.sourceCompileOptions.toTypedArray()
    val compilerCmd = toolConfig.clang.clangCXX(*compilerArgs, source, "-emit-llvm", "-c", "-o", outputFileName)
    runCmd(compilerCmd.toTypedArray(), verbose = cinteropArguments.verbose)
    outputFileName
}

private fun getLibraryResolver(
        cinteropArguments: CInteropArguments, target: KonanTarget
): KotlinLibraryResolverImpl<KonanLibrary> {
    val libraries = cinteropArguments.library
    val repos = cinteropArguments.repo
    return defaultResolver(
            repos,
            libraries.filter { it.contains(org.jetbrains.kotlin.konan.file.File.separator) },
            target,
            Distribution(KonanHomeProvider.determineKonanHome())
    ).libraryResolver()
}

private fun resolveDependencies(
        resolver: KotlinLibraryResolverImpl<KonanLibrary>, cinteropArguments: CInteropArguments
): List<KotlinLibrary> {
    val libraries = cinteropArguments.library
    val noDefaultLibs = cinteropArguments.nodefaultlibs || cinteropArguments.nodefaultlibsDeprecated
    val noEndorsedLibs = cinteropArguments.noendorsedlibs
    return resolver.resolveWithDependencies(
            libraries.toUnresolvedLibraries,
            noStdLib = false,
            noDefaultLibs = noDefaultLibs,
            noEndorsedLibs = noEndorsedLibs
    ).getFullList(TopologicalLibraryOrder)
}

internal fun prepareTool(target: String?, flavor: KotlinPlatform, runFromDaemon: Boolean, propertyOverrides: Map<String, String> = emptyMap()) =
        ToolConfig(target, flavor, propertyOverrides).also {
            if (!runFromDaemon) it.prepare() // Daemon prepares the tool himself. (See KonanToolRunner.kt)
        }

internal fun buildNativeLibrary(
        tool: ToolConfig,
        def: DefFile,
        arguments: CInteropArguments,
        imports: ImportsImpl
): NativeLibrary {
    val additionalHeaders = (arguments.header).toTypedArray()
    val additionalCompilerOpts = (arguments.compilerOpts +
            arguments.compilerOptions + arguments.compilerOption).toTypedArray()

    val headerFiles = def.config.headers + additionalHeaders
    val language = selectNativeLanguage(def.config)
    val compilerOpts: List<String> = mutableListOf<String>().apply {
        addAll(def.config.compilerOpts)
        addAll(tool.getDefaultCompilerOptsForLanguage(language))
        addAll(additionalCompilerOpts)
        addAll(getCompilerFlagsForVfsOverlay(arguments.headerFilterPrefix.toTypedArray(), def))
        add("-Wno-builtin-macro-redefined") // to suppress warning from predefinedMacrosRedefinitions(see below)
    }

    // Expanding macros such as __FILE__ or __TIME__ exposes arbitrary generated filenames and timestamps from the compiler pipeline
    // which are not useful for interop though makes the klib generation non-deterministic. See KT-54284
    // This macro redefinition just maps to their name in the properties available from Kotlin.
    val predefinedMacrosRedefinitions = predefinedMacros.map {
        "#define $it \"$it\""
    }

    val compilation = CompilationImpl(
            includes = headerFiles.map { IncludeInfo(it, null) },
            additionalPreambleLines = def.defHeaderLines + predefinedMacrosRedefinitions,
            compilerArgs = defaultCompilerArgs(language) + compilerOpts + tool.platformCompilerOpts,
            language = language
    )

    val headerFilter: NativeLibraryHeaderFilter
    val includes: List<IncludeInfo>

    val modules = def.config.modules

    if (modules.isEmpty()) {
        require(headerFiles.isEmpty() || !compilation.compilerArgs.contains("-fmodules")) { "cinterop doesn't support having headers in -fmodules mode" }
        val excludeDependentModules = def.config.excludeDependentModules

        val headerFilterGlobs = def.config.headerFilter
        val excludeFilterGlobs = def.config.excludeFilter
        val headerInclusionPolicy = HeaderInclusionPolicyImpl(headerFilterGlobs, excludeFilterGlobs)

        headerFilter = NativeLibraryHeaderFilter.NameBased(headerInclusionPolicy, excludeDependentModules)
        includes = headerFiles.map { IncludeInfo(it, null) }
    } else {
        require(language == Language.OBJECTIVE_C) { "cinterop supports 'modules' only when 'language = Objective-C'" }
        require(headerFiles.isEmpty()) { "cinterop doesn't support having headers and modules specified at the same time" }
        require(def.config.headerFilter.isEmpty()) { "cinterop doesn't support 'headerFilter' with 'modules'" }

        val modulesInfo = getModulesInfo(compilation, modules)

        headerFilter = NativeLibraryHeaderFilter.Predefined(modulesInfo.ownHeaders, modulesInfo.modules)
        includes = modulesInfo.topLevelHeaders
    }

    val excludeSystemLibs = def.config.excludeSystemLibs

    val headerExclusionPolicy = HeaderExclusionPolicyImpl(imports)

    return NativeLibrary(
            includes = includes,
            additionalPreambleLines = compilation.additionalPreambleLines,
            compilerArgs = compilation.compilerArgs,
            headerToIdMapper = HeaderToIdMapper(sysRoot = tool.sysRoot),
            language = compilation.language,
            excludeSystemLibs = excludeSystemLibs,
            headerExclusionPolicy = headerExclusionPolicy,
            headerFilter = headerFilter
    )
}

fun parseKeyValuePairs(
    argumentValue: List<String>,
): Map<String, String> = argumentValue.mapNotNull {
    val keyValueSeparatorIndex = it.indexOf('=')
    if (keyValueSeparatorIndex > 0) {
        it.substringBefore('=') to it.substringAfter('=')
    } else {
        warn("incorrect property format: expected '<key>=<value>', got '$it'")
        null
    }
}.toMap()
