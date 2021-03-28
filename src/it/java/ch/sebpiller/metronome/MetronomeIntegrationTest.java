package ch.sebpiller.metronome;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * This test is considered successful when at most {@value MAX_ERRORS_RATE_ALLOWED} (percentile) of all runs are measured
 * to be off by at most {@value BPM_TOLERANCE} BPMs from the expected value.
 */
class MetronomeIntegrationTest {
    static final float MAX_ERRORS_RATE_ALLOWED = 5 / 100f; // percent allowed of out-of-tolerance result
    static final float BPM_TOLERANCE = 25 / 100f; // when to consider an error occurred

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

    private AtomicInteger errorCount = new AtomicInteger(0);
    private int testedTicks;
    private long last;

    @BeforeEach
    void setUp() {
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


   /* @Parameterized.Parameters(name = "{index} - @{0}bpm")
    public static Object[] getParameters() {
        return new Object[]{
                160,
                140,
                120,
                100,
                85,
        };
    }*/

    @ParameterizedTest
    @ValueSource(ints = {160, /*140, 120, 100, 85*/})
    public void testFastBpmReader(int bpm) throws Exception {
        testMetronom(bpm, builder.withRhythm(() -> bpm).build());
    }

    @Disabled("TODO reimplement")
    @ParameterizedTest
    @ValueSource(ints = {160, /*140, 120, 100, 85*/})
    public void testSlowTempoProvider(int bpm) throws Exception {
        testMetronom(bpm, builder.withRhythm(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
            return bpm;
        }).build());
    }

    @Disabled("TODO reimplement")
    @ParameterizedTest
    @ValueSource(ints = {160, /*140, 120, 100, 85*/})
    //@Test(expected = Error.class)
    public void testSlowAsHellBpmReader(int bpm) throws Exception {
        // TODO fix the expected error as it should be more specific
        testMetronom(bpm, builder.withRhythm(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                // ignore
            }
            return bpm;
        }).build());
    }

    @Disabled("TODO reimplement")
    @ParameterizedTest
    @ValueSource(ints = {160, /*140, 120, 100, 85*/})
    public void testSlowBpmReaderAndSlowListener(int bpm) throws Exception {
        testMetronom(bpm, builder.withRhythm(() -> {
            try {
                Thread.sleep(1200);
            } catch (InterruptedException e) {
                // ignore
            }
            return bpm;
        }).withListener(slowWatcher).build());
    }

    /**
     * Asserts that TicTac has been able to produce at most {@value MAX_ERRORS_RATE_ALLOWED} errors of precision during a
     * run a {@value TEST_MIN_TICKS_TO_VALIDATE} ticks.
     */
    @SuppressWarnings("java:S2925")
    private void testMetronom(int expectedTempo, Metronome metronome) throws InterruptedException {
        System.out.println("testing tic-tac @" + expectedTempo + "bpm...");

        Thread.sleep((long) ((60_000d / expectedTempo * TEST_MIN_TICKS_TO_VALIDATE) + 2000));
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