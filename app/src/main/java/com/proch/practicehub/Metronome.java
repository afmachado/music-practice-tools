package com.proch.practicehub;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Metronome {

    private short[] mTickData;
    private short[] mTockData;
    private boolean mRunning = false;
    private int mTempo;
    private boolean[] mPattern = {true};
    private int mCurrentBeat;
    private ExecutorService mExecutor;
    private Clicker mClicker;
    private float mInitialVolume; // Volume to start metronome, may change and not update this back

    /**
     * Creates a Metronome to play the given volume
     *
     * @param context Context object to allow getting integer resources
     * @param volume  Float between 0 and 1, 1 being the loudest
     */
    public Metronome(Context context, float volume) {
        mTickData = Utility.intToShortArray(context.getResources().getIntArray(R.array.tick_pcm));
        mTockData = Utility.intToShortArray(context.getResources().getIntArray(R.array.tock_pcm));

        mExecutor = Executors.newSingleThreadExecutor();
        mInitialVolume = volume;
    }

    public Metronome(Context context) {
        // Play at default volume, if not specified
        this(context, Clicker.DEFAULT_VOLUME);
    }

    public static float getMinVolume() {
        return Clicker.MIN_VOLUME;
    }

    public static float getMaxVolume() {
        return Clicker.MAX_VOLUME;
    }

    /**
     * Releases resources used by the metronome. Should be called when metronome is no longer in use.
     */
    public void destroy() {
        mExecutor.shutdown();
    }

    /**
     * Starts the metronome at the given tempo and beats.
     *
     * @param tempo    Tempo in beats per minute of the metronome
     * @param beatsOn  Number of consecutive beats it will click for in one cycle
     * @param beatsOff Number of consecutive beats of rest at the end of each cycle
     */
    public void start(int tempo, int beatsOn, int beatsOff) {
        update(tempo, beatsOn, beatsOff);
        mRunning = true;

        mClicker = new Clicker(mTickData, mTockData, mInitialVolume);
        mExecutor.execute(mClicker);
    }

    /**
     * Starts the metronome with the default settings for tempo and beats on/off
     */
    public void start(int tempo) {
        start(tempo, 1, 0);
    }

    /**
     * Stops the metronome if it was running.
     */
    public void stop() {
        mRunning = false;
        mClicker = null;
    }

    /**
     * Updates the metronome with the given tempo and beats pattern.
     *
     * @param tempo    Beats per minute that the metronome will click
     * @param beatsOn  Number of consecutive beats it will click for in one cycle
     * @param beatsOff Number of consecutive beats of rest at the end of each cycle
     */
    public void update(int tempo, int beatsOn, int beatsOff) {
        mTempo = tempo;
        mPattern = generatePattern(beatsOn, beatsOff);
        mCurrentBeat = 0;
    }

    /**
     * Returns true if the metronome is currently running.
     */
    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Returns the tempo in beats per minute that the metronome is set to.
     */
    public int getTempo() {
        return mTempo;
    }

    /**
     * Returns the volume of the metronome's clicker.
     *
     * @return Float value between 0 and 1, 1 being the loudest.
     */
    public float getVolume() {
        if (mClicker != null) {
            return mClicker.getVolume();
        }
        return mInitialVolume;
    }

    /**
     * Sets the volume for the metronome's clicker, if it has one.
     *
     * @param newVolume Float value between 0 and 1
     */
    public void setVolume(float newVolume) {
        mInitialVolume = newVolume; // Start new clickers at this volume
        if (mClicker != null) {
            mClicker.setVolume(newVolume);
        }
    }

    /**
     * Generates a pattern of beatsOn number of trues, and beatsOff number of falses.
     *
     * @param beatsOn  Number of consecutive beats it will click for in one cycle
     * @param beatsOff Number of consecutive beats of rest at the end of each cycle
     * @return Array of booleans of a single cycle for the metronome with true representing clicks
     */
    private boolean[] generatePattern(int beatsOn, int beatsOff) {
        boolean[] result = new boolean[beatsOn + beatsOff];
        // Pattern is all falses by default, so just set beatsOn indices to true
        for (int i = 0; i < beatsOn; i++)
            result[i] = true;

        return result;
    }

    /**
     * Runnable class that keeps looping through the cycle clicking as specified by the pattern array.
     */
    class Clicker implements Runnable {

        private static final int WRITE_CHUNK_IN_FRAMES = 8820; // 200 ms
        private static final int SAMPLE_RATE = 22050;
        private static final int BUFFER_SIZE = 22050;
        private static final float MIN_VOLUME = 0.0f;
        private static final float MAX_VOLUME = 1.0f;
        private static final float DEFAULT_VOLUME = MAX_VOLUME;
        private final short[] mTickData;
        private final short[] mTockData;
        private final AudioTrack mTrack;
        private float mVolume = DEFAULT_VOLUME; // Value between 0 and 1, 1 being full volume

        public Clicker(short[] tickData, short[] tockData, float volume) {
            mTickData = tickData;
            mTockData = tockData;
            mTrack = new AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE, AudioTrack.MODE_STREAM);
            setVolume(volume);
        }

        public float getVolume() {
            return mVolume;
        }

        /**
         * Sets the new volume for the metronome.
         *
         * @param newVolume Float value between MIN_VOLUME and MAX_VOLUME
         */
        public void setVolume(float newVolume) {
            if (newVolume < MIN_VOLUME || newVolume > MAX_VOLUME) {
                throw new IllegalArgumentException("Volume outside of valid range");
            }
            mVolume = newVolume;
            mTrack.setStereoVolume(mVolume, mVolume);
        }

        /**
         * Writes the next beat in the pattern, a tick, tock, or beat of rest, to the AudioTrack and
         * updates data to keep track of where we are in the pattern. Assumes that mTickData.length ==
         * mTockData.length.
         */
        private void writeNextBeatOfPattern() {
            if (mPattern[mCurrentBeat]) {
                if (mCurrentBeat == 0) {
                    mTrack.write(mTockData, 0, mTockData.length);
                } else {
                    mTrack.write(mTickData, 0, mTickData.length);
                }
            } else {
                // Write the amount of rest that a tick or tock would normally take up
                mTrack.write(new short[mTickData.length], 0, mTickData.length);
            }
            mCurrentBeat++;
            mCurrentBeat %= mPattern.length;
        }

        /**
         * Start the clicking of the metronome by writing the tick or tock data or zeros in between.
         */
        public void run() {
            mTrack.play();
            int interval_in_frames = 60 * SAMPLE_RATE / mTempo;
            int frames_since_played = interval_in_frames;

            while (mRunning) {
                interval_in_frames = 60 * SAMPLE_RATE / mTempo; // Recalculate in case tempo changed

                if (frames_since_played >= interval_in_frames) {
                    writeNextBeatOfPattern();
                    frames_since_played = mTickData.length;
                } else {
                    int frames_left_to_wait = interval_in_frames - frames_since_played;

                    // Rest for a full write chunk or until the next click needs to play, whichever is less.
                    int rest_length_in_frames = Math.min(frames_left_to_wait, WRITE_CHUNK_IN_FRAMES);
                    mTrack.write(new short[rest_length_in_frames], 0, rest_length_in_frames);

                    frames_since_played += rest_length_in_frames;
                }
            }
            mTrack.stop();
            mTrack.release();
        }
    }

}
