--------------------------------------------------
Contact info
--------------------------------------------------


You can contact me, Brent Boyer, the author of this project at
	ellipticgroupinc@gmail.com

For now, the latest version of this code should be available somewhere under the
	ellipticgroup.com
domain name, such as
	http://www.ellipticgroup.com/


--------------------------------------------------
Copyright and licensing
--------------------------------------------------


UNLESS NOTED OTHERWISE, EVERY FILE IN THIS PROJECT IS
	Copyright © 2008 Brent Boyer.  All rights reserved.

One big exception is all of the source code (.java files) in the
	.../src
directory, which are all licensed under the terms of the LGPLv3 or later (every source file should have full details at the top).

[+++ am currently retaining all rights to the non .java files simply because I cannot figure out what license to release them under; see the file
	.../license/resources.txt
for details.  Fix this in the future...]


--------------------------------------------------
Background
--------------------------------------------------


I began programming Java in 1999, and immediately started this library to hold all of the code that I thought might be useful in other personal projects of mine.  This library strictly grew organically: code was added when it was needed for some other project.

So, this library was never systematically planned.  There are enormous gaps in its coverage.

Furthermore, because code was continually added as I matured in my Java skills--and has not necessarily been refactored as I got better due to lack of time--the code quality in this library varies highly.  Some of it I am proud of, other parts are embarassing.

Similarly, unit testing was added over time.  I had a really primitive way of doing it until adopting JUnit 4 recently.  So, the test coverage is not as complete as I would like it to be, again due to lack of time.

Finally, parts of this library are undoubtably obsoleted by better equivalent functionality that has now become available in other Java libraries.  For example, bb.util.BidirectionalMap probably has zero advantages over the versions that you can get in the Apache
	http://commons.apache.org/collections/
or Google
	http://code.google.com/p/google-collections/
Collection libraries).


--------------------------------------------------
Build process
--------------------------------------------------


Rather than assume that you are using Ant, Maven, or some IDE, this project comes with its own simple command line build process that should be ready to run immediately after you unpack this project.  See the contents of the
	.../script
directory.

This build process is mostly based off of shell script files (both DOS .bat files, or Unix .sh files).  Those files define environmental variables, make a few calls to a helper Java program (stored in the
	bt.jar
file), and ultimately make calls to Sun's command line Java tools.  So, for these script files to work, you need to have a modern JDK installed (a JRE alone will not suffice) and its
	<jdkInstallDirectory>/bin
directory needs to be in your path.  JDK 1.6 will certainly work; JDK 1.5 may possibly work, and earlier versions are hopeless.

The DOS .bat script files probably require Windows NT+ to work.  The unix.sh script files ought to be pure bourne shell files, but I think that I mixed in some bash functionality.  They have been tested and found working under modern versions of both cygwin and linux.  Beware that the .sh script files may fail under Solaris with its default (and embarassingly primitive) default shell, but you can fix this by installing a modern version of bash.

You should open whatever file you want to confirm its functionality before using it.  Here is a quick outline of the major functionality:
	jc.XXX	Java Compiling
	jd.XXX	JavaDoc generation
	jj.XXX	Java Jar creation (makes a .jar file of the contents of this project's class directory)
	jr.XXX	Java Run (executes one of this project's Java classes; currently mainly used to do JUnit tests)
You can use clever shell functionality, such as compile and run on one command line, using lines like
	dos: jc && jr
	unix: sh jc.sh && sh jr.sh
Combine this with using the vertical arrow key to cycle thru shell history, and you have an extremely quick build tool!

If you do not like this build process, it should be easy to transform it into Ant, Maven, or whatever using the information in the script files.

I also included the IntelliJ project files that I use:
	bbLibrary.iml
	bbLibrary.ipr
	bbLibrary.iws
They likely will not work on your system as is; you will need to change things like the JDK location etc.


--------------------------------------------------
Conventions
--------------------------------------------------


All the text files in this project should be proper text files (that is, use unix \n line ends).  So, if you are a Windows user, you will need a decent text editor to read them (no, NotePad does not cut it).

Items that need future correction have a line starting with three plus symbols ("+++").  This is easy to type, really stands out, and is easy to search for.  Furthermore, in my source code files, whenever I have hack code, I break the normal indentation level and slam the hack code all the way over to the left margin as a glaring indicator that the code is temporary and needs fixing.


--------------------------------------------------
Philosophy and future evolution
--------------------------------------------------


I have always striven to make this library be the best quality that it can be, and I will certainly be evolving this library in the future.  MAINTAINING BACKWARDS COMPATIBILITY (with either previous versions of itself or with old JDKs) WILL ALWAYS LOSE OUT TO THIS QUALITY ETHIC.  I freely refactor--change names, add/drop functionality, use features from the latest JDK--with no regard to how it impacts previous users.  This being my personal code library, I will always revel in this freedom.  I have discovered many defects in my code over the years, know that there are issues with the current code, and surely will find many others.  Equally disturbing, unless someone starts paying me, I may not have time to document changes in future releases which break compatibility with earlier versions.  BEWARE OF THIS IF YOU USE THIS CODE IN YOUR PROJECT.


--------------------------------------------------
Known issues
--------------------------------------------------


---some of the unit tests will fail if execute them on a machine different from mine because they require specific resources that I could not make portable for various reasons
	--for example, bb.net.Emailer.UnitTest will likely fail unless you modify its fileProperties field to point to a path that actually exists on your machine


--------------------------------------------------
Todo
--------------------------------------------------


--documentation:


	+++ should writeup more about the build process, and possibly open source it and put it online too?  Maybe others might like it...
		+++ should I replace the operating system scripts with Java-based Beanshell
			http://www.beanshell.org/
		This would allow one set of files to work ob EVERY operating system, a huge advantage.


	+++ use something like http://www.jgrasp.org/ to generate UML diagrams?


--give this functionality to others:


	+++ should I put this on source forge or something?
		--if ever do place it online, make sure to add it to a code search engine; see
			http://developers.slashdot.org/developers/07/02/05/1313253.shtml


	+++ a lot of the code in this library was written to fix defects in Sun's standard Java libraries.  Could I ever get the code added to those libraries?  For example, consider bb.io.FileUtil: the read/write methods in there should actually be built into File.


--renamings:


	+++ the bb lib packages should be made to be analogous to the jdk ones?
		--example: there should be a bb.lang package, and Math2 should be placed in it because Math is in java.lang
			--similarly for StringUtil?
		--but HashUtil would remain in the bb.util package


	+++ should I rename many of the *Util classes to *s? 
		--example: StringUtil --> Strings


--specific package issues:


	+++ some classes in the bb.gui package need to be dropped or totally rewritten
		--use css for java components: http://weblogs.java.net/blog/enicholas/archive/2008/07/introducing_jav.html
		--use JavaFX?
			http://weblogs.java.net/blog/aim/archive/2009/06/insiders_guide_1.html


	+++ use a better framework in the future to automatically unit test the GUI classes:
		http://www.uispec4j.org/
			http://today.java.net/pub/a/today/2007/05/17/uispec4j-java-gui-testing-made-simple.html
		http://abbot.sourceforge.net/doc/Tutorial-1.shtml
		http://c2.com/cgi/wiki?GuiUnitTesting
	(currently, they must be tested manually...)


	+++ keep an eye on JSR 203 New I/O in JDKTM 7
		http://java.sun.com/developer/technicalArticles/javase/nio/
		http://developers.sun.com/learning/javaoneonline/2008/pdf/TS-5686.pdf
		http://www.artima.com/lejava/articles/more_new_io.html


	+++ in the bb.io.filefilter package, should I add a new
		HiddenMode
	class analogous to the other XxxMode classes, that specifies how hidden Files should be handled?
	Would need to add support for it to BaseFilter and all subclasses...
		--see my current VisibleFilter class
	
	
	+++ the bb.jdbc package is empty and worthless at this point
		--probably should remove it and use something like
			http://commons.apache.org/dbutils/
	
	
	+++ should I change how the bb.util.logging package behaves?
		--I frequently create one Logger2 per class, in order to classify events better than if everything were in one massive log file
		--maybe a better way to do things would be to have a single Logger2 but rely on the class and method info supplied to the logp calls (which should be the only method used) and have Logger2 properly route the event to the appropriate file or whatever
			--this might
				1) allow me to eliminate all those custom log file properties?
				2) get better performance
					--that single Logger2 could initially just accept log events and put them on a BlockingQueue and could then have one or more dedicated internal threads that actually do the event logging
						--see Java Concurrency in Practice p. 152 and 243
						--but see also the warnings in footnote 16, especially about guarantees that the message is actually logged...
		--alternatively, could add a LogUtil.logp(Level level, String sourceClass, String sourceMethod, String msg, Throwable thrown) method
			--this method would use the sourceClass param as a key to a HashMap<String, Logger2> to retrieve the appropriate Logger2
				--this method would lazy initialize that Logger2 if need be
		--here is a great blog on logging performance issues:
			http://jeremymanson.blogspot.com/2009/04/faster-logging-with-faster-logger.html
