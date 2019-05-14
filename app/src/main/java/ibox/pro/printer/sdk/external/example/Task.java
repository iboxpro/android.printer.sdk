package ibox.pro.printer.sdk.external.example;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

import ibox.pro.printer.sdk.external.IPrinterAdapter;
import ibox.pro.printer.sdk.external.PrinterException;
import ibox.pro.printer.sdk.external.PrinterResponse;

public abstract class Task {
    public static abstract class PrinterTask<I, O> extends AsyncTask<I, Void, O> {
        protected final IPrinterAdapter printer;
        protected final WeakReference<IProgressable<O>> progressable;

        public PrinterTask(IPrinterAdapter printer, IProgressable<O> progressable) {
            this.printer = printer;
            this.progressable = progressable == null ? null : new WeakReference<>(progressable);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (progressable != null) {
                IProgressable progressable = this.progressable.get();
                if (progressable != null)
                    progressable.onPreProgress();
            }
        }

        @Override
        protected void onPostExecute(O o) {
            super.onPostExecute(o);
            if (progressable != null) {
                IProgressable progressable = this.progressable.get();
                if (progressable != null)
                    progressable.onPostProgress(o);
            }
        }
    }

    public static class ConnectTask extends PrinterTask<String, Boolean> {
        public ConnectTask(IPrinterAdapter printer, IProgressable<Boolean> progressable) {
            super(printer, progressable);
        }

        @Override
        protected Boolean doInBackground(String... strings) {
            return printer.connect(strings[0]);
        }
    }

    public static class DisconnectTask extends PrinterTask<Void, Void> {
        public DisconnectTask(IPrinterAdapter printer, IProgressable<Void> progressable) {
            super(printer, progressable);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            printer.disconnect();
            return null;
        }
    }

    public static class TestTask extends PrinterTask<String, PrinterResponse> {
        private final WeakReference<Context> context;

        public TestTask(IPrinterAdapter printer, IProgressable<PrinterResponse> progressable, Context context) {
            super(printer, progressable);
            this.context = new WeakReference<>(context);
        }

        private boolean checkResponse(PrinterResponse response) {
            return response != null && response.getErrorCode() == 0;
        }

        @Override
        protected PrinterResponse doInBackground(String... params) {
            String text = params[0];
            boolean buffered = Boolean.parseBoolean(params[0]);

            PrinterResponse response = new PrinterResponse();
            try {
                if (buffered)
                    response = printer.openDocument();
                if (!buffered || checkResponse(response)) response = printer.printText(text, IPrinterAdapter.TextAlignment.LEFT);
                if (checkResponse(response)) response = printer.printBarcode(IPrinterAdapter.Barcode.QR, "this is qr code");
                if (checkResponse(response)) response = printer.printBarcode(IPrinterAdapter.Barcode.Code128C, "128c code");
                if (checkResponse(response)) response = printer.printBarcode(IPrinterAdapter.Barcode.EAN13, "12345");
                if (checkResponse(response)) {
                    Context context = this.context.get();
                    if (context != null) {
                        Bitmap testImage = BitmapFactory.decodeResource(context.getResources(), R.drawable.img_test);
                        response = printer.printImage(testImage);
                        testImage.recycle();
                    }
                }
                if (checkResponse(response)) response = printer.scroll(3);
                if (buffered)
                    response = printer.closeDocument();
            } catch (PrinterException e) {
                e.printStackTrace();
                return null;
            }
            return response;
        }
    }
}
