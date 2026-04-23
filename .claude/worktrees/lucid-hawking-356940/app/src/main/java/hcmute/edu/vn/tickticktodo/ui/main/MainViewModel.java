package hcmute.edu.vn.doinbot.ui.main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {

    private final MutableLiveData<Integer> selectedNavItem = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> currentMode = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> hideCompleted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> showDetails = new MutableLiveData<>(true);

    public LiveData<Integer> getSelectedNavItem() {
        return selectedNavItem;
    }

    public void setSelectedNavItem(int selectedId) {
        selectedNavItem.setValue(selectedId);
    }

    public LiveData<Integer> getCurrentMode() {
        return currentMode;
    }

    public int getCurrentModeValue() {
        Integer mode = currentMode.getValue();
        return mode == null ? 0 : mode;
    }

    public void setCurrentMode(int mode) {
        currentMode.setValue(mode);
    }

    public LiveData<Boolean> getHideCompleted() {
        return hideCompleted;
    }

    public boolean isHideCompleted() {
        Boolean value = hideCompleted.getValue();
        return value != null && value;
    }

    public void toggleHideCompleted() {
        hideCompleted.setValue(!isHideCompleted());
    }

    public LiveData<Boolean> getShowDetails() {
        return showDetails;
    }

    public boolean isShowDetails() {
        Boolean value = showDetails.getValue();
        return value == null || value;
    }

    public void toggleShowDetails() {
        showDetails.setValue(!isShowDetails());
    }
}
