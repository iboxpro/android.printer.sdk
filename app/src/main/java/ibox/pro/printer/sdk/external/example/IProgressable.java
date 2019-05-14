package ibox.pro.printer.sdk.external.example;

public interface IProgressable<R> {
    void onPreProgress();
    void onPostProgress(R result);
}
