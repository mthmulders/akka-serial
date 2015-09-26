import sbt._
import Keys._
import java.io.File
import java.util.jar.Manifest

object NativeKeys {

    val nativeBuildDirectory = settingKey[File]("Directory containing native build scripts.")
    val nativeTargetDirectory = settingKey[File]("Base directory to store native products.")
    val nativeOutputDirectory = settingKey[File]("Actual directory where native products are stored.")
    val nativePackageUnmanagedDirectory = settingKey[File]("Directory containing external products that will be copied to the native jar.")

    val nativeClean = taskKey[Unit]("Clean native build.")
    val nativeBuild = taskKey[File]("Invoke native build.")
}

//windows, as usual, needs special treatment
object CygwinUtil {

  def onCygwin: Boolean = {
    val uname = Process("uname").lines.headOption
    uname map {
      _.toLowerCase.startsWith("cygwin")
    } getOrElse {
      false
    }
  }

  def toUnixPath(path: String) = if (onCygwin) {
    Process(s"cygpath ${path}").lines.head
  } else {
    path
  }

}

object NativeDefaults {
    import NativeKeys._

    val autoClean = Def.task {
        val log = streams.value.log
        val build = nativeBuildDirectory.value

        Process("make distclean", build) #|| Process("make clean", build) ! log
    }

    val autoLib = Def.task {
        val log = streams.value.log
        val build = nativeBuildDirectory.value
        val out = nativeOutputDirectory.value

        val cygOut = CygwinUtil.toUnixPath(out.getAbsolutePath)

        val configure = Process(
            "sh ./configure " + //"sh" is required under cygwin
            "--prefix=" + cygOut + " " +
            "--libdir=" + cygOut + " " +
            "--disable-versioned-lib", //Disable producing versioned library files, not needed for fat jars.
            build)

        val make = Process("make", build)

        val makeInstall = Process("make install", build)

        val ev = configure #&& make #&& makeInstall ! log
        if (ev != 0)
            throw new RuntimeException(s"Building native library failed. Exit code: ${ev}")

        (out ** ("*.la")).get.foreach(_.delete())

        out
    }

    val nativePackageMappings = Def.task {
        val managedDir = nativeTargetDirectory.value
        val unmanagedDir = nativePackageUnmanagedDirectory.value

        val managed = (nativeBuild.value ** "*").get
        val unmanaged = (unmanagedDir ** "*").get

        val managedMappings: Seq[(File, String)] = for (file <- managed; if file.isFile) yield {
            file -> ("native/" + (file relativeTo managedDir).get.getPath)
        }

        val unmanagedMappings: Seq[(File, String)] = for (file <- unmanaged; if file.isFile) yield {
            file -> ("native/" + (file relativeTo unmanagedDir).get.getPath)
        }

        managedMappings ++ unmanagedMappings
    }

    def os = System.getProperty("os.name").toLowerCase.filter(c => !c.isWhitespace)
    def arch = System.getProperty("os.arch").toLowerCase

    val settings: Seq[Setting[_]] = Seq(
        nativeTargetDirectory := target.value / "native",
        nativeOutputDirectory := nativeTargetDirectory.value / (os + "-" + arch),
        nativeClean := autoClean.value,
        nativeBuild := autoLib.value,
        nativePackageUnmanagedDirectory := baseDirectory.value / "lib_native",
        mappings in (Compile, packageBin) ++= nativePackageMappings.value
    )

}

