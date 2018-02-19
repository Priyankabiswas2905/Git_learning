package services

import java.io.File
import javax.inject.Inject

import org.apache.commons.io.FileUtils
import play.api.Logger
import play.api.Play.current
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

trait FileDumpService {
  var fileDumpDir: Option[String]
  var fileDumpMoveDir: Option[String]
  def dump(fileDump: DumpOfFile)
}

/**
  * File dump service.
  *
  */
class FileDumpServiceImpl @Inject()(lifecycle: ApplicationLifecycle) extends FileDumpService {
  Logger.debug("Starting file dumper Plugin")
  val fileSep = System.getProperty("file.separator")
  var fileDumpDir = play.api.Play.configuration.getString("filedump.dir")
  var fileDumpMoveDir = play.api.Play.configuration.getString("filedumpmove.dir")

  lifecycle.addStopHook { () =>
    Logger.debug("Shutting down file dumper Plugin")
    Future.successful(())
  }

  lazy val enabled = {
    import play.api.Play.current
    !current.configuration.getString("filedumpservice").filter(_ == "disabled").isDefined
  }

  def dump(fileDump: DumpOfFile) = {
    Logger.debug("Dumping file " + fileDump.fileName)
    fileDumpDir match {
      case Some(dumpDir) => {
        val fileSep = System.getProperty("file.separator")
        val filePathInDirs = fileDump.fileId.charAt(fileDump.fileId.length() - 3) + fileSep + fileDump.fileId.charAt(fileDump.fileId.length() - 2) + fileDump.fileId.charAt(fileDump.fileId.length() - 1) + fileSep + fileDump.fileId + fileSep + fileDump.fileName
        val fileDumpingDir = dumpDir + filePathInDirs
        val copiedFile = new File(fileDumpingDir)
        FileUtils.copyFile(fileDump.fileToDump, copiedFile)

        fileDumpMoveDir match {
          case Some(dumpMoveDir) => {
            val fileDumpingMoveDir = dumpMoveDir + filePathInDirs
            val movedFile = new File(fileDumpingMoveDir)
            movedFile.getParentFile().mkdirs()

            if (copiedFile.renameTo(movedFile)) {
              Logger.debug("File dumped and moved to staging directory successfully.")
            } else {
              Logger.warn("Could not move dumped file to staging directory.")
            }
          }
          case None => Logger.warn("Could not move dumped file to staging directory. No staging directory set.")
        }
      }
      case None => Logger.warn("Could not dump file. No file dumping directory set.")
    }
  }

}

case class DumpOfFile(
  fileToDump: File,
  fileId: String,
  fileName: String
)