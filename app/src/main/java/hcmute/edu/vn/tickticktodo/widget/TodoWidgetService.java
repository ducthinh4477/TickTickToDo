package hcmute.edu.vn.tickticktodo.widget;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class TodoWidgetService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new TodoWidgetFactory(getApplicationContext(), intent);
    }
}