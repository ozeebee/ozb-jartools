# ozb-jartools: JarFinder and JarGrep


Two useful command line apps to search for entries and content in Java standard archives (jar/war/ear) files (or even zip files if specified).

These tools are implemented with Scala 2.9.3 on the JVM.
So you need a Java Runtime Environment or JDK >= 1.6 to run these tools.

## JarFinder

JarFinder lets you find archive entries recursively under a given directory.

```
Usage: JarFinder [options] <dir> <pattern>

  -i | --ignorecase
        ignore case
  --include <name pattern>
        include only archives whose name match the pattern
  -a | --allarchives
        all archives (include zip files)
  <dir>
        <dir> : directory to search in
  <pattern>
        <pattern> : pattern to look for
```

Here are some example usages:

#### Example 1
Find all occurances of 'Exception.class' entries (files) inside all the (jar/war/ear) archives under the "./Middleware/weblogic_10.3.6" dir:

`$ jarfinder ./Middleware/weblogic_10.3.6 EJBException.class`

sample output:

```
looking for [EJBException.class] in dir [.]
./modules/javax.ejb_3.0.1.jar
 ==> javax/ejb/EJBException.class
 ==> javax/ejb/NoSuchEJBException.class
./wlserver_10.3/server/lib/jrmpclient.jar
 ==> javax/ejb/EJBException.class
 ==> javax/ejb/NoSuchEJBException.class
./wlserver_10.3/server/lib/webserviceclient+ssl.jar
 ==> javax/ejb/EJBException.class
./wlserver_10.3/server/lib/webserviceclient.jar
 ==> javax/ejb/EJBException.class
./wlserver_10.3/server/lib/wlclient.jar
 ==> javax/ejb/EJBException.class
 ==> javax/ejb/NoSuchEJBException.class
./wlserver_10.3/server/lib/wlthint3client.jar
 ==> javax/ejb/EJBException.class
 ==> javax/ejb/NoSuchEJBException.class
found 10 entries in 876 processed archives, scanned 584 directories
in 917 millis
```
#### Example 2
A more complex example:

find all files matching "\*Hierarchical\*gif" (ignoring case) in archives whose name matches "\*ui\*jar" under the ~/Dev/Eclipse dir

`$ jarfinder -i --include *ui*jar ~/Dev/Eclipse *Hierarchical*gif`

sample output:

```
looking for [*Hierarchical*gif] in dir [/Users/ajo/Dev/Eclipse] including archives matching .*ui.*jar
./plugins/org.eclipse.debug.ui_3.7.102.v20111129-1423_r372.jar
 ==> icons/full/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.jdt.ui_3.7.2.v20120109-1427.jar
 ==> icons/full/dlcl16/hierarchicalLayout.gif
 ==> icons/full/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.pde.ui_3.6.100.v20120103_r372.jar
 ==> icons/dlcl16/hierarchicalLayout.gif
 ==> icons/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.team.ui_3.6.101.R37x_v20111109-0800.jar
 ==> icons/full/dlcl16/hierarchicalLayout.gif
 ==> icons/full/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.ui.ide_3.7.0.v20110928-1505.jar
 ==> icons/full/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.ui.views.log_1.0.200.v20110404.jar
 ==> icons/obj16/hierarchical.gif
./plugins/org.eclipse.update.ui_3.2.300.v20100512.jar
 ==> icons/dlcl16/hierarchicalLayout.gif
 ==> icons/elcl16/hierarchicalLayout.gif
./plugins/org.eclipse.wst.jsdt.ui_1.1.102.v201201131900.jar
 ==> icons/full/dlcl16/hierarchicalLayout.gif
 ==> icons/full/elcl16/hierarchicalLayout.gif
found 13 entries in 350 processed archives, scanned 1713 directories
in 5303 millis
```


## JarGrep

JaFinder is a nice tool to locate a given pattern (package, class, …) within a set of archives, yet sometimes it's helpful to search for text *within* archive entries (ex: when searching source code). Enter JarGrep.  

JarGrep lets you search for text within a given archive or recursively within archives located under a directory.

```
Usage: JarGrep [options] <pattern> <file>

  -i | --ignorecase
        ignore case
  -v | --verbose
        show informartion on each file/entry processed
  -r | --recurse
        recursively process the given directory
  --enc <encoding>
        use given encoding instead of platform's default. Ex. 'UTF-8' or 'ISO-8859-1'.
  --include <name pattern>
        include only archives whose name match the pattern
  --includeEntry <name pattern>
        process archive entries whose name match the pattern
  -a | --allarchives
        all archives (include zip files)
  <pattern>
        <pattern> : pattern to look for
  <file>
        <file> : archive file to grep or directory to recurse into
```

> Note: text search is done in text files only, not binary files

#### Example 1

Search for all occurances of 'WLSTTask', recursively in all archives under the modules/ directory
> Note: This may take some time depending on the number of archives and the number of text entries within the archive

`$ jargrep -r WLSTTask ./modules/`

output:

```
looking for [WLSTTask] in dir [./modules]
./modules/org.apache.ant.patch_1.2.0.0_1-7-1.jar
  org/apache/tools/ant/taskdefs/wls_tasks.properties
      line 25 : wlst=weblogic.ant.taskdefs.management.WLSTTask
./modules/org.apache.ant_1.7.1/lib/ant.jar
  org/apache/tools/ant/taskdefs/defaults.properties
      line 255 : wlst=weblogic.ant.taskdefs.management.WLSTTask
found 2 matches in 2 entries and 2 archives, processed 96548 entries and 576 archives
in 7854 millis
```

#### Example 2
Search for all occurances of 'PreferencesDialog' in java files, recursively in all archives matching '\*source\*jar' under the eclipse/ directory using the ISO-8859-1 encoding for reading text files
> Note: forcing the encoding is sometimes necessary to prevent exceptions. If you experience a `java.nio.charset.MalformedInputException: Input length = 1` exception, try forcing the encoding as in the example below

`$ jargrep -r --enc ISO-8859-1 --include *source*jar --includeEntry *.java "PreferencesDialog" eclipse/`

output:

```
looking for [PreferencesDialog] in dir [./eclipse] including archives matching .*source.*jar including entries matching .*\.java
./eclipse/plugins/org.eclipse.ui.ide.source_3.7.0.v20110928-1505.jar
  org/eclipse/ui/views/markers/internal/MarkerView.java
      line 886 : 			 * @see org.eclipse.ui.preferences.ViewPreferencesAction#openViewPreferencesDialog()
      line 888 : 			public void openViewPreferencesDialog() {
      line 889 : 				openPreferencesDialog(getMarkerEnablementPreferenceName(),
      line 903 : 	private void openPreferencesDialog(String markerEnablementPreferenceName,
./eclipse/plugins/org.eclipse.ui.workbench.source_3.7.1.v20120104-1859.jar
  org/eclipse/ui/internal/progress/ProgressView.java
      line 120 : 			 * @see org.eclipse.ui.internal.preferences.ViewPreferencesAction#openViewPreferencesDialog()
      line 122 : 			public void openViewPreferencesDialog() {
  org/eclipse/ui/preferences/ViewPreferencesAction.java
      line 35 : 		openViewPreferencesDialog();
      line 41 : 	public abstract void openViewPreferencesDialog();
found 8 matches in 3 entries and 2 archives, processed 21928 entries and 191 archives
in 6249 millis
```

## Installing

For convenience, I have packaged the tools and all its dependencies (including scala runtime) in a single jar file (using [sbt-assembly](https://github.com/sbt/sbt-assembly)).

So, all you need is a JVM 1.6+.

1. Download the [ozb-jartools-dist-0.2.jar](https://docs.google.com/file/d/0Bxq9-8NxBE3WNzRKWnZYWF9hams/edit?usp=sharing)
2. Run JarFinder  
	`java -cp path/to/ozb-jartools.jar org.ozb.utils.jartools.JarFinder`
3. Run JarGrep
	`java -cp path/to/ozb-jartools.jar org.ozb.utils.jartools.JarGrep`

> You may want to create an (bash) alias in your `.profile` / `.bashrc`, ex:
>  `alias jarfinder='java -cp path/to/ozb-jartools.jar org.ozb.utils.jartools.JarFinder'`

## Building

The jartools are written in Scala and use [sbt](http://www.scala-sbt.org/) (0.12.3) to build.  
I have compiled it with scala 2.9.3 and Java 1.6.


### Dependencies

As defined in [build.sbt](build.sbt), the code depends on

	"com.github.scopt" % "scopt_2.9.2" % "2.1.0" withSources(),
	"org.ozb" %% "ozb-scala-utils" % "0.1" intransitive() withSources()

- [scopt](https://github.com/scopt/scopt) is a library to parse command-line arguments
- [ozb-scala-utils](https://github.com/ozeebee/ozb-scala-utils) is a library containing various scala utilities

## License
This code is open source software licensed under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

## Motivations
Well, when coding I often need to find classes or source code excerpts within source jars (like in the Eclipse source code for instance) and I missed such a tool that let me quickly and easily find source code files matching what I'm searching.  
Hence JarFinder and JarGrep.  
Hope it can be useful to someone else !


