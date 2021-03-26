package ch.sebpiller.metronome;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * This test is considered successful when at most {@value MAX_ERRORS_RATE_ALLOWED} (percentile) of all runs are measured
 * to be off by at most {@value BPM_TOLERANCE} BPMs from the expected value.
 */
@RunWith(Parameterized.class)
public class MetronomeIntegrationTest {
    static final float MAX_ERRORS_RATE_ALLOWED = 5 / 100f; // percent allowed of out-of-tolerance result
    static final float BPM_TOLERANCE = 5 / 100f; // when to consider an error occurred

    // the test is run this length minimum
    static final int TEST_MIN_TICKS_TO_VALIDATE = 50;

    /* watch if metronom does good job */
    private final Metronome.MetronomeListener watcher = new Metronome.MetronomeListener() {
        @Override
        public void missedBeats(int count, float bpm) {
            System.out.println("missed| " + count + " beats");
        }

        @Override
        public void beat(boolean ticOrTac, float bpm) {
            testedTicks++;

            long now = System.nanoTime();
            long elapsed = now - last;
            double realBpm = 60_000_000_000d / elapsed;

            System.out.println(
                    (ticOrTac ? "tic  " : "  tac") +
                            " | measured @ " + String.format("%.4f", realBpm) + " bpm"
            );

            double delta = bpm - realBpm;

            if (delta > BPM_TOLERANCE || delta < -BPM_TOLERANCE) {
                errorCount.incrementAndGet();
            }

            last = now;
        }
    };

    private final Metronome.MetronomeListener slowWatcher = (ticOrTac, expectedTempo) -> {
        watcher.beat(ticOrTac, expectedTempo);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
    };
    private final MetronomeBuilder builder = new MetronomeBuilder().withListener(watcher);
    @Parameterized.Parameter
    public int bpm = 0;
    // just returns bpm
    private final Tempo fastBpmReader = () -> bpm;
    // just returns bpm, but after 150ms delay
    private final Tempo slowTempoProvider = () -> {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
        return bpm;
    };
    // what happens with a bpm provider way too slow ?
    private final Tempo slowAsHellTempoProvider = () -> {
        try {
            Thread.sleep(1200);
        } catch (InterruptedException e) {
            // ignore
        }
        return bpm;
    };
    private AtomicInteger errorCount = new AtomicInteger(0);
    private int testedTicks;
    private long last;

    @Parameterized.Parameters(name = "{index} - @{0}bpm")
    public static Object[] getParameters() {
        return new Object[]{
                160,
                140,
                120,
                100,
                85,
        };
    }

    @Before
    public void setUp() {
        // warmup the thread...
        System.out.println("warmup the thread...");

        try {
            Thread.sleep(2000); //NOSONAR
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        errorCount.set(-1); // the first measure is always an error, since we don't have any other beat to compute a tempo
        testedTicks = 0;
        last = 0;
    }

    @Test
    public void testFastBpmReader() throws Exception {
        testMetronom(builder.withRhythm(fastBpmReader).build());
    }

    @Ignore("TODO reimplement")
    @Test
    public void testSlowTempoProvider() throws Exception {
        testMetronom(builder.withRhythm(slowTempoProvider).build());
    }

    @Ignore("TODO reimplement")
    @Test(expected = Error.class)
    public void testSlowAsHellBpmReader() throws Exception {
        // TODO fix the expected error as it should be more specific
        testMetronom(builder.withRhythm(slowAsHellTempoProvider).build());
    }

    @Ignore("TODO reimplement")
    @Test
    public void testSlowBpmReaderAndSlowListener() throws Exception {
        testMetronom(builder.withRhythm(slowTempoProvider).withListener(slowWatcher).build());
    }

    /**
     * Asserts that TicTac has been able to produce at most {@value MAX_ERRORS_RATE_ALLOWED} errors of precision during a
     * run a {@value TEST_MIN_TICKS_TO_VALIDATE} ticks.
     */
    @SuppressWarnings("java:S2925")
    private void testMetronom(Metronome metronome) throws InterruptedException {
        System.out.println("testing tic-tac @" + bpm + "bpm...");

        Thread.sleep((long) ((60_000d / bpm * TEST_MIN_TICKS_TO_VALIDATE) + 2000));
        while (testedTicks < TEST_MIN_TICKS_TO_VALIDATE) {
            Thread.sleep(100);
        }
        metronome.close();

        assertThat(metronome.isTerminated())
                .describedAs("metronome has not been closed correctly"
                ).isTrue()
        ;
        assertThat(testedTicks)
                .describedAs("less ticks than expected: %s received, where %s at least were expected", testedTicks, TEST_MIN_TICKS_TO_VALIDATE)
                .isGreaterThanOrEqualTo(TEST_MIN_TICKS_TO_VALIDATE)
        ;

        int i = errorCount.get();
        assertThat((float) i / testedTicks)
                .describedAs("number of times the minimal precision was not met: %s of %s rate=%s",
                        errorCount, testedTicks, (float) i / testedTicks)
                .isLessThan(MAX_ERRORS_RATE_ALLOWED)
        ;
    }
}