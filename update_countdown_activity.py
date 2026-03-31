import os

path = 'app/src/main/java/hcmute/edu/vn/tickticktodo/ui/EventCountdownActivity.java'
with open(path, 'r', encoding='utf-8') as f:
    text = f.read()

new_imports = """import androidx.lifecycle.ViewModelProvider;
import hcmute.edu.vn.tickticktodo.viewmodel.CountdownEventViewModel;
import androidx.recyclerview.widget.ItemTouchHelper;"""

if "import androidx.lifecycle.ViewModelProvider;" not in text:
    text = text.replace('import androidx.recyclerview.widget.RecyclerView;', 'import androidx.recyclerview.widget.RecyclerView;\n' + new_imports)

# Add ViewModel field
text = text.replace('private List<CountdownEvent> eventList;', 'private List<CountdownEvent> eventList;\n    private CountdownEventViewModel viewModel;')

# Init ViewModel 
init_views_old = """        rvEvents.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        fabAdd.setOnClickListener(v -> showAddEventDialog());
    }"""
init_views_new = """        rvEvents.setAdapter(adapter);

        viewModel = new ViewModelProvider(this).get(CountdownEventViewModel.class);
        viewModel.getAllEvents().observe(this, events -> {
            // refresh calculation for each event before displaying
            for (CountdownEvent e : events) {
                e.calculateDays();
            }
            adapter.setEvents(events);
            if (events != null && events.isEmpty()) {
                loadData(); // Load defaults if empty
            }
        });

        setupSwipeToDelete();

        btnBack.setOnClickListener(v -> finish());
        fabAdd.setOnClickListener(v -> showAddEventDialog());
    }

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                CountdownEvent eventToDelete = adapter.getEvents().get(position);
                viewModel.delete(eventToDelete);
                Toast.makeText(EventCountdownActivity.this, "Đã xóa sự kiện", Toast.LENGTH_SHORT).show();
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(rvEvents);
    }"""
text = text.replace(init_views_old, init_views_new)

# Modify loadData
load_data_old = """    private void loadData() {
        // Load default holidays and weekends
        eventList.clear();

        // 1. Weekend
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysUntilSaturday = (Calendar.SATURDAY - dayOfWeek + 7) % 7;
        if (daysUntilSaturday == 0) daysUntilSaturday = 7; // if today is Saturday, next weekend
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSaturday);
        eventList.add(new CountdownEvent("Cuối tuần", cal.getTimeInMillis()));

        // 2. New Year (Tết Dương lịch)
        Calendar nyCal = Calendar.getInstance();
        nyCal.set(Calendar.MONTH, Calendar.JANUARY);
        nyCal.set(Calendar.DAY_OF_MONTH, 1);
        if (nyCal.getTimeInMillis() < System.currentTimeMillis()) {
            nyCal.add(Calendar.YEAR, 1);
        }
        eventList.add(new CountdownEvent("Tết Dương lịch", nyCal.getTimeInMillis()));

        // 3. TickTick Usage (Past) - 21 days ago as an example
        Calendar pastCal = Calendar.getInstance();
        pastCal.add(Calendar.DAY_OF_YEAR, -21);
        eventList.add(new CountdownEvent("Sử dụng TickTick", pastCal.getTimeInMillis()));

        adapter.setEvents(eventList);
    }"""
load_data_new = """    private void loadData() {
        // Load default holidays and weekends
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        int daysUntilSaturday = (Calendar.SATURDAY - dayOfWeek + 7) % 7;
        if (daysUntilSaturday == 0) daysUntilSaturday = 7; 
        cal.add(Calendar.DAY_OF_YEAR, daysUntilSaturday);
        viewModel.insert(new CountdownEvent("Cuối tuần", cal.getTimeInMillis()));

        Calendar nyCal = Calendar.getInstance();
        nyCal.set(Calendar.MONTH, Calendar.JANUARY);
        nyCal.set(Calendar.DAY_OF_MONTH, 1);
        if (nyCal.getTimeInMillis() < System.currentTimeMillis()) {
            nyCal.add(Calendar.YEAR, 1);
        }
        viewModel.insert(new CountdownEvent("Tết Dương lịch", nyCal.getTimeInMillis()));

        Calendar pastCal = Calendar.getInstance();
        pastCal.add(Calendar.DAY_OF_YEAR, -21);
        viewModel.insert(new CountdownEvent("Sử dụng TickTick", pastCal.getTimeInMillis()));
    }"""
text = text.replace(load_data_old, load_data_new)

# Modify showAddEventDialog save button
save_old = """            eventList.add(0, new CountdownEvent(title, selectedDate.getTimeInMillis()));
            adapter.notifyItemInserted(0);
            rvEvents.scrollToPosition(0);
            dialog.dismiss();"""
save_new = """            viewModel.insert(new CountdownEvent(title, selectedDate.getTimeInMillis()));
            rvEvents.scrollToPosition(0);
            dialog.dismiss();"""
text = text.replace(save_old, save_new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(text)
print("Updated EventCountdownActivity")
