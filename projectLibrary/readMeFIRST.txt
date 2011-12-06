--------------------------------------------------
Quick answers to some likely common questions
--------------------------------------------------


Let me guess: you are probably primarily interested in either
	a) the source code for the Benchmark class
	b) the source code for the Bootstrap class
	c) simply using a jar file which contains the relevant class files so that you can do your own benchmarking

See:
	a) .../projectLibrary/src/bb/util/Benchmark.java
	b) .../projectLibrary/src/bb/science/Bootstrap.java
	c) .../projectLibrary/lib/bb.jar
		BEWARE: you may need to include a reference to some/all of the other jar files in the lib directory too
		(especially jsci-core.jar and mt.jar)


--------------------------------------------------
Background
--------------------------------------------------


If you are reading this, then you are likely using this project after having read the "Robust Java benchmarking" series of articles:
	Part 1: Issues http://www.ibm.com/developerworks/java/library/j-benchmark1.html
	Part 2: Statistics and solutions http://www.ibm.com/developerworks/library/j-benchmark2/index.html
You found the companion website for the article
	http://www.ellipticgroup.com/html/benchmarkingArticle.html
and have downloaded the projectLibrary.zip file
	http://www.ellipticgroup.com/misc/bbLibrary.zip
and unzipped it and are now reading this document.


--------------------------------------------------
Comments on the current version of the framework
--------------------------------------------------


This project is a slightly specialized version of my entire personal code library.  Every file here, except the one that you are reading now, is found in said library.  The only changes are that I included copies of all the third party jar files that my library depends on in this project's lib directory.  Plus I modified the globalEnvVars.XXX files to point to those jar files.  (In my actual code library, I reference jar files stored in a central location on my computer so that they may be shared among multiple projects.  Obviously, I could not assume that your machine is identically configured.)

I apologize in forcing you to download an entire library containing stuff that you may not be interested in.  My only excuse is time pressure: I had a ton of work to do in finishing up this article, and customizing this library has been squeezed out.  (My Benchmark and Bootstrap classes, which ARE essential, depend on several other classes in the library; those in turn depend on others, and it ended up being a exponentially increasing nightmare to try and pull out just the necessary classes.  I could drop a lot of pretty-but-unessential functionality from Benchmark and Bootstrap to make this task easy, such as the way console output is handled, but I do not like the result of that either.)

Maybe in the future I will find the time to trim this down...
