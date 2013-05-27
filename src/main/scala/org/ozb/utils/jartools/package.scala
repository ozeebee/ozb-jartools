package org.ozb.utils

import java.io.File
import java.io.FileFilter
import org.ozb.utils.io.FileUtils
import scala.util.matching.Regex
import scala.reflect.BeanProperty

package object jartools {
	val allJavaArchives = List("jar", "war", "ear") 
	val allArchives = allJavaArchives :+ "zip"

	class ArchiveFileFilter(includePattern: Option[String], includeAllArchives: Boolean = false) extends FileFilter {
		def accept(file: File): Boolean = {
			if (file.isDirectory())
				true
			else {
				FileUtils.getExtension(file.getName()) match {
					case None => false
					case Some(ext) =>
						val archiveList = if (includeAllArchives) allArchives else allJavaArchives
						// the following will return false ONLY if the include pattern option is defined and if it DOES NOT match filename, otherwise true will be returned
						val incrslt = includePattern.map(p => new Regex(p).pattern.matcher(file.getName()).matches()).getOrElse(true)
						archiveList.contains(ext) && incrslt
				}
			}
		}
	}
	
	class Options (
		var includePattern: Option[String] = None, 
		@BeanProperty
		var ignoreCase: Boolean = false,
		@BeanProperty
		var allArchives: Boolean = false,
		@BeanProperty
		var regexp: Boolean = false
	) {
		override def toString() = {
			"Options{includePattern=[%s],ignoreCase=[%s],allArchives=[%s],regexp=[%s]}" format
				(includePattern, ignoreCase, allArchives, regexp)
		}
	}
	
	class Stats (
		var archCount: Int = 0, // number of processed archives
		var matchCount: Int = 0, // number of matching entries
		var dirCount: Int = 0, // number of scanned directories
		private var time0: Long = System.currentTimeMillis // elapsed time
	) {
		def elapsedTime(): Long = System.currentTimeMillis - time0
	}
	
	trait JarToolEvent

	/** Event published when a directory is entered to be scanned */
	case class DirEvent(dir: File) extends JarToolEvent
	/** Event published when an archive is going to be scanned */
	case class ArchiveEvent(archive: File) extends JarToolEvent
	/** Event published when an archive's entry is matching (JarFinder) or when it's going to be scanned (JarGrep) */
	case class ArchiveEntryEvent(name: String, size: Long = 0, time: java.util.Date, archive: File) extends JarToolEvent

	/** transforms a potentially quoted simple pattern to an (unquoted) regex pattern */
	def toRegexPattern(str: String) = {
			// un-quote pattern if it's quoted
			val QuotedPat = """"(.*)"""".r
			val pattern = str match { // unquote pattern
				case QuotedPat(p) =>p
				case _ => str
			}
			pattern.replace(".", "\\.").replace("*", ".*")
	}
}