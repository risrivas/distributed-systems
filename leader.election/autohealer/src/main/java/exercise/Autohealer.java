package exercise;/*
 *  MIT License
 *
 *  Copyright (c) 2019 Michael Pogrebinsky - Distributed Systems & Cloud Computing with Java
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Autohealer implements Watcher {

    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final int SESSION_TIMEOUT = 30000;

    // Parent Znode where each worker stores an ephemeral child to indicate it is alive
    private static final String AUTOHEALER_ZNODES_PATH = "/workers";

    // Path to the worker jar
    private final String pathToProgram;

    // The number of worker instances we need to maintain at all times
    private final int numberOfWorkers;
    private ZooKeeper zooKeeper;

    public Autohealer(int numberOfWorkers, String pathToProgram) {
        this.numberOfWorkers = numberOfWorkers;
        this.pathToProgram = pathToProgram;
    }

    public void startWatchingWorkers() throws KeeperException, InterruptedException, IOException {
        if (zooKeeper.exists(AUTOHEALER_ZNODES_PATH, false) == null) {
            zooKeeper.create(AUTOHEALER_ZNODES_PATH, new byte[]{}, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        launchWorkersIfNecessary();
    }

    public void connectToZookeeper() throws IOException {
        this.zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
    }

    public void run() throws InterruptedException {
        synchronized (zooKeeper) {
            zooKeeper.wait();
        }
    }

    public void close() throws InterruptedException {
        zooKeeper.close();
    }

    @Override
    public void process(WatchedEvent event) {
        switch (event.getType()) {
            case None:
                if (event.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to Zookeeper");
                } else {
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from Zookeeper event");
                        zooKeeper.notifyAll();
                    }
                }
                break;
            /**
             * Add states code here to respond to the relevant events
             */
            case NodeDeleted:
                System.out.printf("%s was deleted%n", AUTOHEALER_ZNODES_PATH);
                try {
                    launchWorkersIfNecessary();
                } catch (InterruptedException | KeeperException | IOException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    private void launchWorkersIfNecessary() throws InterruptedException, KeeperException, IOException {
        /**
         * Implement this method to watch and launch new workers if necessary
         */
        final Stat stat = zooKeeper.exists(AUTOHEALER_ZNODES_PATH, this);
        if (stat == null) {
            return;
        }

        final List<String> children = zooKeeper.getChildren(AUTOHEALER_ZNODES_PATH, this);

        final int totalWorkers = children.size();
        System.out.printf("Total no of workers currently: %d%n", totalWorkers);

        if (totalWorkers < numberOfWorkers) {
            int newWorkersToCreate = numberOfWorkers - totalWorkers;
            System.out.printf("Creating %d new workers%n", newWorkersToCreate);
            for (int i = 0; i < newWorkersToCreate; i++) {
                startNewWorker();
            }
        }

        System.out.printf("Latest no of workers: %d%n", zooKeeper.getChildren(AUTOHEALER_ZNODES_PATH, this).size());
    }

    /**
     * Helper method to start a single worker
     *
     * @throws IOException
     */
    private void startNewWorker() throws IOException {
        File file = new File(pathToProgram);
        String command = "java -jar " + file.getCanonicalPath();
        //System.out.println("file exists? " + file.exists());
        System.out.println(String.format("Launching worker instance : %s ", command));
        Runtime.getRuntime().exec(command, null, file.getParentFile());
    }
}
