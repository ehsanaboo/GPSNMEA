package com.aboosaeedan.gpsnmea;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import android_serialport_api.SerialPort;

public class MainActivity extends AppCompatActivity {

    private final static float  gpsHorizontalAccuracy = (float)2.5;//m
    private final static float  gpsVelocityAccuracy = (float) 0.1;//m/s

    //ToDO change to serial port physical address
    private final static String serial_port_dev_gps = "/dev/ttyAMA2";
    private final static int    serial_port_baudrate_gps = 9600;

    private byte    gps_buffer_receive;
    private byte[]  gps_sentence_byte;
    private int     gps_sentence_bcount =0;
    private String  gps_sentence_str;
    private int     gps_sentence_length;

    //GPS_DATA serial port
    public final static int
            OPEN = 0,  ERROR = 2,
            READ_GPS = 3, EMPTY_BUFFER = 4;
    private final static int TIME_OUT = 10;//sec
    private final static int GPS_SUPERVISORY_SLEEP_BASE = 100;//millis
    private final static int TIME_OUT_COUNT = TIME_OUT*1000/GPS_SUPERVISORY_SLEEP_BASE;
    private final static int GPS_READ_SLEEP=10;
    protected SerialPort mPortGPS;
    protected InputStream mInputStreamGPS;
    private int gps_status=OPEN;
    private int gps_time_out_counter =0;
    private GPS_Read_Thread mGPS_Read_Thread;
    private GPS_Supervisory_Thread mGPS_Supervisory_Thread;
    private int GPS_SUPERVISORY_SLEEP=GPS_SUPERVISORY_SLEEP_BASE;
    private boolean gps_flag_GGA = false, gps_flag_RMC = false;

    //private Criteria criteria;
    private Location mocLocation;
    private LocationManager mocLocationManager;
    private String mocLocationProvider;
    private GPS_DATA mGPS_DATA_temp ,mGPS_DATA_Current, mGPS_DATA_Old;

    private ViewContainer mViewContainer;
    public TextView rawview_txt,read_status_txt;

    //GPS_DATA Current
    public TextView gps_validityC_txt,gps_timeC_txt,gps_dateC_txt,
            gps_latitudeC_txt,gps_longitudeC_txt,gps_altitudeC_txt,gps_hdopC_txt,gps_sateliteC_txt,gps_speedC_txt,gps_courseC_txt, gps_accuracyC_txt;
    //GPS_DATA Last Valid
    public TextView gps_validityL_txt,gps_timeL_txt,gps_dateL_txt,
            gps_latitudeL_txt,gps_longitudeL_txt,gps_altitudeL_txt,gps_hdopL_txt,gps_sateliteL_txt,gps_speedL_txt,gps_courseL_txt, gps_accuracyL_txt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init_location();
        //mocLocationProvider = LocationManager.GPS_PROVIDER;
        init_view();
        mViewContainer = new ViewContainer();

        mGPS_DATA_Current = new GPS_DATA();
        mGPS_DATA_Old = new GPS_DATA();
        mGPS_DATA_temp = new GPS_DATA();

        //init location service


        mGPS_Read_Thread = new GPS_Read_Thread();
        mGPS_Read_Thread.start();
        mGPS_Supervisory_Thread =new GPS_Supervisory_Thread();
        mGPS_Supervisory_Thread.start();
        gps_time_out_counter=0;

        //moveTaskToBack(true);

    }

    //Threads
    private class GPS_Supervisory_Thread extends Thread {

        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {

                switch (gps_status) {
                    case OPEN: //init & open serial port
                        gps_flag_GGA = false;
                        gps_flag_RMC = false;
                        gps_time_out_counter =0;
                        if (open_gps()) gps_status = EMPTY_BUFFER; else gps_status = ERROR;
                        GPS_SUPERVISORY_SLEEP = 5*GPS_SUPERVISORY_SLEEP_BASE;
                        break;
                    case EMPTY_BUFFER:
                        try {
                            if (mInputStreamGPS != null)
                                mInputStreamGPS.skip(mInputStreamGPS.available());
                            gps_status = READ_GPS;
                            gps_time_out_counter =0;
                        }catch (Exception e){
                            e.printStackTrace();
                        }
                        GPS_SUPERVISORY_SLEEP = GPS_SUPERVISORY_SLEEP_BASE;
                        break;
                    case ERROR: //wait and get ready to recover
                        close_gps();
                        gps_time_out_counter =0;
                        gps_status = OPEN;
                        GPS_SUPERVISORY_SLEEP = 10*GPS_SUPERVISORY_SLEEP_BASE;
                        break;
                    //normal start receiving data
                    case READ_GPS:
                        if (gps_time_out_counter++>TIME_OUT_COUNT) gps_status = ERROR;
                        GPS_SUPERVISORY_SLEEP = GPS_SUPERVISORY_SLEEP_BASE;
                        break;
                }

                //update mocLocation
                if (mGPS_DATA_Current.status) update_location(mGPS_DATA_Current);
                //update view
                mViewContainer.setGPSRaw(gps_sentence_str);
                mViewContainer.setGPSlocation_C(mGPS_DATA_Current);
                mViewContainer.setGPSlocation_L(mGPS_DATA_Old);
                mViewContainer.setReadStatus(gps_status);
                update_view();
                //sleep
                try {
                    sleep(GPS_SUPERVISORY_SLEEP);
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();//preserve the message
                    return;//Stop doing whatever I am doing and terminate
                }
            }
        }
    }

    private class GPS_Read_Thread extends Thread {
                                //
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                try{
                    if (receive_gps() && gps_status==READ_GPS){
                        //reset timeout counter
                        gps_time_out_counter =0;

                        //get what you want
                        GPS_Calculator(gps_sentence_str,gps_sentence_length);

                    }
                    //sleep
                    sleep(GPS_READ_SLEEP);
                } catch (Exception e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();//preserve the message
                    return;//Stop doing whatever I am doing and terminate
                }
            }
        }
    }


    //GPS_DATA Calculator
    private void GPS_Calculator(String message, int length) {

        double temp;

        String[] data_string = new String[30];
        try {
            if (mGPS_DATA_Current.status){
                mGPS_DATA_Old = (GPS_DATA) mGPS_DATA_Current.clone();
            }

            data_string = message.split(",");
            if (data_string[0].equals("GPGGA")) {
                //time UTC
                temp = Double.parseDouble(data_string[1])*100;
                mGPS_DATA_temp.time = (long) temp;
                //latitude DDD.DDDD
                temp = Float.parseFloat(data_string[2]);
                temp = (int) temp/100 + (temp%100)/60;
                if (data_string[3].equals("N")) mGPS_DATA_temp.latitude = temp;
                    else mGPS_DATA_temp.latitude = -temp;
                //longitude in DDD.DDDDD
                temp = Float.parseFloat(data_string[4]);
                temp = (int) temp/100 + (temp%100)/60;
                if (data_string[5].equals("E")) mGPS_DATA_temp.longitude = temp;
                else mGPS_DATA_temp.longitude = -temp;
                //satellite
                mGPS_DATA_temp.satellite = Integer.parseInt(data_string[7]);
                //HDOP
                mGPS_DATA_temp.hdop = (float) Double.parseDouble(data_string[8]);
                //altitude
                mGPS_DATA_temp.altitude = Double.parseDouble(data_string[9]);
                //accuracy
                mGPS_DATA_temp.accuracy = mGPS_DATA_temp.hdop*gpsHorizontalAccuracy;//meter
                gps_flag_GGA = true;
            }else if (data_string[0].equals("GPRMC")) {
                mGPS_DATA_temp.status = data_string[2].equals("A");
                temp = Double.parseDouble(data_string[7]);
                temp = temp*0.514444;//knot to m/s
                if (temp<gpsVelocityAccuracy) mGPS_DATA_temp.speed =0; else
                    mGPS_DATA_temp.speed = (float) temp;
                mGPS_DATA_temp.date = (int) Double.parseDouble(data_string[9]);
                try{
                    mGPS_DATA_temp.course = (float) Double.parseDouble(data_string[8]);
                }catch (Exception e){
                    mGPS_DATA_temp.course = 0;
                }
                gps_flag_RMC = true;

            }
            if (gps_flag_GGA && gps_flag_RMC) {
                mGPS_DATA_Current = (GPS_DATA) mGPS_DATA_temp.clone();
                gps_flag_GGA = false;
                gps_flag_RMC = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //GPS_DATA hardware related
    private boolean receive_gps() {
        if (mInputStreamGPS != null){
            try {
                gps_buffer_receive = byte_reader_gps(2000);
                if (gps_buffer_receive == 36){//$
                    reset_gps_sentence();
                    while (gps_buffer_receive != 13) {//\r\n
                        gps_buffer_receive = byte_reader_gps(2000);
                        gps_sentence_byte[gps_sentence_bcount] = gps_buffer_receive;
                        gps_sentence_bcount++;
                    }
                    if(crc_gps(gps_sentence_byte, gps_sentence_bcount, new String(gps_sentence_byte, gps_sentence_bcount - 3, 2))){
                        gps_sentence_length = gps_sentence_bcount -1;
                        gps_sentence_str = new String(gps_sentence_byte, 0, gps_sentence_length -3);
                        return true;
                    }
                }



            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    };

    private byte byte_reader_gps(int delay){
        final Timer timer = new Timer();

        TimerTask timeout_reached = new TimerTask() {

            @Override
            public void run() {
                gps_status = ERROR;
            }
        };

        try {
            byte[] buffer = new byte[5];

            timer.schedule(timeout_reached, delay);
            timer.purge();
            if (mInputStreamGPS != null) mInputStreamGPS.read(buffer,0,1);
            timer.cancel();
            return buffer[0];
        } catch (IOException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private boolean open_gps(){
        try {
                mPortGPS = new SerialPort(new File(serial_port_dev_gps), serial_port_baudrate_gps, 0);
                mInputStreamGPS = mPortGPS.getInputStream();
                return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void close_gps(){
        try {
            if (mPortGPS!=null) {
                mPortGPS.close();
                mPortGPS = null;
            }
            if (mInputStreamGPS!=null) {
                mInputStreamGPS.close();
                mInputStreamGPS = null;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reset_gps_sentence(){
        gps_sentence_bcount =0;
        gps_sentence_byte = new byte[100];
    }

    private boolean crc_gps(byte[] data , int size, String temp_str){
        byte checksum_calc=0;
        int checksum_red=0;

        try {
            checksum_red = Integer.parseInt(temp_str, 16);// string to hex
        } catch (NumberFormatException e) {
            return false;
        }
        for (int i = 0; i< size-4; i++){
            checksum_calc ^= data[i];
        }

        if (checksum_red == checksum_calc) {
            return true;
        }

        return false;
    }

    //mocLocation
    private void init_location() {
        mocLocationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        //criteria = new Criteria();
        //criteria.setAccuracy( Criteria.ACCURACY_FINE );
        mocLocationProvider = LocationManager.GPS_PROVIDER;
        try {
            mocLocationManager.addTestProvider(mocLocationProvider, false, true,
                    false, false, true, true, true, 0, 5);
        }catch (Exception e){
            e.printStackTrace();
        }
            mocLocationManager.setTestProviderEnabled(mocLocationProvider, true);
            mocLocation = new Location(mocLocationProvider);
            mocLocation.setAccuracy(gpsHorizontalAccuracy);
    }

    private void update_location(GPS_DATA gps_data){
        mocLocation.setAccuracy(gps_data.accuracy);
        mocLocation.setLatitude(gps_data.latitude);
        mocLocation.setLongitude(gps_data.longitude);
        mocLocation.setAltitude(gps_data.altitude);
        mocLocation.setSpeed(gps_data.speed);
        mocLocation.setBearing(gps_data.course);
        mocLocation.setTime(gps_data.time);
        mocLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        mocLocationManager.setTestProviderLocation( mocLocationProvider, mocLocation);
    }

    //display functions
    private void init_view(){
        rawview_txt         = (TextView) findViewById(R.id.txt_rawview);
        rawview_txt.setMovementMethod(new ScrollingMovementMethod());
        read_status_txt     =(TextView)  findViewById(R.id.txt_READStatus);

        gps_validityC_txt   = (TextView) findViewById(R.id.txt_GPSStatusC);
        gps_timeC_txt       = (TextView) findViewById(R.id.txt_GPSTimeC);
        gps_dateC_txt       = (TextView) findViewById(R.id.txt_GPSDateC);
        gps_latitudeC_txt   = (TextView) findViewById(R.id.txt_GPSLatitudeC);
        gps_longitudeC_txt  = (TextView) findViewById(R.id.txt_GPSLongitudeC);
        gps_altitudeC_txt   = (TextView) findViewById(R.id.txt_GPSAltitudeC);
        gps_hdopC_txt       = (TextView) findViewById(R.id.txt_GPSHDOPC);
        gps_sateliteC_txt   = (TextView) findViewById(R.id.txt_GPSSateliteC);
        gps_speedC_txt      = (TextView) findViewById(R.id.txt_GPSSpeedC);
        gps_courseC_txt     = (TextView) findViewById(R.id.txt_GPSCourseC);
        gps_accuracyC_txt   = (TextView) findViewById(R.id.txt_GPSAccuracyC);

        gps_validityL_txt   = (TextView) findViewById(R.id.txt_GPSStatusL);
        gps_timeL_txt       = (TextView) findViewById(R.id.txt_GPSTimeL);
        gps_dateL_txt       = (TextView) findViewById(R.id.txt_GPSDateL);
        gps_latitudeL_txt   = (TextView) findViewById(R.id.txt_GPSLatitudeL);
        gps_longitudeL_txt  = (TextView) findViewById(R.id.txt_GPSLongitudeL);
        gps_altitudeL_txt   = (TextView) findViewById(R.id.txt_GPSAltitudeL);
        gps_hdopL_txt       = (TextView) findViewById(R.id.txt_GPSHDOPL);
        gps_accuracyL_txt   = (TextView) findViewById(R.id.txt_GPSAccuracyL);
        gps_sateliteL_txt   = (TextView) findViewById(R.id.txt_GPSSateliteL);
        gps_speedL_txt      = (TextView) findViewById(R.id.txt_GPSSpeedL);
        gps_courseL_txt     = (TextView) findViewById(R.id.txt_GPSCourseL);
    }

    private void update_view(){
        runOnUiThread(new Runnable() {
            public void run() {
                rawview_txt         .setText(mViewContainer.raw_message);

                read_status_txt     .setText(mViewContainer.read_status);

                gps_validityC_txt   .setText(mViewContainer.status_c);
                gps_timeC_txt       .setText(mViewContainer.time_c);
                gps_dateC_txt       .setText(mViewContainer.date_c);
                gps_latitudeC_txt   .setText(mViewContainer.latitude_c);
                gps_longitudeC_txt  .setText(mViewContainer.longitude_c);
                gps_altitudeC_txt   .setText(mViewContainer.altitude_c);
                gps_hdopC_txt       .setText(mViewContainer.hdop_c);
                gps_accuracyC_txt   .setText(mViewContainer.accuracy_c);
                gps_sateliteC_txt   .setText(mViewContainer.satellite_c);
                gps_speedC_txt      .setText(mViewContainer.speed_c);
                gps_courseC_txt     .setText(mViewContainer.course_c);
                gps_accuracyC_txt   .setText(mViewContainer.accuracy_c);

                gps_validityL_txt   .setText(mViewContainer.status_l);
                gps_timeL_txt       .setText(mViewContainer.time_l);
                gps_dateL_txt       .setText(mViewContainer.date_l);
                gps_latitudeL_txt   .setText(mViewContainer.latitude_l);
                gps_longitudeL_txt  .setText(mViewContainer.longitude_l);
                gps_altitudeL_txt   .setText(mViewContainer.altitude_l);
                gps_hdopL_txt       .setText(mViewContainer.hdop_l);
                gps_accuracyL_txt   .setText(mViewContainer.accuracy_l);
                gps_sateliteL_txt   .setText(mViewContainer.satellite_l);
                gps_speedL_txt      .setText(mViewContainer.speed_l);
                gps_courseL_txt     .setText(mViewContainer.course_l);
                gps_accuracyL_txt   .setText(mViewContainer.accuracy_l);
            }
        });

    }

    private void DisplayError(final int resourceId) {
        runOnUiThread(new Runnable() {
            public void run() {
                AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this);
                b.setTitle("Error");
                b.setMessage(resourceId);
                b.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                });
                b.show();
            }
        });
    }

    //finishings
    public void endApp(){
        try {
            if (mGPS_Supervisory_Thread != null){
                mGPS_Supervisory_Thread.interrupt();
                mGPS_Supervisory_Thread = null;
            }

            if (mGPS_Read_Thread != null) {
                mGPS_Read_Thread.interrupt();
                mGPS_Read_Thread = null;
            }

            close_gps();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

//    @Override
//    public void onBackPressed(){
//        super.onBackPressed();
//        endApp();
//        finish();
//        android.os.Process.killProcess(android.os.Process.myPid());
//        System.exit(0);
//    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        endApp();
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

//    @Override
//    public void onStop(){
//        super.onStop();
//        endApp();
//        finish();
//        android.os.Process.killProcess(android.os.Process.myPid());
//        System.exit(0);
//    }

}
