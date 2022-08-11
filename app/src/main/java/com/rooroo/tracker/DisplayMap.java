package com.rooroo.tracker;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.ortiz.touchview.TouchImageView;

public class DisplayMap extends AppCompatActivity implements SensorEventListener {
    private static final int PHYSICAL_ACTIVITY = 1;
    SensorManager sensorManager;
    TextView tv_steps;
    TextView tv_direction;

    private Bitmap bitmap;
    private Canvas canvas;
    private final Paint paint = new Paint();
    private final Paint tip = new Paint();

    int stepCount = 0;

    long timeWhenPaused = 0;

    boolean paused = false;

    int dir = 0;

    Context mContext;
    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    ImageView dirArrow;

    int x = 2500, y = 2500;
    int lastX = 2500, lastY = 2500;

    Chronometer chronometer;

    @SuppressLint({"WrongViewCast", "WakelockTimeout"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_display_map);

        tv_steps = findViewById(R.id.tv_steps);
        tv_direction = findViewById(R.id.tv_direction);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        chronometer = findViewById(R.id.tv_time);
        chronometer.setFormat("%s");
        chronometer.setBase(SystemClock.elapsedRealtime());

        mContext = getApplicationContext();
        powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        wakeLock =  powerManager.newWakeLock(PARTIAL_WAKE_LOCK,"motionDetection:keepAwake");

        dirArrow = findViewById(R.id.direction_arrow);

        Sensor magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        Sensor accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(listener, accelerometerSensor,SensorManager.SENSOR_DELAY_NORMAL);

        if(ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_DENIED){
            requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, PHYSICAL_ACTIVITY);
        }
        wakeLock.acquire();
        chronometer.start();
    }

    private void drawMap(TouchImageView imageView, int x1, int y1, int x2, int y2){
        if (bitmap == null){
            bitmap = Bitmap.createBitmap(5000, 5000, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(bitmap);
            tip.setColor(Color.WHITE);
            paint.setAntiAlias(true);
            tip.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
            tip.setStyle(Paint.Style.FILL_AND_STROKE);
            tip.setStrokeWidth(8);
            paint.setStrokeWidth(8);
            canvas.drawCircle(x, y, 10, tip);
            paint.setColor(Color.parseColor("#1E88E5"));
            paint.setStrokeCap(Paint.Cap.ROUND);
            tip.setStrokeCap(Paint.Cap.ROUND);
            imageView.setMaxZoom(15);
            imageView.setZoom(5, 0.5f, 0.5f);
        }
        canvas.drawLine(lastX, lastY, x1, y1, paint);
        canvas.drawLine(x1, y1, x2, y2, tip);
        imageView.setImageBitmap(bitmap);
    }

    public void addLocation(View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Add a location");
        final EditText input = new EditText(this);
        input.setHint("Location");
        alert.setView(input);
        alert.setPositiveButton("Add", (dialog, whichButton) -> {
            String value = input.getText().toString();
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(33);
            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float halfTextLength = paint.measureText(value) + 3;
            RectF background = new RectF((int) (x - 3), (int) (y + fontMetrics.top), (int) (x + halfTextLength), (int) (y + fontMetrics.bottom));
            canvas.drawRoundRect(background, 6, 6, paint);
            paint.setColor(Color.parseColor("#1E88E5"));
            canvas.drawText(value, x, y, paint);
        });

        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
        });
        alert.show();
    }

    public void onClick(View view) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle("Confirm Reset");
        alert.setPositiveButton("Reset", (dialog, whichButton) -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            wakeLock.release();
            startActivity(i);
        });
        alert.setNegativeButton("Cancel", (dialog, whichButton) -> {
        });
        alert.show();
    }

    @SuppressLint("SetTextI18n")
    public void onPause(View view) {
        Button pause = (Button) findViewById(R.id.pause_button);
        if (!paused) {
            paused = true;
            timeWhenPaused = chronometer.getBase() - SystemClock.elapsedRealtime();
            chronometer.stop();
            pause.setText("RESUME");
        } else {
            paused = false;
            chronometer.setBase(SystemClock.elapsedRealtime() + timeWhenPaused);
            chronometer.start();
            pause.setText("PAUSE");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(listener);
        }
    }

    final SensorEventListener listener = new SensorEventListener() {
        float[] accelerometerValues = new float[3];
        float[] magneticValues = new float[3];
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (!paused) {
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) accelerometerValues = event.values.clone();
                else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) magneticValues = event.values.clone();
                float[] R = new float[9];
                float[] values = new float[3];
                SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticValues);
                SensorManager.getOrientation(R, values);
                dir = (int) Math.toDegrees(values[0]);
                if (dir < 0) dir = 360 + dir;
                tv_direction.setText(String.valueOf(dir));
                dirArrow.animate().rotation((float) dir).setDuration(60).start();
            }
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };


    @Override
    protected void onResume() {
        super.onResume();
        Sensor countSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (countSensor != null) {
            sensorManager.registerListener((SensorEventListener) this, countSensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Toast.makeText(this, "Sensor not found", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent)  {
        if (!paused) {
            stepCount++;
            tv_steps.setText(String.valueOf(stepCount));
            System.out.println("[" + stepCount + ", " + tv_direction.getText() + "]");
            System.out.println("X-START: " + x);
            System.out.println("Y-START: " + y);
            int newX = (int) (x + (3 * Math.cos(Math.toRadians(dir - 90))));
            int newY = (int) (y + (3 * Math.sin(Math.toRadians(dir - 90))));
            System.out.println("X-END: " + x);
            System.out.println("Y-END: " + y);
            drawMap(findViewById(R.id.tiv_map), x, y, newX, newY);
            lastX = x;
            lastY = y;
            x = newX;
            y = newY;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}