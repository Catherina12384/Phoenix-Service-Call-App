package com.phoenix.servicecall.adapters;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.phoenix.servicecall.R;
import com.phoenix.servicecall.models.Task;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * TaskAdapter — RecyclerView adapter for the Office Tasks list.
 *
 * Features:
 *   - Colour-coded status chips (overdue/red, pending/blue, snoozed/amber, done/green)
 *   - Swipe right  → mark done  (green background, checkmark icon)
 *   - Swipe left   → snooze     (amber background, snooze icon)
 *   - "Done" section header shown as a collapsed/expanded toggle row
 *   - Click on any task → opens TaskDetailActivity
 *
 * Item types:
 *   TYPE_TASK   (0) — normal task row
 *   TYPE_HEADER (1) — "Done (N)" collapsible section header
 */
public class TaskAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_TASK   = 0;
    private static final int TYPE_HEADER = 1;

    // Callbacks
    public interface OnTaskClickListener   { void onTaskClick(Task task); }
    public interface OnSwipeRightListener  { void onSwipeRight(Task task, int position); }
    public interface OnSwipeLeftListener   { void onSwipeLeft(Task task, int position); }

    private final OnTaskClickListener  clickListener;
    private final OnSwipeRightListener swipeRightListener;
    private final OnSwipeLeftListener  swipeLeftListener;

    // Data
    private List<Task>   activeTasks = new ArrayList<>(); // overdue + pending + snoozed
    private List<Task>   doneTasks   = new ArrayList<>(); // done section
    private boolean      doneExpanded = false;

    // Display list (what RecyclerView actually renders)
    // Items are either Task or the String "DONE_HEADER"
    private List<Object> displayItems = new ArrayList<>();

    private final Context context;
    private final Paint   swipePaint = new Paint();

    public TaskAdapter(Context context,
                       OnTaskClickListener clickListener,
                       OnSwipeRightListener swipeRightListener,
                       OnSwipeLeftListener  swipeLeftListener) {
        this.context            = context;
        this.clickListener      = clickListener;
        this.swipeRightListener = swipeRightListener;
        this.swipeLeftListener  = swipeLeftListener;
    }

    // ── Data Update ──────────────────────────────────────────────

    /**
     * Submit a new sorted task list.
     * Splits into active and done groups, rebuilds displayItems.
     */
    public void submitList(List<Task> sorted) {
        if (sorted == null) {
            activeTasks = new ArrayList<>();
            doneTasks   = new ArrayList<>();
            rebuildDisplayList();
            return;
        }

        activeTasks = new ArrayList<>();
        doneTasks   = new ArrayList<>();

        for (Task t : sorted) {
            if (t.isDone()) {
                doneTasks.add(t);
            } else {
                activeTasks.add(t);
            }
        }

        rebuildDisplayList();
    }

    private void rebuildDisplayList() {
        displayItems = new ArrayList<>();
        displayItems.addAll(activeTasks);

        // Always show the Done header if there are done tasks
        if (!doneTasks.isEmpty()) {
            displayItems.add("DONE_HEADER"); // sentinel object
            if (doneExpanded) {
                displayItems.addAll(doneTasks);
            }
        }

        notifyDataSetChanged();
    }

    // ── ViewHolder Creation ──────────────────────────────────────

    @Override
    public int getItemViewType(int position) {
        return (displayItems.get(position) instanceof String) ? TYPE_HEADER : TYPE_TASK;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_done_header, parent, false);
            return new HeaderViewHolder(v);
        }
        View v = inflater.inflate(R.layout.item_task, parent, false);
        return new TaskViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(doneTasks.size(), doneExpanded);
        } else if (holder instanceof TaskViewHolder) {
            Task task = (Task) displayItems.get(position);
            ((TaskViewHolder) holder).bind(task);
        }
    }

    @Override
    public int getItemCount() { return displayItems.size(); }

    // ── Get item (used by swipe handler) ─────────────────────────

    public Object getItem(int position) {
        if (position < 0 || position >= displayItems.size()) return null;
        return displayItems.get(position);
    }

    // ── TaskViewHolder ───────────────────────────────────────────

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private final Chip     chipStatus;
        private final TextView tvCustomerName, tvAgentName, tvPhone, tvServiceType, tvTime;

        TaskViewHolder(View v) {
            super(v);
            chipStatus     = v.findViewById(R.id.chip_status);
            tvCustomerName = v.findViewById(R.id.tv_customer_name);
            tvAgentName    = v.findViewById(R.id.tv_agent_name);
            tvPhone        = v.findViewById(R.id.tv_phone);
            tvServiceType  = v.findViewById(R.id.tv_service_type);
            tvTime         = v.findViewById(R.id.tv_time);
        }

        void bind(Task task) {
            tvCustomerName.setText(task.getCustomerName());
            tvAgentName.setText(task.getCreatedByName());
            tvPhone.setText(task.getCustomerPhone());
            tvServiceType.setText(task.getServiceType());
            tvTime.setText(formatTime(task.getCreatedAt() != null
                    ? task.getCreatedAt().toDate() : null));

            applyStatusChip(task);

            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onTaskClick(task);
            });
        }

        private void applyStatusChip(Task task) {
            String label;
            int textColor, bgColor, strokeColor;

            if (task.isOverdue()) {
                label       = "Overdue";
                textColor   = ContextCompat.getColor(context, R.color.status_overdue);
                bgColor     = ContextCompat.getColor(context, R.color.status_overdue_bg);
                strokeColor = textColor;
            } else if (task.isSnoozed()) {
                label       = "Snoozed";
                textColor   = ContextCompat.getColor(context, R.color.status_snoozed);
                bgColor     = ContextCompat.getColor(context, R.color.status_snoozed_bg);
                strokeColor = textColor;
            } else if (task.isDone()) {
                label       = "Done";
                textColor   = ContextCompat.getColor(context, R.color.status_done);
                bgColor     = ContextCompat.getColor(context, R.color.status_done_bg);
                strokeColor = textColor;
            } else if (task.isDeletionRequested()) {
                label       = "Delete Pending";
                textColor   = ContextCompat.getColor(context, R.color.error);
                bgColor     = ContextCompat.getColor(context, R.color.error_container);
                strokeColor = textColor;
            } else {
                // Pending
                label       = "Pending";
                textColor   = ContextCompat.getColor(context, R.color.status_pending);
                bgColor     = ContextCompat.getColor(context, R.color.status_pending_bg);
                strokeColor = textColor;
            }

            chipStatus.setText(label);
            chipStatus.setTextColor(textColor);
            chipStatus.setChipBackgroundColor(
                    android.content.res.ColorStateList.valueOf(bgColor));
            chipStatus.setChipStrokeColor(
                    android.content.res.ColorStateList.valueOf(strokeColor));
        }

        private String formatTime(Date date) {
            if (date == null) return "";
            return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(date);
        }
    }

    // ── HeaderViewHolder ─────────────────────────────────────────

    class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvHeader;

        HeaderViewHolder(View v) {
            super(v);
            tvHeader = v.findViewById(R.id.tv_done_header);
        }

        void bind(int count, boolean expanded) {
            tvHeader.setText(expanded
                    ? "Done (" + count + ")  ▲"
                    : "Done (" + count + ")  ▼");

            itemView.setOnClickListener(v -> {
                doneExpanded = !doneExpanded;
                rebuildDisplayList();
            });
        }
    }

    // ════════════════════════════════════════════════════════════
    // SWIPE HANDLER (attach to RecyclerView in Fragment)
    // ════════════════════════════════════════════════════════════

    /**
     * Returns an ItemTouchHelper pre-configured for this adapter.
     * Attach via: getSwipeHelper().attachToRecyclerView(recyclerView)
     *
     * Swipe RIGHT → green background + checkmark → mark done
     * Swipe LEFT  → amber background + snooze icon → snooze (Phase 4)
     */
    public ItemTouchHelper getSwipeHelper() {
        return new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // no drag-drop
            }

            @Override
            public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder) {
                // Disable swipe on header rows and done tasks
                if (viewHolder instanceof HeaderViewHolder) return 0;
                Object item = getItem(viewHolder.getAdapterPosition());
                if (item instanceof Task && ((Task) item).isDone()) return 0;
                return super.getSwipeDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                Object item = getItem(pos);
                if (!(item instanceof Task)) return;
                Task task = (Task) item;

                if (direction == ItemTouchHelper.RIGHT) {
                    if (swipeRightListener != null) swipeRightListener.onSwipeRight(task, pos);
                } else {
                    if (swipeLeftListener != null) swipeLeftListener.onSwipeLeft(task, pos);
                }
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {

                View itemView = viewHolder.itemView;
                Drawable icon;

                if (dX > 0) {
                    // Swipe RIGHT — green (done)
                    swipePaint.setColor(ContextCompat.getColor(context, R.color.status_done));
                    c.drawRect(itemView.getLeft(), itemView.getTop(),
                            itemView.getLeft() + dX, itemView.getBottom(), swipePaint);

                    icon = ContextCompat.getDrawable(context, R.drawable.ic_check);
                    if (icon != null) {
                        icon.setTint(Color.WHITE);
                        int margin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconLeft = itemView.getLeft() + margin;
                        int iconTop = itemView.getTop() + margin;
                        int iconRight = iconLeft + icon.getIntrinsicWidth();
                        int iconBottom = itemView.getBottom() - margin;

                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                        // Only draw icon if there's enough room
                        if (dX > (margin * 2 + icon.getIntrinsicWidth())) {
                            icon.draw(c);
                        }
                    }
                } else if (dX < 0) {
                    // Swipe LEFT — amber (snooze)
                    swipePaint.setColor(ContextCompat.getColor(context, R.color.status_snoozed));
                    c.drawRect(itemView.getRight() + dX, itemView.getTop(),
                            itemView.getRight(), itemView.getBottom(), swipePaint);

                    icon = ContextCompat.getDrawable(context, R.drawable.ic_snooze);
                    if (icon != null) {
                        icon.setTint(Color.WHITE);
                        int margin = (itemView.getHeight() - icon.getIntrinsicHeight()) / 2;
                        int iconRight = itemView.getRight() - margin;
                        int iconTop = itemView.getTop() + margin;
                        int iconLeft = iconRight - icon.getIntrinsicWidth();
                        int iconBottom = itemView.getBottom() - margin;

                        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);

                        // Only draw icon if there's enough room
                        if (Math.abs(dX) > (margin * 2 + icon.getIntrinsicWidth())) {
                            icon.draw(c);
                        }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }
        });
    }
}