package io.github.monkeyinspector.source;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;

/** Watch callbacks only schedule work; parsing and runtime synchronization belong to the source worker. */
public final class SourceWatcher implements AutoCloseable {
    private final WatchService watch;
    private final Path root;
    private final Set<Path> registered = new HashSet<>();
    private final Thread thread;
    public SourceWatcher(Path root, Runnable changed) throws IOException {
        this.root = root;
        watch = root.getFileSystem().newWatchService();
        registerDirectories();
        thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    WatchKey key = watch.take();
                    boolean dirty = false, created = false;
                    for (var event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) { dirty = true; continue; }
                        String name = event.context().toString();
                        dirty |= name.endsWith(".kt") || name.endsWith(".java");
                        created |= event.kind() == StandardWatchEventKinds.ENTRY_CREATE;
                    }
                    if (!key.reset()) registered.remove((Path)key.watchable());
                    if (created) { registerDirectories(); dirty = true; }
                    if (dirty) changed.run();
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            catch (ClosedWatchServiceException ignored) { }
            catch (IOException e) { changed.run(); }
        }, "monkey-inspector-watch");
        thread.setDaemon(true); thread.start();
    }
    private void registerDirectories() throws IOException {
        try (var paths = Files.walk(root)) {
            for (var path : paths.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (registered.add(path)) path.register(watch, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            }
        }
    }
    @Override public void close() throws IOException { watch.close(); thread.interrupt(); }
}
