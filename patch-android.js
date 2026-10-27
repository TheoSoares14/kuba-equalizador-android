const fs = require('fs'), path = require('path');
const A = 'android/app/src/main';
const pkgDir = path.join(A, 'java/audio/kuba/equalizador');
fs.mkdirSync(pkgDir, { recursive: true });
fs.mkdirSync(path.join(A, 'res/xml'), { recursive: true });
fs.copyFileSync('native/UsbHidPlugin.java', path.join(pkgDir, 'UsbHidPlugin.java'));
fs.copyFileSync('native/device_filter.xml', path.join(A, 'res/xml/device_filter.xml'));
fs.writeFileSync(path.join(pkgDir, 'MainActivity.java'),
`package audio.kuba.equalizador;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    registerPlugin(UsbHidPlugin.class);
    super.onCreate(savedInstanceState);
  }
}
`);
fs.cpSync('native/res', path.join(A, 'res'), { recursive: true });
const mf = path.join(A, 'AndroidManifest.xml');
let m = fs.readFileSync(mf, 'utf8');
if (!m.includes('usb.host')) m = m.replace('<application', '<uses-feature android:name="android.hardware.usb.host" android:required="false" />\n    <application');
if (!m.includes('device_filter')) m = m.replace('</activity>',
  '<intent-filter><action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" /></intent-filter>\n' +
  '            <meta-data android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" android:resource="@xml/device_filter" />\n        </activity>');
fs.writeFileSync(mf, m);
console.log('Android patch OK');
