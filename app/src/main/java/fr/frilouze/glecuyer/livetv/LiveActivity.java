package fr.frilouze.glecuyer.livetv;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.icu.text.SimpleDateFormat;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;

import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/*
https://developer.android.com/training/tv/tif/channel
 */

public class LiveActivity extends Activity {
    private static final String TAG = "LiveTv";
    final private String LAST_CHANNEL_NB = "LastChannelNb";
    private TvView mTvView;
    private String mInputId = null;
    private ProgressBar mTimeProgress;
    private Cursor mTvChanCursor;
    private int mPreviousChannelNumber;
    private int mChannelNumber;
    private int mVirtualChannelNumber;
    private int mEnteringValue = -1;
    Handler mKeyPadHandler;
    boolean mTextInfoIsOn = false;
    Handler mDisplayMgrHandler;
    Runnable mClearDisplayRunnable;
    ProgramDescription mProgramDescription;
    final Uri mProgramsUri = Uri.parse("content://android.media.tv/program");
    final int mInfoBackgroundColor=0x7F000000;

    class ProgramDescription{
        TextView tv;
        long startTime;
        long endTime;
        String title;
        String description;
        boolean textDescriptionIsOn = false;
        int xScrollingPos;
        int lineHeight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);

        TvInputManager tvInputManager = (TvInputManager) getApplicationContext().getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> tvInputInfo = tvInputManager.getTvInputList();
        Log.d(TAG, "tvInputManager=" + tvInputManager);
        //Log.d(TAG, "tvInputInfo=" + tvInputInfo);
        tvInputManager.registerCallback(new TvInputCallback(), new Handler());

        mTvView = (TvView) findViewById(R.id.dtv_view);
        //mTvView.setTimeShiftPositionCallback(new timeShiftPositionCallback());
        mTvView.setCallback(new TvViewTvInputCallback());

        mTimeProgress = (ProgressBar) findViewById(R.id.chProgressTime);
        mTimeProgress.setMax(100);
        //mTimeProgress.setMin(0);
        mTimeProgress.setProgress(33);

        mProgramDescription = new ProgramDescription();
        mProgramDescription.tv = findViewById(R.id.pgmDescription);
        //mProgramDescription.tv.setMovementMethod(new ScrollingMovementMethod()); // ???
        mProgramDescription.lineHeight = mProgramDescription.tv.getLineHeight();



        for (TvInputInfo tvInputInfoItem : tvInputInfo) { // or RichTvInputService
            if (tvInputInfoItem.getId().contains(".DtvInputService")) {
                mInputId = tvInputInfoItem.getId();
            }
        }
        if (mInputId == null) {
            Toast.makeText(getApplicationContext(), "NO CHANNELS  ! Please run setup", Toast.LENGTH_LONG).show();
            return;
        }

        if (!initChannelsCursor(mInputId)) {
            Toast.makeText(getApplicationContext(), "NO CHANNELS  ! Please run setup", Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences preferences = getPreferences(MODE_PRIVATE);

        tuneToChannelNumber(preferences.getInt(LAST_CHANNEL_NB, 7) );
    }

    @Override
    public void onPause() {
        super.onPause();
        mTvView.reset();
        mTvChanCursor.close();

        Log.i(TAG, "Pausing Live TV");
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(LAST_CHANNEL_NB, mChannelNumber);
        editor.commit();
    }


    @Override
    public void onResume() {
        super.onResume();

        Log.i(TAG, "Resuming Live TV");

        if (!initChannelsCursor(mInputId)) {
            Toast.makeText(getApplicationContext(), "NO CHANNELS  ! Please run setup", Toast.LENGTH_LONG).show();
            return;
        }
        SharedPreferences preferences = getPreferences(MODE_PRIVATE);
        tuneToChannelNumber(preferences.getInt(LAST_CHANNEL_NB, 7) );
    }


    private boolean tuneToChannelNumber( int nb ) {
        return tuneToChannelNumber( nb, false );
    }


    private boolean tuneToChannelNumber(int nb, boolean virtualZap ) {

        int oldChNb = mChannelNumber;
        int newChNb = mChannelNumber;

        if (mTvView == null)
            return false;

        //conmTvView.reset();


        if (setCurrentChannelFromDisplayNumber(nb)) {
            newChNb = nb;
            Log.i(TAG, "Channel NB changed to + " + nb);
        } else {
            Log.i(TAG, "Failed to tune to channel NB " + nb);
        }

        setCurrentChannelFromDisplayNumber(newChNb);
        Uri channelUri = getCurrentChannelUri();

        mVirtualChannelNumber = newChNb;
        if( ! virtualZap ) {
            mTvView.tune(mInputId, channelUri);
            mChannelNumber = newChNb;
            mPreviousChannelNumber = oldChNb;
        }

        clearProgramDescription();
        clearChannelShortInfo();
        displayChannelShortInfo(newChNb);


        if  ( newChNb == 700){
            try {
                ImageView iv= (ImageView)findViewById(R.id.chLogo);

                // get input stream
                InputStream ims = getAssets().open("7-arte.png");
                // load image as Drawable
                Drawable d = Drawable.createFromStream(ims, null);
                // set image to ImageView
                iv.setImageDrawable(d);
                ims .close();
            } catch(final Throwable tx) {

            }
        }


        return true;
    }


    private void clearProgramDescription(){
        mProgramDescription.tv.setText("");
        //tv.setBackgroundColor(0xFFFFFF);
        mProgramDescription.tv.setBackgroundColor(0x00000000);
        mProgramDescription.xScrollingPos = 0;
        mProgramDescription.textDescriptionIsOn = false;
    }


    void displayProgramDescription(){
        TextView tv;
        //LinearLayout ll = findViewById(R.id.descriptionLayout);
        //ll.setBackgroundColor(mInfoBackgroundColor);
        getProgramDescription();

        mProgramDescription.tv.setText(mProgramDescription.description);
        mProgramDescription.tv.setBackgroundColor(mInfoBackgroundColor);
        mProgramDescription.tv.setTextColor(0xFFD0D0D0);

        mProgramDescription.tv.scrollTo(0, mProgramDescription.xScrollingPos);
        mProgramDescription.textDescriptionIsOn = true;
    }


    private void displayChannelName() {
        TextView textChNumber = (TextView) findViewById(R.id.textChName);
        textChNumber.setText(getChannelName());

    }

    private void displayChannelNumber(int nb) {
        TextView textChNumber = (TextView) findViewById(R.id.textChNumber);
        textChNumber.setText("" + nb);
        Log.i(TAG, "Value = " + nb);
    }


    private void updateProgressBar(){
        getProgramDescription();
        long now = new Date().getTime();
        int max = (int)((mProgramDescription.endTime - mProgramDescription.startTime) / 1000);
        mTimeProgress.setMax( max );

        int elapsed = (int)((now - mProgramDescription.startTime)/1000);
        if ( elapsed < 0 )
            elapsed = 0;
        mTimeProgress.setProgress(elapsed);

        SimpleDateFormat formatter = new SimpleDateFormat(" HH:mm ");

        String date = formatter.format(new Date(mProgramDescription.startTime));
        TextView tv = findViewById(R.id.chStartTime);
        tv.setText(date);

        date = formatter.format(new Date(mProgramDescription.endTime));
        tv = findViewById(R.id.chEndTime);
        tv.setText(date);
    }


    private void showProgressBar( boolean yes ){
        updateProgressBar();
        TextView startv = (TextView) findViewById(R.id.chStartTime);
        TextView endtv = (TextView) findViewById(R.id.chEndTime);
        if ( yes ){
            mTimeProgress.setVisibility(View.VISIBLE);
            startv.setVisibility(View.VISIBLE);
            endtv.setVisibility(View.VISIBLE);
        } else {
            mTimeProgress.setVisibility(View.INVISIBLE);
            startv.setVisibility(View.INVISIBLE);
            endtv.setVisibility(View.INVISIBLE);
        }
    }

    private void showChannelLogo( boolean yes ){
        ImageView logo = (ImageView) findViewById(R.id.chLogo);
        if ( yes ){
            logo.setVisibility(View.VISIBLE);
        } else {
            mTimeProgress.setVisibility(View.INVISIBLE);
            logo.setVisibility(View.INVISIBLE);
        }
    }


    private void clearChannelShortInfo(){
        //Log.i(TAG, "clearChannelShortInfo()");
        if ( mProgramDescription.textDescriptionIsOn ){
            clearProgramDescription();
        }
        LinearLayout ll = findViewById(R.id.infoLayout);
        ll.setBackgroundColor(0);

        TextView tv = (TextView) findViewById(R.id.textChNumber);
        tv.setText("");
        tv = (TextView) findViewById(R.id.textChName);
        tv.setText("");
        tv = (TextView) findViewById(R.id.pgmDescription);
        tv.setText("");
        tv = (TextView) findViewById(R.id.pgmTitle);
        tv.setText("");
        tv = (TextView) findViewById(R.id.pgmTitle);
        tv.setText("");
        showProgressBar(false);
        showChannelLogo(false);
        mTextInfoIsOn = false;
    }


    private void displayChannelShortInfo(int channelNumber) {
        displayChannelNumber(channelNumber);
        displayChannelName();
        showProgressBar(true);
        showChannelLogo(true);

        LinearLayout ll = findViewById(R.id.infoLayout);
        ll.setBackgroundColor(mInfoBackgroundColor);

        TextView tv = (TextView) findViewById(R.id.pgmTitle);
        tv.setText(mProgramDescription.title);

        mTextInfoIsOn = true;

        if ( mDisplayMgrHandler == null ){
            mDisplayMgrHandler = new Handler();
            mClearDisplayRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!mProgramDescription.textDescriptionIsOn) {
                        clearChannelShortInfo();
                        mVirtualChannelNumber = mChannelNumber;
                    }
                }
            };
        }

        mDisplayMgrHandler.removeCallbacks(mClearDisplayRunnable);
        mDisplayMgrHandler.postDelayed( mClearDisplayRunnable, 3000);
    }

    private boolean initChannelsCursor(String tvInputId) {
        int nbChannel = 0;
        if (tvInputId == null) {
            Log.e(TAG, "tvInputId is null !");
            mTvChanCursor = null;
            return false;
        } else {
            Log.i(TAG, "tvInputId=" + tvInputId);
        }
        ContentResolver contentResolver = getContentResolver();
        mTvChanCursor = contentResolver.query(TvContract.buildChannelsUriForInput(tvInputId),
                null,
                null,
                null,
                "CAST(" + TvContract.Channels.COLUMN_DISPLAY_NUMBER + " AS int)"); // Sorted by number

        if (mTvChanCursor != null)
            nbChannel = mTvChanCursor.getCount();

        if (nbChannel == 0) {
            Log.e(TAG, "No Channel !");
            return false;
        }
        Log.i(TAG, "Nb Channels=" + nbChannel);

        mTvChanCursor.moveToFirst();
        return true;
    }

    private boolean setCurrentChannelFromDisplayNumber(int displayNumber) {
        boolean isNotEmpty = mTvChanCursor.moveToFirst();
        while ( isNotEmpty ) {
            String display_nber = mTvChanCursor.getString(mTvChanCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER));
            if (display_nber != null && display_nber.equals(Integer.toString(displayNumber))) {
                return true;
            }
            isNotEmpty = mTvChanCursor.moveToNext();
        }
        mTvChanCursor.moveToFirst();
        return false;
    }


    private void tuneToNextChannel(boolean virtualZap){
        if ( virtualZap )
            setCurrentChannelFromDisplayNumber(mVirtualChannelNumber);
        else
            setCurrentChannelFromDisplayNumber(mChannelNumber);
        boolean isNotEmpty = mTvChanCursor.moveToNext();
        if ( isNotEmpty ) {
            String display_nber = mTvChanCursor.getString(mTvChanCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER));
            try {
                tuneToChannelNumber(Integer.parseInt(display_nber), virtualZap);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Not a number : " + display_nber);
            }
        }
    }


    private void tuneToPreviousChannel(boolean virtualZap){
        if ( virtualZap )
            setCurrentChannelFromDisplayNumber(mVirtualChannelNumber);
        else
            setCurrentChannelFromDisplayNumber(mChannelNumber);
        boolean isNotEmpty = mTvChanCursor.moveToPrevious();
        if ( isNotEmpty ) {
            String display_nber = mTvChanCursor.getString(mTvChanCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NUMBER));
            try {
                tuneToChannelNumber(Integer.parseInt(display_nber), virtualZap);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Not a number : " + display_nber);
            }
        }
    }


    private Uri getCurrentChannelUri() {
        if (mTvChanCursor == null)
            return Uri.parse("content://android.media.tv/channel/1");
        long channelId = mTvChanCursor.getLong(mTvChanCursor.getColumnIndex(TvContract.Channels._ID));
        return TvContract.buildChannelUri(channelId);
    }

    private String getChannelName() {
        return mTvChanCursor.getString(mTvChanCursor.getColumnIndex(TvContract.Channels.COLUMN_DISPLAY_NAME));
    }

    private String getChannelDescription() {
        return mTvChanCursor.getString(mTvChanCursor.getColumnIndex(TvContract.Channels.COLUMN_DESCRIPTION));
    }

    private long getChannelId() {
        return mTvChanCursor.getLong(mTvChanCursor.getColumnIndex(TvContract.Channels._ID));
    }





    private long getProgramDescription() {
        long channelId = mTvChanCursor.getLong(mTvChanCursor.getColumnIndex(TvContract.Channels._ID));
        Log.d(TAG, "getProgramDescription(" + mProgramsUri + "  "+ channelId + ")");
        long programEndTime = 0;
        long programStartTime = 0;
        String title;
        String description;
        final Cursor programCursor;
        final String[] projection = {
                TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
                TvContract.Programs.COLUMN_TITLE,
                TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
        };
        final String[] selectionArgs ={ ""+channelId };
        programCursor = getContentResolver().query(
                mProgramsUri,
                projection,
                "channel_id=?",
                selectionArgs,
                null);

        if (programCursor != null) {
            boolean isNotEmpty = programCursor.moveToFirst();
            while (isNotEmpty){
                programStartTime = programCursor.getLong(programCursor.getColumnIndex(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS));
                programEndTime   = programCursor.getLong(programCursor.getColumnIndex(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS));
                title            = programCursor.getString(programCursor.getColumnIndex(TvContract.Programs.COLUMN_TITLE));
                description      = programCursor.getString(programCursor.getColumnIndex(TvContract.Programs.COLUMN_SHORT_DESCRIPTION));

                //DateTimeFormatter fOutput = DateTimeFormatter
                //TimeZone tz = TimeZone.;
                Calendar cal = Calendar.getInstance();
                TimeZone tz = cal.getTimeZone();

                Date now = new Date();
                if ( (now.getTime() >= programStartTime) && ( now.getTime() <  programEndTime) ) {
                    Log.i(TAG, "Start time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(programStartTime)));
                    //Log.i(TAG, "Curr  time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                    Log.i(TAG, "End   time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(programEndTime)));
                    //Log.i(TAG, "Time offset = " + tz.getRawOffset() );
                    Log.i(TAG, "COLUMN_TITLE=" + title);
                    Log.i(TAG, "COLUMN_SHORT_DESCRIPTION=" + description);
                    mProgramDescription.startTime = programStartTime;
                    mProgramDescription.endTime   = programEndTime;
                    mProgramDescription.title     = title;
                    mProgramDescription.description = description;
                }
                if ( (now.getTime() < programStartTime) ) {
                    Log.i(TAG, "Next program:");
                    Log.i(TAG, "Start time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(programStartTime)));
                    //Log.i(TAG, "Curr  time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()));
                    Log.i(TAG, "End   time = " + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(programEndTime)));
                    //Log.i(TAG, "Time offset = " + tz.getRawOffset() );
                    Log.i(TAG, "COLUMN_TITLE=" + mProgramDescription.title);
                    Log.i(TAG, "COLUMN_SHORT_DESCRIPTION=" + mProgramDescription.description);
                }
                isNotEmpty = programCursor.moveToNext();
            }
            programCursor.close();
        }else{
            mProgramDescription.startTime = 0;
            mProgramDescription.endTime   = 1;
            mProgramDescription.title     = "Pas d'info";
            mProgramDescription.description = "";
        }

        return programEndTime;
    }


    private class TvInputCallback extends TvInputManager.TvInputCallback {
        public TvInputCallback() {
            super();
            Log.d(TAG, "TvInputCallback=" + this);
        }

        @Override
        public void onInputStateChanged(String inputId, int state) {
            super.onInputStateChanged(inputId, state);
            Log.d(TAG, "onInputStateChanged=" + inputId + " state=" + state);
        }

        @Override
        public void onInputAdded(String inputId) {
            super.onInputAdded(inputId);
            Log.d(TAG, "onInputAdded=" + inputId);
        }

        @Override
        public void onInputRemoved(String inputId) {
            super.onInputRemoved(inputId);
            Log.d(TAG, "onInputRemoved=" + inputId);
        }

        @Override
        public void onInputUpdated(String inputId) {
            super.onInputUpdated(inputId);
            Log.d(TAG, "onInputUpdated=" + inputId);
        }

        @Override
        public void onTvInputInfoUpdated(TvInputInfo inputInfo) {
            super.onTvInputInfoUpdated(inputInfo);
            Log.d(TAG, "onTvInputInfoUpdated=" + inputInfo);
            inputInfo.getServiceInfo();
        }
    }


    private class TvViewTvInputCallback extends TvView.TvInputCallback {
        public TvViewTvInputCallback() {
            super();
            Log.i(TAG, "TvViewTvInputCallback");
        }

        @Override
        public void onConnectionFailed(String inputId) {
            super.onConnectionFailed(inputId);
            Log.d(TAG, "onConnectionFailed=" + inputId);
        }

        @Override
        public void onDisconnected(String inputId) {
            super.onDisconnected(inputId);
            Log.d(TAG, "onDisconnected=" + inputId);
        }

        @Override
        public void onChannelRetuned(String inputId, Uri channelUri) {
            super.onChannelRetuned(inputId, channelUri);
            Log.d(TAG, "onChannelRetuned=" + inputId);
        }

        @Override
        public void onTracksChanged(String inputId, List<TvTrackInfo> tracks) {
            super.onTracksChanged(inputId, tracks);
            Log.d(TAG, "onTracksChanged=" + inputId);

            if ( tracks != null ) {
                for (int i = 0; i < tracks.size(); i++) {
                    TvTrackInfo trackInfo = tracks.get(i);
                    if ( trackInfo.getType() == TvTrackInfo.TYPE_AUDIO ) {
                        Log.i(TAG, "Audio " + trackInfo.getLanguage());
                    }
                    if ( trackInfo.getType() == TvTrackInfo.TYPE_SUBTITLE ) {
                        Log.i(TAG, "Subtitle " + trackInfo.getLanguage());
                    }
                }
            }

        }

        @Override
        public void onTrackSelected(String inputId, int type, String trackId) {
            super.onTrackSelected(inputId, type, trackId);
            Log.d(TAG, "onTrackSelected=" + inputId);
        }

        @Override
        public void onVideoSizeChanged(String inputId, int width, int height) {
            super.onVideoSizeChanged(inputId, width, height);
            Log.d(TAG, "onVideoSizeChanged=" + inputId);
        }

        @Override
        public void onVideoAvailable(String inputId) {
            super.onVideoAvailable(inputId);
            Log.d(TAG, "onVideoAvailable=" + inputId);
        }

        @Override
        public void onVideoUnavailable(String inputId, int reason) {
            super.onVideoUnavailable(inputId, reason);
            Log.d(TAG, "onVideoUnavailable reason= " + reason);
            switch (reason) {
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING:
                    break;

                case TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING:
                    break;
                case TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN:
                    Log.d(TAG, "Channel " + getChannelName() + " Not allowed!");
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onContentAllowed(String inputId) {
            super.onContentAllowed(inputId);
            Log.d(TAG, "onContentAllowed=" + inputId);
        }

        @Override
        public void onContentBlocked(String inputId, TvContentRating rating) {
            super.onContentBlocked(inputId, rating);
            Log.d(TAG, "onContentBlocked=" + inputId);
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            super.onTimeShiftStatusChanged(inputId, status);
            //Log.d(TAG, "onTimeShiftStatusChanged=" + inputId);
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.v(TAG, "Key: " + KeyEvent.keyCodeToString(event.getKeyCode()));
        long pos;
        boolean ret=true;
        int KeyValue;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if ( mVirtualChannelNumber != mChannelNumber ){
                    tuneToChannelNumber(mVirtualChannelNumber);
                    break;
                }
                if ( mTextInfoIsOn && !mProgramDescription.textDescriptionIsOn ) {
                    displayProgramDescription();
                }else if ( mTextInfoIsOn && mProgramDescription.textDescriptionIsOn ) {
                    //clearChannelShortInfo();
                    clearProgramDescription();
                    displayChannelShortInfo(mVirtualChannelNumber);
                } else {
                    displayChannelShortInfo(mVirtualChannelNumber);
                }
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                if ( mProgramDescription.textDescriptionIsOn ) {
                    int height = mProgramDescription.tv.getHeight();
                    //mProgramDescription.tv.scrollBy(0, lineHeight);
                    mProgramDescription.xScrollingPos -= mProgramDescription.lineHeight;
                    if ( mProgramDescription.xScrollingPos < 0 )
                        mProgramDescription.xScrollingPos = 0;
                    mProgramDescription.tv.scrollTo(0, mProgramDescription.xScrollingPos);
                }
                if ( mTextInfoIsOn && !mProgramDescription.textDescriptionIsOn ) {
                    Log.v(TAG, "Virtual Zap +");
                    tuneToNextChannel(true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if ( mProgramDescription.textDescriptionIsOn ) {
                    int nbline = mProgramDescription.tv.getLineCount();
                    int height = mProgramDescription.tv.getHeight();
                    if ( (mProgramDescription.xScrollingPos + height) < (nbline * mProgramDescription.lineHeight) )
                        mProgramDescription.xScrollingPos += mProgramDescription.lineHeight;
                    mProgramDescription.tv.scrollTo(0, mProgramDescription.xScrollingPos);
                    Log.i(TAG, "nb line = " + nbline + " lineHeight = " + mProgramDescription.lineHeight + " height = " + height);
                }
                if ( mTextInfoIsOn && !mProgramDescription.textDescriptionIsOn ) {
                    Log.v(TAG, "Virtual Zap -");
                    tuneToPreviousChannel(true);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if ( mTextInfoIsOn && mProgramDescription.textDescriptionIsOn ) {
                    clearProgramDescription();
                    displayChannelShortInfo(mVirtualChannelNumber);
                }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if ( mTextInfoIsOn && mProgramDescription.textDescriptionIsOn ) {
                    clearProgramDescription();
                    displayChannelShortInfo(mVirtualChannelNumber);
                }
                break;
            case KeyEvent.KEYCODE_CHANNEL_UP:
                tuneToNextChannel(false);
                break;
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                tuneToPreviousChannel(false);
                break;
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                if ( mEnteringValue == -1 ){
                    mEnteringValue = keyCode - KeyEvent.KEYCODE_0;
                } else {
                    mEnteringValue = (mEnteringValue * 10) + (keyCode - KeyEvent.KEYCODE_0);
                }
                if ( mEnteringValue != -1 ) {
                    displayChannelNumber(mEnteringValue);
                }

                if ( mKeyPadHandler == null ) {
                    mKeyPadHandler = new Handler();
                    mKeyPadHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            /** Do something **/
                            Log.i(TAG, "Handler time out: Value = " + mEnteringValue);
                            if (mEnteringValue >= 0) {
                                tuneToChannelNumber(mEnteringValue);
                                mKeyPadHandler = null;
                            }
                            mEnteringValue = -1;
                        }
                    }, 1400);
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                tuneToChannelNumber(mPreviousChannelNumber);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                break;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                break;

            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                break;
            case KeyEvent.KEYCODE_MEDIA_STOP:

                displayProgramDescription();
                break;
        }
        return ret;
    }
}

