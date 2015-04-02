package com.proch.practicehub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.proch.practicehub.MetronomeService.MetronomeBinder;

public class MetronomeFragment extends Fragment {
    private static final int MIN_TEMPO = 20;
    private static final int MAX_TEMPO = 400;
    private static final int DEFAULT_TEMPO = 120;

    private static final int MIN_BEAT_ON = 1;
    private static final int DEFAULT_BEATS_ON = MIN_BEAT_ON;
    private static final int MAX_BEAT_ON = 32;
    private static final int MIN_BEAT_OFF = 0;
    private static final int DEFAULT_BEATS_OFF = MIN_BEAT_OFF;
    private static final int MAX_BEAT_OFF = 32;
    private ToggleButton mStartStopButton;
    private boolean mRunning;
    private int mTempo;
    private boolean mBound;
    private NumberPicker mTempoNumberPicker;
    private NumberPicker mBeatsOnPicker;
    private NumberPicker mBeatsOffPicker;
    private int mBeatsOn;
    private int mBeatsOff;
    private VerticalSeekBar mTempoSeekBar;
    private TextView mTempoDisplay;
    private SharedPreferences mPreferences;
    private MetronomeService mMetronomeService;
    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            MetronomeBinder binder = (MetronomeBinder) service;
            mMetronomeService = binder.getService();
            setUpServiceListener();
            mBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            mBound = false;
        }
    };
    private long mTempoTapLastTappedTime = 0;
    private Activity mActivity;
    private View mView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.metronome, container, false);
        mActivity = getActivity();

        // Make volume button always control just the media volume
        mActivity.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Load stored persistent data
        mPreferences = mActivity.getSharedPreferences("Metronome", Activity.MODE_PRIVATE);
        mTempo = mPreferences.getInt("tempo", DEFAULT_TEMPO);
        mBeatsOn = mPreferences.getInt("beatsOn", DEFAULT_BEATS_ON);
        mBeatsOff = mPreferences.getInt("beatsOff", DEFAULT_BEATS_OFF);

        setUpStartStopButton();
        setUpBeatsControls();
        setUpTempoControls();
        setUpTempoTapButton();
        setUpTempoDisplay();

        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().getApplicationContext().bindService(
                new Intent(getActivity(), MetronomeService.class),
                mConnection,
                Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateRunningState();
    }

    @Override
    public void onStop() {
        super.onStop();
        saveState();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBound) {
            if (mMetronomeService.isRunning()) {
                mActivity.startService(new Intent(mActivity, MetronomeService.class));
            } else {
                mActivity.stopService(new Intent(mActivity, MetronomeService.class));
            }

            mActivity.getApplicationContext().unbindService(mConnection);
            mBound = false;

            removeServiceListener();
        }
    }

    private void setUpStartStopButton() {
        mStartStopButton = (ToggleButton) mView.findViewById(R.id.metronome_start_button);
        mStartStopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                mRunning = !mRunning;
                if (mRunning) {
                    startMetronome();
                } else {
                    stopMetronome();
                }
                updateStartStopButton();
            }
        });
    }

    /*
     * Sets up the tempo controls with the given tempo
     */
    private void setUpTempoControls() {
        mTempoNumberPicker = (NumberPicker) mView.findViewById(R.id.tempo_number_picker);
        mTempoNumberPicker.setMinValue(MIN_TEMPO);
        mTempoNumberPicker.setMaxValue(MAX_TEMPO);
        mTempoNumberPicker.setWrapSelectorWheel(false);
        mTempoNumberPicker.setOnLongPressUpdateInterval(50);

        mTempoNumberPicker.setValue(mTempo);
        mTempoNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {

            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateTempo(newVal);
            }
        });

        mTempoSeekBar = (VerticalSeekBar) mView.findViewById(R.id.tempo_seekbar);
        mTempoSeekBar.setMax(MAX_TEMPO + 1);
        mTempoSeekBar.setProgress(mTempo);
        mTempoSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
                updateTempo(progress);
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                updateTempo(seekBar.getProgress());
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void setUpBeatsControls() {
        mBeatsOnPicker = (NumberPicker) mView.findViewById(R.id.beats_on_number_picker);
        mBeatsOnPicker.setMinValue(MIN_BEAT_ON);
        mBeatsOnPicker.setMaxValue(MAX_BEAT_ON);
        mBeatsOnPicker.setWrapSelectorWheel(false);
        mBeatsOnPicker.setValue(mBeatsOn);

        mBeatsOnPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateBeatsOn(newVal);
            }
        });

        mBeatsOffPicker = (NumberPicker) mView.findViewById(R.id.beats_off_number_picker);
        mBeatsOffPicker.setMinValue(MIN_BEAT_OFF);
        mBeatsOffPicker.setMaxValue(MAX_BEAT_OFF);
        mBeatsOffPicker.setWrapSelectorWheel(false);
        mBeatsOffPicker.setValue(mBeatsOff);

        mBeatsOffPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateBeatsOff(newVal);
            }
        });

    }

    private void setUpTempoTapButton() {
        ((Button) mView.findViewById(R.id.tempo_tap))
                .setOnClickListener(new View.OnClickListener() {

                    public void onClick(View v) {
                        double diffInSeconds = (System.currentTimeMillis() - mTempoTapLastTappedTime) / 1000.0;
                        if (diffInSeconds < 3) {
                            updateTempo((int) (60 / diffInSeconds));
                        }
                        mTempoTapLastTappedTime = System.currentTimeMillis();
                    }
                });
    }

    private void setUpTempoDisplay() {
        mTempoDisplay = (TextView) mView.findViewById(R.id.tempo_display);
        mTempoDisplay.setText(Integer.toString(mTempo));
    }

    private void startMetronome() {
        if (mBound) {
            mMetronomeService.startMetronome(mTempo, mBeatsOn, mBeatsOff);
        }
    }

    private void stopMetronome() {
        if (mBound) {
            mMetronomeService.stopMetronome();
        }
    }

    private void updateService() {
        if (mBound && mRunning) {
            mMetronomeService.updateMetronome(mTempo, mBeatsOn, mBeatsOff);
        }
    }

    /*
     * Updates the running state of the metronome service by updating the variable and button.
     */
    private void updateRunningState() {
        mRunning = MetronomeService.hasInstanceRunning();
        updateStartStopButton();
    }

    /**
     * Updates the start/stop button to have the right text and text color as well as the correct
     * checked state based on whether or not the metronome is actually running.
     */
    private void updateStartStopButton() {
        mStartStopButton.setChecked(mRunning);

        if (mRunning) {
            mStartStopButton.setTextColor(getResources().getColor(R.color.stop_red));
        } else {
            mStartStopButton.setTextColor(getResources().getColor(R.color.start_green));
        }
    }

    private void updateTempo(int tempo) {
        mTempo = tempo > MAX_TEMPO ? MAX_TEMPO : tempo;
        mTempo = mTempo < MIN_TEMPO ? MIN_TEMPO : mTempo;
        updateService();

        mTempoSeekBar.setProgress(mTempo);
        mTempoNumberPicker.setValue(mTempo);
        ((TextView) mView.findViewById(R.id.tempo_display)).setText(Integer.toString(mTempo));
    }

    private void updateBeatsOn(int beatsOn) {
        mBeatsOn = beatsOn;
        updateService();
    }

    private void updateBeatsOff(int beatsOff) {
        mBeatsOff = beatsOff;
        updateService();
    }

    private void saveState() {
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt("tempo", mTempo);
        editor.putInt("beatsOn", mBeatsOn);
        editor.putInt("beatsOff", mBeatsOff);

        editor.commit();
    }

    /**
     * Sets up a listener for the metronome service to listen for when the metronome and stops and the
     * updates the UI accordingly.
     */
    private void setUpServiceListener() {
        mMetronomeService
                .setOnMetronomeChangeListener(new MetronomeService.OnMetronomeChangeListener() {

                    public void onStop() {
                        updateRunningState();
                    }

                    public void onStart() {
                        updateRunningState();
                    }
                });
    }

    /**
     * Cancels any listener created by the above method.
     */
    private void removeServiceListener() {
        mMetronomeService.setOnMetronomeChangeListener(null);
    }
}