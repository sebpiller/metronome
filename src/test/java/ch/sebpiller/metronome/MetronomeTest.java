package ch.sebpiller.metronome;

import org.junit.Before;
import org.junit.Test;

public class MetronomeTest {
    private Metronome instance;
    @Before
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
