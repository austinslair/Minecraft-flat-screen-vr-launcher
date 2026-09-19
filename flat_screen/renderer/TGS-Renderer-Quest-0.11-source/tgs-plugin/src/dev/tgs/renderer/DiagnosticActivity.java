// TGS experimental additions, 2026. SPDX-License-Identifier: LGPL-2.1-only
package dev.tgs.renderer;
import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.widget.*;
import android.content.*;
import android.system.Os;
import java.io.File;

public class DiagnosticActivity extends Activity {
    private static native String check(String directory);
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(1); layout.setPadding(24,32,24,24);
        TextView report=new TextView(this); report.setTextSize(16); report.setTextIsSelectable(true);
        report.setText("Checking pixel conversion and offscreen rendering…\nIf this screen closes, send the launcher's log and your phone model.");
        layout.addView(report);
        Button copy=new Button(this); copy.setText("Copy report"); copy.setEnabled(false); layout.addView(copy);
        copy.setOnClickListener(v->{ ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("TGS check",report.getText())); Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show(); });
        ScrollView scroll=new ScrollView(this); scroll.addView(layout); setContentView(scroll);
        new Thread(()->{
            String result;
            try {
                File dir=new File(getFilesDir(),"renderer-check");
                if(!dir.isDirectory()&&!dir.mkdirs()) throw new IllegalStateException("Cannot create diagnostic directory");
                Os.setenv("MG_DIR_PATH",dir.getAbsolutePath(),true);
                Os.setenv("TGS_FAST_UPLOADS","1",true);
                Os.setenv("TGS_CHUNK_STAGING","1",true);
                Os.setenv("TGS_QUEST","1",true);
                Os.setenv("TGS_OPAQUE_WINDOW","1",true);
                Os.setenv("TGS_STREAM_UPLOADS","1",true);
                System.loadLibrary("tgscheck");
                result=check(getApplicationInfo().nativeLibraryDir);
            } catch(Throwable error) { result="FAIL: "+error; }
            final String text="TGS Quest 0.11 / "+getPackageName()+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n\n"+result
                +"\n\nThis is a smoke test, not Minecraft compatibility or an FPS benchmark.";
            runOnUiThread(()->{report.setText(text);copy.setEnabled(true);});
        },"tgs-check").start();
    }
    @Override public void onDestroy() { super.onDestroy(); if(isFinishing()) android.os.Process.killProcess(android.os.Process.myPid()); }
}
