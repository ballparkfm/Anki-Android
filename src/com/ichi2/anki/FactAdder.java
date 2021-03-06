package com.ichi2.anki;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ichi2.anki.Fact.Field;

/**
 * Allows the user to add a fact.
 * 
 * A card is a presentation of a fact, and has two sides: a question and an answer.
 * Any number of fields can appear on each side.
 * When you add a fact to Anki, cards which show that fact are generated.
 * Some models generate one card, others generate more than one.
 * 
 * @see http://ichi2.net/anki/wiki/KeyTermsAndConcepts#Cards
 */
public class FactAdder extends Activity {

    /**
	 * Broadcast that informs us when the sd card is about to be unmounted
	 */
	private BroadcastReceiver mUnmountReceiver = null;
	
    private LinearLayout fieldsLayoutContainer;
    
    private Button mSave;
    private Button mCancel;
    
    private Card editorCard;
    
    LinkedList<FieldEditText> editFields;

    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerExternalStorageListener();
        
        setContentView(R.layout.card_editor);
        
        fieldsLayoutContainer = (LinearLayout) findViewById(R.id.CardEditorEditFieldsLayout);
        
        mSave = (Button) findViewById(R.id.CardEditorSaveButton);
        mCancel = (Button) findViewById(R.id.CardEditorCancelButton);

        editorCard = Reviewer.getEditorCard();

        // Card -> FactID -> FieldIDs -> FieldModels
        
        Fact cardFact = editorCard.getFact();
        TreeSet<Field> fields = cardFact.getFields();
        
        editFields = new LinkedList<FieldEditText>();
        
        Iterator<Field> iter = fields.iterator();
        while (iter.hasNext()) {
            FieldEditText newTextbox = new FieldEditText(this, iter.next());
            TextView label = newTextbox.getLabel();
            editFields.add(newTextbox);
            
            fieldsLayoutContainer.addView(label);
            fieldsLayoutContainer.addView(newTextbox);
            // Generate a new EditText for each field
            
        }

        mSave.setOnClickListener(new View.OnClickListener() 
        {

            public void onClick(View v) {
                
                Iterator<FieldEditText> iter = editFields.iterator();
                while (iter.hasNext())
                {
                    FieldEditText current = iter.next();
                    current.updateField();
                }
                setResult(RESULT_OK);
                finish();
            }
            
        });
        
        mCancel.setOnClickListener(new View.OnClickListener() 
        {
            
            public void onClick(View v) {
                setResult(RESULT_CANCELED);
                finish();
            }
            
        });
    }

    @Override
    public void onDestroy()
    {
    	super.onDestroy();
    	if(mUnmountReceiver != null)
    		unregisterReceiver(mUnmountReceiver);
    }
	
    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
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
    
    private class FieldEditText extends EditText
    {

        Field pairField;
        
        public FieldEditText(Context context, Field pairField) {
            super(context);
            this.pairField = pairField;
            this.setText(pairField.value);
            // TODO Auto-generated constructor stub
        }
        
        public TextView getLabel() 
        {
            TextView label = new TextView(this.getContext());
            label.setText(pairField.fieldModel.name);
            return label;
        }
        
        public void updateField()
        {
            pairField.value = this.getText().toString();
        }
    }
    
}
