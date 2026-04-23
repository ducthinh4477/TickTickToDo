package hcmute.edu.vn.doinbot.agent.integration.providers;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import hcmute.edu.vn.doinbot.agent.integration.IntegrationProvider;
import hcmute.edu.vn.doinbot.agent.integration.model.ExternalEvent;

public class AndroidCalendarProvider implements IntegrationProvider {

    private final Context appContext;

    public AndroidCalendarProvider(Context context) {
        this.appContext = context.getApplicationContext();
    }

    @Override
    public String getProviderName() {
        return "DEVICE_CALENDAR";
    }

    @Override
    public List<ExternalEvent> getEvents(long fromMillis, long toMillis) {
        List<ExternalEvent> events = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return events;
        }

        String[] projection = new String[] {
                CalendarContract.Events._ID,
                CalendarContract.Events.TITLE,
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.DTEND,
                CalendarContract.Events.EVENT_LOCATION
        };

        String selection = CalendarContract.Events.DELETED + " = 0"
                + " AND " + CalendarContract.Events.DTSTART + " < ?"
                + " AND " + CalendarContract.Events.DTEND + " > ?";

        String[] selectionArgs = new String[] {
                String.valueOf(toMillis),
                String.valueOf(fromMillis)
        };

        Cursor cursor = null;
        try {
            cursor = appContext.getContentResolver().query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    CalendarContract.Events.DTSTART + " ASC"
            );

            if (cursor == null) {
                return events;
            }

            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String title = cursor.getString(1);
                long start = cursor.getLong(2);
                long end = cursor.getLong(3);
                String location = cursor.getString(4);

                if (end <= 0L) {
                    end = start + 30L * 60L * 1000L;
                }
                if (end <= start) {
                    continue;
                }

                events.add(new ExternalEvent(
                        "cal-" + id,
                        title,
                        start,
                        end,
                        getProviderName(),
                        true,
                        location
                ));
            }
        } catch (SecurityException ignored) {
            events.clear();
        } catch (Exception ignored) {
            events.clear();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return events;
    }
}