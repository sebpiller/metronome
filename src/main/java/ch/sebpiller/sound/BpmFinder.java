package ch.sebpiller.sound;

import ddf.minim.AudioInput;
import ddf.minim.AudioListener;
import ddf.minim.Minim;
import ddf.minim.analysis.BeatDetect;
import ddf.minim.javasound.JSMinim;
import org.apache.commons.collections4.queue.CircularFifoQueue;

import javax.sound.sampled.AudioFormat;
import java.util.Queue;

/**
 * Bpm finder computes an average of the 10 last detected BPM in the audio stream.
 */
public class BpmFinder implements AudioListener {
    // 20 detections in the buffer means the average is computed with the 20 most recent values.
    private final Queue<Float> mostRecentDetectedBpms = new CircularFifoQueue<>(20);
    private BeatDetect beatDetect;
    private long lastBeatNanoTime = 0;

    public BpmFinder() {
        initAudioInput(new Minim(new JSMinim(this)).getLineIn());
    }

    public BpmFinder(AudioInput audioInput) {
        initAudioInput(audioInput);
    }

    private void initAudioInput(AudioInput audioInput) {
        AudioFormat format = audioInput.getFormat();
        beatDetect = new BeatDetect(format.getFrameSize(), format.getSampleRate());
        beatDetect.detectMode(BeatDetect.SOUND_ENERGY/**//*FREQ_ENERGY*/);
        beatDetect.setSensitivity(300);
        audioInput.addListener(this);
        //audioInput.setVolume(100);
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
        beatDetect.detect(samp);

        if (beatDetect.isOnset() || beatDetect.isKick()) {
            long l = System.nanoTime();

            if (lastBeatNanoTime != 0) {
                float detectedBpm = (float) (((60d * 1_000_000 * 1_000)) / (l - lastBeatNanoTime));

                if (detectedBpm > 100 && detectedBpm < 160) {
                    synchronized (mostRecentDetectedBpms) {
                        mostRecentDetectedBpms.add(detectedBpm);
                    }
                }
            }

            lastBeatNanoTime = l;
        }
    }

    public float getAverageBpm() {
        synchronized (mostRecentDetectedBpms) {
            float average = (float) mostRecentDetectedBpms.stream().mapToDouble((x) -> x).summaryStatistics().getAverage();
            return average;
        }
    }
}