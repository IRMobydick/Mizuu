/*
 * Copyright (C) 2014 Michell Bak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miz.service;

import java.util.ArrayList;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.miz.functions.MizLib;
import com.miz.functions.TvShowLibraryUpdateCallback;
import com.miz.identification.ShowStructure;
import com.miz.identification.TvShowIdentification;
import com.miz.mizuu.R;
import com.miz.utils.LocalBroadcastUtils;
import com.miz.utils.TvShowDatabaseUtils;
import com.miz.utils.WidgetUtils;

public class IdentifyTvShowEpisodeService extends IntentService implements TvShowLibraryUpdateCallback {

	private boolean mDebugging = true;
	private String mNewShowId, mOldShowId, mLanguage;
	private ArrayList<ShowStructure> mFiles = new ArrayList<ShowStructure>();
	private ArrayList<String> mFilepaths = new ArrayList<String>();
	private final int NOTIFICATION_ID = 4150;
	private int mEpisodeCount, mTotalFiles;
	private NotificationManager mNotificationManager;
	private NotificationCompat.Builder mBuilder;

	public IdentifyTvShowEpisodeService() {
		super("IdentifyTvShowEpisodeService");
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		log("onDestroy()");

		if (mNotificationManager == null)
			mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		mNotificationManager.cancel(NOTIFICATION_ID);

		LocalBroadcastUtils.updateTvShowLibrary(this);

		WidgetUtils.updateTvShowWidgets(this);
	}

	@Override
	protected void onHandleIntent(Intent intent) {

		if (MizLib.isTvShowLibraryBeingUpdated(this)) {
			Handler mHandler = new Handler(Looper.getMainLooper());
			mHandler.post(new Runnable() {            
				@Override
				public void run() {
					Toast.makeText(IdentifyTvShowEpisodeService.this, R.string.cant_identify_while_updating, Toast.LENGTH_LONG).show();                
				}
			});

			return;
		}

		log("clear()");
		clear();

		log("setup()");
		setup();

		log("Intent extras");
		Bundle b = intent.getExtras();
		mNewShowId = b.getString("newShowId");
		mOldShowId = b.getString("oldShowId");
		mLanguage = b.getString("language", "all");
		mFilepaths = b.getStringArrayList("filepaths");

		if (mFilepaths != null) {
			log("setupList()");
			setupList();

			log("removeOldDatabaseEntries()");
			removeOldDatabaseEntries();

			log("start()");
			start();
		}
	}

	private void setupList() {	
		for (String path : mFilepaths) {
			mFiles.add(new ShowStructure(path));
		}

		// Count all episodes
		for (ShowStructure ss : mFiles) {
			mTotalFiles += ss.getEpisodes().size();
		}
	}

	private void removeOldDatabaseEntries() {
		// Remove all old database mappings
		for (String path : mFilepaths) {
			TvShowDatabaseUtils.removeEpisode(this, mOldShowId, path);
		}
		
		LocalBroadcastUtils.updateTvShowLibrary(this);
	}

	private void start() {
		TvShowIdentification identification = new TvShowIdentification(this, this, mFiles);
		identification.setShowId(mNewShowId);
		identification.setLanguage(mLanguage);
		identification.start();
	}

	/**
	 * Clear all instance variables
	 * since this is an {@link IntentService}.
	 */
	private void clear() {
		mNewShowId = "";
		mOldShowId = "";
		mLanguage = "";
		mFiles = new ArrayList<ShowStructure>();
	}

	private void setup() {
		// Setup up notification
		mBuilder = new NotificationCompat.Builder(getApplicationContext());
		mBuilder.setSmallIcon(R.drawable.refresh);
		mBuilder.setTicker(getString(R.string.identifying_episodes));
		mBuilder.setContentTitle(getString(R.string.identifying_episodes));
		mBuilder.setContentText(getString(R.string.gettingReady));
		mBuilder.setOngoing(true);
		mBuilder.setOnlyAlertOnce(true);
		mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.refresh));

		// Build notification
		Notification updateNotification = mBuilder.build();

		// Show the notification
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(NOTIFICATION_ID, updateNotification);

		// Tell the system that this is an ongoing notification, so it shouldn't be killed
		startForeground(NOTIFICATION_ID, updateNotification);
	}

	@Override
	public void onTvShowAdded(String showId, String title, Bitmap cover, Bitmap backdrop, int count) {
		// We're done!
	}

	@Override
	public void onEpisodeAdded(String showId, String title, Bitmap cover, Bitmap photo) {
		mEpisodeCount++;
		updateEpisodeAddedNotification(showId, title, cover, photo);
	}

	private void updateEpisodeAddedNotification(String showId, String title, Bitmap cover, Bitmap backdrop) {
		String contentText = getString(R.string.stringJustAdded) + ": " + title;

		mBuilder.setLargeIcon(cover);
		mBuilder.setContentTitle(getString(R.string.identifying_episodes) + " (" + (int) ((100.0 / (double) mTotalFiles) * (double) mEpisodeCount) + "%)");
		mBuilder.setContentText(contentText);
		mBuilder.setStyle(
				new NotificationCompat.BigPictureStyle()
				.setSummaryText(contentText)
				.bigPicture(backdrop)
				);

		// Show the updated notification
		mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
	}

	private void log(String msg) {
		if (mDebugging)
			Log.d("IdentifyTvShowEpisodeService", msg);
	}
}