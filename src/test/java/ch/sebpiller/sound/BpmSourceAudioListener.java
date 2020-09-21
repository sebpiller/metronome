package ch.sebpiller.sound;

import ch.sebpiller.tictac.BpmSource;
import ddf.minim.AudioInput;
import ddf.minim.AudioListener;
import ddf.minim.Minim;
import ddf.minim.analysis.BeatDetect;
import ddf.minim.javasound.JSMinim;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import javax.sound.sampled.AudioFormat;
import java.util.Queue;

/**
 * A BpmSource that connects directly an AudioStream (system line-in by default) and automatically find the tempo of
 * the music actually playing.
 */
public class BpmSourceAudioListener implements BpmSource, AudioListener {
    // 20 items in the buffer: that means the tempo returned is computed using the average
    // of the ~ 8..12 last seconds of measured beat.
    private final Queue<Float> mostRecentDetectedBpms = new CircularFifoQueue<>(20);
    private BeatDetect beatDetect;
    private long lastBeatNanoTime = 0;

    /**
     * use the system line in to catch sound.
     */
    public BpmSourceAudioListener() {
        initAudioInput(new Minim(new JSMinim(this)).getLineIn());
    }

    public BpmSourceAudioListener(AudioInput audioInput) {
        initAudioInput(audioInput);
    }

    private void initAudioInput(AudioInput audioInput) {
        AudioFormat format = audioInput.getFormat();
        beatDetect = new BeatDetect(format.getFrameSize(), format.getSampleRate());
        beatDetect.detectMode(BeatDetect.SOUND_ENERGY/**//*FREQ_ENERGY*/);
        beatDetect.setSensitivity(300);
        audioInput.addListener(this);
    }

    @Override
    public void samples(float[] samp) {
        onSamplesAcquired(samp);
    }

    @Override
    public void samples(float[] sampL, float[] sampR) {
        onSamplesAcquired(sampL);
    }

    private void onSamplesAcquired(float[] samp) {
        long now = System.nanoTime();
        beatDetect.detect(samp);

        if (beatDetect.isOnset() || beatDetect.isKick()) {

            if (lastBeatNanoTime != 0) {
                float detectedBpm = (float) (((60d * 1_000_000 * 1_000)) / (now - lastBeatNanoTime));

                // suspicious tempos are ignored
                if (detectedBpm > 80 && detectedBpm < 180) {
                    synchronized (mostRecentDetectedBpms) {
                        mostRecentDetectedBpms.add(detectedBpm);
                    }
                }
            }

            lastBeatNanoTime = now;
        }
    }

    @Override
    public float getBpm() {
        synchronized (mostRecentDetectedBpms) {
            float average = (float) mostRecentDetectedBpms.stream().mapToDouble((x) -> x).summaryStatistics().getAverage();
            return average;
        }
    }
}