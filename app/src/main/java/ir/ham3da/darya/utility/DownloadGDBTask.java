package ir.ham3da.darya.utility;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.File;

import java.io.FileOutputStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

import java.net.URL;

import ir.ham3da.darya.ActivityCollection;
import ir.ham3da.darya.App;
import ir.ham3da.darya.ganjoor.GanjoorDbBrowser;
import ir.ham3da.darya.R;
import ir.ham3da.darya.adaptors.GDBListAdaptor;
import ir.ham3da.darya.adaptors.ScheduleGDB;

public class DownloadGDBTask extends AsyncTask<String, Integer, Long>
{

    private GDBListAdaptor.ViewHolder gdbHolder;
    private Context mContext;
    private String dlPath;
    private String fileName;
    String TAG = "DownloadGDBTask";

    ActivityCollection activityCollection;


    GanjoorDbBrowser GanjoorDbBrowser1;

    private CustomProgress customProgressDlg;

    ScheduleGDB ScheduleGDB1;

    private boolean has_error;
    private boolean DownloadAll;

    public DownloadGDBTask(GDBListAdaptor.ViewHolder holder, Context context, ScheduleGDB scheduleGDB, boolean downloadAll) {
        gdbHolder = holder;
        dlPath = AppSettings.getDownloadPath(context);
        ScheduleGDB1 = scheduleGDB;
        fileName = scheduleGDB._FileName;
        mContext = context;
        activityCollection = (ActivityCollection) mContext;
        GanjoorDbBrowser1 = new GanjoorDbBrowser(mContext);
        has_error = false;
        this.customProgressDlg = new CustomProgress(mContext);
        DownloadAll = downloadAll;
    }

    /**
     * Before starting background thread
     * Show Progress Bar Dialog
     */

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        this.customProgressDlg.showProgress(this.mContext.getString(R.string.downloading), "0 %", false, false, false);

        this.customProgressDlg.setProgress(0, mContext.getString(R.string.file_received), mContext.getString(R.string.file_size), ScheduleGDB1._CateName );

        this.customProgressDlg.getView(R.id.progress_description).setVisibility(View.VISIBLE);
        this.customProgressDlg.getView(R.id.progress_text1).setVisibility(View.VISIBLE);
        this.customProgressDlg.getView(R.id.progress_text2).setVisibility(View.VISIBLE);
        this.customProgressDlg.getView(R.id.cancel_layout).setVisibility(View.VISIBLE);


        Button cancel_actionBtn = (Button) this.customProgressDlg.getView(R.id.cancel_actionBtn);

        cancel_actionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e("Cancel", "onClick: cliced");
                cancel(true);
                customProgressDlg.dismiss();
            }
        });
    }

    /**
     * Downloading file in background thread
     */
    @Override
    protected Long doInBackground(String... urls) {
        long totalBytesCopied = 0;

        try {

            URL url = new URL(urls[0]);
            HttpURLConnection conection = (HttpURLConnection) url.openConnection();

            conection.setReadTimeout(10000);
            conection.setConnectTimeout(15000);
            conection.connect();
            // this will be useful so that you can show a tipical 0-100% progress bar
            long lenghtOfFile = conection.getContentLength();

            // download the file
            InputStream input = new BufferedInputStream(url.openStream(), 8192);

            // Output stream
            OutputStream output = new FileOutputStream(dlPath + "/" + fileName);

            byte[] dl_data = new byte[1024];
            int mLength;

            int resCode = conection.getResponseCode();

            if (resCode != HttpURLConnection.HTTP_OK) {

                has_error = true;
                totalBytesCopied = 0;

            } else {

                while ((mLength = input.read(dl_data)) > 0) {
                    if (isCancelled()) {
                        conection.disconnect();
                        break;
                    }

                    totalBytesCopied += mLength;

                    int progress = (int) Math.round(((double) totalBytesCopied / (double) lenghtOfFile) * 100);
                    if (progress > 0) {
                        publishProgress(progress, (int) totalBytesCopied, (int) lenghtOfFile);
                    }
                    // writing data to file
                    output.write(dl_data, 0, mLength);
                }
            }

            output.flush();
            output.close();
            input.close();
            conection.disconnect();

        } catch (Exception ex) {
            has_error = true;
            String exception = UtilFunctions.getStackTrace(ex);
            Log.e("doInBackground", "error4: " + ex.getMessage());
            totalBytesCopied = 0;
        }

        return totalBytesCopied;
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();

        cancel(true);
    }

    /**
     * Updating progress bar
     */
    @Override
    protected void onProgressUpdate(Integer... progress) {
        long totalBytesCopied = (long) progress[1];
        long mLength = (long) progress[2];

        String formatBytesCopied = mContext.getString(R.string.file_received) + " " + android.text.format.Formatter.formatFileSize(mContext, totalBytesCopied);

        String formatFileLength = mContext.getString(R.string.file_size) + " " + android.text.format.Formatter.formatFileSize(mContext, mLength);

        String progressText = formatBytesCopied + " / " + formatFileLength;

        this.customProgressDlg.setProgress(progress[0], formatBytesCopied, formatFileLength);

    }

    /**
     * After completing background task
     * Dismiss the progress dialog
     **/
    @Override
    protected void onPostExecute(Long res) {

        super.onPostExecute(res);

        if (res > 0 && !has_error) {
            File file = new File(dlPath + "/" + fileName);
            if (file.exists()) {
                if(ScheduleGDB1._DoUpdate)
                {
                    GanjoorDbBrowser1.DeletePoet(ScheduleGDB1._PoetID);
                }
                boolean imported = GanjoorDbBrowser1.ImportGdb(dlPath + "/" + fileName, ScheduleGDB1._Update_info);

                if (imported)
                {
                    activityCollection.GDBListAdaptor1.notifyNewImported(ScheduleGDB1._Pos, ScheduleGDB1._PoetID, DownloadAll);
                    activityCollection.ShowSuccessDownloadToast(DownloadAll);
                    try {
                        file.delete();
                    }
                    catch (Exception ex){
                        Log.e(TAG, "onPostExecute: "+ex.getMessage());
                    }

                    App globalVariable = (App) mContext.getApplicationContext();
                    globalVariable.setUpdatePoetList(true);

                }
            }
        }
        else
        {
            activityCollection.DownloadFailToast();
        }

        this.customProgressDlg.dismiss();
    }

}
