package org.ozb.utils.jartools

import scopt.OptionParser
import scala.collection.JavaConversions._
import scala.util.matching.Regex
import java.io.File
import java.io.FilenameFilter
import java.io.FileFilter
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.io.InputStream
import scala.io.Source
import org.ozb.utils.io.FileUtils.withZipFile
import org.ozb.utils.io.FileUtils.isBinaryContent
import org.ozb.utils.io.IOUtils.withInputStream
import org.ozb.utils.pubsub.Publisher
import scala.reflect.BeanProperty

/**
 * Find text within archive entries.
 * 
 * Example usages:
 * 
 * 1. Search for all occurances of 'setDomainEnv' in jar file toto.jar:
 * 		$ jargrep setDomainEnv toto.jar
 * 2. Search for all occurances of 'setDomainEnv', recursively in all archives under the mydir/ directory
 * 		$ jargrep -r setDomainEnv mydir/
 * 3. Search for all occurances of 'PreferencesDialog' in java files, recursively in all archives matching '*source*jar' under the eclipse/ directory
 * 	  using the ISO-8859-1 encoding for reading text files
 * 		$ jargrep -r --enc ISO-8859-1 --include *source*jar --includeEntry *.java "PreferencesDialog" eclipse/
 */
object JarGrep {
	def main(args: Array[String]) {
		var pattern: String = null
		var fileOrDir: File = null
		val parser = new OptionParser[JarGrepOptions]("JarGrep") {
			arg[String]("<pattern>") text("pattern to look for") action { (x, c) => 
				pattern = x; c }
			arg[File]("<file>") text("archive file to grep or directory to recurse into") action { (x, c) => 
				fileOrDir = x; c }
			opt[Unit]('i', "ignorecase") text("ignore case") action { (_, c) => 
				c.ignoreCase = true; c }
			opt[Unit]('v', "verbose") text("show informartion on each file/entry processed") action { (_, c) => 
				c.verbose = true; c }
			opt[Unit]('r', "recurse") text("recursively process the given directory") action { (_, c) => 
				c.recurse = true; c }
			opt[String]("enc") valueName("<encoding>") text("use given encoding instead of platform's default. Ex. 'UTF-8' or 'ISO-8859-1'.") action { (x, c) =>
				c.encoding = Some(x); c }
			opt[String]("include") valueName("<name pattern>") text("include only archives whose name match the pattern") action { (x, c) => 
				c.includePattern = Some(toRegexPattern(x)); c }
			opt[String]("includeEntry") valueName("<name pattern>") text("process archive entries whose name match the pattern") action { (x, c) => 
				c.includeEntryPattern = Some(toRegexPattern(x)); c }
			
			opt[Unit]('a', "allarchives") text("all archives (include zip files)") action { (_, c) => 
				c.allArchives = true; c }
		}

		parser.parse(args, new JarGrepOptions()) map { config =>
			if (config.recurse)
				println("looking for [" + pattern + "] in dir [" + fileOrDir + "]" +
							(config.includePattern.map(" including archives matching " + _).getOrElse("")) +
							(config.includeEntryPattern.map(" including entries matching " + _).getOrElse(""))
						)
			else
				println("looking for [" + pattern + "] in file [" + fileOrDir + "]")
				
			val jarGrep = new JarGrep()
			var lastArchiveMatch: File = null
			// setup subscriber
			jarGrep.subscribe(_ match {
				case e: DirEvent =>
				case e: ArchiveEvent => if (config.verbose)	println("  processing archive [%s]" format (e.archive))
				case e: ArchiveEntryEvent => //println("    inspecting entry " + e.name)
				case e: LineMatchEvent => 
				case e: EntryMatchesEvent =>
					// print archive name (once) if an entry matches
					if (e.archive != lastArchiveMatch)
						println(e.archive.getPath)
					lastArchiveMatch = e.archive
					println("  " + e.entry)
					e.matches.foreach(m => println("        line %d : %s" format (m._1, m._2)))

			})
			val stats = jarGrep.search(fileOrDir, pattern, config)
			if (config.recurse)
				println("found %d matches in %d entries and %d archives, processed %d entries and %d archives" format (stats.lineMatchCount, stats.matchCount, stats.archMatchCount, stats.entryCount, stats.archCount))
			else
				println("found %d matches in %d entries, processed %d entries" format (stats.lineMatchCount, stats.matchCount, stats.entryCount))
			println("in " + stats.elapsedTime() + " millis")
		}
	}
}

class JarGrepOptions (
	var verbose: Boolean = false,
	@BeanProperty
	var recurse: Boolean = false,
	var includeEntryPattern: Option[String] = None,
	var encoding: Option[String] = None
) extends Options {
	override def toString() = {
		"Options{includePattern=[%s],ignoreCase=[%s],allArchives=[%s],regexp=[%s],recurse=[%s],includeEntryPattern=[%s],encoding=[%s]}" format
			(includePattern, ignoreCase, allArchives, regexp, recurse, includeEntryPattern, encoding)
	}

}
	
case class JarGrepStats (
	var lineMatchCount: Int = 0, // number of matching lines
	var archMatchCount: Int = 0, // number of matching archives
	var entryCount: Int = 0 // number of processed entries
) extends Stats

/** Event published when a line is matching */
case class LineMatchEvent(archive: File, entry: String, linenum: Int, line: String) extends JarToolEvent
/** Event published when an archive entry has at least one matching line */
case class EntryMatchesEvent(archive: File, entry: String, matches: Seq[(Int, String)]) extends JarToolEvent

class JarGrep extends JarTool[JarGrepOptions, JarGrepStats] {

	def search(fileOrDir: File, pattern: String, options: JarGrepOptions) = {
		val stats = new JarGrepStats()
		// the regex to be matched
		val regex = if (options.ignoreCase) new Regex("(?i)" + pattern) else new Regex(pattern)
		
		if (options.recurse) {
			// check directory exists and is a dir
			if (! fileOrDir.isDirectory())
				throw new IllegalArgumentException("dir [" + fileOrDir + "] is not a valid directory")
			
			val fileFilter = new ArchiveFileFilter(options.includePattern, options.allArchives)
			
			scanDir(fileOrDir, fileFilter, regex, options, stats)
		} else {
			// check file exists and is a readable
			if (! fileOrDir.canRead())
				throw new IllegalArgumentException("file [" + fileOrDir + "] cannot be read")
					
			scanArchive(fileOrDir, regex, options, stats)
		}
		stats
	}
	
	override protected def scanArchive(file: File, regex: Regex, options: JarGrepOptions, stats: JarGrepStats): Unit = {
		try { withZipFile(file) { zfile =>
			val entries: Iterator[ZipEntry] = options.includeEntryPattern match { // converted to scala Iterator thanks to implicit definitions in JavaConversions
				case Some(p) => zfile.entries() filter (e => new Regex(p).pattern.matcher(e.getName()).matches)
				case None => zfile.entries()
			}
			val matchFound: Boolean = entries.foldLeft(false) { (result: Boolean, entry: ZipEntry) =>
				val entryMatches = processEntry(file, zfile, entry, regex, options, stats)
				if (entryMatches.size > 0) {
					publish(EntryMatchesEvent(file, entry.getName(), entryMatches))
					stats.matchCount += 1
				}
				
				result || entryMatches.size > 0
			}
			if (matchFound)
				stats.archMatchCount += 1
		} } catch {
			case ex: ZipException => err("Could not open zip file %s, exception : %s" format (file, ex))
		}
	}
	
	/**
	 * return a sequence of matches : a Tuple2 containing the line number and the matching line
	 */
	def processEntry(file: File, zipfile: ZipFile, entry: ZipEntry, regex: Regex, options: JarGrepOptions, stats: JarGrepStats): Seq[Tuple2[Int, String]] = {
		if (entry.getName().endsWith("/"))
			return Seq.empty;	// skip directory entries
		
		publish(ArchiveEntryEvent(entry.getName(), entry.getSize(), new java.util.Date(entry.getTime()), file))
		stats.entryCount += 1
		
		if (entry.getSize() == 0) { // skip 0 length entries
			if (options.verbose)	println("    skipping entry [%s] : empty file" format (entry.getName()))
			return Seq.empty;
		}

		if (isBinaryEntry(zipfile, entry)) { // skip binary entries
			if (options.verbose)	println("    skipping entry [%s] : binary file" format (entry.getName()))
			return Seq.empty;
		}

		withInputStream(zipfile.getInputStream(entry)) { is =>
			if (options.verbose)	println("    processing entry [%s]" format (entry.getName()))
			var linenum = 0
			val source = options.encoding.map(Source.fromInputStream(is, _)).getOrElse(Source.fromInputStream(is))
			source.getLines().foldLeft(Seq.empty[Tuple2[Int, String]]) { (result: Seq[Tuple2[Int, String]], line: String) =>
				//println("%s    line : %s" format (indent, line))
				linenum += 1
				if (regex.findFirstIn(line).isDefined) { // match found !
					stats.lineMatchCount += 1
					publish(LineMatchEvent(file, entry.getName(), linenum, line))
					result :+ (linenum, line) // add match : a Tuple2 containing the line number and the matching line
				}
				else
					result
			}
		}
	}

	def isBinaryEntry(zipfile: ZipFile, entry: ZipEntry): Boolean = 
			withInputStream(zipfile.getInputStream(entry))(isBinaryContent(_, false))

}
