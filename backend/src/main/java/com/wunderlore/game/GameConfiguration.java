package com.wunderlore.game;

import java.util.random.RandomGenerator;
import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfiguration {

    @Bean
    public RandomGenerator randomGenerator() {
        return RandomGenerator.getDefault();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
