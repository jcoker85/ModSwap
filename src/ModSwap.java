import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;

/*
  Backs up and removes mod files from game directory to revert it back to an un-modded state
 */
public class ModSwap {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("You must provide at least 3 arguments: the main path, the modded installation sub path, the preferred name of your backup directory, and the original installation sub path (required if you are running this for the first time).");
            return;
        }

        Map<String, String> origHashes = new HashMap<>();
        Map<String, String> modHashes = new HashMap<>();
        LinkedHashSet<Path> dirsToDelete = new LinkedHashSet<>();

        String mainPath = args[0];
        String modPath = args[1];
        String backupName = args[2];
        String origPath = generateHashesAndGetOriginalSubpath(mainPath, modPath, backupName, args, origHashes, modHashes);

        //compare files
        for (String modFilePath : modHashes.keySet()) {
            boolean md5MisMatch = false;
            boolean fileMatch = false;
            String storedOrigFilePath = "";
            String modShortPath = modFilePath.replace(mainPath + File.separator + modPath, "");
            for (String origFilePath : origHashes.keySet()) {
                String origShortPath = origFilePath.replace(mainPath + File.separator + origPath, "");
                if (modShortPath.equalsIgnoreCase(origShortPath)) {
                    //if we find a corresponding file without an MD5 match, flag
                    if (!modHashes.get(modFilePath).equalsIgnoreCase(origHashes.get(origFilePath))) {
                        storedOrigFilePath = origFilePath;
                        md5MisMatch = true;
                        //if we find a corresponding file with an MD5 match, we don't need to process the file
                    } else {
                        fileMatch = true;
                    }
                    //since corresponding or matching file found, we can skip the rest of the loop
                    break;
                }
            }
            //if there is no file match, we need copy files and delete empty directories
            if (!fileMatch) {
                dirsToDelete.add(copyFilesAndDeleteEmptyDirs(mainPath, backupName, origPath, modFilePath, modShortPath, storedOrigFilePath, md5MisMatch));
            }
        }
        //create output file for original installation details
        createOrigHashFile(mainPath, backupName, origPath, origHashes);
        //clean up empty directories
        for (Path path : dirsToDelete) {
            deleteDirectoryStream(path);
        }
        System.out.println("\nYou may now delete your original installation files");
    }

    private static String generateHashesAndGetOriginalSubpath(String mainPath, String modPath, String backupName, String[] args, Map<String, String> origHashes, Map<String, String> modHashes) throws InterruptedException {
        String origPath = "";
        //thread processing
        Phaser ph = new Phaser();
        int phase = ph.getPhase();
        ExecutorService es = Executors.newWorkStealingPool();
        ph.register();
        //generate MD5 hashes for all files in modded installation path
        es.execute(new MD5Hash(ph, es, new File(mainPath + File.separator + modPath), modHashes));

        //we can get the original file data from the backup file or scanning original game files
        try {
            File origFiles = new File(mainPath + File.separator + backupName + File.separator + "origFiles.csv");
            Scanner sc = new Scanner(origFiles);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] s = line.split(",");
                origPath = s[1];
                origHashes.put(s[0] + File.separator + s[1] + s[2], s[3]);
            }
            sc.close();
        //if file not found, generate MD5 hashes for all files in original installation path
        } catch (FileNotFoundException fe) {
            if (args.length == 4) {
                //if origFiles.csv not found, the unmodded installation directory must be provided
                origPath = args[3];
                ph.register();
                es.execute(new MD5Hash(ph, es, new File(mainPath + File.separator + origPath), origHashes));
            } else {
                System.out.println("You need to provide the unmodded installation directory or make sure origFiles.csv is in your backup folder.");
            }
        }

        //phaser logic prevents early shutdown when using recursive threads in Java 8
        ph.awaitAdvance(phase);
        es.shutdown();
        es.awaitTermination(10, TimeUnit.MINUTES);
        //return the original installation subpath
        return origPath;
    }

    private static Path copyFilesAndDeleteEmptyDirs (String mainPath, String backupName, String origPath, String modFilePath, String modShortPath, String storedOrigFilePath, boolean md5MisMatch) throws InterruptedException, IOException {
        File modSrcFile = new File(modFilePath.substring(0, modFilePath.lastIndexOf(File.separator)));
        File modSrcFileWithFileName = new File(modFilePath);
        File modDestFile = new File(mainPath + File.separator + backupName + File.separator + modShortPath.substring(0, modShortPath.lastIndexOf(File.separator)));
        File modDestFileWithFileName = new File(mainPath + File.separator + backupName + File.separator + modShortPath);
        modDestFile.mkdirs();
        //if there is an MD5 mismatch, we need to copy modded and original files to the backup directory
        if (md5MisMatch) {
            System.out.println("[INFO] MD5 mismatch for file " + modShortPath + ", so copying original and moving modded files to backup directory.");
            File origSrcFileWithFileName = new File(storedOrigFilePath);
            String origShortPath = storedOrigFilePath.replace(mainPath + File.separator + origPath, "");
            File origDestFileWIthFileName = new File(mainPath + File.separator + backupName + File.separator + origShortPath + "_orig");
            //copy original files
            try {
                Files.copy(origSrcFileWithFileName.toPath(), origDestFileWIthFileName.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (NoSuchFileException ne) {
                System.out.println("[WARNING] Unable to copy original file " + mainPath + File.separator + backupName + File.separator + origShortPath);
            }
            //it must rest
            sleep(50);
        //if there is not an MD5 mismatch, we only need to copy modded to the backup directory
        } else {
            System.out.println("[INFO] No corresponding file found for path " + modShortPath + ", so moving modded file to backup directory.");
        }
        //copy modded files
        Files.copy(modSrcFileWithFileName.toPath(), modDestFileWithFileName.toPath(), StandardCopyOption.REPLACE_EXISTING);
        //add empty directories to list to clean up later
        Files.delete(modSrcFileWithFileName.toPath());
        //it must rest
        sleep(50);
        return modSrcFile.toPath();
    }

    private static void createOrigHashFile(String mainPath, String backupName, String origPath, Map<String, String> origHashes) throws IOException {
        File backupDir = new File(mainPath + File.separator + backupName);
        backupDir.mkdirs();
        BufferedWriter writer = new BufferedWriter(new FileWriter(mainPath + File.separator + backupName + File.separator + "origFiles.csv"));
        for (String origFilePath : origHashes.keySet()) {
            writer.write(mainPath + "," + origPath + "," + origFilePath.replace(mainPath + File.separator + origPath, "") + "," + origHashes.get(origFilePath) + "\n");
        }
        writer.close();
    }

    /*
      Stack overflow specialty: https://stackoverflow.com/questions/26017545/delete-all-empty-folders-in-java
     */
    private static void deleteDirectoryStream(Path path) throws IOException {
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(File::isDirectory)
                    .forEach(File::delete);
        } catch (NoSuchFileException nsfe) {
            System.out.println("[INFO] " + path + " has already been deleted!");
        }
    }
}
