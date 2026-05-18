package com.ecommerce.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ThreadPoolExecutorConfig {

    @Bean("recommendRecallExecutor")
    public ThreadPoolExecutor recommendRecallExecutor() {
        AtomicInteger counter = new AtomicInteger(1);
        return new ThreadPoolExecutor(
                4, 20,
                1000, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000),
                r -> new Thread(r, "recommend-recall-" + counter.getAndIncrement()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
