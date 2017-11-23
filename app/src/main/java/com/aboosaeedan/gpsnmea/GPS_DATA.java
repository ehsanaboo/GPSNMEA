package com.aboosaeedan.gpsnmea;

/**
 * Created by Ehsan on 11/14/2017.
 */

public class GPS_DATA implements Cloneable{

    public boolean
            status=false;
    public int
            date=0,
            satellite=0;
    public long
            time=0;
    public float
            speed=0,
            course=0,
            hdop=0,
            accuracy=0;

    public double
            altitude=0,
            latitude=0,
            longitude=0;

    public GPS_DATA() {

        date=0;
        time = 0;
        status= false;
        satellite=0;
        speed=0;
        latitude=0;
        longitude=0;
        altitude=0;
        course=0;
        hdop=0;
        accuracy=0;

    }

    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}