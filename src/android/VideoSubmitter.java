package com.fusionetics.plugins.bodymap;

import com.fusionetics.plugins.bodymap.ThisPlugin;
import com.fusionetics.plugins.bodymap.FusioneticsExercise;
import com.fusionetics.plugins.bodymap.ApiSettings;
import com.fusionetics.plugins.bodymap.UploadEventHandler;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.UnknownServiceException;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import android.util.Log;
import android.os.AsyncTask;
// import android.app.ProgressDialog;
import android.content.Context;


//https://stackoverflow.com/questions/11766878/sending-files-using-post-with-httpurlconnection
public class VideoSubmitter extends AsyncTask<Void, Long, Void> {
    private HttpURLConnection httpConn;
    private DataOutputStream request;
    private String requestURL;
    HashMap<String,String> headers;
    HashMap<String,String> formFields;
    HashMap<String,File> files;
    private final String boundary =  "*****";
    private final String crlf = "\r\n";
    private final String twoHyphens = "--";
    private final String endingSeparator = crlf + twoHyphens + boundary + twoHyphens + crlf;
    private final String formFieldSeparator = twoHyphens + boundary + crlf;
    // private ProgressDialog progressDialog;
    private UploadEventHandler context;
    long currentFileSize = 0;

    /**
     * This constructor initializes a new HTTP POST request with content type
     * is set to multipart/form-data
     *
     * @param requestURL
     * @throws IOException
     */
    public VideoSubmitter(String requestURL, HashMap<String,String> formFields, HashMap<String,File> files, HashMap<String,String> headers, UploadEventHandler context)
            throws IOException {

        this.requestURL = requestURL;
        this.formFields = formFields;
        this.files = files;
        this.headers = headers;
        this.context = context;
    }

    private void setupConnection() throws MalformedURLException, IOException, ProtocolException {
        // creates a unique boundary based on time stamp
        Log.d(ThisPlugin.TAG, "VideoSubmitter.setupConnection - url:" + requestURL);
        URL url = new URL(requestURL);
        httpConn = (HttpURLConnection) url.openConnection();
        httpConn.setUseCaches(false);
        httpConn.setDoOutput(true); // indicates POST method
        httpConn.setDoInput(true);
        httpConn.setRequestMethod("POST");

        addHeaders();

        Log.d(ThisPlugin.TAG, "VideoSubmitter.setupConnection - about to set up stream");
        httpConn.getOutputStream();

        request = new DataOutputStream(httpConn.getOutputStream());
    }

    private void addHeaders() {
        httpConn.setRequestProperty("Connection", "Keep-Alive");
        httpConn.setRequestProperty("Cache-Control", "no-cache");
        httpConn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + this.boundary);

        if(headers != null) {
            for(Map.Entry<String, String> entry : headers.entrySet()) {
                httpConn.setRequestProperty (entry.getKey(), entry.getValue());
            }
        }
    }

    private void addFormFieldsAndFiles() throws IOException {
        Log.d(ThisPlugin.TAG, "VideoSubmitter.addFormFieldsAndFiles");
        for(Map.Entry<String, String> entry : formFields.entrySet()) {
            addFormField(entry.getKey(), entry.getValue());
        }
        for(Map.Entry<String,File> file : files.entrySet()) {
            addFilePart(file.getKey(), file.getValue());
        }
    }

    @Override
    protected void onPostExecute(Void v) {
        context.OnCompleted();
    }

    @Override
    protected void onProgressUpdate(Long... progress) {
        for(long i: progress) {
            Log.d(ThisPlugin.TAG, "VideoSubmitter.onProgressUpdate - " + i);
            if(currentFileSize == 0)
                context.OnProgress(0);
            else
                context.OnProgress((int)(i*100/currentFileSize));
        }
    }

    @Override
    protected Void doInBackground(Void... s) {

        Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground");

        try {
            setupConnection();
            addFormFieldsAndFiles();

            Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - about to write ending stuff");

            request.writeBytes(this.endingSeparator);

            Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - bytes written");
            request.flush();
            request.close();
            Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - flushed and closed");

            // checks server's status code first
            int status = httpConn.getResponseCode();

            Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - status:"+Integer.toString(status));

            if (status == HttpURLConnection.HTTP_OK) {
                Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - returned OK response"); //  + response
            } else {
                throw new IOException("Server returned non-OK status: " + status);
            }
            String response = getResponse();

            httpConn.disconnect();
        }
        catch(Exception ex) {
            Log.e(ThisPlugin.TAG, "VideoSubmitter.doInBackground exception: " + ex.getMessage(), ex);
        }

        return null;
    }

    private String getResponse() {
        try {
            InputStream responseStream = new BufferedInputStream(httpConn.getInputStream());
            if(responseStream != null) {
                BufferedReader responseStreamReader = new BufferedReader(new InputStreamReader(responseStream));
    
                String line = "";
                StringBuilder stringBuilder = new StringBuilder();
        
                while ((line = responseStreamReader.readLine()) != null) {
                    stringBuilder.append(line).append("\n");
                }
                responseStreamReader.close();
        
                String response = stringBuilder.toString();
        
                Log.d(ThisPlugin.TAG, "VideoSubmitter.doInBackground - response:"+ response);
                return response;
            } else {
                Log.e(ThisPlugin.TAG, "VideoSubmitter.getResponse - connection didn't get a response stream");
            }
    
        } catch(IOException ioEx) {
            Log.e(ThisPlugin.TAG, "VideoSubmitter.getResponse - IOException thrown", ioEx);
        } catch (UnknownServiceException serviceEx) {
            Log.e(ThisPlugin.TAG, "VideoSubmitter.getResponse - UnknownServiceException thrown", serviceEx);
        }
        return null;
    }

    /**
     * Adds a form field to the request
     *
     * @param name  field name
     * @param value field value
     */
    private void addFormField(String name, String value)throws IOException  {
        Log.d(ThisPlugin.TAG, "addFormField: " + name + "=" + value);
        request.writeBytes(this.formFieldSeparator);
        request.writeBytes("Content-Disposition: form-data; name=\"" + name + "\""+ this.crlf);
        request.writeBytes("Content-Type: text/plain; charset=UTF-8" + this.crlf);
        request.writeBytes(this.crlf);
        request.writeBytes(value+ this.crlf);
        request.flush();
        Log.d(ThisPlugin.TAG, "addFormField done");
    }

    /**
     * Adds a upload file section to the request
     *
     * @param fieldName  name attribute in <input type="file" name="..." />
     * @param uploadFile a File to be uploaded
     * @throws IOException
     */
    private void addFilePart(String fieldName, File uploadFile)
            throws IOException {

        Log.d(ThisPlugin.TAG, "addFilePart: " + fieldName);

        String fileName = uploadFile.getName();
        request.writeBytes(this.formFieldSeparator);
        request.writeBytes("Content-Disposition: form-data; name=\"" +
                fieldName + "\";filename=\"" +
                fileName + "\"" + 
                this.crlf + this.crlf);

        long progress = 0;
        int bytesRead = 0;
        int bufSize = 8 * 1024;
        byte buf[] = new byte[bufSize];

        currentFileSize = uploadFile.length();
        Log.d(ThisPlugin.TAG, "Writing file: " + uploadFile.getName() + ", file size: " + uploadFile.length());

        BufferedInputStream bufInput = new BufferedInputStream(new FileInputStream(uploadFile));
        while ((bytesRead = bufInput.read(buf)) != -1) {
          // write output
          request.write(buf, 0, bytesRead);
          request.flush();
          progress += bytesRead;
          // update progress bar
          publishProgress(progress);
          try {
            Thread.sleep(1);
          } catch(InterruptedException e) {
            Log.d(ThisPlugin.TAG, "Thread.sleep threw an exception");
          }
        }
    }

}


