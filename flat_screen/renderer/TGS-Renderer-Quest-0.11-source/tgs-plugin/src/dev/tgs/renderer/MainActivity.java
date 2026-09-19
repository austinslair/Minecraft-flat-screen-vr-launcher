// TGS experimental additions, 2026. SPDX-License-Identifier: LGPL-2.1-only
package dev.tgs.renderer;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout column=new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL); column.setPadding(28,40,28,24);
        column.setBackgroundColor(Color.rgb(18,24,32));
        TextView title=new TextView(this);
        title.setText("TGS Quest 0.11");
        title.setTextColor(Color.WHITE); title.setTextSize(29); column.addView(title);
        TextView body=new TextView(this);
        body.setText("ARM64 renderer • Minecraft Java 1.20.1 and newer OpenGL versions\n\n"
            +"Adaptive staging for slow geometry uploads, with command streaming and the existing Quest fixes. Resolution and graphics quality stay unchanged.\n\n"
            +"1. Run the renderer check below.\n"
            +"2. Restart ZalithLauncher 2 or DroidBridge.\n"
            +"3. Select TGS Quest 0.11.\n"
            +"4. Use the Java runtime required by your Minecraft version.\n\n"
            +"The old 1.21.1 selection cutoff is removed. Forge, Fabric and NeoForge are not blocked by name. Every version and modpack has not been tested; Vulkan rendering is not supported by this OpenGL plugin.\n\n"
            +"This installs as an update to TGS Quest 0.10. Select TGS Quest 0.11 in your launcher after installation. If the old Reference app is installed, uninstall that app to leave only one TGS renderer.\n\n"
            +"FPS gains still need testing on your headset.");
        body.setTextColor(Color.rgb(204,214,225)); body.setTextSize(16); body.setPadding(0,22,0,20); column.addView(body);
        Button check=new Button(this); check.setText("Run renderer check");
        check.setOnClickListener(v->startActivity(new Intent(this,DiagnosticActivity.class))); column.addView(check);
        Button open=new Button(this); open.setText("Open DroidBridge");
        open.setOnClickListener(v->{
            Intent launch=getPackageManager().getLaunchIntentForPackage("ca.dnamobile.droidbridgelauncher");
            if(launch!=null) startActivity(launch);
            else Toast.makeText(this,"Open your installed launcher manually.",Toast.LENGTH_LONG).show();
        }); column.addView(open);
        Button zalith=new Button(this);zalith.setText("Open ZalithLauncher 2");
        zalith.setOnClickListener(v->{Intent launch=getPackageManager().getLaunchIntentForPackage("com.movtery.zalithlauncher.v2");if(launch!=null)startActivity(launch);else Toast.makeText(this,"Open your launcher manually.",Toast.LENGTH_SHORT).show();});column.addView(zalith);
        Button license=new Button(this); license.setText("Credits and license");
        license.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
            .setTitle("TGS experimental renderer")
            .setMessage("Independent test build based on MobileGlues fbc4e412.\n\n"
                +"MobileGL-Dev and contributors: LGPL-2.1-only. TGS additions: LGPL-2.1-only. "
                +"Third-party notices and complete corresponding source accompany the APK. "
                +"No Minecraft code or assets are included. Not an official MobileGlues or DroidBridge release.")
            .setPositiveButton("Close",null).show()); column.addView(license);
        ScrollView scroll=new ScrollView(this); scroll.addView(column); setContentView(scroll);
    }
}
