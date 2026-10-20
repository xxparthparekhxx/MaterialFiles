/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.file

import android.webkit.MimeTypeMap
import me.zhanghai.android.files.compat.getMimeTypeFromExtensionCompat
import me.zhanghai.android.files.provider.common.PosixFileType
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asPathName

fun MimeType.Companion.guessFromPath(path: String): MimeType {
    val fileName = path.asPathName().fileName ?: return DIRECTORY
    return guessFromExtension(fileName.asFileName().singleExtension)
}

fun MimeType.Companion.guessFromExtension(extension: String): MimeType {
    val extension = extension.lowercase()
    return extensionToMimeTypeOverrideMap[extension]
        ?: MimeTypeMap.getSingleton().getMimeTypeFromExtensionCompat(extension)?.asMimeTypeOrNull()
        ?: GENERIC
}

// @see https://android.googlesource.com/platform/external/mime-support/+/master/mime.types
// @see https://android.googlesource.com/platform/frameworks/base/+/master/mime/java-res/android.mime.types
// @see http://www.iana.org/assignments/media-types/media-types.xhtml
// @see https://salsa.debian.org/debian/media-types/-/blob/master/mime.types
// @see /usr/share/mime/packages/freedesktop.org.xml
private val extensionToMimeTypeOverrideMap = mapOf(
    // Fixes
    "csv" to "text/csv", // Was "text/comma-separated-values"
    "sh" to "application/x-sh", // Was "text/x-sh"
    // Addition
    // The system map has no type for cfg, so it was octet-stream and Always could not stick.
    "cfg" to "text/plain",
    "bz" to "application/x-bzip",
    "bz2" to "application/x-bzip2",
    "7z" to "application/x-7z-compressed",
    "iso" to "application/x-iso9660-image",
    "rar" to "application/vnd.rar",
    "wim" to "application/x-ms-wim",
    "swm" to "application/x-ms-wim",
    "esd" to "application/x-ms-wim",
    "z" to "application/x-compress",
    "lz" to "application/x-lzip",
    "lzip" to "application/x-lzip",
    "tlz" to "application/x-lzip-compressed-tar",
    "lpzip" to "application/zip",
    "lzma" to "application/x-lzma",
    "p7b" to "application/x-pkcs7-certificates",
    "spc" to "application/x-pkcs7-certificates", // Clashes with "chemical/x-galactic-spc"
    "ts" to "application/typescript", // Clashes with "video/mp2ts"
    "py3" to "text/x-python",
    "py3x" to "text/x-python",
    "pyx" to "text/x-python",
    "wsgi" to "text/x-python",
    "yml" to "application/yaml",
    "asm" to "text/x-asm",
    "s" to "text/x-asm",
    "cs" to "text/x-csharp",
    "azw" to "application/vnd.amazon.ebook",
    "ibooks" to "application/x-ibooks+zip",
    "msg" to "application/vnd.ms-outlook",
    "mkd" to "text/markdown",
    "conf" to "text/plain",
    "ini" to "text/plain",
    "list" to "text/plain",
    "log" to "text/plain",
    "prop" to "text/plain",
    "properties" to "text/plain",
    "rc" to "text/plain",
    // Source code not covered by the system MIME map, so it does not fall back to the generic icon.
    "kt" to "text/x-source",
    "kts" to "text/x-source",
    "go" to "text/x-source",
    "swift" to "text/x-source",
    "lua" to "text/x-source",
    "groovy" to "text/x-source",
    "gradle" to "text/x-source",
    "proto" to "text/x-source",
    "graphql" to "text/x-source",
    "gql" to "text/x-source",
    "vue" to "text/x-source",
    "svelte" to "text/x-source",
    "jsx" to "text/x-source",
    "tsx" to "text/x-source",
    "cjs" to "text/x-source",
    "clj" to "text/x-source",
    "cljs" to "text/x-source",
    "cljc" to "text/x-source",
    "edn" to "text/x-source",
    "ex" to "text/x-source",
    "exs" to "text/x-source",
    "erl" to "text/x-source",
    "hrl" to "text/x-source",
    "ml" to "text/x-source",
    "mli" to "text/x-source",
    "fs" to "text/x-source",
    "fsx" to "text/x-source",
    "vb" to "text/x-source",
    "sol" to "text/x-source",
    "zig" to "text/x-source",
    // "vhd" is not claimed, because it is the extension of a virtual hard disk image.
    "vhdl" to "text/x-source",
    "jl" to "text/x-source",
    "r" to "text/x-source",
    "rkt" to "text/x-source",
    "lisp" to "text/x-source",
    "el" to "text/x-source",
    "elm" to "text/x-source",
    "cr" to "text/x-source",
    "f" to "text/x-source",
    "f90" to "text/x-source",
    "f95" to "text/x-source",
    "for" to "text/x-source",
    "cob" to "text/x-source",
    "cbl" to "text/x-source",
    "awk" to "text/x-source",
    "sed" to "text/x-source",
    "ps1" to "text/x-source",
    "psm1" to "text/x-source",
    "hcl" to "text/x-source",
    "tf" to "text/x-source",
    "tfvars" to "text/x-source",
    "nix" to "text/x-source",
    "toml" to "text/x-source",
    "astro" to "text/x-source",
    "csproj" to "text/x-source",
    "vbproj" to "text/x-source",
    "nuspec" to "text/x-source",
    "sln" to "text/x-source"
).mapValues { it.value.asMimeType() }

fun MimeType.Companion.forSpecialPosixFileType(type: PosixFileType): MimeType? =
    specialPosixFileTypeToMimeTypeMap[type]

// See also https://developer.gnome.org/shared-mime-info-spec/
/** @see specialPosixFileTypeToNameResMap */
private val specialPosixFileTypeToMimeTypeMap = mapOf(
    PosixFileType.CHARACTER_DEVICE to "inode/chardevice",
    PosixFileType.BLOCK_DEVICE to "inode/blockdevice",
    PosixFileType.FIFO to "inode/fifo",
    PosixFileType.SYMBOLIC_LINK to "inode/symlink",
    PosixFileType.SOCKET to "inode/socket"
).mapValues { it.value.asMimeType() }

val MimeType.extension: String?
    // TODO: Add compat implementation as well.
    get() = MimeTypeMap.getSingleton().getExtensionFromMimeType(value)

val MimeType.intentType: String
    get() = intentMimeType.value

private val MimeType.intentMimeType: MimeType
    get() = mimeTypeToIntentMimeTypeMap[this] ?: this

private val mimeTypeToIntentMimeTypeMap = listOf(
    // Allows matching "text/*"
    "application/ecmascript" to "text/ecmascript",
    "application/javascript" to "text/javascript",
    "application/json" to "text/json",
    "application/typescript" to "text/typescript",
    "application/yaml" to "text/x-yaml",
    "application/x-sh" to "text/x-shellscript",
    "application/x-shellscript" to "text/x-shellscript",
    // Open source files with any text editor.
    "text/x-source" to "text/plain",
    // Allows matching generic
    MimeType.GENERIC.value to MimeType.ANY.value
).associate { it.first.asMimeType() to it.second.asMimeType() }

val Collection<MimeType>.intentType: String
    get() {
        if (isEmpty()) {
            return MimeType.ANY.value
        }
        val intentMimeTypes = map { it.intentMimeType }
        val firstIntentMimeType = intentMimeTypes.first()
        if (intentMimeTypes.all { firstIntentMimeType.match(it) }) {
            return firstIntentMimeType.value
        }
        val wildcardIntentMimeType = MimeType.of(firstIntentMimeType.type, "*", null)
        if (intentMimeTypes.all { wildcardIntentMimeType.match(it) }) {
            return wildcardIntentMimeType.value
        }
        return MimeType.ANY.value
    }
