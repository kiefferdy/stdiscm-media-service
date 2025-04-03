package com.stdiscm.producer;

import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProducerMain {
    private static final Logger logger = LoggerFactory.getLogger(ProducerMain.class);

    public static void main(String[] args) {
        // Parse command line arguments
        Options options = new Options();
        
        Option pOpt = Option.builder("p")
                .longOpt("producers")
                .hasArg()
                .desc("Number of producer threads")
                .type(Number.class)
                .required(true)
                .build();
        
        Option hostOpt = Option.builder("h")
                .longOpt("host")
                .hasArg()
                .desc("Consumer host address")
                .type(String.class)
                .required(true)
                .build();
        
        Option portOpt = Option.builder("t")
                .longOpt("port")
                .hasArg()
                .desc("Consumer port")
                .type(Number.class)
                .required(true)
                .build();
        
        Option dirOpt = Option.builder("d")
                .longOpt("directories")
                .hasArg()
                .desc("Comma-separated list of directories containing videos")
                .type(String.class)
                .required(true)
                .build();
        
        options.addOption(pOpt);
        options.addOption(hostOpt);
        options.addOption(portOpt);
        options.addOption(dirOpt);
        
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd;
        
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("producer", options);
            System.exit(1);
            return;
        }
        
        int numProducers;
        try {
            numProducers = ((Number)cmd.getParsedOptionValue("p")).intValue();
        } catch (ParseException e) {
            System.out.println("Error parsing producer count: " + e.getMessage());
            formatter.printHelp("producer", options);
            System.exit(1);
            return;
        }
        String consumerHost = cmd.getOptionValue("h");
        int consumerPort;
        try {
            consumerPort = ((Number)cmd.getParsedOptionValue("t")).intValue();
        } catch (ParseException e) {
            System.out.println("Error parsing port: " + e.getMessage());
            formatter.printHelp("producer", options);
            System.exit(1);
            return;
        }
        String directoriesStr = cmd.getOptionValue("d");
        String[] directories = directoriesStr.split(",");
        
        if (directories.length < numProducers) {
            logger.error("Number of directories ({}) must be at least the number of producers ({})", 
                    directories.length, numProducers);
            System.exit(1);
            return;
        }
        
        logger.info("Starting {} producer threads, connecting to {}:{}", 
                numProducers, consumerHost, consumerPort);
        
        // Create and start producer threads
        ExecutorService executor = Executors.newFixedThreadPool(numProducers);
        List<VideoProducer> producers = new ArrayList<>();
        
        for (int i = 0; i < numProducers; i++) {
            VideoProducer producer = new VideoProducer(i + 1, directories[i], consumerHost, consumerPort);
            producers.add(producer);
            executor.submit(producer);
        }
        
        // Shutdown hook to gracefully terminate producers
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down producers...");
            for (VideoProducer producer : producers) {
                producer.stop();
            }
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            logger.info("Producers shutdown complete");
        }));
        
        // Main thread waits for executor service to complete
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            logger.error("Main thread interrupted", e);
        }
    }
}
