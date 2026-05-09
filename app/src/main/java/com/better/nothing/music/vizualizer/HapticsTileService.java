package com.better.nothing.music.vizualizer;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class HapticsTileService extends TileService {
    @Override public void onStartListening() { super.onStartListening(); refresh(); }
    @Override public void onClick() {
        super.onClick();
        boolean running = AudioCaptureService.isRunning();
        
        if (!running) {
            // If viz is inactive, enable haptics in prefs and START the viz
            getSharedPreferences("viz_prefs", MODE_PRIVATE)
                    .edit().putBoolean("haptic_motor_enabled", true).apply();
            refresh();
            
            unlockAndRun(() -> {
                Intent i = new Intent(this, PermissionTrampolineActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        4,
                        i,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                startActivityAndCollapse(pendingIntent);
            });
        } else {
            // Service is running, toggle haptics via intent
            Intent intent = new Intent(this, AudioCaptureService.class);
            intent.setAction(AudioCaptureService.ACTION_TOGGLE_HAPTICS);
            startService(intent);
            refresh();
        }
    }
    
    private void refresh() {
        boolean vizRunning = AudioCaptureService.isRunning();
        boolean hapticsEnabled = AudioCaptureService.isHapticEnabledGlobal(this);
        
        Tile t = getQsTile(); if (t == null) return;
        
        // Active only if running AND enabled
        boolean isActive = vizRunning && hapticsEnabled;
        t.setState(isActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        
        t.setLabel("Haptic viz");
        if (!vizRunning) {
            t.setSubtitle(hapticsEnabled ? "Enabled" : "Disabled");
        } else {
            t.setSubtitle(hapticsEnabled ? "Active" : "Ready");
        }
        
        // Use the new vibration icon
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_vibration));
        t.updateTile();
    }
}
