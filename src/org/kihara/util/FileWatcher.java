package org.kihara.util;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.*;

public class FileWatcher extends Thread {

    @FunctionalInterface
    public interface OnChangeCallback {
        void onChange(Path dir, List<WatchEvent<?>> events);
    }

    public static FileWatcher watch(OnChangeCallback callback, String... dirs) throws IOException {
        return watch(callback, Paths.get(dirs[0], Arrays.copyOfRange(dirs, 1, dirs.length)));
    }

    public static FileWatcher watch(OnChangeCallback callback, Path... dirs) throws IOException {
        FileWatcher thread = new FileWatcher(callback, dirs);
        thread.start();
        return thread;
    }

    private final OnChangeCallback callback;
    private final Path[] dirs;
    private final WatchService watchService;

    private Map<WatchKey, Path> dirsMapping = new HashMap<>();
    private boolean isRunning = true;

    public FileWatcher(OnChangeCallback callback, Path... dirs) throws IOException {
        this.callback = callback;
        this.dirs = dirs;
        watchService = FileSystems.getDefault().newWatchService();
    }

    @Override
    public void run() {
        try {
            register(dirs);
            while (isRunning) {
                WatchKey key = watchService.take();
                List<WatchEvent<?>> events = key.pollEvents();
                Path dir = dirsMapping.get(key);
                callback.onChange(dir, events);

                for (WatchEvent<?> event : events) {
                    if (event.kind() == ENTRY_CREATE) {
                        Path name = (Path) event.context();
                        Path child = dir.resolve(name);
                        if (Files.isDirectory(child)) {
                            registerTree(child);
                        }
                    }
                }

                if (!key.reset()) {
                    dirsMapping.remove(key);
                    if (dirsMapping.isEmpty())
                        break;
                }
            }
        } catch (IOException | InterruptedException ignored) {}
    }

    public void interrupt() {
        isRunning = false;
    }

    private void register(Path... dirs) throws IOException {
        for (Path dir : dirs) {
            registerTree(dir);
        }
    }

    private void registerTree(final Path child) throws IOException {
        Files.walkFileTree(child, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        dirsMapping.put(key, dir);
    }
}