package hcmute.edu.vn.tickticktodo.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import hcmute.edu.vn.tickticktodo.R;
import hcmute.edu.vn.tickticktodo.ui.VoicePromptActivity;

public class VoiceTaskTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setLabel(getString(R.string.qs_voice_tile_label));
            tile.setState(Tile.STATE_ACTIVE);
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();

        Intent openVoicePrompt = new Intent(this, VoicePromptActivity.class);
        openVoicePrompt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    1005,
                    openVoicePrompt,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(openVoicePrompt);
        }
    }
}
