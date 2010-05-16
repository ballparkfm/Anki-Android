package com.ichi2.anki;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.ichi2.utils.DiffEngine;
import com.tomgibara.android.veecheck.util.PrefSettings;

public class Reviewer extends Activity {
	
	/**
	 * Tag for logging messages
	 */
	private static final String TAG = "Ankidroid";
	
	/**
	 * Result codes that are returned when this activity finishes.
	 */
	public static final int RESULT_SESSION_COMPLETED = 1;
	public static final int RESULT_NO_MORE_CARDS = 2;
	
	public static final int EDIT_CURRENT_CARD = 2;
	
	/**
	 * Menus
	 */
	private static final int MENU_SUSPEND = 0;
	private static final int MENU_EDIT = 1;
	
	/**
	 * Max and min size of the font of the questions and answers
	 */
	private static final int MAX_FONT_SIZE = 14;
	private static final int MIN_FONT_SIZE = 3;
	
	/**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;

	/**
	 * Variables to hold preferences
	 */
	private boolean prefCorporalPunishments;
	private boolean prefTimer;
	private boolean prefWhiteboard;
	private boolean prefWriteAnswers;
	private String prefDeckFilename;
	
	public String cardTemplate;
	
	/**
	 * Variables to hold layout objects that we need to update or handle events for
	 */
	private WebView mCard;
	private ToggleButton mToggleWhiteboard, mFlipCard;
	private EditText mAnswerField;
	private Button mEase0, mEase1, mEase2, mEase3;
	private Chronometer mCardTimer;
	private Whiteboard mWhiteboard;
	private ProgressDialog mProgressDialog;
	
	private Card mCurrentCard;
	private static Card editorCard; // To be assigned as the currentCard or a new card to be sent to and from the editor
	private int mCurrentEase;
	private long mSessionTimeLimit;
	private int mSessionCurrReps;

	// Handler for the flip toogle button, between the question and the answer
	// of a card
	private CompoundButton.OnCheckedChangeListener mFlipCardHandler = new CompoundButton.OnCheckedChangeListener()
	{
		//@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean showAnswer)
		{
			Log.i(TAG, "Flip card changed:");
			if (showAnswer)
				displayCardAnswer();
			else
				displayCardQuestion();
		}
	};
	
	// Handler for the Whiteboard toggle button.
	CompoundButton.OnCheckedChangeListener mToggleOverlayHandler = new CompoundButton.OnCheckedChangeListener()
	{
		public void onCheckedChanged(CompoundButton btn, boolean state)
		{
			
			setOverlayState(state);
			if(!state)
			{
				mWhiteboard.clear();
			}
		}
	};
	
	private View.OnClickListener mSelectEaseHandler = new View.OnClickListener()
	{
		public void onClick(View view)
		{
			switch (view.getId())
			{
			case R.id.ease1:
				mCurrentEase = 1;
				if (prefCorporalPunishments)
				{
					Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
					v.vibrate(500);
				}
				break;
			case R.id.ease2:
				mCurrentEase = 2;
				break;
			case R.id.ease3:
				mCurrentEase = 3;
				break;
			case R.id.ease4:
				mCurrentEase = 4;
				break;
			default:
				mCurrentEase = 0;
				return;
			}
			
			Reviewer.this.mSessionCurrReps++; // increment number reps counter
			DeckTask.launchDeckTask(
					DeckTask.TASK_TYPE_ANSWER_CARD,
					mAnswerCardHandler,
					new DeckTask.TaskData(mCurrentEase, AnkiDroidApp.deck(), mCurrentCard));
		}
	};

	DeckTask.TaskListener mUpdateCardHandler = new DeckTask.TaskListener()
    {
        public void onPreExecute() {
            mProgressDialog = ProgressDialog.show(Reviewer.this, "", "Saving changes...", true);
        }

        public void onPostExecute(DeckTask.TaskData result) {

            // Set the correct value for the flip card button - That triggers the
            // listener which displays the question of the card
            mFlipCard.setChecked(false);
            mWhiteboard.clear();
            mCardTimer.setBase(SystemClock.elapsedRealtime());
            mCardTimer.start();

            mProgressDialog.dismiss();
        }

        public void onProgressUpdate(DeckTask.TaskData... values) 
        {
            mCurrentCard = values[0].getCard();
        }
    };
	
	private DeckTask.TaskListener mAnswerCardHandler = new DeckTask.TaskListener()
	{
	    boolean sessioncomplete;
	    boolean nomorecards;

		public void onPreExecute() {
			Reviewer.this.setProgressBarIndeterminateVisibility(true);
			//disableControls();
			blockControls();
		}

		public void onPostExecute(DeckTask.TaskData result) {
		    // Check for no more cards before session complete. If they are both true,
			// no more cards will take precedence when returning to study options.
			if (nomorecards)
			{
				Reviewer.this.setResult(RESULT_NO_MORE_CARDS);
				Reviewer.this.finish();
			} else if (sessioncomplete)
			{
			    Reviewer.this.setResult(RESULT_SESSION_COMPLETED);
			    Reviewer.this.finish();
			}
		}

		public void onProgressUpdate(DeckTask.TaskData... values) {
			sessioncomplete = false;
			nomorecards = false;

		    // Check to see if session rep or time limit has been reached
		    Deck deck = AnkiDroidApp.deck();
		    long sessionRepLimit = deck.getSessionRepLimit();
		    long sessionTime = deck.getSessionTimeLimit();
		    Toast sessionMessage = null;

		    if( (sessionRepLimit > 0) && (Reviewer.this.mSessionCurrReps >= sessionRepLimit) )
		    {
		    	sessioncomplete = true;
		    	sessionMessage = Toast.makeText(Reviewer.this, "Session question limit reached", Toast.LENGTH_SHORT);
		    } else if( (sessionTime > 0) && (System.currentTimeMillis() >= Reviewer.this.mSessionTimeLimit) ) //Check to see if the session time limit has been reached
		    {
		        // session time limit reached, flag for halt once async task has completed.
		        sessioncomplete = true;
		        sessionMessage = Toast.makeText(Reviewer.this, "Session time limit reached", Toast.LENGTH_SHORT);

		    } else {
		        // session limits not reached, show next card
		        Card newCard = values[0].getCard();

		        // If the card is null means that there are no more cards scheduled for review.
		        if (newCard == null)
		        {
		        	nomorecards = true;
		        	return;
		        }
		        
		        // Start reviewing next card
		        Reviewer.this.mCurrentCard = newCard;
		        Reviewer.this.setProgressBarIndeterminateVisibility(false);
		        //Reviewer.this.enableControls();
				Reviewer.this.unblockControls();
		        Reviewer.this.reviewNextCard();
		    }

			// Show a message to user if a session limit has been reached.
			if (sessionMessage != null)
				sessionMessage.show();
		}

	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Log.i(TAG, "Reviewer - onCreate");
		
		// Make sure a deck is loaded before continuing.
		if (AnkiDroidApp.deck() == null)
		{
			setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
			finish();
		}
		
		// Remove the status bar and make title bar progress available
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		registerExternalStorageListener();
		
		restorePreferences();
		initLayout(R.layout.flashcard_portrait);
		updateTitle();
		cardTemplate = getResources().getString(R.string.card_template);
		
		// Initialize session limits
		long timelimit = AnkiDroidApp.deck().getSessionTimeLimit() * 1000;
		Log.i(TAG, "SessionTimeLimit: " + timelimit + " ms.");
		mSessionTimeLimit = System.currentTimeMillis() + timelimit;
		mSessionCurrReps = 0;
		
		/* Load the first card and start reviewing.
		 * Uses the answer card task to load a card, but since we send null
		 * as the card to answer, no card will be answered.
		 */
		DeckTask.launchDeckTask(
				DeckTask.TASK_TYPE_ANSWER_CARD, 
				mAnswerCardHandler, 
				new DeckTask.TaskData(
						0,
						AnkiDroidApp.deck(),
						null));
	}
	
    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	Log.i(TAG, "Reviewer - onDestroy()");
    	if(mUnmountReceiver != null)
    		unregisterReceiver(mUnmountReceiver);
    }
    
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
	  super.onConfigurationChanged(newConfig);

	  Log.i(TAG, "onConfigurationChanged");

	  LinearLayout sdLayout = (LinearLayout) findViewById(R.id.sd_layout);
	  if(newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
		  sdLayout.setPadding(0, 50, 0, 0);
	  else if(newConfig.orientation == Configuration.ORIENTATION_PORTRAIT)
		  sdLayout.setPadding(0, 100, 0, 0);

	  mWhiteboard.rotate();
	  
	}
	
    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    	Log.i(TAG, "mUnmountReceiver - Action = Media Eject");
                    	finishNoStorageAvailable();
                    } 
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    private void finishNoStorageAvailable()
    {
    	setResult(StudyOptions.CONTENT_NO_EXTERNAL_STORAGE);
		finish();
    }
    
	// Set the content view to the one provided and initialize accessors.
	private void initLayout(Integer layout)
	{
		setContentView(layout);

		mCard = (WebView) findViewById(R.id.flashcard);
		mEase0 = (Button) findViewById(R.id.ease1);
		mEase1 = (Button) findViewById(R.id.ease2);
		mEase2 = (Button) findViewById(R.id.ease3);
		mEase3 = (Button) findViewById(R.id.ease4);
		mCardTimer = (Chronometer) findViewById(R.id.card_time);
		mFlipCard = (ToggleButton) findViewById(R.id.flip_card);
		mToggleWhiteboard = (ToggleButton) findViewById(R.id.toggle_overlay);
		mWhiteboard = (Whiteboard) findViewById(R.id.whiteboard);
		mAnswerField = (EditText) findViewById(R.id.answer_field);

		hideControls();

		mEase0.setOnClickListener(mSelectEaseHandler);
		mEase1.setOnClickListener(mSelectEaseHandler);
		mEase2.setOnClickListener(mSelectEaseHandler);
		mEase3.setOnClickListener(mSelectEaseHandler);
		mFlipCard.setChecked(true); // Fix for mFlipCardHandler not being called on first deck load.
		mFlipCard.setOnCheckedChangeListener(mFlipCardHandler);
		mToggleWhiteboard.setOnCheckedChangeListener(mToggleOverlayHandler);

		mCard.setFocusable(false);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuItem item;
		item = menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.menu_edit_card);
		item.setIcon(android.R.drawable.ic_menu_edit);
		item = menu.add(Menu.NONE, MENU_SUSPEND, Menu.NONE, R.string.menu_suspend_card);
		item.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}
	
	/** Handles item selections */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case MENU_EDIT:
			editorCard = mCurrentCard;
			Intent editCard = new Intent(this, CardEditor.class);
			startActivityForResult(editCard, EDIT_CURRENT_CARD);
			return true;
		case MENU_SUSPEND:
			mFlipCard.setChecked(true);
			DeckTask.launchDeckTask(DeckTask.TASK_TYPE_SUSPEND_CARD, 
					mAnswerCardHandler,
					new DeckTask.TaskData(0, AnkiDroidApp.deck(), mCurrentCard));
			return true;
		}
		return false;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == EDIT_CURRENT_CARD)
        {
			if(resultCode == RESULT_OK)
			{
				Log.i(TAG, "Saving card...");
				DeckTask.launchDeckTask(
                        DeckTask.TASK_TYPE_UPDATE_FACT,
                        mUpdateCardHandler,
                        new DeckTask.TaskData(0, AnkiDroidApp.deck(), mCurrentCard));
				//TODO: code to save the changes made to the current card.
	            mFlipCard.setChecked(true);
	            displayCardQuestion();
			}
			else if(resultCode == StudyOptions.CONTENT_NO_EXTERNAL_STORAGE)
			{
				finishNoStorageAvailable();
			}
		}
	}
	
	public static Card getEditorCard () {
        return editorCard;
    }

	private void showControls()
	{
		mCard.setVisibility(View.VISIBLE);
		mEase0.setVisibility(View.VISIBLE);
		mEase1.setVisibility(View.VISIBLE);
		mEase2.setVisibility(View.VISIBLE);
		mEase3.setVisibility(View.VISIBLE);
		mFlipCard.setVisibility(View.VISIBLE);
		
		if (!prefTimer)
		{
			mCardTimer.setVisibility(View.GONE);
		} else
		{
			mCardTimer.setVisibility(View.VISIBLE);
		}
		if (!prefWhiteboard)
		{
			mToggleWhiteboard.setVisibility(View.GONE);
			mWhiteboard.setVisibility(View.GONE);
		} else
		{
			mToggleWhiteboard.setVisibility(View.VISIBLE);
			if (mToggleWhiteboard.isChecked())
			{
				mWhiteboard.setVisibility(View.VISIBLE);
			}
		}
		
		if (!prefWriteAnswers)
		{
			mAnswerField.setVisibility(View.GONE);
		} else
		{
			mAnswerField.setVisibility(View.VISIBLE);
		}
	}
	
	public void setOverlayState(boolean enabled)
	{
		mWhiteboard.setVisibility((enabled) ? View.VISIBLE : View.GONE);
	}
	
	private void hideControls()
	{
		mCard.setVisibility(View.GONE);
		mEase0.setVisibility(View.GONE);
		mEase1.setVisibility(View.GONE);
		mEase2.setVisibility(View.GONE);
		mEase3.setVisibility(View.GONE);
		mFlipCard.setVisibility(View.GONE);
		mCardTimer.setVisibility(View.GONE);
		mToggleWhiteboard.setVisibility(View.GONE);
		mWhiteboard.setVisibility(View.GONE);
		mAnswerField.setVisibility(View.GONE);
	}
	
	/* COMMENT: Using unblockControls() and blockControls() instead (06-05-2010)
	private void enableControls()
	{
		mCard.setEnabled(true);
		mEase0.setEnabled(true);
		mEase1.setEnabled(true);
		mEase2.setEnabled(true);
		mEase3.setEnabled(true);
		mFlipCard.setEnabled(true);
		mCardTimer.setEnabled(true);
		mToggleWhiteboard.setEnabled(true);
		mWhiteboard.setEnabled(true);
		mAnswerField.setEnabled(true);
	}
	
	private void disableControls()
	{
		mCard.setEnabled(false);
		mEase0.setEnabled(false);
		mEase1.setEnabled(false);
		mEase2.setEnabled(false);
		mEase3.setEnabled(false);
		mFlipCard.setEnabled(false);
		mCardTimer.setEnabled(false);
		mToggleWhiteboard.setEnabled(false);
		mWhiteboard.setEnabled(false);
		mAnswerField.setEnabled(false);
	}*/
	
	private void unblockControls()
	{
		mCard.setEnabled(true);
		switch(mCurrentEase)
		{
			case 1:
				mCard.setEnabled(true);
				mEase0.setClickable(true);
				mEase1.setEnabled(true);
				mEase2.setEnabled(true);
				mEase3.setEnabled(true);
				break;
				
			case 2:
				mCard.setEnabled(true);
				mEase0.setEnabled(true);
				mEase1.setClickable(true);
				mEase2.setEnabled(true);
				mEase3.setEnabled(true);
				break;
				
			case 3:
				mCard.setEnabled(true);
				mEase0.setEnabled(true);
				mEase1.setEnabled(true);
				mEase2.setClickable(true);
				mEase3.setEnabled(true);
				break;
				
			case 4:
				mCard.setEnabled(true);
				mEase0.setEnabled(true);
				mEase1.setEnabled(true);
				mEase2.setEnabled(true);
				mEase3.setClickable(true);
				break;
				
			default:
				mCard.setEnabled(true);
				mEase0.setEnabled(true);
				mEase1.setEnabled(true);
				mEase2.setEnabled(true);
				mEase3.setEnabled(true);
				break;
		}
		mFlipCard.setEnabled(true);
		mCardTimer.setEnabled(true);
		mToggleWhiteboard.setEnabled(true);
		mWhiteboard.setEnabled(true);
		mAnswerField.setEnabled(true);
	}
	
	private void blockControls()
	{
		mCard.setEnabled(false);
		switch(mCurrentEase)
		{
			case 1:
				mCard.setEnabled(false);
				mEase0.setClickable(false);
				mEase1.setEnabled(false);
				mEase2.setEnabled(false);
				mEase3.setEnabled(false);
				break;
				
			case 2:
				mCard.setEnabled(false);
				mEase0.setEnabled(false);
				mEase1.setClickable(false);
				mEase2.setEnabled(false);
				mEase3.setEnabled(false);
				break;
				
			case 3:
				mCard.setEnabled(false);
				mEase0.setEnabled(false);
				mEase1.setEnabled(false);
				mEase2.setClickable(false);
				mEase3.setEnabled(false);
				break;
				
			case 4:
				mCard.setEnabled(false);
				mEase0.setEnabled(false);
				mEase1.setEnabled(false);
				mEase2.setEnabled(false);
				mEase3.setClickable(false);
				break;
				
			default:
				mCard.setEnabled(false);
				mEase0.setEnabled(false);
				mEase1.setEnabled(false);
				mEase2.setEnabled(false);
				mEase3.setEnabled(false);
				break;
		}
		mFlipCard.setEnabled(false);
		mCardTimer.setEnabled(false);
		mToggleWhiteboard.setEnabled(false);
		mWhiteboard.setEnabled(false);
		mAnswerField.setEnabled(false);
	}
	
	private SharedPreferences restorePreferences()
	{
		SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
		prefCorporalPunishments = preferences.getBoolean("corporalPunishments", false);
		prefTimer = preferences.getBoolean("timer", true);
		prefWhiteboard = preferences.getBoolean("whiteboard", true);
		prefWriteAnswers = preferences.getBoolean("writeAnswers", false);
		prefDeckFilename = preferences.getString("deckFilename", "");

		return preferences;
	}
	
	private void updateCard(String content)
	{
		Log.i(TAG, "updateCard");

		content = Sound.extractSounds(prefDeckFilename, content);
		content = Image.loadImages(prefDeckFilename, content);
		
		// We want to modify the font size depending on how long is the content
		// Replace each <br> with 15 spaces, then remove all html tags and spaces
		String realContent = content.replaceAll("\\<br.*?\\>", "               ");
		realContent = realContent.replaceAll("\\<.*?\\>", "");
		realContent = realContent.replaceAll("&nbsp;", " ");

		// Calculate the size of the font depending on the length of the content
		int size = Math.max(MIN_FONT_SIZE, MAX_FONT_SIZE - (int)(realContent.length()/5));
		mCard.getSettings().setDefaultFontSize(size);

		//In order to display the bold style correctly, we have to change font-weight to 700
		content = content.replaceAll("font-weight:600;", "font-weight:700;");

		Log.i(TAG, "content card = \n" + content);
		String card = cardTemplate.replace("::content::", content);
		mCard.loadDataWithBaseURL("", card, "text/html", "utf-8", null);
		Sound.playSounds();
	}
	
	private void reviewNextCard()
	{
		updateTitle();
		mFlipCard.setChecked(false);
		
		mWhiteboard.clear();
		mCardTimer.setBase(SystemClock.elapsedRealtime());
		mCardTimer.start();
	}
	
	private void displayCardQuestion()
	{
		updateCard(mCurrentCard.question);
		
		showControls();
		//mFlipCard.setChecked(false);
		
		mEase0.setVisibility(View.INVISIBLE);
		mEase1.setVisibility(View.INVISIBLE);
		mEase2.setVisibility(View.INVISIBLE);
		mEase3.setVisibility(View.INVISIBLE);

		// If the user wants to write the answer
		if(prefWriteAnswers)
			mAnswerField.setVisibility(View.VISIBLE);
		
		mFlipCard.requestFocus();
	}
	
	private void displayCardAnswer()
	{
		Log.i(TAG, "displayCardAnswer");
		
		mCardTimer.stop();

		mEase0.setVisibility(View.VISIBLE);
		mEase1.setVisibility(View.VISIBLE);
		mEase2.setVisibility(View.VISIBLE);
		mEase3.setVisibility(View.VISIBLE);

		mAnswerField.setVisibility(View.GONE);

		mEase2.requestFocus();

		// If the user wrote an answer
		if(prefWriteAnswers)
		{
			if(mCurrentCard != null)
			{
				// Obtain the user answer and the correct answer
				String userAnswer = mAnswerField.getText().toString();
				String correctAnswer = (String) mCurrentCard.answer.subSequence(
						mCurrentCard.answer.indexOf(">")+1,
						mCurrentCard.answer.lastIndexOf("<"));

				// Obtain the diff and send it to updateCard
				DiffEngine diff = new DiffEngine();
				updateCard(diff.diff_prettyHtml(
						diff.diff_main(userAnswer, correctAnswer)) +
						"<br/>" + mCurrentCard.answer);
			}
			else
			{
				updateCard("");
			}
		}
		else
		{
			updateCard(mCurrentCard.answer);
		}
	}
	
	private void updateTitle()
	{
		Deck deck = AnkiDroidApp.deck();
		String unformattedTitle = getResources().getString(R.string.studyoptions_window_title);
		setTitle(String.format(unformattedTitle, deck.deckName, deck.revCount + deck.failedSoonCount, deck.cardCount));
	}
}