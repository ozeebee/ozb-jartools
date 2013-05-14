package org.ozb.utils.jartools

import java.io.File
import java.io.FileFilter
import java.util.zip.ZipException
import java.util.zip.ZipFile

import scala.collection.JavaConversions._
import scala.reflect.BeanProperty
import scala.util.matching.Regex

import org.ozb.utils.io.FileUtils.withZipFile
import org.ozb.utils.pubsub.Publisher

import scopt.OptionParser

/**
 * Find entries inside archives recursively.
 * Ex:
 * 1. find all occurances of 'Exception' entries (files) inside all the (jar/war/ear) archives under the WEB-INF dir
 *		$ jarfinder ./WEB-INF Exception
 * 2. find all occurances of python (.py) files inside jar files whose name matches "*template*jar"
 * 		$ jarfinder --include "*template*jar" . .py
 * 3. find all files matching "*Expand*gif" (ignoring case) in jar archives whose name matches "*ui*jar" under the Eclipse/ dir 
 * 		$ jarfinder -i --include *ui*jar ~/Dev/Eclipse *Expand*gif
 */
object JarFinder {
	def main(args: Array[String]) {
		val config = new Options()
		var basedir: String = null
		var dir: File = null
		var pattern: String = null
		val parser = new OptionParser("JarFinder") {
			//opt("d", "dir", "<dir>", "the directory to search in", {v: String => config.basedir = v; config.dir = new java.io.File(v)})
			arg("<dir>", "<dir> : directory to search in", { v: String => basedir = v; dir = new java.io.File(v) })
			arg("<pattern>", "<pattern> : pattern to look for", { pattern = _ })
			opt("i", "ignorecase", "ignore case", { config.ignoreCase = true })
			opt(None, "include", "<name pattern>", "include only archives whose name match the pattern", { v: String => config.includePattern = Some(toRegexPattern(v)) })
			opt("a", "allarchives", "all archives (include zip files)", { config.allArchives = true })
			opt("e", "regexp", "use regular expression", { config.regexp = true })
		}
		
		if (parser.parse(args)) {
			println("looking for [" + pattern + "] in dir [" + basedir + "]" +
						(config.includePattern.map(" including archives matching " + _).getOrElse(""))
					)
			
			val jarFinder = new JarFinder()
			var lastArchiveMatch: File = null
			// setup subscriber
			jarFinder.subscribe(_ match {
				case e: ArchiveEntryEvent =>
					if (e.archive != lastArchiveMatch)
						println("." + e.archive.getPath diff basedir)
					lastArchiveMatch = e.archive
					println(" ==> " + e.name)
				case _ =>
			})
			val stats = jarFinder.search(dir, pattern, config)
			println("found " + stats.matchCount + " entries in " + stats.archCount + " processed archives, scanned " + stats.dirCount + " directories")
			println("in " + stats.elapsedTime() + " millis")
		}
	}	
}

class JarFinder extends JarTool[Options, Stats] {
	def search(dir: File, pattern: String, options: Options) = {
		val stats = new Stats()
		// check directory exists and is a dir
		if (! dir.isDirectory())
			throw new IllegalArgumentException("dir [" + dir + "] is not a valid directory")
		
		val fileFilter = new ArchiveFileFilter(options.includePattern, options.allArchives) 
		
		// the regex to be matched against archive entries
		val pattern2 = if (options.regexp) pattern else pattern.replace(".", "\\.").replace("*", ".*")
		val regex = if (options.ignoreCase) new Regex("(?i)"+pattern2) else new Regex(pattern2)
		println("start search with regex : %s" format regex)
		scanDir(dir, fileFilter, regex, options, stats)
		stats
	}
	
	override protected def scanArchive(file: File, regex: Regex, options: Options, stats: Stats) {
		try { withZipFile(file) { zfile =>
			val entries = zfile.entries() // converted to scala Iterator thanks to implicit definitions in JavaConversions
			// find entries matching the pattern and that are not paths (directories)
			val matches = entries filter (entry =>
				 ! entry.getName().endsWith("/") && ! (regex findFirstIn entry.getName).isEmpty 
			)
			if (! matches.isEmpty) {
//				println("." + file.getPath diff options.basedir)
				matches foreach {entry => 
					stats.matchCount += 1
					publish(ArchiveEntryEvent(entry.getName(), entry.getSize(), new java.util.Date(entry.getTime()), file))
				}
			}
		} } catch {
			case ex: ZipException => err("Could not open zip file %s, exception : %s" format (file, ex))
		}
	}
	
}
