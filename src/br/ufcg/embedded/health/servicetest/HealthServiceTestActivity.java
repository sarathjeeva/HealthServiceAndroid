package br.ufcg.embedded.health.servicetest;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import br.ufcg.embedded.health.R;
import br.ufcg.embedded.health.database.HealthDAO;
import br.ufcg.embedded.health.structures.HealthData;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphView.LegendAlign;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.GraphViewSeries.GraphViewSeriesStyle;
import com.jjoe64.graphview.LineGraphView;
import com.signove.health.service.HealthAgentAPI;
import com.signove.health.service.HealthServiceAPI;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthServiceTestActivity extends Activity {
    private static final String APP_PREF_FILE = "appPreferences";
    private static final String NOTIFICATION_ACTIVE = "notificationActive";

    int[] specs = { 0x1004 };
    HealthServiceAPI api;
    private HealthServiceTestActivity frame = this;

    private TextView sugestion;
    private TextView menssage;
    private TextView device;
    private TextView data;
    private TextView history;
    private CheckBox cbNotification;
    private TimePicker timePicker;
    private Button btnOk;
    private Button btnShowGraphic;
    private Button btnClearHistory;
    private AlarmManager alarmManager;
    private Intent myIntent;
    private List<HealthData> datasHistory;
    private ListView list;
    private HealthAgentAPI.Stub agent;
    private ImageView imgHelpBluetooth;
    private ImageView imgHelpManual;

    private Map<String, String> map;
    private PopupWindow popUp;
    private LinearLayout layoutPopUp;
    private LinearLayout layoutMain;
    private Button btnGraphic;
    private Button btnSave;
    private boolean popUpVisible = false;

    private int width = 0;
    private int height = 0;

    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.w("HST", "Service connection established");

            // that's how we get the client side of the IPC connection
            api = HealthServiceAPI.Stub.asInterface(service);
            agent = new AgentHealth(map, api, frame).getAgent();
            try {
                Log.w("HST", "Configuring...");
                api.ConfigurePassive(agent, specs);
            } catch (RemoteException e) {
                Log.e("HST", "Failed to add listener", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.w("HST", "Service connection closed");
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        width = getResources().getDisplayMetrics().widthPixels;
        height = getResources().getDisplayMetrics().heightPixels;

        ActionBar actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(true);

        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        myIntent = new Intent(this, NotificationReceiver.class);

        SharedPreferences sharedPreferences = getSharedPreferences(
                APP_PREF_FILE, MODE_PRIVATE);
        boolean notification = sharedPreferences.getBoolean(
                NOTIFICATION_ACTIVE, true);

        OnClickListener o = new OnClickListener() {

            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.checkBoxNotifications:
                        if (cbNotification.isChecked()) {
                            timePicker.setEnabled(true);
                        } else {
                            timePicker.setEnabled(false);
                        }
                        break;
                    case R.id.btnOk:
                        if (cbNotification.isChecked()) {
                            setAlarm();
                        } else {
                            alarmManager.cancel(PendingIntent.getBroadcast(
                                    getApplicationContext(), 0, myIntent, 0));
                        }
                        setNotificationActive(cbNotification.isChecked());
                        Toast.makeText(
                                getApplicationContext(),
                                getResources().getString(
                                        R.string.notifications_preferences_success),
                                        Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.btnShowGraphic:
                        layoutMain.setVisibility(View.GONE);
                        initGraphic();
                        popUp.showAtLocation(layoutPopUp, Gravity.START, 20, 20);
                        popUp.update(20, 20, width, height - 50);
                        popUpVisible = true;
                        break;
                    case R.id.btnClearHistory:
                        HealthDAO mDAO = HealthDAO.getInstance(frame);
                        mDAO.deleteAll();
                        updateListHistory();
                        Toast.makeText(getApplicationContext(),
                                getResources().getString(R.string.history_clean),
                                Toast.LENGTH_SHORT).show();
                        break;
                    default:
                        break;
                }

            }
        };

        TabHost tabs = (TabHost) findViewById(R.id.tabhost);
        tabs.setup();
        TabHost.TabSpec spec = tabs.newTabSpec("tag1");
        spec.setContent(R.id.data);
        spec.setIndicator(null, getResources().getDrawable(R.drawable.icon_data));
        tabs.addTab(spec);
        spec = tabs.newTabSpec("tag2");
        spec.setContent(R.id.history);
        spec.setIndicator(null, getResources().getDrawable(R.drawable.icon_history));
        tabs.addTab(spec);
        spec = tabs.newTabSpec("tag3");
        spec.setContent(R.id.settings);
        spec.setIndicator(null, getResources().getDrawable(R.drawable.icon_settings));
        tabs.addTab(spec);
        tabs.setCurrentTab(0);

        list = (ListView) findViewById(R.id.listViewDataHistory);
        sugestion = (TextView) findViewById(R.id.tvSugestion);
        menssage = (TextView) findViewById(R.id.tvMsg);
        device = (TextView) findViewById(R.id.tVDevice);
        data = (TextView) findViewById(R.id.tVMeasurement);
        history = (TextView) findViewById(R.id.tvHistoryEmpty);
        timePicker = (TimePicker) findViewById(R.id.timePicker1);
        cbNotification = (CheckBox) findViewById(R.id.checkBoxNotifications);
        btnOk = (Button) findViewById(R.id.btnOk);
        btnShowGraphic = (Button) findViewById(R.id.btnShowGraphic);
        btnClearHistory = (Button) findViewById(R.id.btnClearHistory);
        imgHelpBluetooth = (ImageView) findViewById(R.id.imageViewHelp);
        imgHelpManual = (ImageView) findViewById(R.id.imageViewHelp2);

        imgHelpBluetooth.setOnClickListener(onClickHelpBluetooth);
        imgHelpManual.setOnClickListener(onClickHelpManual);
        cbNotification.setOnClickListener(o);
        btnOk.setOnClickListener(o);
        btnShowGraphic.setOnClickListener(o);
        btnClearHistory.setOnClickListener(o);

        cbNotification.setChecked(notification);

        map = new HashMap<String, String>();
        popUp = new PopupWindow(this);
        btnGraphic = new Button(this);
        btnGraphic
        .setText(getResources().getString(R.string.btn_close_graphic));
        btnGraphic.setOnClickListener(onclick);
        btnSave = (Button) findViewById(R.id.buttonSave);
        btnSave.setOnClickListener(onClickSave);


        layoutPopUp = new LinearLayout(this);
        layoutPopUp.setOrientation(LinearLayout.VERTICAL);
        layoutMain = (LinearLayout) findViewById(R.id.linearMain);
        // initGraphic();
        popUp.setContentView(layoutPopUp);

        updateListHistory();

        Intent intent = new Intent("com.signove.health.service.HealthService");
        startService(intent);
        bindService(intent, serviceConnection, 0);
        Log.w("HST", "Activity created");

        sugestion.setText("--");
        menssage.setText("--");
        device.setText("--");
        data.setText("--");
    }

    private OnClickListener onclick = new OnClickListener() {

        @Override
        public void onClick(View v) {
            popUp.dismiss();
            layoutMain.setVisibility(View.VISIBLE);
            layoutPopUp.removeAllViews();
        }
    };

    private OnClickListener onClickHelpBluetooth = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(frame);
            builder1.setTitle(frame.getResources().getString(R.string.helper));
            builder1.setMessage(frame.getResources().getString(R.string.helper_BT));
            builder1.setCancelable(true);
            builder1.setPositiveButton("Ok",
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

            AlertDialog alert11 = builder1.create();
            alert11.show();
        }
    };

    private OnClickListener onClickHelpManual = new OnClickListener() {
        @Override
        public void onClick(View v) {
            AlertDialog.Builder builder1 = new AlertDialog.Builder(frame);
            builder1.setTitle(frame.getResources().getString(R.string.helper));
            builder1.setMessage(frame.getResources().getString(R.string.helper_manual));
            builder1.setCancelable(true);
            builder1.setPositiveButton("Ok",
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
                }
            });

            AlertDialog alert11 = builder1.create();
            alert11.show();
        }
    };

    private OnClickListener onClickSave = new OnClickListener() {

        @Override
        public void onClick(View v) {
            EditText editTextSys = (EditText) findViewById(R.id.editTextSystolic);
            EditText editTextDis = (EditText) findViewById(R.id.editTextDiastolic);
            String sys = editTextSys.getText().toString();
            String dis = editTextDis.getText().toString();
            HealthDAO healthDao = HealthDAO.getInstance(frame);

            try {
                HealthData dataInsert = new HealthData(getResources()
                        .getString(R.string.manual_data),
                        Double.parseDouble(sys), Double.parseDouble(dis), 0.0,
                        new Date());
                healthDao.save(dataInsert);

                editTextDis.setText("");
                editTextSys.setText("");

                showResults(dataInsert);
                updateListHistory();
            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),
                        getResources().getString(R.string.invalid_data),
                        Toast.LENGTH_SHORT).show();
            }

        }

        private void showResults(HealthData dataInsert) {
            data.setText(frame.getResources().getString(R.string.pressure_sys)
                    + " " + dataInsert.getSystolic().intValue() + "\n"
                    + frame.getResources().getString(R.string.pressure_dis)
                    + " " + dataInsert.getDiastolic().intValue() + "\n" + "*"
                    + frame.getResources().getString(R.string.unit_mmHg));
            sugestion.setText(dataInsert.analyzePressure(frame));

        }

    };

    @Override
    public void onBackPressed() {
        if (popUpVisible) {
            popUp.dismiss();
            layoutMain.setVisibility(View.VISIBLE);
            layoutPopUp.removeAllViews();
            popUpVisible = false;
        } else {
            super.onBackPressed();
            super.finish();
        }

    }

    private void initGraphic() {
        List<HealthData> datasHistoryInternal = HealthDAO.getInstance(this)
                .ListAll();
        Collections.reverse(datasHistoryInternal);
        GraphViewData[] datasGraphSys = new GraphViewData[datasHistoryInternal
                                                          .size()];
        GraphViewData[] datasGraphDia = new GraphViewData[datasHistoryInternal
                                                          .size()];
        for (int i = 0; i < datasHistoryInternal.size(); i++) {
            datasGraphSys[i] = new GraphViewData(i + 1, datasHistoryInternal
                    .get(i).getSystolic());
            datasGraphDia[i] = new GraphViewData(i + 1, datasHistoryInternal
                    .get(i).getDiastolic());
        }
        GraphViewSeriesStyle seriesStyleSys = new GraphViewSeriesStyle();
        seriesStyleSys.color = Color.RED;
        GraphViewSeriesStyle seriesStyleDia = new GraphViewSeriesStyle();
        seriesStyleDia.color = Color.GREEN;
        GraphViewSeries graphDys = new GraphViewSeries(getResources()
                .getString(R.string.graph_systolic), seriesStyleSys,
                datasGraphSys);
        GraphViewSeries graphDia = new GraphViewSeries(getResources()
                .getString(R.string.graph_diastolic), seriesStyleDia,
                datasGraphDia);
        GraphView graphView = new LineGraphView(this, getResources().getString(
                R.string.graph_title)) {
            @Override
            protected String formatLabel(double value, boolean isValueX) {
                return String.valueOf((int) value);
            }
        };
        graphView.addSeries(graphDys);
        graphView.addSeries(graphDia);
        graphView.getGraphViewStyle().setTextSize(18);
        graphView.setViewPort(2, 10);
        graphView.getGraphViewStyle().setNumHorizontalLabels(5);
        graphView.setHorizontalScrollBarEnabled(true);
        graphView.setScrollable(true);
        graphView.setScalable(true);
        graphView.setShowLegend(true);
        graphView.setLegendAlign(LegendAlign.MIDDLE);
        if ((double) width / height >= 0.75){
            layoutPopUp.addView(graphView, width - 15, height - 100);
        } else {
            layoutPopUp.addView(graphView, width - 15, height - 150);
            graphView.setLegendWidth(width / 2 - 30);
        }
        layoutPopUp.addView(btnGraphic);
    }

    public void updateListHistory() {
        datasHistory = HealthDAO.getInstance(this).ListAll();
        Button btnShowHistory = (Button) findViewById(R.id.btnShowGraphic);
        if (datasHistory.size() == 0 ) {
            history.setText(getResources().getString(R.string.history_empty));
            btnShowHistory.setEnabled(false);
        } else if (datasHistory.size() == 1 ) {
            btnShowHistory.setEnabled(false);
            history.setText("");
        } else {
            btnShowHistory.setEnabled(true);
            history.setText("");
        }
        HealthDataAdapter adapter = new HealthDataAdapter(
                getApplicationContext(), datasHistory);
        list.setAdapter(adapter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Log.w("HST", "Unconfiguring...");
            api.Unconfigure(agent);
        } catch (Throwable t) {
            Log.w("HST", "Erro tentando desconectar");
        }
        Log.w("HST", "Activity destroyed");
        unbindService(serviceConnection);

    }

    private void setNotificationActive(boolean value) {
        SharedPreferences sharedPreferences = getSharedPreferences(
                APP_PREF_FILE, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(NOTIFICATION_ACTIVE, value);
        editor.commit();
    }

    private void setAlarm() {

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                myIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        GregorianCalendar data = calculateNotificationDate();

        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,
                data.getTimeInMillis(), 1000 * 60 * 60 * 24, pendingIntent);
    }

    private GregorianCalendar calculateNotificationDate() {
        Calendar actualCal = new GregorianCalendar();
        actualCal.setTimeInMillis(System.currentTimeMillis());

        int dayOfMonth = actualCal.get(Calendar.DAY_OF_MONTH);
        int month = actualCal.get(Calendar.MONTH);
        int year = actualCal.get(Calendar.YEAR);

        if ((actualCal.get(Calendar.HOUR_OF_DAY) > timePicker.getCurrentHour())
                || (actualCal.get(Calendar.HOUR_OF_DAY) == timePicker
                .getCurrentHour())
                && (actualCal.get(Calendar.MINUTE) > timePicker
                        .getCurrentMinute())) {
            if (actualCal.get(Calendar.DAY_OF_MONTH) == actualCal
                    .getActualMaximum(Calendar.DAY_OF_MONTH)) {
                if (actualCal.get(Calendar.MONTH) == 11) {
                    year += 1;
                    month = 0;
                    dayOfMonth = 1;
                } else {
                    month += 1;
                    dayOfMonth = 1;
                }
            } else {
                dayOfMonth += 1;
            }
        }

        return new GregorianCalendar(year, month, dayOfMonth,
                timePicker.getCurrentHour(), timePicker.getCurrentMinute());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                AlertDialog.Builder builder1 = new AlertDialog.Builder(frame);
                builder1.setTitle(frame.getResources().getString(R.string.about));
                builder1.setMessage(frame.getResources().getString(R.string.about_text));
                builder1.setCancelable(true);
                builder1.setPositiveButton("Ok",
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });

                AlertDialog alert11 = builder1.create();
                alert11.show();
                break;

            default:
                break;
        }

        return true;
    }
}
