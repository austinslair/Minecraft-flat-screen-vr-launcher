#!/usr/bin/env python3
"""Build the native fork and one signed, installable renderer plugin without Gradle.

Requires Android NDK r27d, CMake+Ninja, build-tools 35, android-35 and JDK 17.
See README.md. Uses a local debug signing key, never a production key.
"""
from pathlib import Path
import argparse, os, shutil, subprocess, zipfile, json, hashlib

def main():
    p=argparse.ArgumentParser()
    p.add_argument('--toolchain',type=Path,required=True)
    p.add_argument('--java-home',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--native-build',type=Path,required=True)
    p.add_argument('--reuse-native',action='store_true',help='Package an already built core; skips CMake')
    args=p.parse_args()
    root=Path(__file__).resolve().parents[1]; plugin=root/'tgs-plugin'
    tool=args.toolchain.resolve(); output=args.output.resolve(); output.mkdir(parents=True,exist_ok=True)
    work=output/'work';work.mkdir(exist_ok=True)
    native=args.native_build.resolve()
    ndk=tool/'android-ndk-r27d'; llvm=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
    cmake=tool/'cmake/bin/cmake';bt=tool/'build-tools/android-15'
    platform=tool/'platforms/android-35/android.jar'
    jhome=args.java_home.resolve();java=jhome/'bin/java'
    env=dict(os.environ,JAVA_HOME=str(jhome),TMPDIR=str(work))
    env['LD_LIBRARY_PATH']=str(jhome/'lib')+':'+str(bt/'lib64')+(':'+env['LD_LIBRARY_PATH'] if env.get('LD_LIBRARY_PATH') else '')
    def run(command): subprocess.run([str(x) for x in command],check=True,env=env)
    j=[java,'-Djava.io.tmpdir='+str(work)]
    if not args.reuse_native:
        run([cmake,'-S',root/'MobileGlues-cpp','-B',native,'-G','Ninja',
         '-DCMAKE_MAKE_PROGRAM='+str(tool/'cmake/bin/ninja'),
         '-DCMAKE_TOOLCHAIN_FILE='+str(ndk/'build/cmake/android.toolchain.cmake'),
         '-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-26',
         '-DANDROID_STL=c++_static','-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON','-DCMAKE_BUILD_TYPE=Release'])
        run([cmake,'--build',native,'--parallel','4'])
    run([llvm/'aarch64-linux-android26-clang++','-std=c++17','-O2','-fPIC','-shared',
         '-static-libstdc++','-fvisibility=hidden','-Wl,--exclude-libs,ALL','-Wl,-z,max-page-size=16384','-Wl,-z,defs','-Wall','-Wextra',
         plugin/'native/check.cpp','-ldl','-o',native/'libtgscheck.so'])
    classes=work/'classes';classes.mkdir(exist_ok=True)
    sources=sorted((plugin/'src').rglob('*.java'))
    run(j+['-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-classpath',platform,'-d',classes]+sources)
    dex=work/'dex';dex.mkdir(exist_ok=True)
    run(j+['-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--min-api','26','--lib',platform,'--output',dex]+sorted(classes.rglob('*.class')))
    key=work/'tgs-debug.jks'
    if not key.exists():
        run([jhome/'bin/keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android',
             '-alias','tgsdebug','-dname','CN=TGS Experimental Test, O=TGS','-keyalg','RSA','-keysize','2048','-validity','3650','-noprompt'])
    libraries={}
    for name in ['libmobileglues.so','libtgscheck.so']:
        dest=work/name;shutil.copyfile(native/name,dest);run([llvm/'llvm-strip','--strip-unneeded',dest]);libraries[name]=dest
    results=[]
    label='TGS Quest 0.11'
    # Retain the 0.8 Streaming identity so this installs as an update.
    package='dev.tgs.renderer.mobileglues.quest8.fast'
    manifest=work/'manifest.xml'
    shared_env='LIBGL_ES=3;POJAV_RENDERER=opengles3;POJAVEXEC_EGL=libmobileglues.so;LIBGL_EGL=libmobileglues.so;TGS_QUEST=1;TGS_OPAQUE_WINDOW=1;TGS_FAST_UPLOADS=1;TGS_FRAME_STATS=1;TGS_CHUNK_STAGING=1;TGS_STREAM_UPLOADS=1'
    metadata={'renderer_id':'opengles3','renderer_name':label,
      'renderer_description':'Quest rendering fixes with reduced presentation and upload overhead',
      'renderer_library':'libmobileglues.so','renderer_egl':'libmobileglues.so','renderer_env':shared_env,
      'renderer':label+':libmobileglues.so:libmobileglues.so','des':label,
      'pojavEnv':shared_env.replace(';',':'),'boatEnv':'LIBGL_ES=3:TGS_QUEST=1:TGS_OPAQUE_WINDOW=1:TGS_FAST_UPLOADS=1:TGS_FRAME_STATS=1:TGS_CHUNK_STAGING=1:TGS_STREAM_UPLOADS=1',
      'minMCVer':'1.20.1'}
    metas='\n'.join('<meta-data android:name="'+k+'" android:value="'+v+'" />' for k,v in metadata.items())
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'''+package+'''" android:versionCode="11" android:versionName="0.11-quest">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35" />
<uses-feature android:glEsVersion="0x00030000" android:required="true" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
<queries><package android:name="ca.dnamobile.droidbridgelauncher" /><package android:name="com.movtery.zalithlauncher.v2" /><package android:name="com.movtery.zalithlauncher" /></queries>
<application android:label="'''+label+'''" android:resizeableActivity="true" android:allowBackup="false" android:extractNativeLibs="true" android:theme="@android:style/Theme.Material.NoActionBar">
<meta-data android:name="fclPlugin" android:value="true" />
<meta-data android:name="zalithRendererPlugin" android:value="true" />
<meta-data android:name="com.oculus.supportedDevices" android:value="quest3|quest3s" />
'''+metas+'''
<activity android:name="dev.tgs.renderer.MainActivity" android:exported="true"><layout android:defaultWidth="1024dp" android:defaultHeight="640dp" /><intent-filter><action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LAUNCHER" /></intent-filter></activity>
<activity android:name="dev.tgs.renderer.DiagnosticActivity" android:exported="false" android:process=":diagnostics" />
</application></manifest>''')
    unsigned=work/'unsigned.apk';aligned=work/'aligned.apk'
    run([bt/'aapt2','link','-I',platform,'--manifest',manifest,'-o',unsigned])
    with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as z:
        z.write(dex/'classes.dex','classes.dex')
        for name,lib in libraries.items():z.write(lib,'lib/arm64-v8a/'+name)
        z.write(root/'LICENSE','assets/LICENSE-MobileGlues.txt')
        z.write(plugin/'README.md','assets/TGS-README.md')
        for base in [root/'MobileGlues-cpp/3rdparty',root/'MobileGlues-cpp/include/ska']:
            for license_file in sorted(base.rglob('*')):
                if license_file.is_file() and license_file.name.lower().startswith(('license','copying','notice')):
                    z.write(license_file,'assets/notices/'+str(license_file.relative_to(root/'MobileGlues-cpp')))
    run([bt/'zipalign','-f','-p','4',unsigned,aligned])
    apk=output/'TGS-Renderer-Quest-0.11-arm64.apk'
    run(j+['-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias','tgsdebug',
           '--ks-pass','pass:android','--key-pass','pass:android','--out',apk,aligned])
    run(j+['-jar',bt/'lib/apksigner.jar','verify','--verbose',apk])
    results.append({'file':apk.name,'package':package,'fast_uploads':'1','stream_uploads':'1','sha256':hashlib.sha256(apk.read_bytes()).hexdigest()})
    (output/'build-results.json').write_text(json.dumps(results,indent=2)+'\n')
    print(json.dumps(results,indent=2))
if __name__=='__main__':main()
