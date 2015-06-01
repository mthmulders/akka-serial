import sbt._
import sbtrelease.ReleasePlugin
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import java.util.regex.Pattern

object CustomReleasePlugin extends AutoPlugin {

  implicit class RegexHelper(val sc: StringContext) extends AnyVal {
    def rq(args: Any*): String = {
      val escaped = args map {arg =>
       Pattern.quote(arg.toString)
      }
      sc.s(escaped: _*)
    }
  }

  def runReleaseTask[A](task: TaskKey[A]): State => State = { st: State =>
    val extracted = Project.extract(st)
    val ref = extracted.get(Keys.thisProjectRef)
    extracted.runAggregated(task in Global in ref, st)
  }

  object autoImport {
    val documentationFilter = settingKey[PathFinder]("Files to consider updating in updateDocumentation.")
    val updateDocumentation = taskKey[Seq[File]]("Update to the current version any sbt dependencies referencing this project.")
  }
  import autoImport._

  override def requires = ReleasePlugin
  override def trigger = allRequirements

  private val UpdateTag = ConcurrentRestrictions.Tag("Updatetag")

  val updateDocumentationTask = Def.task {
    val out = Keys.streams.value.log

    val organization = Keys.organization.value
    val name = Keys.name.value
    val newVersion = releaseVersion.value apply Keys.version.value

    val pattern = rq"""("$organization" %%? "$name" % )(".+")""".r

    for (file <- documentationFilter.value.get) yield {
      out.info("Updating self references in " + file.getPath)
      val content = IO.read(file)
      val replaced = pattern.replaceAllIn(content, {m =>
        m.group(1) + s""""$newVersion""""
      })
      IO.write(file, replaced)
      file
    }
  } tag UpdateTag

  override lazy val projectSettings = Seq(
    documentationFilter := {
      val base = (Keys.baseDirectory in ThisBuild).value
      base * ("README" || "README.*" || "readme" || "readme.*")
    },
    updateDocumentation := updateDocumentationTask.value,
    Keys.concurrentRestrictions in Global += Tags.exclusive(UpdateTag),
    releaseProcess := Seq(
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      runReleaseTask(updateDocumentation),
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
}
