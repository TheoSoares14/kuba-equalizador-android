package audio.kuba.equalizador;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "UsbHid")
public class UsbHidPlugin extends Plugin {
  private static final int VID = 0x3302, PID = 0x8902;
  private static final String ACTION = "audio.kuba.equalizador.USB_PERMISSION";
  private UsbManager mgr;
  private UsbDeviceConnection conn;
  private UsbInterface intf;
  private UsbEndpoint outEp;
  private PluginCall pending;

  @Override
  public void load() {
    mgr = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
    BroadcastReceiver r = new BroadcastReceiver() {
      @Override public void onReceive(Context c, Intent i) {
        if (pending == null) return;
        PluginCall call = pending; pending = null;
        UsbDevice d = i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (d != null && i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) open(d, call);
        else { call.reject("Permissão USB negada. Toque em Permitir quando o Android perguntar."); getBridge().releaseCall(call); }
      }
    };
    ContextCompat.registerReceiver(getContext(), r, new IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
  }

  private UsbDevice find() {
    for (UsbDevice d : mgr.getDeviceList().values())
      if (d.getVendorId() == VID && d.getProductId() == PID) return d;
    return null;
  }

  @PluginMethod
  public void connect(PluginCall call) {
    UsbDevice d = find();
    if (d == null) { call.reject("Cabo não encontrado. Plugue o cabo no celular (USB-C) e tente de novo."); return; }
    if (mgr.hasPermission(d)) { open(d, call); return; }
    getBridge().saveCall(call); pending = call;
    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
    Intent it = new Intent(ACTION).setPackage(getContext().getPackageName());
    mgr.requestPermission(d, PendingIntent.getBroadcast(getContext(), 0, it, flags));
  }

  private void open(UsbDevice d, PluginCall call) {
    try {
      close0();
      conn = mgr.openDevice(d);
      if (conn == null) { call.reject("Não deu para abrir o cabo."); return; }
      UsbInterface best = null; UsbEndpoint bestOut = null;
      for (int n = 0; n < d.getInterfaceCount(); n++) {
        UsbInterface x = d.getInterface(n);
        if (x.getInterfaceClass() != UsbConstants.USB_CLASS_HID) continue;
        UsbEndpoint o = null;
        for (int e = 0; e < x.getEndpointCount(); e++) {
          UsbEndpoint ep = x.getEndpoint(e);
          if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT && ep.getDirection() == UsbConstants.USB_DIR_OUT) o = ep;
        }
        if (best == null || (bestOut == null && o != null)) { best = x; bestOut = o; }
      }
      if (best == null) { call.reject("Interface HID não encontrada no cabo."); return; }
      if (!conn.claimInterface(best, true)) { call.reject("Não deu para assumir a interface do cabo."); return; }
      intf = best; outEp = bestOut;
      JSObject r = new JSObject();
      r.put("name", d.getProductName() != null ? d.getProductName() : "Cabo");
      call.resolve(r);
    } catch (Exception e) {
      call.reject("Erro ao abrir o cabo: " + e.getMessage());
    } finally {
      getBridge().releaseCall(call);
    }
  }

  @PluginMethod
  public void sendReport(PluginCall call) {
    if (conn == null || intf == null) { call.reject("Cabo não conectado"); return; }
    try {
      int id = call.getInt("reportId", 0);
      JSArray arr = call.getArray("data");
      int len = Math.max(32, arr.length() + 1);
      byte[] buf = new byte[len];
      buf[0] = (byte) id;
      for (int i = 0; i < arr.length(); i++) buf[i + 1] = (byte) arr.getInt(i);
      int r = -1;
      if (outEp != null) r = conn.bulkTransfer(outEp, buf, buf.length, 1000);
      if (r < 0) r = conn.controlTransfer(0x21, 0x09, (0x02 << 8) | id, intf.getId(), buf, buf.length, 1000);
      if (r < 0) call.reject("Falha na transferência USB (código " + r + ", endpoint " + (outEp != null ? "OUT" : "controle") + ")");
      else call.resolve();
    } catch (Exception e) {
      call.reject("Erro ao enviar: " + e.getMessage());
    }
  }

  @PluginMethod
  public void close(PluginCall call) { close0(); call.resolve(); }

  private void close0() {
    try { if (conn != null) { if (intf != null) conn.releaseInterface(intf); conn.close(); } } catch (Exception ignored) {}
    conn = null; intf = null; outEp = null;
  }
}
