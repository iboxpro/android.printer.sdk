package ibox.pro.printer.sdk.external.example;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Set;

import ibox.pro.printer.sdk.external.BluetoothPrinter;
import ibox.pro.printer.sdk.external.Build;
import ibox.pro.printer.sdk.external.IPrinterAdapter;
import ibox.pro.printer.sdk.external.PrinterFactory;
import ibox.pro.printer.sdk.external.PrinterResponse;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Spinner spnModels, spnDevices;
    private CheckBox cbBuffered;
    private Button btnConnect, btnDisconnect, btnTest;
    private TextView lblModel, lblDevice;
    private ProgressBar progressBar;

    private IPrinterAdapter mPrinter;
    private LinkedHashMap<String, String> devices = new LinkedHashMap<>();

    private ActivityResultLauncher<String[]> checkBtPermissionsOnConnect = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(),
        result -> {
            if (BluetoothPrinter.CheckBluetoothPermissions(this)) {
                updateDevicesList();
            } else {
                Toast.makeText(this, R.string.error_permissions, Toast.LENGTH_LONG).show();
            }
        }
    );

    private IProgressable<Boolean> connectProgress = new IProgressable<Boolean>() {
        @Override
        public void onPreProgress() {
            showProgress(true);
        }

        @Override
        public void onPostProgress(Boolean success) {
            showProgress(false);
            updateUI();
        }
    };
    private IProgressable<Void> disconnectProgress = new IProgressable<Void>() {
        @Override
        public void onPreProgress() {
            showProgress(true);
        }

        @Override
        public void onPostProgress(Void result) {
            showProgress(false);
            updateUI();
        }
    };
    private IProgressable<PrinterResponse> testProgress = new IProgressable<PrinterResponse>() {
        @Override
        public void onPreProgress() {
            showProgress(true);
        }

        @Override
        public void onPostProgress(PrinterResponse result) {
            showProgress(false);
            if (result == null)
                Toast.makeText(MainActivity.this, "Failed! (exception)", Toast.LENGTH_LONG).show();
            else if (result.getErrorCode() != 0)
                Toast.makeText(MainActivity.this, "Failed! " + result.getErrorMessage(), Toast.LENGTH_LONG).show();
        }
    };

    @SuppressLint("MissingPermission")
    private void updateDevicesList() {
        devices.clear();

        devices.put("USB", IPrinterAdapter.USB_MODE);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices != null) {
                for (BluetoothDevice device : bondedDevices) {
                    devices.put(
                        device.getName() == null || device.getName().isEmpty()
                            ? device.getAddress()
                            : device.getName(),
                        device.getAddress());
                    }
            }
        }

        if (spnDevices != null && spnDevices.getAdapter() != null) {
            ((ArrayAdapter<?>) spnDevices.getAdapter()).notifyDataSetChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle("Ibox Printer SDK v" + Build.VERSIONCODE);

        spnModels = findViewById(R.id.spn_models);
        spnDevices = findViewById(R.id.spn_devices);
        cbBuffered = findViewById(R.id.cb_buffered);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        btnTest = findViewById(R.id.btn_test);
        lblModel = findViewById(R.id.lbl_model);
        lblDevice = findViewById(R.id.lbl_device);
        progressBar = findViewById(R.id.progressBar);

        ArrayList<String> deviceModels = new ArrayList<>(PrinterFactory.PrinterType.values().length);
        for (PrinterFactory.PrinterType printerType : PrinterFactory.PrinterType.values())
            deviceModels.add(printerType.name());
        spnModels.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceModels));
        spnModels.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                PrinterFactory.PrinterType printerType = PrinterFactory.PrinterType.valueOf(String.valueOf(parent.getItemAtPosition(position)));
                if (mPrinter != null)
                    disconnect();
                if (mPrinter == null)
                    mPrinter = PrinterFactory.GetDeviceAdapter(MainActivity.this, printerType);
                updateUI();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        if (BluetoothPrinter.CheckBluetoothPermissions(this)) {
            updateDevicesList();
        } else {
            checkBtPermissionsOnConnect.launch(BluetoothPrinter.GetMissingBluetoothPermissions(this).toArray(new String[] {}));
        }

        spnDevices.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, devices.keySet().toArray(new String[] {})));

        updateUI();

        btnConnect.setOnClickListener(this);
        btnDisconnect.setOnClickListener(this);
        btnTest.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_connect)
            connect();
        else if (view.getId() == R.id.btn_disconnect)
            disconnect();
        else if (view.getId() == R.id.btn_test)
            test();
    }

    private void updateUI() {
        lblDevice.setEnabled(mPrinter != null);
        spnDevices.setEnabled(mPrinter != null);
        btnConnect.setEnabled(mPrinter != null && spnDevices.getSelectedItem() != null);
        btnDisconnect.setEnabled(mPrinter != null && mPrinter.isConnected());
        cbBuffered.setEnabled(mPrinter != null && mPrinter.isConnected());
        btnTest.setEnabled(mPrinter != null && mPrinter.isConnected());
    }

    private void connect() {
        if (mPrinter != null) {
            String printerAddress = devices.get(spnDevices.getSelectedItem().toString());
            if (printerAddress.trim().length() > 0)
                new Task.ConnectTask(mPrinter, connectProgress).execute(printerAddress);
        }
    }

    private void disconnect() {
        if (mPrinter != null)
            new Task.DisconnectTask(mPrinter, disconnectProgress).execute();
    }

    private void test() {
        if (mPrinter != null) {
            boolean buffered = cbBuffered.isChecked();
            String text = getString(R.string.text);

            new Task.TestTask(mPrinter, testProgress, this).execute(text, String.valueOf(buffered));
        }
    }

    private void showProgress(boolean show) {
        lblModel.setEnabled(!show);
        spnModels.setEnabled(!show);
        lblDevice.setEnabled(!show);
        spnDevices.setEnabled(!show);
        btnConnect.setEnabled(!show);
        btnDisconnect.setEnabled(!show);
        cbBuffered.setEnabled(!show);
        btnTest.setEnabled(!show);

        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
