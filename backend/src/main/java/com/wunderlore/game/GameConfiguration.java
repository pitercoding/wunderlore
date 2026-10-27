package com.wunderlore.game;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameConfiguration {

    @Bean
    public RandomGenerator randomGenerator() {
        // java.util.Random lives in java.base; RandomGenerator.getDefault()
        // needs the jdk.random module, which the JRE runtime image lacks.
        return new Random();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
