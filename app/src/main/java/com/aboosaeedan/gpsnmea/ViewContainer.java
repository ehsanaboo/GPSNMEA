package com.aboosaeedan.gpsnmea;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ehsan on 11/14/2017.
 */

public class ViewContainer {
    private final static int gpsLog_LINES = 10;
    private List<String> gpsLog;

    public String raw_message;

    public String
            status_c,
            date_c,
            satellite_c,
            time_c,
            speed_c,
            course_c,
            hdop_c,
            altitude_c,
            latitude_c,
            longitude_c,
            accuracy_c;

    public String
            status_l,
            date_l,
            satellite_l,
            time_l,
            speed_l,
            course_l,
            hdop_l,
            altitude_l,
            latitude_l,
            longitude_l,
            accuracy_l;


    public String
            read_status;

    public ViewContainer(){
        gpsLog = new ArrayList<String>();
        raw_message = "";
    }

    public void setGPSRaw(String str){

        if (str != null) {
            if (str.length() > 0) {
                gpsLog.add(str);
            }
            // remove the first line if log is too large
            if (gpsLog.size() >= gpsLog_LINES) {
                gpsLog.remove(0);
            }
            raw_message = "";
            for (String str2 : gpsLog) {
                raw_message += str2 + "\r\n";
            }
        }else{
            raw_message="";
        }
    }

    public void setGPSlocation_C(GPS_DATA gps_data){
        if (gps_data !=null) {
            status_c = String.valueOf(gps_data.status);
            date_c = String.valueOf(gps_data.date);
            satellite_c = String.valueOf(gps_data.satellite);
            time_c = String.valueOf(gps_data.time);
            speed_c = String.valueOf(gps_data.speed);
            course_c = String.valueOf(gps_data.course);
            hdop_c = String.valueOf(gps_data.hdop);
            altitude_c = String.valueOf(gps_data.altitude);
            latitude_c = String.valueOf(gps_data.latitude);
            longitude_c = String.valueOf(gps_data.longitude);
            accuracy_c = String.valueOf(gps_data.accuracy);
        }
    }

    public void setGPSlocation_L(GPS_DATA gps_data){
        if (gps_data !=null) {
            status_l = String.valueOf(gps_data.status);
            date_l = String.valueOf(gps_data.date);
            satellite_l = String.valueOf(gps_data.satellite);
            time_l = String.valueOf(gps_data.time);
            speed_l = String.valueOf(gps_data.speed);
            course_l = String.valueOf(gps_data.course);
            hdop_l = String.valueOf(gps_data.hdop);
            altitude_l = String.valueOf(gps_data.altitude);
            latitude_l = String.valueOf(gps_data.latitude);
            longitude_l = String.valueOf(gps_data.longitude);
            accuracy_l = String.valueOf(gps_data.accuracy);
        }
    }

    public void setReadStatus(int gps_status){
        switch (gps_status){
            case MainActivity.OPEN:
                read_status = "Openning";
                break;
            case MainActivity.EMPTY_BUFFER:
                read_status = "Clean up";
                break;
            case MainActivity.ERROR:
                read_status = "Error";
                break;
            case MainActivity.READ_GPS:
                read_status = "Reading";
                break;
        }

    }

}
