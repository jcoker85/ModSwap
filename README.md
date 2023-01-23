# ModSwap

Swaps between modded and unmodded versions of game based on file presence and MD5 hash. 

1) Generates MD5 hash of all files in modded installation directory and unmodded installation directory
2) Copies mod files to backup folder that aren't present in unmodded directory
3) Copies mod files and original files to backup folder that are both present but have different hashes (original file will have suffix _orig)
4) Creates CSV file of unmodded installation directory files and hashes which allows you to delete unmodded directory

## Examples
```
java -jar ModSwap.jar J: Test2 Backup Test1
```
First run, this copies modded files from J:\Test2 to J:\Backup, copies original files from J:\Test1 to J:\Backup, 
and creates CSV file in J:\Backup\origFiles.csv

```
java -jar ModSwap.jar J: Test2 Backup 
```

Subsequent runs, after deleting original files directory, you can drop the unmodded installation directory
argument since it no longer exists. 

NOTE: This program does not copy files from Backup directory back into unmodded directory, so you'll need to do that manually. 