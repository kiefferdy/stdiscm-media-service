package com.stdiscm.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ConsumerApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ConsumerApplication.class, args);
        
        if (args.length < 3) {
            System.out.println("Usage: java -jar consumer.jar <consumer-threads> <max-queue-size> <port>");
            context.close();
            System.exit(1);
        }
        
        try {
            int consumerThreads = Integer.parseInt(args[0]);
            int maxQueueSize = Integer.parseInt(args[1]);
            int port = Integer.parseInt(args[2]);
            
            VideoConsumerManager manager = context.getBean(VideoConsumerManager.class);
            manager.initialize(consumerThreads, maxQueueSize, port);
            
        } catch (NumberFormatException e) {
            System.out.println("Invalid argument format. All arguments must be integers.");
            context.close();
            System.exit(1);
        }
    }
}
