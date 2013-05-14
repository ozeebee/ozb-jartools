package org.ozb.utils.jartools

import org.ozb.utils.pubsub.Publisher
import java.io.File
import java.io.FileFilter
import scala.util.matching.Regex

abstract class JarTool[O <: Options, S <: Stats] extends Publisher[JarToolEvent] {

	/**
	 * Scan a directory recursively and process archives matching the given file filter by
	 * applying the passed function on the archive
	 */
	protected def scanDir(scanArchFunc: (File, Regex, O, S) => Unit)(dir: File, fileFilter: FileFilter, regex: Regex, options: O, stats: S): Unit = {
		stats.dirCount += 1
		publish(DirEvent(dir))
		val files = dir.listFiles(fileFilter)
		files foreach { file =>
			if (! file.isDirectory()) {
				stats.archCount += 1
				publish(ArchiveEvent(file))
				scanArchFunc(file, regex, options, stats)
			}
			else
				scanDir(scanArchFunc)(file, fileFilter, regex, options, stats)
		}
	}
	
	protected def scanDir(dir: File, fileFilter: FileFilter, regex: Regex, options: O, stats: S): Unit =
			scanDir(scanArchive)(dir, fileFilter, regex, options, stats)
	

	protected def scanArchive(file: File, regex: Regex, options: O, stats: S)

	protected def err(msg: String) = {
		println("[ERR] " + msg)
	}

}