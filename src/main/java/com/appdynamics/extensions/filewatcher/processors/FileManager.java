/*
 *  Copyright 2020. AppDynamics LLC and its affiliates.
 *  All Rights Reserved.
 *  This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *  The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.filewatcher.processors;

/*
 * @author Aditya Jagtiani
 */

import com.appdynamics.extensions.filewatcher.config.FileMetric;
import com.appdynamics.extensions.filewatcher.config.PathToProcess;
import com.appdynamics.extensions.filewatcher.helpers.GlobPathMatcher;
import com.appdynamics.extensions.filewatcher.util.FileWatcherUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileManager.class);

    private WatchService watchService;
    private Map<WatchKey, Path> watchKeys;
    private String baseDirectory;
    private PathToProcess pathToProcess;
    private FileMetricsProcessor fileMetricsProcessor;
    private Map<String, FileMetric> fileMetrics;

    public FileManager(WatchService watchService, Map<WatchKey, Path> watchKeys, String baseDirectory,
                       PathToProcess pathToProcess, FileMetricsProcessor fileMetricsProcessor) {
        this.watchService = watchService;
        this.watchKeys = watchKeys;
        this.baseDirectory = baseDirectory;
        this.pathToProcess = pathToProcess;
        this.fileMetricsProcessor = fileMetricsProcessor;
        this.fileMetrics = new HashMap<>();
    }

    public void run() {
        LOGGER.info("Attempting to walk directory {}", baseDirectory);
        try {
            walk(baseDirectory);
            fileMetricsProcessor.printMetrics(fileMetrics);
            watch();
        } catch (InterruptedException | IOException ex) {
            LOGGER.error("Error encountered while walking {}", baseDirectory, ex);
        }
    }

    //#TODO walk method can be made as a utility method
    private void walk(String baseDirectory) throws IOException {
        GlobPathMatcher globPathMatcher = (GlobPathMatcher) FileWatcherUtil.getPathMatcher(pathToProcess);
        Files.walkFileTree(Paths.get(baseDirectory), new CustomFileWalker(baseDirectory, globPathMatcher, pathToProcess,
                fileMetrics));
    }

    private void watch() throws IOException, InterruptedException {
        LOGGER.info("Watching path {} for events", baseDirectory);
        registerPath(Paths.get(baseDirectory));
        FileWatcher fileWatcher = new FileWatcher(watchService, watchKeys, baseDirectory,
                fileMetrics, pathToProcess, fileMetricsProcessor);
        while(true) {
            if(!watchKeys.isEmpty()) {
                fileWatcher.processWatchEvents();
            }
            else {
                break;
            }
        }

    }

    private void registerPath(Path path) throws IOException {
        if (!watchKeys.containsValue(path)) {
            LOGGER.debug("Now registering path {} with the Watch Service", path.getFileName());
            WatchKey key = path.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watchKeys.put(key, path);
        }
    }
}