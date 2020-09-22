package ch.sebpiller.tictac;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.assertj.core.api.Assertions.assertThat;

@Ignore
@RunWith(Parameterized.class)
public class TicTacTest {
    static final float MAX_ERRORS_RATE_ALLOWED = 5 / 100f; // percent allowed of out-of-tolerance result
    static final float BPM_TOLERANCE = 1 / 100f; // when to consider an error occurred

    // the test is run this length minimum
    static final int TEST_MIN_TICKS_TO_VALIDATE = 50;

    @Parameterized.Parameter
    public int bpm = 0;
    // just returns bpm
    private final BpmSource fastBpmReader = () -> bpm;
    // just returns bpm, but after 150ms delay
    private final BpmSource slowBpmReader = () -> {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
        return bpm;
    };
    // what happens with a bpm provider way too slow ?
    private final BpmSource slowAsHellBpmReader = () -> {
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            // ignore
        }
        return bpm;
    };
    private int errorCount, testedTicks;
    private long last;

    /* watch if tic tac does good job */
    private final TicTac.TicTacListener watcher = (ticOrTac, expectedTempo) -> {
        testedTicks++;

        long now = System.nanoTime();
        long elapsed = now - last;
        double realBpm = 60_000_000_000d / elapsed;

        System.out.println(
                (ticOrTac ? "tic  " : "  tac") +
                        " | measured @ " + String.format("%.4f", realBpm) + " bpm"
        );

        double delta = expectedTempo - realBpm;

        if (delta > BPM_TOLERANCE || delta < -BPM_TOLERANCE) {
            errorCount++;
        }

        last = now;
    };

    private final TicTac.TicTacListener slowWatcher = (ticOrTac, expectedTempo) -> {
        watcher.beat(ticOrTac, expectedTempo);
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // ignore
        }
    };
    private TicTabBuilder builder = new TicTabBuilder().withListener(watcher);

    @Parameterized.Parameters
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
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        errorCount = -1; // the first measure is always an error, since we don't have any other beat to compute a tempo
        testedTicks = 0;
        last = 0;
    }

    @Ignore("just displaying tic-tac")
    @Test
    public void testDisplay() throws Exception {
        TicTac ticTac = new TicTabBuilder().connectedToBpm(fastBpmReader).build();
        Thread.sleep(10_000);
        ticTac.close();
    }

    @Test
    public void testFastBpmReader() throws Exception {
        testTicTac(builder.connectedToBpm(fastBpmReader).build());
    }

    @Test
    public void testSlowBpmReader() throws Exception {
        testTicTac(builder.connectedToBpm(slowBpmReader).build());
    }

    @Test(expected = Error.class)
    public void testSlowAsHellBpmReader() throws Exception {
        // TODO fix the expected error as it should be more specific
        testTicTac(builder.connectedToBpm(slowAsHellBpmReader).build());
    }

    @Test
    public void testSlowBpmReaderAndSlowListener() throws Exception {
        testTicTac(builder.connectedToBpm(slowBpmReader).withListener(slowWatcher).build());
    }

    /**
     * Asserts that TicTac has been able to produce at most {@value MAX_ERRORS_RATE_ALLOWED} errors of precision during a
     * run a {@value TEST_MIN_TICKS_TO_VALIDATE} ticks.
     */
    private void testTicTac(TicTac ticTac) throws InterruptedException {
        System.out.println("testing tic-tac @" + bpm + "bpm...");

        Thread.sleep((long) ((60_000d / bpm * TEST_MIN_TICKS_TO_VALIDATE) + 2000));
        ticTac.close();

        assertThat(ticTac.nt.isTerminated())
                .describedAs("tic tac has not been closed correctly"
                ).isTrue()
        ;
        assertThat(testedTicks)
                .describedAs("less ticks than expected: %s received, where %s at least were expected", testedTicks, TEST_MIN_TICKS_TO_VALIDATE)
                .isGreaterThanOrEqualTo(TEST_MIN_TICKS_TO_VALIDATE)
        ;
        assertThat((float) (errorCount) / testedTicks)
                .describedAs("number of times the minimal precision was not met: %s of %s rate=%s",
                        errorCount, testedTicks, (float) (errorCount) / testedTicks)
                .isLessThan(MAX_ERRORS_RATE_ALLOWED)
        ;
    }
}