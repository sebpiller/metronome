package ch.sebpiller.metronome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Metronome2_IntegrationTest {
    private Metronome instance;

    @BeforeEach
    void setUp() throws Exception {
        instance = new MetronomeBuilder().withRhythm(() -> 120).build();
    }

    @Test
    void test() throws InterruptedException {
        Thread.sleep(20_000);
        instance.stop();
        instance.waitTermination();
    }
}
