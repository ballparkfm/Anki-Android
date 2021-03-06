
package com.ichi2.anki;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Environment;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.RemoteViews;

import com.tomgibara.android.veecheck.util.PrefSettings;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;

public class AnkiDroidWidget extends AppWidgetProvider {
    private static final String TAG = "AnkiDroidWidget";


    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate");

        context.startService(new Intent(context, UpdateService.class));
    }

    public static class UpdateService extends Service {
        private static final String TAG = "AnkiDroidWidgetUpdateService";

        // Simple class to hold the deck information for the widget
        public class DeckInformation {
            private String deckName;
            private int newCards;
            private int dueCards;
            private int failedCards;


            public String getDeckName() {
                return deckName;
            }


            public int getNewCards() {
                return newCards;
            }


            public int getDueCards() {
                return dueCards;
            }


            public int getFailedCards() {
                return failedCards;
            }


            public DeckInformation(String deckName, int newCards, int dueCards, int failedCards) {
                this.deckName = deckName;
                this.newCards = newCards; // Blue
                this.dueCards = dueCards; // Black
                this.failedCards = failedCards; // Red
            }


            @Override
            public String toString() {
                String deckName = getDeckName().length() > 13 ? getDeckName().substring(0, 13) : getDeckName();

                return String.format("%s %d %d", deckName, getNewCards(), getDueCards());
            }


            public CharSequence getDeckText() {
                String deckName = getDeckName().length() > 13 ? getDeckName().substring(0, 13) : getDeckName();

                SpannableStringBuilder sb = new SpannableStringBuilder();

                sb.append(deckName);
                sb.append(" ");

                SpannableString red = new SpannableString(Integer.toString(getFailedCards()));
                red.setSpan(new ForegroundColorSpan(Color.RED), 0, red.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString black = new SpannableString(Integer.toString(getDueCards()));
                black.setSpan(new ForegroundColorSpan(Color.BLACK), 0, black.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                SpannableString blue = new SpannableString(Integer.toString(getNewCards()));
                blue.setSpan(new ForegroundColorSpan(Color.BLUE), 0, blue.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                sb.append(red);
                sb.append(" ");
                sb.append(black);
                sb.append(" ");
                sb.append(blue);

                return sb;

                // return
                // String.format("%s  <font color='red'>%d</font> <font color='black'>%d</font> <font color='blue'>%d</font>",
                // deckName,
                // getFailedCards(),
                // getDueCards(),
                // getNewCards()
                // );
            }
        }


        @Override
        public void onStart(Intent intent, int startId) {
            Log.i(TAG, "OnStart");

            RemoteViews updateViews = buildUpdate(this);

            ComponentName thisWidget = new ComponentName(this, AnkiDroidWidget.class);
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            manager.updateAppWidget(thisWidget, updateViews);
        }


        private RemoteViews buildUpdate(Context context) {
            Log.i(TAG, "buildUpdate");

            // Resources res = context.getResources();
            RemoteViews updateViews = new RemoteViews(context.getPackageName(), R.layout.ankidroid_widget_view);
            Deck currentDeck = AnkiDroidApp.deck();

            boolean isMounted = !Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());

            if (isMounted) {
                updateViews.setTextViewText(R.id.anki_droid_text, context.getText(R.string.sdcard_missing_message));
                return updateViews;
            }

            if (currentDeck != null) { // Close the current deck, otherwise we'll have problems
                currentDeck.closeDeck();
            }

            // Fetch the deck information, sorted by due cards
            ArrayList<DeckInformation> decks = fetchDeckInformation();
            // ArrayList<DeckInformation> decks = mockFetchDeckInformation(); // TODO use real instead of mock

            if (currentDeck != null) {
                AnkiDroidApp.setDeck(currentDeck);
                Deck.openDeck(currentDeck.getDeckPath());
            }

            SpannableStringBuilder sb = new SpannableStringBuilder();

            int totalDue = 0;

            // If there are less than 3 decks display all, otherwise only the first 3
            for (int i = 0; i < decks.size() && i < 3; i++) {
                DeckInformation deck = decks.get(i);
                sb.append(deck.getDeckText());
                sb.append('\n');

                totalDue += deck.getDueCards();
            }

            if (sb.length() > 1) { // Get rid of the trailing \n
                sb.subSequence(0, sb.length() - 1);
            }

            updateViews.setTextViewText(R.id.anki_droid_text, sb);

            int minimumCardsDueForNotification = Integer.parseInt(PrefSettings.getSharedPrefs(context).getString(
                    "minimumCardsDueForNotification", "25"));

            if (totalDue >= minimumCardsDueForNotification) { // Raise a notification
                SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
                String ns = Context.NOTIFICATION_SERVICE;
                NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);

                int icon = R.drawable.anki;
                CharSequence tickerText = String.format(
                        getString(R.string.widget_minimum_cards_due_notification_ticker_text), totalDue);
                long when = System.currentTimeMillis();

                Notification notification = new Notification(icon, tickerText, when);

                if (preferences.getBoolean("widgetVibrate", false)) {
                    notification.defaults |= Notification.DEFAULT_VIBRATE;
                }
                if (preferences.getBoolean("widgetBlink", false)) {
                    notification.defaults |= Notification.DEFAULT_LIGHTS;
                }

                Context appContext = getApplicationContext();
                CharSequence contentTitle = getText(R.string.widget_minimum_cards_due_notification_ticker_title);
                String contentText = sb.toString();
                // XXX Martin: have to test
                Intent notificationIntent = new Intent(this, AnkiDroidApp.class);
                PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

                notification.setLatestEventInfo(appContext, contentTitle, contentText, contentIntent);

                final int WIDGET_NOTIFY_ID = 1;
                mNotificationManager.notify(WIDGET_NOTIFY_ID, notification);
            }

            return updateViews;
        }


        @SuppressWarnings("unused")
        private ArrayList<DeckInformation> mockFetchDeckInformation() {
            final int maxDecks = 10;
            ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(maxDecks);

            for (int i = 0; i < maxDecks; i++) {
                String deckName = String.format("my anki deck number %d", i);
                information.add(new DeckInformation(deckName, i * 20, i * 25, i * 5));
            }

            Collections.sort(information, new ByDueComparator());
            Collections.reverse(information);

            return information;
        }


        private ArrayList<DeckInformation> fetchDeckInformation() {
            Log.i(TAG, "fetchDeckInformation");

            SharedPreferences preferences = PrefSettings.getSharedPrefs(getBaseContext());
            String deckPath = preferences.getString("deckPath", "/sdcard");

            File dir = new File(deckPath);

            if (dir == null) {
                return new ArrayList<DeckInformation>();
            }

            File[] fileList = dir.listFiles(new AnkiFileFilter());

            if (fileList == null || fileList.length == 0) {
                return new ArrayList<DeckInformation>();
            }

            // For the deck information
            ArrayList<DeckInformation> information = new ArrayList<DeckInformation>(fileList.length);

            for (File file : fileList) {
                try { // Run through the decks and get the information
                    String absPath = file.getAbsolutePath();
                    String deckName = file.getName().replaceAll(".anki", "");

                    Deck deck = Deck.openDeck(absPath);
                    int dueCards = deck.failedSoonCount + deck.revCount;
                    int newCards = deck.newCountToday;
                    int failedCards = deck.failedSoonCount;
                    deck.closeDeck();

                    // Add the information about the deck
                    information.add(new DeckInformation(deckName, newCards, dueCards, failedCards));
                } catch (Exception e) {
                    Log.i(TAG, "Could not open deck");
                    Log.e(TAG, e.toString());
                }
            }

            if (!information.isEmpty() && information.size() > 1) { // Sort and reverse the list if there are decks
                Log.i(TAG, "Sorting deck");

                Collections.sort(information, new ByDueComparator());
                Collections.reverse(information);
            }

            return information;
        }

        // Sorter for the decks based on number due
        public class ByDueComparator implements java.util.Comparator<DeckInformation> {
            @Override
            public int compare(DeckInformation deck1, DeckInformation deck2) {

                if (deck2.dueCards == deck2.dueCards) {
                    return 0;
                }

                if (deck1.dueCards > deck2.dueCards) {
                    return 1;
                }

                return -1;
            }
        }

        private static final class AnkiFileFilter implements FileFilter {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile() && pathname.getName().endsWith(".anki");
            }
        }


        @Override
        public IBinder onBind(Intent arg0) {
            Log.i(TAG, "onBind");
            // TODO Auto-generated method stub
            return null;
        }
    }
}
