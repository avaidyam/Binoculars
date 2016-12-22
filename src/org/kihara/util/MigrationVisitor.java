package org.kihara.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * The MigrationVisitor allows traversing the entire directory and completely
 * copying or moving it over using Java NIO (similar to how `mv` works).
 */
public class MigrationVisitor extends SimpleFileVisitor<Path> {

    private final Path fromPath;
    private final Path toPath;
    private final CopyOption copyOption[];
    private final boolean move;

    /**
     *
     *
     * @param origin
     * @param destination
     * @param move
     * @param copyOption
     * @throws IOException
     */
    public static void migrate(Path origin, Path destination, boolean move, CopyOption... copyOption) throws IOException {
        Files.walkFileTree(origin, new MigrationVisitor(origin, destination, move, copyOption));
        if (move) Files.deleteIfExists(origin);
    }

    /**
     *
     *
     * @param origin
     * @param destination
     * @param move
     * @param copyOption
     */
    public MigrationVisitor(Path origin, Path destination, boolean move, CopyOption... copyOption) {
        this.fromPath = origin;
        this.toPath = destination;
        this.copyOption = copyOption;
        this.move = move;
    }

    /**
     *
     */
    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        Path targetPath = toPath.resolve(fromPath.relativize(dir));
        if(!Files.exists(targetPath))
            Files.createDirectory(targetPath);
        return FileVisitResult.CONTINUE;
    }

    /**
     *
     */
    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Path to = toPath.resolve(fromPath.relativize(file));
        if (this.move)
            Files.move(file, to, copyOption);
        else Files.copy(file, to, copyOption);
        return FileVisitResult.CONTINUE;
    }
}
