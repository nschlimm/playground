#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Programmer notes:
#
# See this forum for a discussion of this file: http://www.unix.com/shell-programming-scripting/46785-sh-file-syntax-checking.html
#
# Possible future enhancements:
#	--make this script file POSIX compatible (currently it is NOT due to the special find command options used below)
#	--there are many more options for the find command (e.g. how symbolic links are handled--currently they are not followed)
#	which might want to expose as command line arguments of this script.
#		--on the other hand, offering these options will make it even harder to be POSIX compatible...
#--------------------------------------------------


#----------environment variables


# Initialize option variables to preclude inheriting values from the environment:
opt_h="false"	# default is do not print help
opt_p="./"	# default is the current working directory
opt_R="-maxdepth 1"	# default limits the search to just path itself (i.e. do not drill down into subdirectories)


#----------functions


printHelp() {
	echo "Usage: sh checkSyntax.sh [-h] [-p path] [-R]"
	echo
	echo "DESCRIPTION"
	echo "Checks the syntax of bourne (or compatible) shell script files." \
		" The target may be either a single shell script file," \
		" or every shell script file in a directory (this is the default behavior, with the current directory the target)," \
		" or every shell script file in an entire directory tree." \
		" A shell script file is considered to be any file with the extension .sh REGARDLESS OF ITS CONTENTS." \
		" If the user has used this extension for other file types, then the syntax check may fail." \
		" The syntax checking will be done by the shell that is executing this file (e.g. bash on a typical Linux system)," \
		" so all the .sh files in the search path must be syntax compatible with this shell." \
		" A good description of differences between bash and bourne shells, for instance is found here: http://www.faqs.org/docs/bashman/bashref_122.html."
	echo
	echo "OPTIONS"
	echo "-h prints help and then exits; no value should be specified; all other options are ignored"
	echo
	echo "-p if supplied, then requires a value that is either the path to a single .sh file or the path to a directory;" \
		" if omitted, then the current working directory will be searched"
	echo
	echo "-R if present and if the path to be searched is a directory, then searches subdirectories too; no value should be specified"
	echo
	echo "DEPENDENCIES"
	echo "This script assumes that a suitable version of the find command will be the first one found in the user's PATH." \
		" This find command must support the -maxdepth, -type, and -iname options." \
		" GNU find works, but other variants may not."
	echo
	echo "+++ BUGS"
	echo "--the code is broken if any element in a path being searched contains leading whitespace"
}


#----------main


# Parse command-line options:
while getopts 'hp:R' option
do
	case "$option" in
	"h")
		opt_h="true"
		;;
	"p")
		opt_p="$OPTARG"
		;;
	"R")
		opt_R=""
		;;
	?)
		# Note: if supply an invalid option, getopts should have already printed an error line by this point, so no need to duplicate it
		echo
		printHelp
		exit 1
		;;
	esac
done


# Print help and then exit if -h is a command-line option:
if [ $opt_h = "true" ]; then
	printHelp
	exit
fi


# Find all the .sh files and check them:
#for shFile in `find $opt_p $opt_R -type f -iname "*.sh"`	# to understand this line, execute "man find"; was inspired by this script: http://www.debianhelp.org/node/1167
#	Dropped the above line because it fails on path elements which contain whitespace; the solution below is discussed here: http://www.unix.com/shell-programming-scripting/27487-how-read-filenames-space-between.html
find $opt_p $opt_R -type f -iname "*.sh" | while read shFile
do
	( sh -n "$shFile" )	# sh -n will merely syntax check (never execute) shFile, and will print to stdout any errors found; encase in parentheses to execute in a subshell so that if a syntax error is found, which causes sh -n to have a non-zero exit code, then this parent shell does not see it; critical since it will stop executing (due to the set -e line at the top of the file) otherwise
done
