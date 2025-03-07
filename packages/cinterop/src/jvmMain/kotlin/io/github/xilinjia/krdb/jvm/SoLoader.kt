/*
 * Copyright 2021 Realm Inc.
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

package io.github.xilinjia.krdb.jvm

import io.github.xilinjia.krdb.internal.SDK_VERSION
import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * Load the C++ dynamic libraries from the fat Jar.
 * The fat Jar contains three platforms (Win, Linux and Mac) the loader detects the host platform
 * then extract and install the libraries.
 *
 * Note: this class should be invoke dynamically using reflection so the classloader can have accesses
 * to the dynamic libraries files located inside the fat Jar.
 */
class SoLoader {
    private val platform: Platform = Platform.currentOS()
    private val libraryName = "realmc"

    @Suppress("unused") // Called using reflection. See /packages/jni-swig-stub/realm.i
    fun load() {
        // First attempt to load the native library code using `System.loadLibrary()`. This is the
        // default way of shipping dependencies to JVM Desktop apps, but it does require that the
        // author of these apps have extracted the library from our JAR file and manually placed it
        // in the location defined by "java.libraries.path".
        //
        // If this fails, we will fallback to finding the native code inside the JAR file and
        // store it in the users cache directory.
        //
        // See https://github.com/realm/realm-kotlin/issues/1105 for more information.
        try {
            System.loadLibrary(libraryName)
        } catch (ex: UnsatisfiedLinkError) {
            load(libraryName)
        }
    }

    private fun load(libraryName: String) {
        // load the embedded .so file located inside the Jar file.
        // unpacking the file is skipped if the file is already installed.
        // instead, the on-disk file will be loaded.

        // for each SO file:
        // check if the library is already installed in the default platform location
        // path should be <default user lib dir>/io.github.xilinjia.krdb/libraryVersion]/librealmffi.so
        // if the full path exists then load it otherwise unpack and load it.
        val libraryInstallationLocation: File = defaultAbsolutePath(libraryName)
        if (!libraryInstallationLocation.exists() || SDK_VERSION.endsWith("-SNAPSHOT", ignoreCase = true)) {
            unpackAndInstall(libraryName, libraryInstallationLocation)
        }
        @Suppress("UnsafeDynamicallyLoadedCode")
        // System.loadLibrary does not accept a full path to the lib (needs to be in the current Java paths)
        System.load(libraryInstallationLocation.absolutePath)
    }

    private fun defaultAbsolutePath(libraryName: String): File {
        return File(
            platform.defaultSystemLocation +
                File.separator +
                SDK_VERSION +
                File.separator +
                (platform.prefix + libraryName + "." + platform.suffix)
        )
    }

    private fun libPathInsideJar(libraryName: String) =
        "${platform.shortName}/${platform.prefix}$libraryName.${platform.suffix}"

    private fun unpackAndInstall(libraryName: String, absolutePath: File) {
        absolutePath.parentFile.mkdirs()
        javaClass.getResourceAsStream(libPathInsideJar(libraryName)).use { lib ->
            Files.newOutputStream(absolutePath.toPath()).use {
                lib.copyTo(it)
            }
        }
    }
}

private enum class Platform(
    val shortName: String,
    val prefix: String,
    val suffix: String,
    val defaultSystemLocation: String
) {
    MACOS(
        shortName = "/jni/macos",
        prefix = "lib",
        suffix = "dylib",
        defaultSystemLocation = "${System.getProperty("user.home")}/Library/Caches/io.github.xilinjia.krdb/"
    ),
    LINUX(
        shortName = "/jni/linux",
        prefix = "lib",
        suffix = "so",
        defaultSystemLocation = "${System.getProperty("user.home")}/.cache/io.github.xilinjia.krdb/"
    ),
    WINDOWS(
        shortName = "/jni/windows",
        prefix = "",
        suffix = "dll",
        defaultSystemLocation = (
            System.getenv("LOCALAPPDATA")
                ?: "${System.getProperty("user.home")}/AppData/Local"
            ) + "/io-realm-kotlin/"
    );

    companion object {
        fun currentOS(): Platform {
            val os = System.getProperty("os.name").lowercase(Locale.getDefault())
            return when {
                os.contains("win") -> {
                    WINDOWS
                }
                os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
                    LINUX
                }
                os.contains("mac") -> {
                    MACOS
                }
                else -> error("Unsupported OS: $os")
            }
        }
    }
}
