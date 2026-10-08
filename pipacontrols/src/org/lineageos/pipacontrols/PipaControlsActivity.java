/*
 * Copyright (C) 2026 nullpointer1101
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols;

import android.os.Bundle;
import android.util.Log;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

/**
 * Entry point for Pipa Controls — device-specific settings hub.
 *
 * Registered under com.android.settings.category.ia.homepage
 */
public class PipaControlsActivity extends CollapsingToolbarBaseActivity {

    private static final String TAG      = "PipaControls";
    private static final String TAG_FRAG = "pipa_controls";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.i(TAG, "Opening Pipa Controls");

        getSupportFragmentManager()
                .beginTransaction()
                .replace(
                        com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                        new PipaControlsFragment(),
                        TAG_FRAG)
                .commit();
    }
}
