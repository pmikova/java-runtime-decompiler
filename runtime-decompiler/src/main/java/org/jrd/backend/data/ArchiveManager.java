package org.jrd.backend.data;

import org.jrd.backend.communication.FsAgent;
import org.jrd.backend.core.OutputController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ArchiveManager {

    private static class ArchiveManagerHolder {
        private static final ArchiveManager INSTANCE = new ArchiveManager();
    }

    public static ArchiveManager getInstance() {
        return ArchiveManagerHolder.INSTANCE;
    }

    final String tmpdir = System.getProperty("java.io.tmpdir");
    final String fileSeparator = System.getProperty("file.separator");
    ArchivePathManager pathManager = new ArchivePathManager();
    final String jrdFolder = tmpdir + fileSeparator + "jrd-" + UUID.randomUUID().toString() + fileSeparator;
    int currentD = 0;

    /**
     * Finds out whether desired class is contained in <code>c</code>
     *
     * @param clazz Class to search
     * @return Whether class is in this file
     * @throws IOException Error while reading streams
     */
    public boolean isClassInFile(String clazz, File c) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(c))) {
            delete();
            pathManager = new ArchivePathManager();
            pathManager.setClazz(clazz);
            pathManager.addPathPart(c.getName());
            return findClazz(zis, clazz);
        }
    }

    /**
     * Recursive search through nested jars
     *
     * @param zis   ZipInputStream of current jar
     * @param clazz Class to search
     * @return Whether class is in this file
     * @throws IOException Error while reading streams
     */
    private boolean findClazz(ZipInputStream zis, String clazz) throws IOException {
        ZipEntry entry = null;
        while ((entry = zis.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                if (shouldOpen(entry.getName())) {
                    pathManager.addPathPart(entry.getName());
                    if(findClazz(new ZipInputStream(zis), clazz)) {
                        return true;
                    }
                    pathManager.removePathPart(entry.getName());
                } else {
                    String clazzInJar = FsAgent.toClass(entry.getName());
                    if (clazzInJar.equals(clazz)) {
                        pathManager.setFound();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines whether this file can be opened with ZipInputStream
     * @param n Name of the file
     * @return Whether file can be opened with ZipInputStream
     */
    public static boolean shouldOpen(String n) throws IOException {
        /* This way has been selected as there's no other "easier" way of determining if it is an archive.
           We initially tried to use streams - open a stream over ZipEntry, but because of the way streams work this method is not possible as it will
           edit the original entry and there's no way of returning. This caused some branches to be skipped while searching.
           Also closing stream derived from another stream, will close all streams that are connected, even the parent stream. This was a concern as there might be a lot of
           streams opened and none of them could be closed until they are all fully searched.
         * Option to add custom extensions will be added */
        return ArchiveManagerOptions.getInstance().isInner(n);
    }

    /**
     * Returns whether extraction is necessary
     *
     * @return If extraction is necessary
     */
    public boolean needExtract() {
        return !(pathManager.getPathSize() == 1);
    }

    /**
     * Unpacks files necessary to access desired class
     *
     * @return .jar containing desired class
     * @throws IOException Error while reading streams
     */
    public File unpack(File c) throws IOException {
        /*if (pathManager.isExtracted()) {
            // If file is already extracted, return the extracted one
            return new File(jrdFolder + (pathManager.getPathSize() - 2) + fileSeparator + (pathManager.get(pathManager.getPathSize() - 1)));
        }*/

        File f = new File(jrdFolder);
        if (f.exists() && !delete()) { // do not log if it didn't even exist before
            OutputController.getLogger().log(OutputController.Level.MESSAGE_ALL, "Could not delete jrd temp directory at '" + f.getAbsolutePath() + "'!");
        }

        // Create my dir in tmpdir
        if (!f.mkdir() && !f.exists()) {
            throw new IOException("Could not create directory at '" + f.getAbsolutePath() + "'");
        }

        File ret = recursiveUnpack(c);
        if (ret != null) {
            pathManager.setExtracted();
        }
        return ret;

    }

    /**
     * Recursively unpacks all required archives
     * @param toUnpack Archive to be unpacked
     * @return File pointer to last archive
     * @throws IOException if an I/O error occurs during unpacking
     */
    private File recursiveUnpack(File toUnpack) throws IOException {
        File destDir = new File(jrdFolder + currentD);
        if (!destDir.mkdir() && !destDir.exists()) {
            throw new IOException("Could not create directory '" + destDir.getAbsolutePath() + "'");
        }

        byte[] buffer = new byte[1024];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(toUnpack))) {
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    FileOutputStream outputStream = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, len);
                    }
                    outputStream.close();
                }
            }
        }

        currentD++;
        if (currentD == pathManager.getPathSize() - 1) {
            return new File(destDir.getAbsolutePath() + fileSeparator + pathManager.get(currentD));
        } else if (currentD == pathManager.getPathSize()) {
            throw new IOException("Unknown exception");
        }
        return recursiveUnpack(new File(destDir.getAbsolutePath() + fileSeparator + pathManager.get(currentD)));
    }

    /**
     * ZipSlip guard
     * @param destinationDir Destination directory
     * @param zipEntry Zip entry
     * @return File object pointing to "destinationDir/zipEntry"
     * @throws IOException cannot happen
     */
    public File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    /**
     * Packs unpacked files
     */
    public void pack(File c) throws IOException {
        // Go from end to start
        int i = pathManager.getPathSize();
        i -= 2;
        for (; i >= 0; i--) {
            // Create new zip that will contain edited files
            String[] tmp = pathManager.get(i).split(Pattern.quote(fileSeparator));
            String path = jrdFolder + tmp[tmp.length - 1] + fileSeparator;
            FileOutputStream fileStream = new FileOutputStream(path);
            ZipOutputStream zOut = new ZipOutputStream(fileStream);
            File f2zip = new File(jrdFolder + (i));
            File[] children = f2zip.listFiles();

            if (children != null) {
                for (File f : children) {
                    recursiveZip(f, f.getName(), zOut);
                }
            }

            zOut.finish();
            zOut.close();
            fileStream.close();
            // Move it into the temp file if it's not last, so it can be packaged
            if (i > 0) {
                Files.copy(Path.of(path), Path.of(jrdFolder + (i - 1) + fileSeparator + pathManager.get(i)), REPLACE_EXISTING);
            } else {
                // It's the last, replace the original
                Files.copy(Path.of(path), c.toPath(), REPLACE_EXISTING);
            }
            // Delete once it was moved
            Directories.deleteWithException(path);
        }
    }

    /**
     * Recursively adds file or files inside folder to archive
     * @param f2zip File/Folder to be archived
     * @param fName Name of the file
     * @param zOut Zip output stream used to output zipped bytes
     * @throws IOException if an I/O error occurs when writing to the output stream
     */
    public void recursiveZip(File f2zip, String fName, ZipOutputStream zOut) throws IOException {
        if (f2zip.isDirectory()) {
            if (!fName.endsWith(fileSeparator)) {
                fName += "/";
            }
            zOut.putNextEntry(new ZipEntry(fName));
            zOut.closeEntry();

            File[] children = f2zip.listFiles();

            if (children != null) {
                for (File child : children) {
                    recursiveZip(child, fName + child.getName(), zOut);
                }
            }

            return;
        }

        try (FileInputStream fis = new FileInputStream(f2zip)) {
            ZipEntry zEntry = new ZipEntry(fName);
            zOut.putNextEntry(zEntry);
            byte[] bytes = new byte[1024];
            int length;

            while ((length = fis.read(bytes)) >= 0) {
                zOut.write(bytes, 0, length);
            }

            zOut.closeEntry();
        }
    }

    /**
     * Deletes jrd temporary folder
     *
     * @return whether folder was successfully deleted
     */
    public boolean delete() {
        currentD = 0;
        return deleteRecursive(new File(jrdFolder));
    }

    /**
     * Deletes all nested files and directories, so the root can be deleted
     * @param f Directory to be deleted
     * @return Whether directory was deleted
     */
    private boolean deleteRecursive(File f) {
        File[] content = f.listFiles();

        if (content != null) {
            for (File file: content) {
                deleteRecursive(file);
            }
        }

        boolean wasDeleted = f.delete();
        if (!wasDeleted) {
            OutputController.getLogger().log(OutputController.Level.MESSAGE_DEBUG, "Could not delete file '" + f.getAbsolutePath() + "'.");
        }

        return wasDeleted;
    }

    private static class ArchivePathManager {
        private String clazz = "";
        private boolean found = false;
        private boolean extracted = false;
        private ArrayList<String> currentPath = new ArrayList<>();

        public boolean wasFound() {
            return found;
        }

        public boolean isExtracted() {
            return extracted;
        }

        public void setExtracted() {
            extracted = true;
        }

        public void setFound() {
            found = true;
        }

        public void addPathPart(String str) {
            currentPath.add(str);
        }

        public int getPathSize() {
            return currentPath.size();
        }

        public String get(int i) {
            return currentPath.get(i);
        }

        public void removePathPart(String str) {
            currentPath.remove(str);
        }

        public String getCurrentClazz() {
            return clazz;
        }

        public void setClazz(String str) {
            clazz = str;
        }
    }
}