// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import de.agundur.eternalwatch.core.*;

public final class MainActivity extends Activity {
    private static final int BG=0xff101a17,CARD=0xff19251f,INK=0xfff4f0e5,MUTED=0xffacbfb0,GOLD=0xffd7bc81;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LinearLayout overview,settings;
    private WatchFaceView face;
    private TextView status,date,sample,dayName,dayDescription,rolePlay,invasionHeading,invasionText,invasionAge,selectionCount;
    private Switch monitoring,sound;
    private Button overviewTab,settingsTab;
    private final List<CheckBox> dayBoxes=new ArrayList<>();
    private ScrollView scroll;
    private boolean syncing;
    private long lastRefresh;
    private final Runnable tick=new Runnable() {
        @Override public void run() {
            render();
            if(android.os.SystemClock.elapsedRealtime()-lastRefresh>FeedRepository.pollSeconds(MainActivity.this)*1000L) {
                FeedRepository.request(MainActivity.this,false);lastRefresh=android.os.SystemClock.elapsedRealtime();
            }
            handler.postDelayed(this,1000);
        }
    };
    private android.content.SharedPreferences prefs() { return FeedRepository.prefs(this); }
    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density*value+.5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);WatchNotifications.channels(this);
        if((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
                && getIntent().getBooleanExtra("qa_keep_awake",false))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(20),dp(22),dp(20),dp(28));scroll.addView(root);
        scroll.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
                Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard.bottom));
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        TextView title=text(root,getString(R.string.app_name),30,INK);title.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        text(root,getString(R.string.subtitle),14,MUTED);
        LinearLayout tabs=row(root);tabs.setPadding(0,dp(18),0,dp(12));
        overviewTab=button(tabs,R.string.overview,()->showTab(false));settingsTab=button(tabs,R.string.alerts,()->showTab(true));
        overviewTab.setId(R.id.tab_overview);settingsTab.setId(R.id.tab_settings);
        overview=column();settings=column();root.addView(overview);root.addView(settings);overview.setTag("overview");settings.setTag("settings");
        buildOverview();buildSettings();showTab(state!=null && state.getBoolean("settings",false));setContentView(scroll);
        if(state==null) FeedRepository.request(this,false);
    }
    private void buildOverview() {
        LinearLayout clockCard=card(overview);
        status=text(clockCard,getString(R.string.waiting),12,GOLD);status.setId(R.id.feed_status);
        face=new WatchFaceView(this);face.setId(R.id.watch_face);clockCard.addView(face,new LinearLayout.LayoutParams(-1,dp(250)));
        date=text(clockCard,"",15,INK);date.setGravity(Gravity.CENTER);
        sample=text(clockCard,"",11,MUTED);sample.setGravity(Gravity.CENTER);
        button(clockCard,R.string.refresh,()->FeedRepository.request(this,true)).setId(R.id.refresh_feed);
        LinearLayout day=card(overview);label(day,R.string.special_heading);
        dayName=text(day,getString(R.string.waiting),23,INK);dayName.setId(R.id.special_day);
        dayDescription=text(day,"",15,MUTED);rolePlay=text(day,"",14,MUTED);
        LinearLayout guides=card(overview);label(guides,R.string.agundur_heading);
        text(guides,getString(R.string.guides_title),22,INK);text(guides,getString(R.string.guides_description),13,MUTED);
        LinearLayout guidesRow=row(guides);
        button(guidesRow,R.string.quests_link,()->openUrl("https://eternal-lands.agundur.de/quests.html")).setId(R.id.open_quests);
        button(guidesRow,R.string.dailies_link,()->openUrl("https://eternal-lands.agundur.de/dailies/")).setId(R.id.open_dailies);
        LinearLayout dailyRow=row(guides);
        button(dailyRow,R.string.haidir_link,()->openUrl("https://eternal-lands.agundur.de/dailies/haidir.html"));
        button(dailyRow,R.string.daritha_link,()->openUrl("https://eternal-lands.agundur.de/dailies/daritha.html"));
        button(dailyRow,R.string.xaquelina_link,()->openUrl("https://eternal-lands.agundur.de/dailies/xaquelina.html"));
        button(guides,R.string.agundur_website,()->openUrl("https://eternal-lands.agundur.de/")).setId(R.id.open_agundur);
        LinearLayout invasion=card(overview);invasionHeading=label(invasion,R.string.last_report);
        invasionText=text(invasion,getString(R.string.no_report),16,INK);invasionText.setId(R.id.invasion_report);
        invasionAge=text(invasion,"",12,GOLD);text(invasion,getString(R.string.report_note),12,MUTED);
        LinearLayout widget=card(overview);button(widget,R.string.add_widget,this::pinWidget).setId(R.id.add_widget);
        text(widget,getString(R.string.widget_help),13,MUTED);
        LinearLayout foot=row(overview);button(foot,R.string.source_link,()->openUrl("https://mercator.holy-eternalland.de/api"));button(foot,R.string.about,this::about);
    }
    private void buildSettings() {
        LinearLayout live=card(settings);text(live,getString(R.string.monitor_heading),22,INK);
        monitoring=switcher(live,R.string.monitor_enabled,prefs().getBoolean("monitor_enabled",false),checked->{ if(!syncing) setMonitoring(checked); });monitoring.setId(R.id.monitor_toggle);
        text(live,getString(R.string.monitor_help),13,MUTED);text(live,getString(R.string.restart_help),13,MUTED);
        switcher(live,R.string.invasion_enabled,prefs().getBoolean("invasions",true),checked->{ prefs().edit().putBoolean("invasions",checked).apply();sound.setEnabled(checked); });
        sound=switcher(live,R.string.invasion_sound,prefs().getBoolean("sound",false),checked->prefs().edit().putBoolean("sound",checked).apply());sound.setEnabled(prefs().getBoolean("invasions",true));
        switcher(live,R.string.show_seconds,prefs().getBoolean("seconds",false),checked->prefs().edit().putBoolean("seconds",checked).apply());
        text(live,getString(R.string.poll_interval),14,MUTED);
        int[] intervals={15,30,60,120,300};String[] options=new String[intervals.length];int selected=1;
        for(int i=0;i<intervals.length;i++) { options[i]=getString(R.string.interval_seconds,intervals[i]);if(intervals[i]==FeedRepository.pollSeconds(this)) selected=i; }
        Spinner interval=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,options);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);interval.setAdapter(adapter);interval.setSelection(selected);live.addView(interval);
        interval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p,View v,int position,long id) { prefs().edit().putInt("poll_seconds",intervals[position]).apply(); }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        button(live,R.string.open_notification_settings,()->startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        if(Build.VERSION.SDK_INT>=33) {
            Intent languageSettings=new Intent(Settings.ACTION_APP_LOCALE_SETTINGS,Uri.parse("package:"+getPackageName()));
            if(languageSettings.resolveActivity(getPackageManager())!=null)
                button(live,R.string.app_language,()->startActivity(languageSettings));
        }
        LinearLayout days=card(settings);text(days,getString(R.string.selected_days),22,INK);text(days,getString(R.string.day_help),13,MUTED);
        EditText search=new EditText(this);search.setSingleLine();search.setHint(R.string.search_days);search.setTextColor(INK);search.setHintTextColor(MUTED);search.setId(R.id.search_days);days.addView(search);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH || (event!=null && event.getKeyCode()==KeyEvent.KEYCODE_ENTER)) {
                getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(),0);
                return true;
            }
            return false;
        });
        LinearLayout controls=row(days);button(controls,R.string.select_all,()->setAll(true)).setId(R.id.select_all_days);button(controls,R.string.clear_days,()->setAll(false)).setId(R.id.clear_selected_days);selectionCount=text(days,"",12,GOLD);
        Set<String> selectedDays=prefs().getStringSet("selected_days",Collections.emptySet());
        for(String name:SpecialDays.NAMES) {
            CheckBox check=new CheckBox(this);check.setText(name);check.setTextSize(15);check.setTextColor(INK);check.setMinHeight(dp(48));check.setPadding(0,dp(3),0,dp(3));check.setChecked(SpecialDays.selected(name,selectedDays));
            check.setOnCheckedChangeListener((button,checked)->{
                if(syncing)return;
                Set<String> names=new HashSet<>(prefs().getStringSet("selected_days",Collections.emptySet()));
                if(checked)names.add(name);else names.remove(name);prefs().edit().putStringSet("selected_days",names).apply();updateCount();
            });days.addView(check);dayBoxes.add(check);
        }
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            @Override public void onTextChanged(CharSequence s,int start,int before,int count) { for(CheckBox check:dayBoxes) check.setVisibility(check.getText().toString().toLowerCase(Locale.ROOT).contains(s.toString().toLowerCase(Locale.ROOT))?View.VISIBLE:View.GONE); }
            @Override public void afterTextChanged(Editable e) { }
        });updateCount();
    }
    private void showTab(boolean showSettings) {
        overview.setVisibility(showSettings?View.GONE:View.VISIBLE);settings.setVisibility(showSettings?View.VISIBLE:View.GONE);
        overviewTab.setTextColor(showSettings?MUTED:GOLD);settingsTab.setTextColor(showSettings?GOLD:MUTED);scroll.smoothScrollTo(0,0);
    }
    private void setAll(boolean selected) {
        Set<String> values=selected?new HashSet<>(SpecialDays.NAMES):new HashSet<>();prefs().edit().putStringSet("selected_days",values).apply();
        syncing=true;for(CheckBox c:dayBoxes)c.setChecked(selected);syncing=false;updateCount();
    }
    private void updateCount() { int count=prefs().getStringSet("selected_days",Collections.emptySet()).size();selectionCount.setText(getResources().getQuantityString(R.plurals.selection_count,count,count)); }
    private void setMonitoring(boolean enabled) {
        if(enabled && Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            syncing=true;monitoring.setChecked(false);syncing=false;requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);return;
        }
        if(enabled && !WatchNotifications.permitted(this)) {
            Toast.makeText(this,R.string.notifications_blocked,Toast.LENGTH_LONG).show();syncing=true;monitoring.setChecked(false);syncing=false;return;
        }
        prefs().edit().putBoolean("monitor_enabled",enabled).commit();FeedRepository.resetAlarms();
        if(enabled) startForegroundService(new Intent(this,WatchService.class));else stopService(new Intent(this,WatchService.class));
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==1 && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED) {
            syncing=true;monitoring.setChecked(true);syncing=false;setMonitoring(true);
        } else if(request==1) Toast.makeText(this,R.string.notification_required,Toast.LENGTH_LONG).show();
    }
    private void render() {
        long now=System.currentTimeMillis();GameSnapshot data=FeedRepository.snapshot(this);face.update(data,now,prefs().getBoolean("seconds",false));
        String error=prefs().getString("error","");boolean valid=data!=null && data.currentSeconds(now)>=0;
        status.setText(getString("loading".equals(error)?R.string.loading:"network".equals(error)?R.string.network_error:"invalid".equals(error)?R.string.invalid_data:valid?R.string.live:data==null?R.string.waiting:R.string.outdated));
        status.setTextColor(valid?0xff9cccae:GOLD);
        if(data!=null) {
            date.setText(getString(R.string.game_date,data.day,data.month,data.year));sample.setText(getString(R.string.last_sample,DateFormat.getTimeInstance(DateFormat.SHORT,getResources().getConfiguration().getLocales().get(0)).format(new Date(data.clockStamp))));
            dayName.setText(data.name);boolean redundant=SpecialDays.key(data.name).equals(SpecialDays.key(data.description));
            dayDescription.setText(data.description);dayDescription.setVisibility(redundant||data.description.isEmpty()?View.GONE:View.VISIBLE);
            rolePlay.setText(data.rolePlay);rolePlay.setVisibility(data.rolePlay.isEmpty()?View.GONE:View.VISIBLE);
            invasionText.setText(data.invasionText.isEmpty()?getString(R.string.no_report):data.invasionText);
            invasionHeading.setText(data.recentInvasion(now)?R.string.recent_report:R.string.last_report);
            invasionAge.setText(data.invasionStamp>0?age(data.invasionStamp,now):"");
        }
        syncing=true;monitoring.setChecked(prefs().getBoolean("monitor_enabled",false));syncing=false;
    }
    private String age(long stamp,long now) {
        long seconds=(now-stamp)/1000;
        if(seconds< -60)return getString(R.string.future_report);if(seconds<60)return getString(R.string.just_now);
        if(seconds<3600)return getString(R.string.report_minutes,seconds/60);if(seconds<86400)return getString(R.string.report_hours,seconds/3600);
        return getString(R.string.report_date,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT,getResources().getConfiguration().getLocales().get(0)).format(new Date(stamp)));
    }
    private void pinWidget() {
        AppWidgetManager manager=getSystemService(AppWidgetManager.class);
        if(manager.isRequestPinAppWidgetSupported())manager.requestPinAppWidget(new ComponentName(this,WatchWidget.class),null,null);
        else Toast.makeText(this,R.string.widget_pin_unavailable,Toast.LENGTH_LONG).show();
    }
    private void about() {
        new AlertDialog.Builder(this).setTitle(R.string.app_name).setMessage(getString(R.string.about_text,"0.1.0-alpha.1"))
            .setPositiveButton(R.string.close,null).setNeutralButton(R.string.view_license,(d,w)->{
                try(java.io.InputStream in=getAssets().open("LICENSE.txt")) {
                    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
                    new AlertDialog.Builder(this).setTitle(R.string.view_license).setMessage(out.toString("UTF-8")).setPositiveButton(R.string.close,null).show();
                } catch(java.io.IOException ignored) { }
            }).show();
    }
    private void openUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); } catch(ActivityNotFoundException ignored) { } }
    private LinearLayout column() { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v; }
    private LinearLayout row(LinearLayout parent) { LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);parent.addView(v);return v; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout v=column();GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0xff344438);v.setBackground(bg);v.setPadding(dp(18),dp(16),dp(18),dp(16));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(14);parent.addView(v,params);return v;
    }
    private TextView text(LinearLayout parent,String value,int size,int color) { TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setPadding(0,dp(5),0,dp(5));parent.addView(v);return v; }
    private TextView label(LinearLayout parent,int value) { TextView v=text(parent,getString(value),11,GOLD);v.setLetterSpacing(.1f);return v; }
    private Button button(LinearLayout parent,int value,Runnable action) {
        Button v=new Button(this,null,android.R.attr.borderlessButtonStyle);v.setText(value);v.setTextSize(13);v.setTextColor(GOLD);v.setAllCaps(false);v.setMinHeight(dp(48));v.setOnClickListener(w->action.run());
        parent.addView(v,new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,-2,parent.getOrientation()==LinearLayout.HORIZONTAL?1:0));return v;
    }
    private interface Toggle { void change(boolean value); }
    private Switch switcher(LinearLayout parent,int label,boolean checked,Toggle action) {
        Switch v=new Switch(this);v.setText(label);v.setTextColor(INK);v.setTextSize(15);v.setMinHeight(dp(56));v.setPadding(0,dp(8),0,dp(8));v.setChecked(checked);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));v.setOnCheckedChangeListener((b,c)->action.change(c));return v;
    }
    @Override protected void onResume() { super.onResume();handler.post(tick); }
    @Override protected void onPause() { handler.removeCallbacks(tick);super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle out) { out.putBoolean("settings",settings.getVisibility()==View.VISIBLE);super.onSaveInstanceState(out); }
}
