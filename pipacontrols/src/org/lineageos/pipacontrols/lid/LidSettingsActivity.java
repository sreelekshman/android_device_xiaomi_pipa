/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.lid;

import android.os.Bundle;
import android.util.Log;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Settings activity for Smart Cover / lid configuration.
 */
public class LidSettingsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG     = "XiaomiLidSettings";
    private static final String TAG_LID = "lid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Opening lid settings");

        getSupportFragmentManager().beginTransaction().replace(
                com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                new LidSettingsFragment(), TAG_LID).commit();
    }
}
