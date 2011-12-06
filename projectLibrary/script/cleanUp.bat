::--------------------------------------------------
:: Deletes any of the helper files created by some of the other bat files.
::--------------------------------------------------


if not exist javaClassFiles.txt goto continue1
del  /q  javaClassFiles.txt
:continue1


if not exist javaPackages.txt goto continue2
del  /q  javaPackages.txt
:continue2


if not exist javaSourceFiles.txt goto continue3
del  /q  javaSourceFiles.txt
:continue3
