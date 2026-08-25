package com.routebox.util;

import com.routebox.common.constants.ConfConstants;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @Description File Util
 * @Date 2025/5/9
**/
@Slf4j
public class FileUtil {

    /**
     * @Description save batch log bytes to file
     * @Date 2025/5/9
     **/
    public static void saveBytesFile(byte[] bytes, String filename) {
        Path savePath = Paths.get(ConfConstants.zipPath() + filename);

        try {
            Files.createDirectories(savePath.getParent());
            Files.write(savePath, bytes);

            log.info("File save success: {}", savePath);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    /**
     * @Description decompress zip file
     * @Date 2025/5/9
     **/
    public static void unzip(String zipFilename) {
        Path zipFilePath = Paths.get(ConfConstants.zipPath() + zipFilename);

        if (!Files.exists(zipFilePath)) {
            log.error("File not exist: {}", zipFilePath);
            return;
        }

        Path unzipFilePath = Paths.get(ConfConstants.unzipPath() + zipFilename.substring(0, zipFilename.lastIndexOf(".")));
        try {
            Files.createDirectories(unzipFilePath.getParent());
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {
                ZipEntry zipEntry;

                while ((zipEntry = zis.getNextEntry()) != null) {
                    Path newPath = resolveZipEntry(unzipFilePath, zipEntry);
                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(newPath);
                    } else {
                        Files.createDirectories(newPath.getParent());
                        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(newPath.toFile()))) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = zis.read(buffer)) > 0) {
                                bos.write(buffer, 0, len);
                            }
                        }
                    }

                    zis.closeEntry();
                }
            }

            log.info("File unzip success: {}", unzipFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @Description resolve zip entry path to unzip file path
     * @Date 2025/5/9
     **/
    private static Path resolveZipEntry(Path rootDir, ZipEntry entry) {
        Path targetDirResolved = rootDir.resolve(entry.getName());

        if (!targetDirResolved.normalize().startsWith(rootDir.normalize()))
            throw new IllegalArgumentException("ZIP entry with invalid relative path: " + entry.getName());

        return targetDirResolved;
    }

    /**
     * @Description Delete directory and its contents
     * @Date 2025/6/7
     **/
    public static void deleteDirectoryDepth(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public @Nonnull FileVisitResult visitFile(@Nonnull Path file, @Nonnull BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @Nonnull FileVisitResult postVisitDirectory(@Nonnull Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
