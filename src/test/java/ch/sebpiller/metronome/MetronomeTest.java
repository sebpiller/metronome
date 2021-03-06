package ch.sebpiller.metronome;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetronomeTest {
    private Metronome instance;

    @BeforeEach
    public void setUp() throws Exception {
        instance = new MetronomeBuilder().withRhythm(() -> 120).build();
    }

    @Test
    public void test() throws InterruptedException {
        Thread.sleep(20_000);
        instance.stop();
        instance.waitTermination();
    }
}
