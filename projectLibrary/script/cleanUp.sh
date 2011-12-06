#!/bin/sh
set -e	# exit on first failed command
set -u	# exit if encounter never set variable


#--------------------------------------------------
# Deletes any of the helper files created by some of the other sh files.
#--------------------------------------------------


if [ -e ./javaClassFiles.txt ]; then
	rm  ./javaClassFiles.txt
fi


if [ -e ./javaPackages.txt ]; then
	rm  ./javaPackages.txt
fi


if [ -e ./javaSourceFiles.txt ]; then
	rm  ./javaSourceFiles.txt
fi
